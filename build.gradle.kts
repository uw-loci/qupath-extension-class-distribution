plugins {
    // To optionally create a shadow/fat jar that bundles up dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
}

// Configure your extension here
qupathExtension {
    name = "qupath-extension-class-distribution"
    group = "io.github.michaelsnelson"
    version = "0.1.8"
    description = "Live pie chart of annotation class distribution across a QuPath project."
    automaticModule = "io.github.michaelsnelson.extension.classdistribution"
}

repositories {
    mavenLocal()
    mavenCentral()
}

val javafxVersion = "17.0.2"

dependencies {
    // Main dependencies for QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation(libs.bundles.logging)
    testImplementation(libs.qupath.fxtras)
    testImplementation("org.openjfx:javafx-base:$javafxVersion")
    testImplementation("org.openjfx:javafx-graphics:$javafxVersion")
    testImplementation("org.openjfx:javafx-controls:$javafxVersion")
}

// For troubleshooting deprecation warnings
tasks.withType<JavaCompile> {
    options.release.set(21) // QuPath 0.7 runs on Java 21; pin bytecode target so any build JDK emits loadable classes
    options.compilerArgs.add("-Xlint:deprecation")
    options.compilerArgs.add("-Xlint:unchecked")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs = listOf(
        "--add-modules", "javafx.base,javafx.graphics,javafx.controls",
        "--add-opens", "javafx.graphics/javafx.stage=ALL-UNNAMED"
    )
}
// QuPath 0.7.0's maven artifacts are published as requiring JVM 25 (org.gradle.jvm.version=25),
// even though the QuPath app runs on Java 21. options.release=21 makes Gradle resolve a
// JVM-21-compatible classpath, which then rejects those JVM-25 artifacts on a clean build. Force
// the resolvable classpaths to request JVM 25 so the deps resolve; bytecode target (21) is
// unaffected, so the jar still loads on Java 21. (Upstream QuPath metadata bug; remove if fixed.)
configurations.configureEach {
    if (isCanBeResolved) {
        attributes {
            attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
        }
    }
}
