plugins {
    application
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.kafka:kafka-clients:4.1.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
}

application {
    mainClass = "academy.dataflow.wind.hello.HelloApp"
}
