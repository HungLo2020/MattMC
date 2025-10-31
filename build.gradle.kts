// --- imports ---
import org.gradle.api.tasks.bundling.Zip
import java.text.SimpleDateFormat
import java.util.Date
// ---------------

plugins {
    application
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

application {
    // Your main class
    mainClass.set("MattMC.Main")
}

dependencies {
    val lwjgl = "3.3.4"

    implementation("org.lwjgl:lwjgl:$lwjgl")
    implementation("org.lwjgl:lwjgl-glfw:$lwjgl")
    implementation("org.lwjgl:lwjgl-opengl:$lwjgl")
    implementation("org.lwjgl:lwjgl-stb:$lwjgl")

    // Linux natives only (add macOS/Windows if you need cross-platform zips)
    runtimeOnly("org.lwjgl:lwjgl:$lwjgl:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjgl:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjgl:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjgl:natives-linux")
    
    // JSON parsing for block models and blockstates
    implementation("com.google.code.gson:gson:2.10.1")
}

/**
 * Include everything under project `packaging/` at the root of the distribution.
 * Put your run.sh here (and make it executable in your repo).
 */
distributions {
    main {
        contents {
            from("packaging") {
                into("")                 // place next to bin/ and lib/
                // Keep exec bit on Unix zips (0755). If Gradle warns, you can omit and chmod after unzip.
                fileMode = 0b111_101_101
            }
        }
    }
}

// ----- Portable zip with version/platform/timestamp -----

// e.g. -PappVersion=0.1.0 (defaults to "dev")
val appVersion = (findProperty("appVersion") as String?) ?: "dev"

// crude platform tag for the filename
val osName = System.getProperty("os.name").lowercase()
val platform = when {
    osName.contains("linux") -> "linux"
    osName.contains("mac") || osName.contains("darwin") -> "mac"
    osName.contains("win") -> "win"
    else -> "unknown"
}

/**
 * Build an installable folder first (includes packaging/ via distributions.main),
 * then zip that folder to releases/MattMC-<ver>-<platform>-<timestamp>.zip
 */
tasks.register<Zip>("portableZip") {
    group = "distribution"
    description = "Build timestamped portable zip into releases/"

    dependsOn(tasks.installDist)

    val appName = "MattMC"
    //val ts = SimpleDateFormat("yyyyMMdd-HHmm").format(Date())

    archiveBaseName.set(appName)
    archiveVersion.set("$appVersion")
    destinationDirectory.set(layout.buildDirectory.dir("releases"))

    // Zip the installed app directory: build/install/MattMC
    from(layout.buildDirectory.dir("install/$appName")) {
        into(appName) // keep MattMC/ folder at the root of the zip
    }
}
