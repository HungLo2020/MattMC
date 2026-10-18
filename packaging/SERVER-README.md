# MattMC Server

This directory (`MattMC/run/server/`) contains the dedicated server launchers and
is the server's working directory: `server.properties`, the world and the
access lists are created here.

## Running the Server

### Linux/macOS
```bash
./run-server.sh
```

### Windows
```cmd
run-server.bat
```

## Server Settings

- **Memory Allocation**: 2GB (default)
  - Modify the `-Xmx` and `-Xms` flags in the launch scripts to adjust
- **Mode**: Headless (--nogui)
  - Remove the `--nogui` flag to run with GUI

## Notes

- The server shares the bundled JDK with the client (`MattMC/run/jdk/<platform>/`)
  and its libraries and natives (`MattMC/lib/`, `MattMC/natives/`).
- Updates replace these launchers and this README; your server data here is kept.
- Sync Up/Down covers everything in `MattMC/run/`, including this server directory.
