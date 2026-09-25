#!/usr/bin/env python3
"""Check publishable files for private state and infrastructure details.

Default: inspect tracked working files and unignored new files, skipping deletions.
--staged: inspect the index, including tracked files that are now git-ignored.
Reports locations and categories, never the matched values. This heuristic check
does not replace reviewing prose, binary artifacts, or repository history.
"""

import argparse
import fnmatch
import ipaddress
from pathlib import Path
import re
import subprocess


PRIVATE_PATHS = (
    '.claude/*', '.codex/*', '.agents/*', '.private/*', 'AGENTS.md',
    'docs/MC_TODO_*.md', 'docs/RUNBOOK_*.md', 'docs/fdroid_MR*_comment_*.md',
    '*.local.properties', '*.jks', '*.keystore', '*.tar.gpg',
    '*keystore.properties', '*.password.txt', '.env', '.env.*', '*/.env', '*/.env.*',
)
TEXT_PATTERNS = {
    'personal home path': re.compile(r'(?:/(?:Users|home)/|[A-Za-z]:[/\\]Users[/\\])[A-Za-z][\w.-]+[/\\]'),
    'private workspace reference': re.compile(r'\b(?:vault_\d+|remote_[v]ault|hardware_[i]nventory\.md|NOW[.]md)\b'),
    'private key material': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'),
    'credential token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,}|glpat-[A-Za-z0-9_-]{16,}|AKIA[0-9A-Z]{16})\b'),
}
IPV4 = re.compile(r'(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])')
PRIVATE_NETWORKS = tuple(ipaddress.ip_network(net) for net in (
    '10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16', '100.64.0.0/10',
))


def findings(path, data):
    if any(fnmatch.fnmatchcase(path, pattern) for pattern in PRIVATE_PATHS):
        return [(0, 'private file')]
    if data.startswith((b'\xff\xfe', b'\xfe\xff')):
        text = data.decode('utf-16', errors='replace')
    elif b'\0' in data:
        return []  # Binary artifacts need a separate review/rebuild.
    else:
        text = data.decode('utf-8', errors='replace')
    issues = []
    for number, line in enumerate(text.splitlines(), 1):
        for label, pattern in TEXT_PATTERNS.items():
            if pattern.search(line):
                issues.append((number, label))
        for match in IPV4.finditer(line):
            try:
                address = ipaddress.ip_address(match.group())
            except ValueError:
                continue
            # Network ranges are generic documentation/configuration, not a host.
            cidr = re.match(r'/\d{1,2}\b', line[match.end():])
            if cidr:
                try:
                    ipaddress.ip_network(match.group() + cidr.group())
                    continue
                except ValueError:
                    pass
            if any(address in network for network in PRIVATE_NETWORKS):
                issues.append((number, 'private network address'))
                break
    return issues


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--staged', action='store_true')
    args = parser.parse_args()
    root = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip())
    command = ['git', 'ls-files', '-z', '--cached']
    if not args.staged:
        command += ['--others', '--exclude-standard']
    names = subprocess.check_output(command, cwd=root).decode().split('\0')
    failed = False
    checked = 0
    for name in sorted(set(filter(None, names))):
        if args.staged:
            data = subprocess.check_output(['git', 'show', ':' + name], cwd=root)
        else:
            path = root / name
            if not path.is_file():
                continue
            data = path.read_bytes()
        checked += 1
        for line, label in findings(name, data):
            print(f'{name}:{line}: {label}')
            failed = True
    if not failed:
        print(f'PASS: {checked} files checked for known private paths, addresses and credential patterns; prose and binaries still require review.')
    return int(failed)


if __name__ == '__main__':
    raise SystemExit(main())
