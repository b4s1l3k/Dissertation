plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "d.semisalov"
version = "0.1.0"

repositories {
    mavenCentral()
}

tasks.withType<JavaExec> {
    jvmArgs = listOf(
        "-Xms8G",
        "-Xmx16G",
        "-XX:+UseG1GC",
        "-XX:+UseStringDeduplication",
        "-XX:MaxGCPauseMillis=100",
        "-XX:+ParallelRefProcEnabled",
        "-XX:+AlwaysPreTouch",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+UseLargePages"
    )
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-cassandra")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.netty:netty-codec-http2:4.1.100.Final")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.github.serpro69:kotlin-faker:1.16.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("org.xerial.snappy:snappy-java:1.1.10.7")
    implementation("com.github.luben:zstd-jni:1.5.6-9")
    implementation("com.typesafe:config:1.4.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-afterburner:2.18.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    runtimeOnly("com.datastax.oss:java-driver-core:4.13.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}