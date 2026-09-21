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
}

// No Kafka client here: the service only talks to its database.
dependencies {
    // The @JsonNaming annotation on the record drives the snake_case payload.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    // JDBC driver for the service's own database.
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("ch.qos.logback:logback-classic:1.5.18")
}

application {
    mainClass = "academy.dataflow.wind.outbox.OutboxApp"
}
