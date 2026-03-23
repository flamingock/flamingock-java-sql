/**
 * Flamingock SQL project structure and module classification plugin.
 * Defines module categories and provides utilities for project classification.
 */

val allProjects = setOf(
    "flamingock-sql-util",
    "flamingock-sql-test-util",
    "flamingock-template-sql"
)

fun Project.isLibraryModule(): Boolean = project != rootProject

// Make variables available to other plugins
extra["allProjects"] = allProjects

// Apply appropriate plugins based on project type
when {
    project == rootProject -> { /* Do not publish root project */ }
    isLibraryModule() -> {
        apply(plugin = "flamingock.java-library")
        apply(plugin = "flamingock.license")
        apply(plugin = "flamingock.publishing")
    }
}
