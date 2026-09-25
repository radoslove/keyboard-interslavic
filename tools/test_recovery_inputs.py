#!/usr/bin/env python3
"""Validate recovery inputs with synthetic files; never open the real key."""

import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class RecoveryInputs(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='keyboard-recovery-inputs-')
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.apk = self.base / 'input.apk'
        self.key = self.base / 'synthetic.jks'
        self.backup = self.base / 'synthetic.tar.gpg'
        self.output = self.base / 'output.apk'
        self.apk.write_bytes(b'synthetic unsigned input')
        self.key.write_bytes(b'synthetic keystore')
        self.backup.write_bytes(b'synthetic encrypted archive')
        self.env = dict(os.environ, ANDROID_HOME=str(self.base / 'sdk'))

    def invoke(self, **overrides):
        options = {
            '--sha256': hashlib.sha256(self.apk.read_bytes()).hexdigest(),
            '--backup': str(self.backup),
            '--backup-sha256': hashlib.sha256(self.backup.read_bytes()).hexdigest(),
            '--keystore': str(self.key),
        }
        options.update(overrides)
        command = [sys.executable, str(ROOT / 'tools/sign_apk_from_backup.py'),
                   str(self.apk), str(self.output)]
        for option, value in options.items():
            command.extend((option, value))
        return subprocess.run(command, env=self.env, capture_output=True, text=True)

    def test_malformed_archive_hash(self):
        result = self.invoke(**{'--backup-sha256': 'bad'})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('--backup-sha256 requires 64 hexadecimal characters', result.stderr)

    def test_wrong_input_hash(self):
        result = self.invoke(**{'--sha256': '0' * 64})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Input APK SHA-256 mismatch', result.stderr)
        self.assertFalse(self.output.exists())

    def test_wrong_backup_hash_before_decryption(self):
        result = self.invoke(**{'--backup-sha256': '0' * 64})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Archive checksum mismatch', result.stderr)
        self.assertNotIn('Opening the backup', result.stdout)

    def test_existing_output_is_preserved(self):
        self.output.write_bytes(b'keep this output')
        result = self.invoke()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('will not be overwritten', result.stderr)
        self.assertEqual(self.output.read_bytes(), b'keep this output')

    def test_verified_local_backup_reaches_gpg(self):
        # Fake GPG only confirms the bytes received; no real password prompt.
        gpg = self.base / 'gpg'
        gpg.write_text('#!/bin/sh\ncat > "' + str(self.base / 'received') + '"\nexit 1\n')
        gpg.chmod(0o700)
        self.env['PATH'] = str(self.base) + os.pathsep + os.environ.get('PATH', '')
        result = self.invoke()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Could not reopen the archive', result.stderr)
        self.assertEqual((self.base / 'received').read_bytes(), self.backup.read_bytes())
        self.assertFalse(self.output.exists())

    def test_bootstrap_requires_file_and_checksum(self):
        commands = (
            ['--key'],
            ['--key', str(self.backup)],
            ['--key', str(self.backup), '--archive-sha256', 'bad'],
            ['--archive-sha256', '0' * 64],
        )
        for args in commands:
            with self.subTest(args=args):
                result = subprocess.run(['bash', str(ROOT / 'tools/bootstrap_build_machine.sh'), *args],
                                        capture_output=True, text=True)
                self.assertEqual(result.returncode, 2)


if __name__ == '__main__':
    unittest.main()
