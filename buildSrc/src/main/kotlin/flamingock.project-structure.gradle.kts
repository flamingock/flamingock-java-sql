/**
 * Flamingock SQL project structure and module classification plugin.
 * Defines module categories and provides utilities for project classification.
 */

// Module categorization
val templateProjects = setOf(
    "flamingock-java-template-sql"
)

val allProjects = templateProjects

// Project classification utilities
fun Project.isBomModule(): Boolean = name.endsWith("-bom")
fun Project.isLibraryModule(): Boolean = !isBomModule()

// Module category lookup
fun Project.getProjectCategory(): String? = when (name) {
    in templateProjects -> "templates"
    else -> null
}

// Module bundle utilities
fun getProjectsForBundle(bundle: String?): Set<String> = when (bundle) {
    "templates" -> templateProjects
    "all" -> allProjects
    else -> setOf()
}

// Make variables available to other plugins
extra["templateProjects"] = templateProjects
extra["allProjects"] = allProjects

// Apply appropriate plugins based on project type
when {
    project == rootProject -> { /* Do not publish root project */ }
    isBomModule() -> {
        apply(plugin = "java-platform")
        apply(plugin = "flamingock.license")
        apply(plugin = "flamingock.publishing")
    }
    isLibraryModule() -> {
        apply(plugin = "flamingock.java-library")
        apply(plugin = "flamingock.license")
        apply(plugin = "flamingock.publishing")
    }
    else -> {
        apply(plugin = "java-library")
        apply(plugin = "flamingock.license")
        apply(plugin = "flamingock.publishing")
    }
}
