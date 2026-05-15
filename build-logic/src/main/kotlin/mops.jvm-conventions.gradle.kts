plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://artifacts.itemis.cloud/repository/maven-mps")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
