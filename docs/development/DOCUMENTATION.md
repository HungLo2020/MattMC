# Documentation maintenance

The published wiki is built from `docs/`. Start at [its root index](../index.md)
and follow the relevant section indexes before changing a subsystem. The
repository's [README Agents section](https://github.com/HungLo2020/MattMC/blob/master/README.md#agents) holds the shared
agent instructions; `AGENTS.md` links to that same file.

## Maintain pages with the implementation

- Update the relevant architecture, development, testing, and gameplay pages
  when behavior, commands, ownership, or file locations change.
- Document a topic once and link to it from related pages. Keep the README's
  agent guidance short and put detailed procedures here in `docs/`.
- Label proposed work and historical measurements. Do not present an old
  benchmark or acceptance result as verification of a newer implementation.
- Repair incoming links when moving or removing a page. Preserve established
  filenames and published URLs when possible.

## Directory indexes

Every directory containing Markdown documentation, including intermediate
directories leading to it, has one index. Asset-only directories are excluded.
An index links to:

1. Every other Markdown file directly in its directory.
2. The index of every immediate child documentation directory.

This makes every page reachable from `docs/index.md` without flattening all
descendants into each parent. Existing grouping, descriptions, and link order
can remain hand-written.

Use `index.md` for new directory indexes. Existing `home.md` and pages named
after their directory (such as `Items.md` or `CHANGELOG.md`) remain valid.
Names are matched without regard to case, and each directory must have exactly
one such index. Do not add `index.md` alongside an existing recognized index.

When adding a page, add it to the local index. When adding a directory, add its
index and link that index from the parent. When moving or deleting a page,
update both the index entries and other pages linking to it.

## Check and preview

From the repository root:

```sh
python3 DevUtils/RunWiki.py check
python3 DevUtils/RunWiki.py build
python3 DevUtils/RunWiki.py serve
```

On Windows, use `python` instead of `python3`. These commands prepare the ignored
`.venv-wiki/` environment using `requirements-docs.txt` when necessary.

`check` runs `DevUtils/CheckDocs.py`. It checks directory-index coverage, local
file/image links, Markdown heading anchors, the README's Agents section, and
the `AGENTS.md -> README.md` symlink. It reads the Markdown extensions from `mkdocs.yml` so heading IDs and
reference-style links are handled consistently. It checks the README and all
Markdown pages under `docs/`; it does not crawl external websites or verify the
factual accuracy of prose. Relative links from the wiki to repository files
outside `docs/` should use the full GitHub URL so they work on the published site.

`build` runs the same check followed by `mkdocs build --strict`; `serve` checks
once before starting the local preview. Re-run `check` after editing during a
preview session. The wiki CI workflow checks documentation before building,
including on pull requests. Only pushes and manual runs on `master` deploy.
