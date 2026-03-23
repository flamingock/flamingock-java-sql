description = "Test utilities for SQL database testing with Testcontainers"

dependencies {
    implementation("com.zaxxer:HikariCP:3.4.5")
    implementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    implementation("org.testcontainers:testcontainers-mysql:2.0.2")
    implementation("org.testcontainers:testcontainers-mssqlserver:2.0.2")
    implementation("org.testcontainers:testcontainers-oracle-xe:2.0.2")
    implementation("org.testcontainers:testcontainers-postgresql:2.0.2")
    implementation("org.testcontainers:testcontainers-mariadb:2.0.2")
}
