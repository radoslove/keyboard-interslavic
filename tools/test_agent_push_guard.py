#!/usr/bin/env python3
"""Exercise the push guard against disposable local Git repositories."""

import os
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
MARKERS = (
    "CODEX_THREAD_ID", "CODEX_SESSION_ID", "CODEX_SANDBOX",
    "CLAUDECODE", "CLAUDE_CODE_ENTRYPOINT",
)


def run(cwd, env, *args, success=True):
    result = subprocess.run(args, cwd=cwd, env=env, capture_output=True, text=True)
    if success and result.returncode:
        raise AssertionError(result.stderr)
    return result


def main():
    # Remove markers ONLY in these disposable test processes. Never use this
    # environment for the real project or any non-test remote.
    test_env = {k: v for k, v in os.environ.items()
                if k not in MARKERS and not k.startswith("GIT_")}
    test_env.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": os.devnull})
    with tempfile.TemporaryDirectory(prefix="keyboard-push-guard-") as temporary:
        base = Path(temporary)
        repo, remote = base / "work", base / "remote.git"
        run(base, test_env, "git", "init", "--quiet", "--bare", str(remote))
        run(base, test_env, "git", "init", "--quiet", "--initial-branch=main", str(repo))
        run(repo, test_env, "git", "config", "user.name", "Push guard test")
        run(repo, test_env, "git", "config", "user.email", "test@example.invalid")
        run(repo, test_env, "git", "config", "core.hooksPath", str(ROOT / ".githooks"))
        run(repo, test_env, "git", "commit", "--quiet", "--allow-empty", "-m", "Test fixture")
        run(repo, test_env, "git", "remote", "add", "origin", str(remote))
        for marker in MARKERS:
            agent_env = dict(test_env, **{marker: "push-guard-test"})
            result = run(repo, agent_env, "git", "push", "origin", "HEAD:main", success=False)
            assert result.returncode != 0 and "Push blocked:" in result.stderr, marker
            refs = run(base, test_env, "git", "--git-dir", str(remote), "show-ref", success=False)
            assert refs.returncode == 1 and not refs.stdout, marker
        agent_env = dict(test_env, CODEX_THREAD_ID="push-guard-test")
        for arguments in (("origin", "HEAD:feature/test"), ("origin", "+HEAD:main"),
                          ("--force", "origin", "HEAD:main")):
            result = run(repo, agent_env, "git", "push", *arguments, success=False)
            assert result.returncode != 0 and "Push blocked:" in result.stderr, arguments
        run(repo, test_env, "git", "push", "--quiet", "--set-upstream", "origin", "main")
        expected = run(repo, test_env, "git", "rev-parse", "HEAD").stdout.strip()
        actual = run(base, test_env, "git", "--git-dir", str(remote), "rev-parse", "main").stdout.strip()
        assert actual == expected, "Normal-terminal control push did not reach the test remote"
        run(repo, test_env, "git", "commit", "--quiet", "--allow-empty", "-m", "Second fixture")
        result = run(repo, agent_env, "git", "push", success=False)
        assert result.returncode != 0 and "Push blocked:" in result.stderr, "Implicit push"
        actual = run(base, test_env, "git", "--git-dir", str(remote), "rev-parse", "main").stdout.strip()
        assert actual == expected, "Blocked push changed the test remote"
    print("PASS: all five agent markers, feature and force refspecs, and implicit push blocked; manual control push allowed.")


if __name__ == "__main__":
    main()
