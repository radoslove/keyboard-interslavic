#!/usr/bin/env python3
"""Regression checks for the public-tree scanner; no actual private data."""

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from check_public_tree import findings


class PublicTreeChecks(unittest.TestCase):
    def test_private_files(self):
        for path in ('.claude/agent-memory/example.md', 'docs/MC_TODO_example.md',
                     'android-app/diagnostics.local.properties', 'keys/test.jks',
                     'android-app/keystore.properties', '.env'):
            self.assertTrue(findings(path, b''), path)

    def test_private_addresses_and_home_paths(self):
        private = '100.' + '64.0.1'
        home = '/Users/' + 'example/work/project'
        self.assertTrue(findings('docs/example.md', private.encode()))
        self.assertTrue(findings('source.kt', ('http://' + private + '/api/test').encode()))
        self.assertTrue(findings('docs/example.md', home.encode()))
        self.assertTrue(findings('source.klc', home.encode('utf-16')))

    def test_public_examples_and_source_code(self):
        text = b'https://example.invalid/api/test\nhttp://127.0.0.1:8080\n$HOME/project\n'
        self.assertFalse(findings('docs/example.md', text))
        self.assertFalse(findings('source.py', b"range = '10.0.0.0/8'\n"))

    def test_fake_credential(self):
        fake = 'gh' + 'p_' + 'x' * 30
        self.assertTrue(findings('example.txt', fake.encode()))

    def test_staged_check_catches_forced_private_file(self):
        checker = Path(__file__).resolve().with_name('check_public_tree.py')
        with tempfile.TemporaryDirectory(prefix='keyboard-public-tree-') as temporary:
            root = Path(temporary)
            env = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
            env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
            def run(*args):
                return subprocess.run(args, cwd=root, env=env, capture_output=True, text=True)
            self.assertEqual(run('git', 'init', '--quiet').returncode, 0)
            (root / '.gitignore').write_text('*.local.properties\n')
            (root / 'test.local.properties').write_text('value=synthetic\n')
            self.assertEqual(run(sys.executable, str(checker)).returncode, 0)
            self.assertEqual(run('git', 'add', '-f', 'test.local.properties').returncode, 0)
            result = run(sys.executable, str(checker), '--staged')
            self.assertEqual(result.returncode, 1)
            self.assertIn('private file', result.stdout)
            self.assertNotIn('synthetic', result.stdout)


if __name__ == '__main__':
    unittest.main()
