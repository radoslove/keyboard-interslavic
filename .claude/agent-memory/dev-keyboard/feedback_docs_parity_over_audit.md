---
name: feedback-docs-parity-over-audit
description: For README/doc work in keyboard-interslavic the owner wants MS/EN parity and polish, not a forensic fact-audit of every sentence
metadata:
  type: feedback
---

For documentation work in this repo, the bar is **parity and polish**, not exhaustive
verification. Owner, mid-task on the README merge (2026-08-10): *"nie musi byc
super-dokladnie tylko ladnie zeby bylo tak samo w obu jezykach."*

**Why:** doc prose is not a language ruling. Routing every small connective Interslavic
sentence through `interslavic-tutor` / `/isv-verify` costs rounds and buys little when the
sentence is built from words already used elsewhere in the same verified document. The
owner reads the result as one page and notices *mismatch between the MS paragraph and its
EN gloss* far more than a debatable word choice.

**How to apply:**
- Reuse verified phrasing already in the document before writing new MS prose; screen only
  genuinely new words, offline against `dictionary/main_isv.combined`, and keep going.
- A word missing from that wordlist is **not** a verdict — several words already in the
  verified README (`svaip`, `ugol`, `gorny`, `razšireneho`, `polsky`) are absent from it.
  See the standing never-correct-on-an-absence rule.
- Still non-negotiable regardless of the lowered bar: standard orthography only, and every
  number quoted must be one you actually measured in the repo, never estimated.
- This does **not** relax verification for *claims* (counts, PR/build state) — only for
  prose style.

Related: [[feedback-try-before-push]]
