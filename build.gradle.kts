plugins {
    kotlin("jvm") version "2.1.10"
}

group = "io.jcrpc"
version = "0.3.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
