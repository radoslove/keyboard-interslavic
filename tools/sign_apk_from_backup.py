#!/usr/bin/env python3
"""Sign using the pinned hetz backup; decrypted credentials stay in memory.

Requires Python 3, Java 21, GPG (configured pinentry), SSH access to hetz,
Android SDK build-tools 34.0.0 and an already restored ~/keystores JKS.
See docs/MC_TODO_2026-09-23.md for the recovery report and usage.
"""
import hashlib
import os
import argparse
import base64
import io
import tarfile
import tempfile
from pathlib import Path
import subprocess
import zipfile

parser = argparse.ArgumentParser(description='Sign a locally built APK with the restored key, using signing passwords from the verified GPG backup on hetz. Only GPG prompts for a password; signing passwords are not saved.')
parser.add_argument('apk', type=Path, help='Unsigned APK to sign')
parser.add_argument('output', type=Path, help='New signed APK; must not exist')
parser.add_argument('--sha256', required=True, help='Expected SHA-256 of the unsigned input APK')
args = parser.parse_args()
KEYSTORE = Path.home() / 'keystores/medzuslovjansky-release.jks'
APK = args.apk.resolve()
OUTPUT = args.output.resolve()
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
    raise SystemExit('Ustaw ANDROID_HOME na SDK z build-tools 34.0.0.')
SIGNER = str(Path(sdk) / 'build-tools/34.0.0/apksigner')
CERT_SHA = '5fa81cd2fd62cbdd3580076b941c1711b4ddc625609b5c80ec7c135a56e3b98a'
INPUT_SHA = args.sha256.lower()
if len(INPUT_SHA) != 64 or any(c not in '0123456789abcdef' for c in INPUT_SHA):
    raise SystemExit('--sha256 wymaga 64 znakow szesnastkowych.')

def backup_passwords():
    encrypted = subprocess.run(['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10',
        'claude@100.91.132.98', 'cat ~/keystore-backup/medzuslovjansky-keystore-backup.tar.gpg'],
        capture_output=True, timeout=45)
    if encrypted.returncode:
        raise SystemExit('Nie udalo sie pobrac backupu z hetz.')
    if hashlib.sha256(encrypted.stdout).hexdigest() != '01a836bc3fd60df222334d058eabffd0b735806c3a77b4ecc1fff853d6f43728':
        raise SystemExit('Niezgodna suma archiwum; przerwano.')
    print('Otwieram backup: okno GPG pyta o haslo ARCHIWUM .tar.gpg.', flush=True)
    decrypted = subprocess.run(['gpg', '--decrypt'], input=encrypted.stdout,
                               capture_output=True, timeout=600)
    if decrypted.returncode:
        raise SystemExit('Nie udalo sie ponownie otworzyc archiwum; odtworzony JKS pozostaje bez zmian.')
    with tarfile.open(fileobj=io.BytesIO(decrypted.stdout), mode='r:') as archive:
        print('Pliki w archiwum:', ', '.join(m.name for m in archive.getmembers()), flush=True)
        keys = [m for m in archive.getmembers() if m.isfile() and Path(m.name).name == 'medzuslovjansky-release.jks']
        configs = [m for m in archive.getmembers() if m.isfile() and Path(m.name).name == 'keystore.properties']
        if len(keys) != 1 or archive.extractfile(keys[0]).read() != KEYSTORE.read_bytes():
            raise SystemExit('Odtworzony JKS nie odpowiada dokladnie plikowi z backupu.')
        if len(configs) != 1:
            raise SystemExit('Niejednoznaczna konfiguracja w backupie.')
        properties = archive.extractfile(configs[0]).read()
    # Use java.util.Properties to preserve Java escaping and encoding exactly.
    with tempfile.TemporaryDirectory(prefix='keyboard-properties-parser-') as temporary:
        source = Path(temporary) / 'ReadSigningProperties.java'
        source.write_text("""import java.util.*;
import java.nio.charset.StandardCharsets;
class ReadSigningProperties {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.load(System.in);
        for (String key : new String[]{"keyAlias", "storePassword", "keyPassword"}) {
            String value = properties.getProperty(key);
            if (value == null || value.isEmpty()) System.exit(2);
            System.out.println(Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
""")
        parsed = subprocess.run(['java', str(source)], input=properties, capture_output=True, timeout=30)
    lines = parsed.stdout.splitlines()
    if parsed.returncode or len(lines) != 3:
        raise SystemExit('Backup zawiera niekompletna konfiguracje podpisu.')
    values = [base64.b64decode(line, validate=True) for line in lines]
    if values[0] != b'medzuslovjansky' or any(b'\n' in v or b'\r' in v or b'\0' in v for v in values[1:]):
        raise SystemExit('Nieprawidlowa konfiguracja podpisu w backupie.')
    print('Konfiguracja podpisu jest w backupie. Hasla zostana uzyte tylko w pamieci.', flush=True)
    return values[1], values[2]

def entries(filename):
    with zipfile.ZipFile(filename) as archive:
        return {n: hashlib.sha256(archive.read(n)).digest() for n in archive.namelist()}

if not KEYSTORE.is_file():
    raise SystemExit('Brak odtworzonego keystore.')
if hashlib.sha256(APK.read_bytes()).hexdigest() != INPUT_SHA:
    raise SystemExit('SHA-256 wejsciowego APK nie zgadza sie; przerwano przed podpisaniem.')
if OUTPUT.exists():
    raise SystemExit('Plik wynikowy juz istnieje; nie zostanie nadpisany.')

archived_passwords = backup_passwords()
store_password = archived_passwords[0]
cert = subprocess.run(['keytool', '-exportcert', '-keystore', str(KEYSTORE),
                       '-alias', 'medzuslovjansky'], input=store_password+b'\n',
                      capture_output=True, timeout=30)
if cert.returncode:
    details = (cert.stdout + cert.stderr).lower()
    reason = 'haslo odrzucone' if b'password was incorrect' in details or b'password verification failed' in details else 'blad keytool'
    raise SystemExit('Nie udalo sie otworzyc JKS: ' + reason + '. Nie podpisano APK.')
if hashlib.sha256(cert.stdout).hexdigest() != CERT_SHA:
    raise SystemExit('Odcisk certyfikatu JKS nie zgadza sie z wydaniem. Nie podpisano APK.')
print('JKS: haslo zaakceptowane, odcisk certyfikatu zgodny.', flush=True)
key_password = archived_passwords[1]
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
work = tempfile.TemporaryDirectory(prefix='.signing-', dir=OUTPUT.parent)
SIGNED = Path(work.name) / 'signed.apk'
result = subprocess.run([SIGNER, 'sign', '--ks', str(KEYSTORE), '--ks-key-alias', 'medzuslovjansky',
    '--ks-pass', 'stdin', '--key-pass', 'stdin', '--v1-signing-enabled', 'false',
    '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'false', '--v4-signing-enabled', 'false',
    '--debuggable-apk-permitted', 'false', '--out', str(SIGNED), str(APK)],
    input=store_password+b'\n'+key_password+b'\n', capture_output=True, timeout=60)
del store_password, key_password, archived_passwords
if result.returncode:
    work.cleanup()
    raise SystemExit('Podpisanie nie powiodlo sie; sprawdz haslo klucza. Sekrety nie zostaly zapisane.')
verification = subprocess.run([SIGNER, 'verify', '--verbose', '--print-certs', str(SIGNED)],
                              capture_output=True, text=True, timeout=30)
if verification.returncode or f'Signer #1 certificate SHA-256 digest: {CERT_SHA}' not in verification.stdout:
    work.cleanup()
    raise SystemExit('Weryfikacja podpisu nie powiodla sie; usunieto wynik testu.')
if entries(APK) != entries(SIGNED):
    work.cleanup()
    raise SystemExit('Zawartosc APK zmienila sie poza podpisem; usunieto wynik testu.')
try:
    os.link(SIGNED, OUTPUT)
except FileExistsError:
    raise SystemExit('Plik wynikowy pojawil sie w trakcie podpisywania; nie zostal nadpisany.')
finally:
    work.cleanup()
print(verification.stdout)
print('Zawartosc wpisow ZIP bez zmian; podpisano odzyskanym kluczem.')
print('Wynik:', OUTPUT)
print('SHA-256:', hashlib.sha256(OUTPUT.read_bytes()).hexdigest())
print('Nie zapisano hasel ani nie opublikowano APK.')
