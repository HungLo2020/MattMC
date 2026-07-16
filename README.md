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
- run ```./gradlew runClient``` to launch the client or ```./gradlew runServer``` to launch the server. Or optioinally use the built in scripts, ```./DevUtils/RunDev.sh``` to launch the game in the dev environment. ```./DevUtils/ExportToDownloads.sh``` to export the build.
