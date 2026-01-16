# MattMC Server

This directory contains the server build for MattMC.

## Running the Server

### Linux/macOS
```bash
./run-server.sh
```

### Windows
```cmd
run-server.bat
```

## Server Configuration

The server uses the same JAR files as the client, located in the `../lib` directory.

Server files and world data are stored in the `run/` subdirectory.

## Server Settings

- **Memory Allocation**: 2GB (default)
  - Modify the `-Xmx` and `-Xms` flags in the launch scripts to adjust
- **Mode**: Headless (--nogui)
  - Remove the `--nogui` flag to run with GUI

## Notes

- The server shares the bundled JDK with the client (located at `../run/jdk-25/`)
- All dependencies are shared with the client in the `../lib/` directory
- Server runtime files are isolated in the `server/run/` directory
