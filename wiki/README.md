# MattMC Wiki Tooling

The wiki source lives in `docs/` as normal Markdown files. These scripts create
a local Python environment, install the wiki tooling, and run MkDocs without
requiring system-wide Python packages.

## Linux/macOS

```bash
./wiki/RunWiki.sh
```

Open the local URL printed by `mkdocs serve`.

To build the static site locally:

```bash
./wiki/RunWiki.sh build
```

## Windows PowerShell

```powershell
.\wiki\RunWiki.ps1
```

To build the static site locally:

```powershell
.\wiki\RunWiki.ps1 build
```

Both scripts automatically create or repair `.venv-wiki/` if MkDocs is not
installed yet. You can also force setup directly:

```bash
./wiki/RunWiki.sh setup
```

```powershell
.\wiki\RunWiki.ps1 setup
```

## Notes

- Markdown files in `docs/` are version controlled.
- The generated `site/` directory is ignored.
- The local `.venv-wiki/` Python environment is ignored.
- GitHub Pages builds the same site from `mkdocs.yml`.
