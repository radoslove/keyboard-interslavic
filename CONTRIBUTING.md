# Contributing

Keep public changes focused on source, reproducible build instructions, technical
test results and user documentation. Write technical documentation in English;
preserve intentional Interslavic text and matching English translations.

Do not commit conversation transcripts, agent memory, personal or family details,
account setup notes, device inventories, backup locations, credentials or machine
security status. Keep operational records outside the repository. Use ignored
local configuration for machine-specific values and synthetic data in examples.

Before preparing a change for review:

```bash
python3 tools/check_public_tree.py
python3 check_docs.py
git diff --check
```

After staging, the maintainer can check the actual index:

```bash
python3 tools/check_public_tree.py --staged
```

The public-tree check catches known private file types, home paths, infrastructure
addresses and credential patterns. Review prose and binary artifacts separately;
a pattern scan is not a guarantee that content is appropriate for publication.
Adding an ignore rule does not remove a file already tracked by Git or erase its
historical versions.

Change generators and regenerate their outputs rather than hand-editing generated
artifacts. Verify keyboard interactions on real devices before reporting them as
working. An Android debug build may use local diagnostic endpoints; release builds
must not contain those values or gain Internet permission.

Agents create commits only when explicitly requested. The maintainer pushes.
See [the agent push guard](docs/AGENT_GIT_GUARD.md) for the local Git hook.
