# MattMC Build & Runtime Troubleshooting

This guide covers common issues and their solutions when building and running MattMC.

## Build Issues

### Gradle Daemon Issues

**Symptom**: Build hangs or fails with daemon errors

**Solution**:
```bash
# Stop all Gradle daemons
./gradlew --stop

# Clear Gradle cache
rm -rf ~/.gradle/caches/

# Rebuild
./gradlew clean build
```

### Out of Memory Errors

**Symptom**: `OutOfMemoryError` during compilation

**Solution**: Increase heap size in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx12G -XX:+UseG1GC
```

### Dependency Download Failures

**Symptom**: Cannot download dependencies from remote repositories

**Solution**:
```bash
# Use bundled dependencies for offline builds
./libraries/download-dependencies.sh

# Or retry with clean cache
./gradlew clean build --refresh-dependencies
```

### Compilation Errors

**Symptom**: Java compilation errors

**Solutions**:
1. Ensure you're using Java 21 or higher: `java -version`
2. Clean and rebuild: `./gradlew clean build`
3. Check for modified source files: `git status`
4. Reset to a clean state: `git checkout .`

## Runtime Issues

### JDK Not Found

**Symptom**: `Could not find bundled JDK`

**Solution**:
```bash
# Download the bundled JDK
./libraries/download_jdk.sh

# Or set JAVA_HOME to your JDK installation
export JAVA_HOME=/path/to/jdk-21
```

### Graphics/Rendering Issues

**Symptom**: Black screen, crashes on startup, or rendering glitches

**Solutions**:
1. Update graphics drivers
2. Check GPU compatibility
3. Try software rendering: Add `-Dsodium.force_software_rendering=true` to JVM args
4. Check logs in `logs/latest.log`

### Native Library Errors

**Symptom**: `UnsatisfiedLinkError` or `Could not load native library`

**Solutions**:
1. Ensure you're running on a supported platform (Linux, Windows, macOS)
2. Check system architecture matches LWJGL natives
3. Clear native library cache: `rm -rf run/natives/`

### Port Already in Use (Server)

**Symptom**: `Address already in use` when starting server

**Solution**:
```bash
# Find process using port 25565
lsof -i :25565  # Linux/macOS
netstat -ano | findstr :25565  # Windows

# Kill the process or change port in server.properties
```

## Performance Issues

### Low FPS

**Solutions**:
1. Reduce render distance in game settings
2. Enable performance optimizations (Sodium is already included)
3. Allocate more RAM to the game
4. Close other applications

### High Memory Usage

**Solutions**:
1. Reduce allocated heap size if it's too high
2. Use G1GC or ZGC garbage collector
3. Reduce render distance and view distance
4. Restart the game periodically

## Development Issues

### IDE Not Recognizing Source Files

**Symptom**: IntelliJ IDEA or Eclipse doesn't recognize project structure

**Solution**:
```bash
# Regenerate IDE files
./gradlew cleanIdea idea  # IntelliJ
./gradlew cleanEclipse eclipse  # Eclipse

# Or import as Gradle project directly
```

### Hot Reload Not Working

**Symptom**: Code changes not reflected without full restart

**Solution**: MattMC doesn't support hot reload. You must rebuild and restart:
```bash
./gradlew build
./DevUtils/RunDev.sh
```

## Getting Help

If you encounter issues not covered here:

1. **Check the logs**: 
   - Build logs: `build/reports/`
   - Runtime logs: `run/logs/latest.log`
   - Error dumps: `run/crash-reports/`

2. **Search existing issues**: https://github.com/HungLo2020/MattMC/issues

3. **Ask for help**:
   - Open a new issue with:
     - Description of the problem
     - Steps to reproduce
     - Log files
     - System information (OS, Java version, etc.)

## Debug Mode

Enable debug logging for more detailed output:

```bash
# Add to JVM arguments
-Dlog4j.configurationFile=log4j2-debug.xml

# Or set environment variable
export MATTMC_DEBUG=true
```

## Clean Rebuild

When all else fails, do a complete clean rebuild:

```bash
# Stop all processes
./gradlew --stop

# Clean everything
./gradlew clean
rm -rf build/ run/ .gradle/

# Rebuild from scratch
./gradlew build

# Run
./gradlew runClient
```
