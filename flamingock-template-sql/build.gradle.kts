val templateApiVersion: String by rootProject.extra
val flamingockVersion: String by rootProject.extra

dependencies {
    implementation("io.flamingock:flamingock-template-api:$templateApiVersion")
    implementation(project(":flamingock-sql-util"))

    testAnnotationProcessor("io.flamingock:flamingock-processor:$flamingockVersion")
    testAnnotationProcessor(files(sourceSets.main.get().output))
    testImplementation("io.flamingock:flamingock-auditstore-sql:$flamingockVersion")
    testImplementation("com.zaxxer:HikariCP:4.0.3")
    testImplementation("com.h2database:h2:2.1.214")
    testImplementation("org.testcontainers:testcontainers:1.19.8")
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
    testImplementation("org.postgresql:postgresql:42.7.3")
}

description = "SQL change templates for declarative database schema and data changes"

tasks.withType<JavaCompile>().configureEach {
    if (name.contains("Test", ignoreCase = true)) {
        options.compilerArgs.addAll(listOf(
            "-Asources=${projectDir}/src/test/java",
            "-Aresources=${projectDir}/src/test/resources"
        ))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
