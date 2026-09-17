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
}

// No Kafka client here: the service only talks to its database.
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("ch.qos.logback:logback-classic:1.5.18")
}

application {
    mainClass = "academy.dataflow.wind.outbox.OutboxApp"
}
