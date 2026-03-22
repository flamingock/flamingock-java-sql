description = "SQL utilities and dialect helpers for database operations and testing"

dependencies {
    testImplementation("com.zaxxer:HikariCP:3.4.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation("org.testcontainers:testcontainers-mysql:2.0.2")
    testImplementation("org.testcontainers:testcontainers-mssqlserver:2.0.2")
    testImplementation("org.testcontainers:testcontainers-oracle-xe:2.0.2")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.2")
    testImplementation("org.testcontainers:testcontainers-mariadb:2.0.2")
}
