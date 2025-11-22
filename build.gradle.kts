// --- imports ---
import org.gradle.api.tasks.bundling.Zip
import java.text.SimpleDateFormat
import java.util.Date
// ---------------

plugins {
    application
    idea
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

application {
    // Your main class
    mainClass.set("mattmc.client.main.Main")
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
    
    // JOML for matrix math (used by Minecraft for uvlock transformations)
    implementation("org.joml:joml:1.10.5")
    
    // Logging framework
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks.test {
    useJUnitPlatform()
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
                // ISSUE-020 fix: Use new filePermissions API instead of deprecated fileMode
                filePermissions {
                    user {
                        read = true
                        write = true
                        execute = true
                    }
                    group {
                        read = true
                        execute = true
                    }
                    other {
                        read = true
                        execute = true
                    }
                }
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
tasks.register<JavaExec>("runDebugTest") {
	mainClass.set("mattmc.world.level.lighting.VertexLightSamplingTest")
	classpath = sourceSets["test"].runtimeClasspath
}
