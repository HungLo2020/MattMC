plugins { application }
repositories { mavenCentral() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

application { mainClass.set("MattMC.Main") }

val lwjgl = "3.3.4"
val os = org.gradle.internal.os.OperatingSystem.current()
val arch = System.getProperty("os.arch")
val natives = when {
    os.isLinux   -> "natives-linux"
    os.isWindows -> "natives-windows"
    os.isMacOsX  -> if (arch == "aarch64" || arch == "arm64") "natives-macos-arm64" else "natives-macos"
    else -> throw GradleException("Unsupported OS: ${os.name}")
}

dependencies {
    implementation("org.lwjgl:lwjgl:$lwjgl")
    implementation("org.lwjgl:lwjgl-glfw:$lwjgl")
    implementation("org.lwjgl:lwjgl-opengl:$lwjgl")
    implementation("org.lwjgl:lwjgl-stb:$lwjgl")

    runtimeOnly("org.lwjgl:lwjgl:$lwjgl:$natives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjgl:$natives")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjgl:$natives")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjgl:$natives")
}
