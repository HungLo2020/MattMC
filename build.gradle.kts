plugins { application }
repositories { mavenCentral() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

application { mainClass.set("MattMC.Main") }

dependencies {
    val lwjgl = "3.3.4"

    implementation("org.lwjgl:lwjgl:$lwjgl")
    implementation("org.lwjgl:lwjgl-glfw:$lwjgl")
    implementation("org.lwjgl:lwjgl-opengl:$lwjgl")
    implementation("org.lwjgl:lwjgl-stb:$lwjgl")

    // natives for Linux
    runtimeOnly("org.lwjgl:lwjgl:$lwjgl:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjgl:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjgl:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjgl:natives-linux")
}
