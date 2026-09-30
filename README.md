# MattMC

> **A high-performance, modular port of Minecraft Java Edition 1.21.10 — No Bullshit.**

This repository contains a complete, decompiled source code port of Minecraft Java Edition 1.21.10 (both client and server), with a focus on performance optimization and modular architecture.

Wiki for this project is available at: https://hunglo2020.github.io/MattMC/

The source code for the wiki is in this repo: [Wiki](docs/index.md)

Source Code: https://github.com/HungLo2020/MattMC

## What Makes MattMC Different

### No Bullshit
- **Full Source Access**: Thousands of Java source files available for inspection and modification
- **Transparent Build Process**: Clear Gradle configuration with documented tasks
- **No Proprietary Launchers**: Direct execution via standard Java tooling
- **Offline Capable**: Run and develop without forced authentication or telemetry

## Quick Start
- download or clone the repository, ```git clone https://github.com/HungLo2020/MattMC.git```
- run ```./DevUtils/SetupProject.sh``` on Linux/macOS or ```.\DevUtils\SetupProject.ps1``` on Windows to set up the project.
- run `./gradlew runClient` to launch the client or `./gradlew runServer` to launch the server. Alternatively, use `python3 DevUtils/RunDev.py` to launch the development client or `python3 DevUtils/ExportToDownloads.py` to export the build. On Windows, use `gradlew.bat` and `python`.

May need to launch with "code --disable-gpu" on linux. stupid.

## Agents

- Before changing a subsystem, start at [the documentation index](docs/index.md)
  and read the relevant development, testing, and feature documentation.
- Update affected documentation alongside code changes. Keep documented
  behavior, architecture, commands, and file paths accurate.
- Put developer documentation in `docs/development/`, organized into relevant
  system and subsystem subdirectories. Documentation elsewhere in `docs/` is
  for players; write it for that audience.
- Make developer docs practical: explain how to work on a subsystem, its important
  constraints, useful commands, and troubleshooting. Link to code for implementation
  details instead of restating it. Keep pages short, focused, and easy to scan.
- Preserve the documentation/wiki hierarchy. Every documentation directory
  must have one clearly identified index linking to every other Markdown
  file directly inside it and to each immediate child documentation directory's index.
- When adding, moving, renaming, or removing pages, update the affected
  indexes and incoming links in the same change. Preserve existing index
  filenames and published URLs where practical.
- Clearly distinguish current behavior, proposed work, and historical results.
  Report verification accurately.
- Follow the [documentation maintenance guide](docs/development/DOCUMENTATION.md)
  and run `python3 DevUtils/RunWiki.py check` after documentation changes.

`AGENTS.md` is a symlink to this README, keeping these instructions in one place.
