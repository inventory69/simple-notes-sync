package dev.dettmer.simplenotes.models

data class ContributorsFile(val contributors: List<Contributor> = emptyList())

data class Contributor(
    val login: String,
    val name: String? = null,
    val role: String = "code",
    val note: String? = null
)
