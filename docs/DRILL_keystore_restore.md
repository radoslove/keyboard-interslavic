# Signing-key recovery

Verify that an independently stored encrypted backup can restore the project's
signing identity on another machine. Keep the actual backup locations, archive
checksums, device inventory and recovery results in private operational records.

## Prerequisites

Use Java 21, Android platform 34, build-tools 34.0.0, Python 3, GPG with a working
pinentry program, and Bash 3.2 or later. Run from the repository root:

```bash
./tools/bootstrap_build_machine.sh
```

Without arguments, the bootstrap only checks prerequisites. A missing signing
configuration is expected on machines that build unsigned APKs.

Obtain the encrypted `.tar.gpg` backup independently of the machine being
recovered. Get its expected SHA-256 from a trusted private record, rather than
from the downloaded file itself. Keep both the archive and recovered keystore
outside the repository. Enter passwords only in the local GPG/keytool prompt.

## Restore

The variables below refer to local paths and a privately recorded checksum:

```bash
./tools/bootstrap_build_machine.sh \
  --key "$BACKUP_FILE" \
  --archive-sha256 "$BACKUP_SHA256"
```

The bootstrap verifies the archive hash, restores the project JKS under
`$HOME/keystores/`, sets restrictive permissions and cleans its temporary files.
It does not download backups or contain infrastructure addresses.

The bootstrap may create an **empty** `android-app/keystore.properties` scaffold.
For unsigned builds followed by separate signing, remove only that empty scaffold.
Preserve any pre-existing configuration that already contains signing credentials.

The archive's `keystore.properties` can contain a path from its original machine.
The signing helper below uses the explicitly supplied local JKS and checks that
its contents match the archived key; it does not use that original path.

## Build and sign

Build the intended source revision with the project's documented toolchain:

```bash
cd android-app
./gradlew --no-daemon clean assembleRelease
```

Back at the repository root, calculate the unsigned input hash using
`shasum -a 256` or `sha256sum`. Choose an output file that does not already exist:

```bash
python3 tools/sign_apk_from_backup.py \
  android-app/app/build/outputs/apk/release/app-release-unsigned.apk \
  dist/recovery/signed-test.apk \
  --sha256 "$UNSIGNED_APK_SHA256" \
  --backup "$BACKUP_FILE" \
  --backup-sha256 "$BACKUP_SHA256" \
  --keystore "$RESTORED_KEYSTORE"
```

The helper verifies the encrypted archive and local JKS, decrypts signing
credentials in memory, and passes passwords to the signing tools over standard
input. It checks the certificate before signing and verifies the resulting
signature and unchanged ZIP-entry contents. It does not publish anything.

## Verify the result

The public signing certificate SHA-256 is:

```text
5fa81cd2fd62cbdd3580076b941c1711b4ddc625609b5c80ec7c135a56e3b98a
```

Check the signed output independently:

```bash
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --verbose --print-certs \
  dist/recovery/signed-test.apk
```

Successful signing proves key recovery. Reproducibility is a separate check:
compare a rebuild with a published APK from the **same source revision**, using
the appropriate signature-transfer procedure. The bootstrap's `--build` uses
the reference hash in [INSTALL.md](INSTALL.md); confirm that it matches the
revision being built before using that comparison.

## Cleanup

Keep signing material and credentials out of Git. Remove temporary decrypted
archives and test files. If a recovery machine will retain the key, protect its
storage and keep recovery credentials separately. Record machine-specific
results privately; public release notes need only the public artifact hashes,
signing fingerprint and reproducibility results.
