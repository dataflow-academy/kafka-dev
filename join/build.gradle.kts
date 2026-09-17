plugins {
    application
}

// Deliberately not a toolchain: that would demand exactly this JDK be
// installed. options.release compiles against 25 with whatever JDK runs the
// build, as long as it is 25 or newer.
tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

repositories {
    mavenCentral()
    // Confluent's serializers are not on Maven Central.
    maven("https://packages.confluent.io/maven")
}

dependencies {
    implementation("org.apache.kafka:kafka-streams:4.1.0")
    implementation("org.apache.kafka:kafka-clients:4.1.0")
    // Confluent's schemaless JSON serializer - a thin Jackson wrapper, no registry.
    implementation("io.confluent:kafka-json-serializer:8.0.0")
    // The @JsonNaming annotation on the records drives the snake_case wire format.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
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
    // RocksDB, the state store, is a native library.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
    mainClass = "academy.dataflow.wind.join.JoinApp"
}

// The master data source: ./gradlew publishRegistry
tasks.register<JavaExec>("publishRegistry") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "academy.dataflow.wind.join.RegistryPublisher"
}
