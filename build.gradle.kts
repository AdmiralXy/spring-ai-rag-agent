import cz.habarta.typescript.generator.JsonLibrary
import cz.habarta.typescript.generator.TypeScriptOutputKind

plugins {
    java
    checkstyle
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("cz.habarta.typescript-generator") version "3.2.1263"
    id("com.google.cloud.tools.jib") version "3.4.2"
}

group = "io.github.admiralxy"
version = "1.0.0"
description = "agent"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spring.io/snapshot")
    maven("https://repo.spring.io/milestone")
}

extra["springAiVersion"] = "1.0.0-M3"

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Spring AI
    implementation(platform("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}"))
    implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter")

    // Database
    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")

    // Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jib {
    container {
        from {
            image = "openjdk:25-jre-alpine"
        }
        jvmFlags = listOf(
            "-XX:+UseZGC",
            "-XX:+ZGenerational"
        )
    }
}


tasks {
    generateTypeScript {
        jsonLibrary = JsonLibrary.jackson2
        outputKind = TypeScriptOutputKind.module
        classPatterns = listOf("io.github.admiralxy.agent.controller.request.**", "io.github.admiralxy.agent.controller.response.**")
    }
}
