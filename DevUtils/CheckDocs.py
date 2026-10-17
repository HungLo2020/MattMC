#!/usr/bin/env python3
"""Check wiki indexes and local links without building or modifying the site.

Run through `python3 DevUtils/RunWiki.py check` to use the wiki environment.
"""

from __future__ import annotations

import os
from concurrent.futures import ProcessPoolExecutor
from functools import lru_cache
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]


class PageLinks(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.links: list[tuple[str, bool]] = []
        self.ids: set[str] = set()

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if attributes.get("id"):
            self.ids.add(attributes["id"])
        if tag == "a":
            if attributes.get("name"):
                self.ids.add(attributes["name"])
            if "href" in attributes:
                self.links.append((attributes["href"] or "", False))
        elif tag in {"img", "source", "video", "audio", "script", "iframe"}:
            if attributes.get("src"):
                self.links.append((attributes["src"], True))

    handle_startendtag = handle_starttag


_WORKER_CONFIG = None


def _render_links(page: Path, config) -> tuple[list[tuple[str, bool]], set[str]]:
    import markdown

    rendered = markdown.markdown(
        page.read_text(encoding="utf-8-sig"),
        extensions=config.markdown_extensions,
        extension_configs=config.mdx_configs,
    )
    result = PageLinks()
    result.feed(rendered)
    return result.links, result.ids


def _init_worker() -> None:
    global _WORKER_CONFIG
    from mkdocs.config import load_config

    _WORKER_CONFIG = load_config(config_file=str(ROOT / "mkdocs.yml"))


def _render_in_worker(page: Path) -> tuple[list[tuple[str, bool]], set[str]]:
    return _render_links(page, _WORKER_CONFIG)


def main() -> int:
    try:
        import markdown  # noqa: F401
        from mkdocs.config import load_config
    except ImportError:
        print("Use python3 DevUtils/RunWiki.py check to prepare the wiki dependencies.")
        return 1

    config = load_config(config_file=str(ROOT / "mkdocs.yml"))
    docs = Path(config.docs_dir).resolve()
    errors: list[str] = []
    agents = ROOT / "AGENTS.md"
    if not agents.is_symlink() or agents.readlink() != Path("README.md"):
        errors.append("AGENTS.md must be a relative symlink to README.md")

    rendered: dict[Path, tuple[list[tuple[str, bool]], set[str]]] = {}

    @lru_cache(maxsize=None)
    def parse(page: Path) -> PageLinks:
        links, ids = rendered[page] if page in rendered else _render_links(page, config)
        result = PageLinks()
        result.links, result.ids = links, ids
        return result

    def display(path: Path) -> str:
        return str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path)

    if "agents" not in parse(ROOT / "README.md").ids:
        errors.append("README.md must contain the Agents section")

    pages = sorted(p for p in docs.rglob("*") if p.is_file() and p.suffix.lower() == ".md")
    # Rendering dominates the check; pages are independent, so render them in
    # parallel. Each worker loads the same mkdocs configuration.
    workers = max(1, min(os.cpu_count() or 1, 16))
    if workers > 1 and len(pages) > 50:
        with ProcessPoolExecutor(max_workers=workers, initializer=_init_worker) as pool:
            rendered.update(zip(pages, pool.map(_render_in_worker, pages, chunksize=32)))
    directories = {docs}
    for page in pages:
        directories.update(p for p in page.parents if p.is_relative_to(docs))

    indexes = {}
    for directory in sorted(directories):
        candidates = [p for p in pages if p.parent == directory and
                      p.stem.casefold() in {"index", "home", directory.name.casefold()}]
        if len(candidates) != 1:
            found = ", ".join(p.name for p in candidates) or "none"
            errors.append(f"{display(directory)}: expected one index (index.md, home.md, "
                          f"or directory-named Markdown); found {found}")
        else:
            indexes[directory] = candidates[0]

    linked_pages: dict[Path, set[Path]] = {}
    for page in [ROOT / "README.md", *pages]:
        linked_pages[page] = set()
        for href, is_asset in parse(page).links:
            try:
                link = urlsplit(href)
            except ValueError:
                errors.append(f"{display(page)}: invalid link {href!r}")
                continue
            if link.scheme or link.netloc:
                continue  # Offline check: external URLs are not fetched.
            raw_path = unquote(link.path)
            if raw_path.startswith("/"):
                errors.append(f"{display(page)}: use a relative source link instead of {href!r}")
                continue
            target = (page.parent / raw_path).resolve() if raw_path else page
            if not target.is_relative_to(ROOT):
                errors.append(f"{display(page)}: link escapes the repository: {href!r}")
                continue
            if page.is_relative_to(docs) and not target.is_relative_to(docs):
                errors.append(f"{display(page)}: use a GitHub URL for repository files "
                              f"outside docs/: {href!r}")
                continue
            if not target.is_file():
                errors.append(f"{display(page)}: missing file {href!r}")
                continue
            if not is_asset:
                linked_pages[page].add(target)
            if link.fragment and target.suffix.lower() == ".md":
                if unquote(link.fragment) not in parse(target).ids:
                    errors.append(f"{display(page)}: missing heading/anchor {href!r}")

    for directory, index in sorted(indexes.items()):
        required = {p for p in pages if p.parent == directory and p != index}
        required.update(child for parent, child in indexes.items() if parent.parent == directory)
        for missing in sorted(required - linked_pages[index]):
            errors.append(f"{display(index)}: missing index entry for {display(missing)}")

    if errors:
        for error in sorted(set(errors)):
            print(f"ERROR: {error}")
        print(f"Documentation check failed: {len(set(errors))} problem(s).")
        return 1
    print(f"Documentation check passed: {len(pages)} wiki pages, {len(indexes)} directory "
          "indexes, README links, and AGENTS.md symlink. External URLs were not checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
