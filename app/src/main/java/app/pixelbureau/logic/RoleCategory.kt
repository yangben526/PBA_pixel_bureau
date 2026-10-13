package app.pixelbureau.logic

/** Display-only grouping of an agent from their existing duties text (no new data). */
enum class RoleCategory(val label: String, val icon: String, val tint: Long, private val keys: List<String>) {
    Engineering("Engineering", "💻", 0xFF6FA8F5, listOf("engineer", "develop", "code", "coding", "program", "software", "backend", "frontend", "devops", "qa", "test")),
    Design("Design", "🎨", 0xFFF59AC0, listOf("design", "ui", "ux", "art", "illustrat", "visual", "brand")),
    Growth("Growth", "📈", 0xFFF7B267, listOf("market", "sales", "growth", "seo", "social", "content", "copy", "writer", "pr ")),
    Research("Research", "🔬", 0xFFB59AF5, listOf("research", "analy", "data", "science", "insight", "report")),
    Ops("Ops", "🧰", 0xFF7CCB8A, listOf("support", "ops", "operation", "admin", "hr", "finance", "legal", "manage", "project", "assistant")),
    General("General", "⭐", 0xFFD9C9A8, emptyList());

    companion object {
        fun of(duties: String): RoleCategory {
            val t = " " + duties.lowercase() + " "
            return entries.firstOrNull { c -> c.keys.any { k -> Regex("(?<![a-z])" + Regex.escape(k.trim())).containsMatchIn(t) } } ?: General
        }
    }
}
