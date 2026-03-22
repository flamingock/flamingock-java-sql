---
name: Project Overview
description: flamingock-java-sql is a single-module Gradle library providing SQL change templates for the Flamingock migration framework
type: project
---

**Flamingock Java SQL Template** - provides `SqlTemplate` for executing raw SQL from YAML change files in Flamingock pipelines.

- **Build:** Gradle 8.5 (Kotlin DSL), Java 8 target, GraalVM 17 runtime
- **Version:** 1.3.0-SNAPSHOT
- **Key deps:** flamingock-template-api:1.3.1, sql-util:1.2.0-beta.2
- **Supports 11 DB dialects:** MySQL, MariaDB, PostgreSQL, Oracle, SQL Server, Sybase, SQLite, H2, Firebird, DB2, Informix
- **Release:** JReleaser to Maven Central, Apache 2.0 license
- **CI/CD:** GitHub Actions (build, release, license check, commit validation)

**Why:** This is the core SQL template library for the Flamingock ecosystem, enabling declarative SQL migrations.

**How to apply:** Understand that changes here affect all Flamingock users relying on SQL templates. DB dialect support and splitter logic are critical paths.
