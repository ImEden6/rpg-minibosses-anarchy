# upstream baseline check

Upstream: https://github.com/cleannrooster/rpg-minibosses, branch `1.20.1`, commit `2b42823`
("CHANGES", 2026-03-29). That's the tip of the branch and the only commit with
`mod_version=1.9.1+1.20.1`.

Compared against the `upstream baseline` commit (`262cbfa`) here, `.git` excluded.

## differences

None. Every file matches byte for byte, so it's not just a whitespace or line ending match.

## notes

- The "ANARCHY - RPG Minibosses" name is upstream's own. It came in with upstream commit `390f044`
  ("catch up", 2025-05-06). Not a fork edit.
- No fork edits are hiding in the baseline, so changes.md doesn't get a "pre-baseline fork edits"
  section. Everything the fork changed is in the commits after `262cbfa`.
