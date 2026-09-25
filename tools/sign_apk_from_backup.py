#!/usr/bin/env python3
"""Sign using a verified local backup; decrypted credentials stay in memory.

Requires Python 3, Java 21, GPG (configured pinentry), Android SDK build-tools
34.0.0 and a restored JKS. See docs/DRILL_keystore_restore.md for usage.
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

parser = argparse.ArgumentParser(description='Sign a locally built APK with a restored key and a verified local GPG backup. Only GPG prompts for a password; signing passwords are not saved.')
parser.add_argument('apk', type=Path, help='Unsigned APK to sign')
parser.add_argument('output', type=Path, help='New signed APK; must not exist')
parser.add_argument('--sha256', required=True, help='Expected SHA-256 of the unsigned input APK')
parser.add_argument('--backup', required=True, type=Path, help='Local encrypted .tar.gpg backup')
parser.add_argument('--backup-sha256', required=True, help='Expected backup SHA-256 from a trusted private record')
parser.add_argument('--keystore', required=True, type=Path, help='Restored JKS to verify and use')
args = parser.parse_args()
KEYSTORE = args.keystore.expanduser().resolve()
BACKUP = args.backup.expanduser().resolve()
APK = args.apk.resolve()
OUTPUT = args.output.resolve()
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
    raise SystemExit('Set ANDROID_HOME to an SDK with build-tools 34.0.0.')
SIGNER = str(Path(sdk) / 'build-tools/34.0.0/apksigner')
CERT_SHA = '5fa81cd2fd62cbdd3580076b941c1711b4ddc625609b5c80ec7c135a56e3b98a'
INPUT_SHA = args.sha256.lower()
BACKUP_SHA = args.backup_sha256.lower()
for option, value in (('--sha256', INPUT_SHA), ('--backup-sha256', BACKUP_SHA)):
    if len(value) != 64 or any(c not in '0123456789abcdef' for c in value):
        raise SystemExit(option + ' requires 64 hexadecimal characters.')

def backup_passwords():
    encrypted = BACKUP.read_bytes()
    if hashlib.sha256(encrypted).hexdigest() != BACKUP_SHA:
        raise SystemExit('Archive checksum mismatch; stopped.')
    print('Opening the backup: GPG will request the .tar.gpg ARCHIVE password.', flush=True)
    decrypted = subprocess.run(['gpg', '--decrypt'], input=encrypted,
                               capture_output=True, timeout=600)
    if decrypted.returncode:
        raise SystemExit('Could not reopen the archive; the restored JKS is unchanged.')
    with tarfile.open(fileobj=io.BytesIO(decrypted.stdout), mode='r:') as archive:
        print('Archive contents:', ', '.join(m.name for m in archive.getmembers()), flush=True)
        keys = [m for m in archive.getmembers() if m.isfile() and Path(m.name).name == 'medzuslovjansky-release.jks']
        configs = [m for m in archive.getmembers() if m.isfile() and Path(m.name).name == 'keystore.properties']
        if len(keys) != 1 or archive.extractfile(keys[0]).read() != KEYSTORE.read_bytes():
            raise SystemExit('The restored JKS does not exactly match the backup.')
        if len(configs) != 1:
            raise SystemExit('The backup must contain exactly one signing configuration.')
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
        raise SystemExit('The backup contains an incomplete signing configuration.')
    values = [base64.b64decode(line, validate=True) for line in lines]
    if values[0] != b'medzuslovjansky' or any(b'\n' in v or b'\r' in v or b'\0' in v for v in values[1:]):
        raise SystemExit('The backup contains an invalid signing configuration.')
    print('Signing configuration found in the backup. Passwords will be used only in memory.', flush=True)
    return values[1], values[2]

def entries(filename):
    with zipfile.ZipFile(filename) as archive:
        return {n: hashlib.sha256(archive.read(n)).digest() for n in archive.namelist()}

if not KEYSTORE.is_file():
    raise SystemExit('The restored keystore is missing.')
if hashlib.sha256(APK.read_bytes()).hexdigest() != INPUT_SHA:
    raise SystemExit('Input APK SHA-256 mismatch; stopped before signing.')
if OUTPUT.exists():
    raise SystemExit('The output file already exists; it will not be overwritten.')

archived_passwords = backup_passwords()
store_password = archived_passwords[0]
cert = subprocess.run(['keytool', '-exportcert', '-keystore', str(KEYSTORE),
                       '-alias', 'medzuslovjansky'], input=store_password+b'\n',
                      capture_output=True, timeout=30)
if cert.returncode:
    details = (cert.stdout + cert.stderr).lower()
    reason = 'password rejected' if b'password was incorrect' in details or b'password verification failed' in details else 'keytool error'
    raise SystemExit('Could not open the JKS: ' + reason + '. The APK was not signed.')
if hashlib.sha256(cert.stdout).hexdigest() != CERT_SHA:
    raise SystemExit('The JKS certificate fingerprint does not match the release. The APK was not signed.')
print('JKS: password accepted, certificate fingerprint matches.', flush=True)
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
    raise SystemExit('Signing failed; check the key password. No credentials were saved.')
verification = subprocess.run([SIGNER, 'verify', '--verbose', '--print-certs', str(SIGNED)],
                              capture_output=True, text=True, timeout=30)
if verification.returncode or f'Signer #1 certificate SHA-256 digest: {CERT_SHA}' not in verification.stdout:
    work.cleanup()
    raise SystemExit('Signature verification failed; the test output was removed.')
if entries(APK) != entries(SIGNED):
    work.cleanup()
    raise SystemExit('APK contents changed beyond the signature; the test output was removed.')
try:
    os.link(SIGNED, OUTPUT)
except FileExistsError:
    raise SystemExit('The output file appeared during signing; it was not overwritten.')
finally:
    work.cleanup()
print(verification.stdout)
print('ZIP-entry contents unchanged; signed with the recovered key.')
print('Output:', OUTPUT)
print('SHA-256:', hashlib.sha256(OUTPUT.read_bytes()).hexdigest())
print('No passwords were saved and no APK was published.')
