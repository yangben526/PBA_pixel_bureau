package app.pixelbureau.logic

/**
 * Minimal agent-tool registry. Common tools apply to every agent when toggled on
 * in Settings; Special tools stay per-agent / roster-side.
 */
enum class AgentToolKind { Common, Special }

data class AgentTool(
    val id: String,
    val title: String,
    val description: String,
    val kind: AgentToolKind,
    /** JSON Schema for arguments (MCP tools). Null keeps the built-in schema. */
    val parametersJson: String? = null,
)

/** Floor-wide capabilities listed under Settings → Agent tools → Common. */
val CommonAgentTools: List<AgentTool> = listOf(
    AgentTool(
        id = "internet_search",
        title = "Internet search",
        description = "Search the web and read a few of the pages for up-to-date information",
        kind = AgentToolKind.Common,
    ),
    AgentTool(
        id = "time_tool",
        title = "Time",
        description = "Read the phone clock and convert a time to any place on Earth",
        kind = AgentToolKind.Common,
    ),
)

fun enabledCommonTools(
    internetSearchEnabled: Boolean,
    timeToolEnabled: Boolean = true,
): List<AgentTool> {
    return CommonAgentTools.filter { tool ->
        when (tool.id) {
            "internet_search" -> internetSearchEnabled
            "time_tool" -> timeToolEnabled
            else -> true
        }
    }
}

/** OpenAI-compatible tools[] entry for chat/completions. */
fun agentToolJson(tool: AgentTool): String {
    return when (tool.id) {
        "internet_search" -> buildString {
            val description = tool.description +
                " Required: a non-empty query string, for example Maybank latest news." +
                " The phone searches, then opens a few result pages and reads them." +
                " The results, including page text, are notes for you. Answer the user in your own words." +
                " Do not paste the result list, the page text, or the query."
            append("{\"type\":\"function\",\"function\":{")
            append("\"name\":").append(jsonString(tool.id)).append(',')
            append("\"description\":").append(jsonString(description)).append(',')
            append("\"parameters\":{")
            append("\"type\":\"object\",")
            append("\"properties\":{\"query\":{\"type\":\"string\",")
            append("\"description\":").append(jsonString("Web search query")).append("}},")
            append("\"required\":[\"query\"],")
            append("\"additionalProperties\":false")
            append("}}}")
        }
        "time_tool" -> buildString {
            val description = "Read this phone's clock, or convert a time to any city or timezone on Earth. " +
                "Pass place, time, and from_place. Use an empty string for a field you do not need. " +
                "place empty means the phone timezone. time empty means now. from_place empty means the phone timezone."
            append("{\"type\":\"function\",\"function\":{")
            append("\"name\":").append(jsonString(tool.id)).append(',')
            append("\"description\":").append(jsonString(description)).append(',')
            append("\"parameters\":{\"type\":\"object\",\"properties\":{")
            append("\"place\":{\"type\":\"string\",\"description\":")
            append(jsonString("Destination city, region, or IANA timezone. Empty string means the phone timezone. Examples: Tokyo, New York, Europe/Paris, UTC+8."))
            append("},")
            append("\"time\":{\"type\":\"string\",\"description\":")
            append(jsonString("Clock time to convert. Empty string means now. Examples: 15:30, 3:30 PM, 2026-01-15 15:30."))
            append("},")
            append("\"from_place\":{\"type\":\"string\",\"description\":")
            append(jsonString("Where that clock time is from. Empty string means the phone timezone. Examples: London, America/Los_Angeles, UTC."))
            append("}},")
            append("\"required\":[\"place\",\"time\",\"from_place\"],")
            append("\"additionalProperties\":false")
            append("}}}")
        }
        else -> buildString {
            append("{\"type\":\"function\",\"function\":{")
            append("\"name\":").append(jsonString(tool.id)).append(',')
            append("\"description\":").append(jsonString(tool.description)).append(',')
            append("\"parameters\":").append(tool.parametersJson ?: "{\"type\":\"object\",\"properties\":{}}")
            append("}}")
        }
    }
}

fun toolsPromptSection(tools: List<AgentTool>): String {
    if (tools.isEmpty()) return ""
    return buildString {
        appendLine()
        appendLine("Available tools (use them when you need current facts or verification):")
        for (tool in tools) {
            append("- ")
            append(tool.id)
            append(": ")
            appendLine(tool.description)
        }
        if (tools.any { it.id == "internet_search" }) {
            append("internet_search is already available. Pass a non-empty query string when you need current facts. The phone searches, then opens a few result pages and reads them. The results are notes for you. Then answer the user in your own words. Do not paste the result list, the page text, or the query, and do not describe the call format.")
        }
        if (tools.any { it.id == "time_tool" }) {
            if (tools.any { it.id == "internet_search" }) {
                appendLine()
            }
            append("time_tool is already available. Use it for the current time and to convert a clock time to any place on Earth. Pass place, time, and from_place, with an empty string for any field you do not need. Then write the time in the reply. Do not guess the clock, and do not describe the call format.")
        }
            if (tools.any { it.parametersJson != null }) {
            appendLine()
            append("Tools named server__tool come from connected MCP servers. Call them when they fit the request, then answer in your own words.")
        }
    }.trimEnd()
}
