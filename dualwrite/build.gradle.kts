plugins {
    application
}

// Deliberately not a toolchain: options.release compiles against 25 with
// whatever JDK runs the build, as long as it is 25 or newer.
tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

repositories {
    mavenCentral()
    // Confluent's serializers are not on Maven Central.
    maven("https://packages.confluent.io/maven")
}

dependencies {
    implementation("org.apache.kafka:kafka-clients:4.1.0")
    implementation("io.confluent:kafka-json-serializer:8.0.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("ch.qos.logback:logback-classic:1.5.18")
}

// Confluent republishes kafka-clients as `<kafka>-ccs`, which Gradle's
// "newest wins" would pick over 4.1.0. Pin ours.
configurations.all {
    resolutionStrategy {
        force("org.apache.kafka:kafka-clients:4.1.0")
    }
}

application {
    mainClass = "academy.dataflow.wind.dualwrite.DualWriteApp"
}
