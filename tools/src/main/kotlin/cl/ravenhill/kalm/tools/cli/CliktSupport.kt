package cl.ravenhill.kalm.tools.cli

internal fun detectMissingOptionValue(
    args: List<String>,
    optionNames: Set<String>
): String? {
    for (index in args.indices) {
        val token = args[index]
        val optionName = token.substringBefore('=')
        if (optionName !in optionNames) {
            continue
        }
        if (token.contains('=') && token.substringAfter('=').isBlank()) {
            return "Missing value for option $optionName"
        }
        if (!token.contains('=')) {
            val next = args.getOrNull(index + 1)
            if (next == null || next.startsWith("-")) {
                return "Missing value for option $optionName"
            }
        }
    }
    return null
}

internal fun extractNoSuchOptionName(message: String): String {
    val marker = "option "
    val markerIndex = message.indexOf(marker)
    if (markerIndex < 0) {
        return message
    }
    return message.substring(markerIndex + marker.length).trim().trim('\'', '"')
}
