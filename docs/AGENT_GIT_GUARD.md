# Agent push guard

Agents prepare and verify local changes, and create commits when explicitly
requested. The maintainer pushes. **All pushes from agent sessions are blocked**, regardless of
the target branch. The hook supports Codex and Claude session markers.

## Installation

Run once in each checkout:

```sh
chmod +x .githooks/pre-push
git config --local core.hooksPath .githooks
```

Check for existing custom hooks before changing `core.hooksPath`; do not overwrite
another hook installation.
Git configuration is local and does not travel with a clone.

## Behavior

`.githooks/pre-push` rejects pushes when Git inherits any of these nonempty agent
markers: `CODEX_THREAD_ID`, `CODEX_SESSION_ID`, `CODEX_SANDBOX`, `CLAUDECODE`, or
`CLAUDE_CODE_ENTRYPOINT`. This also covers Git started from a child script.
Normal user terminals without those markers can push as usual.

The hook runs before remote refs are updated, although Git may already have
contacted the remote. It does not require Codex's separate hook trust mechanism.
It does not run a push, inspect language, or create commits.

This is an accidental-push guard, not an access-control boundary. Git hooks can
be bypassed and environment variables can be removed. Agents must not do either.
Server-side permissions are needed for enforcement against an uncooperative
process. The working agreement also prohibits unsolicited staging and commits;
this hook enforces the push restriction only.

## Verification

```sh
python3 tools/test_agent_push_guard.py
git config --local --get core.hooksPath
```

The test uses disposable local repositories, verifies that agent pushes fail
without changing the remote, and verifies that a simulated normal terminal can
push. It never contacts GitHub or changes this repository's index or history.
