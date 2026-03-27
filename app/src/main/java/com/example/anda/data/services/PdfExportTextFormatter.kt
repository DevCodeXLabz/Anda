package com.example.anda.data.services

object PdfExportTextFormatter {

    private val scriptStyleRegex = Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1>")
    private val commentRegex = Regex("(?is)<!--.*?-->")
    private val breakRegex = Regex("(?i)<br\\s*/?>")
    private val horizontalRuleRegex = Regex("(?i)<hr\\s*/?>")
    private val listItemOpenRegex = Regex("(?i)<li\\b[^>]*>")
    private val listItemCloseRegex = Regex("(?i)</li\\s*>")
    private val rowOpenRegex = Regex("(?i)<tr\\b[^>]*>")
    private val rowCloseRegex = Regex("(?i)</tr\\s*>")
    private val cellOpenRegex = Regex("(?i)<t[dh]\\b[^>]*>")
    private val cellCloseRegex = Regex("(?i)</t[dh]\\s*>")
    private val blockOpenRegex = Regex(
        "(?i)<(?:address|article|aside|blockquote|body|caption|colgroup|dd|div|dl|dt|fieldset|figcaption|figure|footer|form|h[1-6]|header|html|legend|main|nav|ol|p|pre|section|table|tbody|thead|tfoot|ul)\\b[^>]*>"
    )
    private val blockCloseRegex = Regex(
        "(?i)</(?:address|article|aside|blockquote|body|caption|colgroup|dd|div|dl|dt|fieldset|figcaption|figure|footer|form|h[1-6]|header|html|legend|main|nav|ol|p|pre|section|table|tbody|thead|tfoot|ul)\\s*>"
    )
    private val remainingTagRegex = Regex("(?is)<[^>]+>")
    private val lineWhitespaceRegex = Regex("[\\t\\u000B\\u000C\\u00A0 ]+")
    private val tablePipeRegex = Regex("\\s*\\|\\s*")
    private val trailingPipeRegex = Regex("(?:\\s*\\|\\s*)+$")
    private val leadingPipeRegex = Regex("^(?:\\s*\\|\\s*)+")
    private val decimalEntityRegex = Regex("&#(\\d+);")
    private val hexEntityRegex = Regex("&#x([0-9a-fA-F]+);")
    private val orderedListPrefixRegex = Regex("^\\d+\\.\\s+")

    fun normalizeForPdf(input: String): String {
        if (input.isBlank()) return ""

        var text = input
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        text = scriptStyleRegex.replace(text, "\n")
        text = commentRegex.replace(text, "\n")
        text = horizontalRuleRegex.replace(text, "\n${"─".repeat(48)}\n")
        text = breakRegex.replace(text, "\n")
        text = listItemOpenRegex.replace(text, "\n• ")
        text = listItemCloseRegex.replace(text, "")
        text = rowOpenRegex.replace(text, "\n")
        text = rowCloseRegex.replace(text, "\n")
        text = cellOpenRegex.replace(text, "")
        text = cellCloseRegex.replace(text, " | ")
        text = blockOpenRegex.replace(text, "\n")
        text = blockCloseRegex.replace(text, "\n")
        text = remainingTagRegex.replace(text, " ")
        text = decodeHtmlEntities(text)

        val normalizedLines = mutableListOf<String>()
        var lastWasBlank = false

        for (rawLine in text.split('\n')) {
            val normalizedLine = normalizeLine(rawLine)
            if (normalizedLine.isBlank()) {
                if (!lastWasBlank && normalizedLines.isNotEmpty()) {
                    normalizedLines += ""
                }
                lastWasBlank = true
            } else {
                normalizedLines += normalizedLine
                lastWasBlank = false
            }
        }

        return normalizedLines.joinToString("\n").trim()
    }

    fun wrapForPdf(text: String, maxChars: Int): List<String> {
        require(maxChars > 0) { "maxChars must be greater than zero" }
        if (text.isEmpty()) return listOf("")

        val wrappedLines = mutableListOf<String>()
        for (line in text.split('\n')) {
            if (line.isEmpty()) {
                wrappedLines += ""
                continue
            }
            wrapSingleLine(line, maxChars, wrappedLines)
        }

        return if (wrappedLines.isEmpty()) listOf("") else wrappedLines
    }

    private fun normalizeLine(line: String): String {
        val trimmedStart = line.trimStart()
        val orderedPrefix = orderedListPrefixRegex.find(trimmedStart)?.value.orEmpty()
        val prefix = when {
            trimmedStart.startsWith("• ") -> "• "
            orderedPrefix.isNotEmpty() -> orderedPrefix
            else -> ""
        }
        val content = if (prefix.isEmpty()) line else trimmedStart.removePrefix(prefix)
        val normalizedContent = content
            .replace(lineWhitespaceRegex, " ")
            .replace(tablePipeRegex, " | ")
            .replace(leadingPipeRegex, "")
            .replace(trailingPipeRegex, "")
            .trim()

        return if (normalizedContent.isEmpty()) {
            prefix.trimEnd()
        } else {
            prefix + normalizedContent
        }
    }

    private fun wrapSingleLine(line: String, maxChars: Int, output: MutableList<String>) {
        val leadingSpaces = line.takeWhile { it == ' ' }
        val trimmedLine = line.drop(leadingSpaces.length)
        val orderedPrefix = orderedListPrefixRegex.find(trimmedLine)?.value.orEmpty()
        val (firstPrefix, continuationPrefix, content) = when {
            trimmedLine.startsWith("• ") -> {
                Triple("$leadingSpaces• ", "$leadingSpaces  ", trimmedLine.removePrefix("• ").trim())
            }
            orderedPrefix.isNotEmpty() -> {
                val continuation = " ".repeat(orderedPrefix.length)
                Triple("$leadingSpaces$orderedPrefix", "$leadingSpaces$continuation", trimmedLine.removePrefix(orderedPrefix).trim())
            }
            else -> {
                Triple(leadingSpaces, leadingSpaces, trimmedLine.trim())
            }
        }

        if (content.isEmpty()) {
            output += firstPrefix.trimEnd()
            return
        }

        val words = content.split(Regex("\\s+")).filter { it.isNotEmpty() }
        var currentPrefix = firstPrefix
        var currentLine = StringBuilder()

        fun flushCurrentLine() {
            if (currentLine.isNotEmpty()) {
                output += currentPrefix + currentLine.toString()
                currentLine = StringBuilder()
                currentPrefix = continuationPrefix
            }
        }

        fun appendWord(word: String) {
            var remaining = word
            while (remaining.isNotEmpty()) {
                val availableChars = (maxChars - currentPrefix.length - currentLine.length - if (currentLine.isEmpty()) 0 else 1)
                    .coerceAtLeast(1)

                if (remaining.length <= availableChars) {
                    if (currentLine.isNotEmpty()) currentLine.append(' ')
                    currentLine.append(remaining)
                    remaining = ""
                } else {
                    if (currentLine.isNotEmpty()) {
                        flushCurrentLine()
                        continue
                    }
                    currentLine.append(remaining.take(availableChars))
                    remaining = remaining.drop(availableChars)
                    flushCurrentLine()
                }
            }
        }

        for (word in words) {
            val separatorLength = if (currentLine.isEmpty()) 0 else 1
            val projectedLength = currentPrefix.length + currentLine.length + separatorLength + word.length
            if (projectedLength <= maxChars) {
                if (currentLine.isNotEmpty()) currentLine.append(' ')
                currentLine.append(word)
            } else {
                appendWord(word)
            }
        }

        if (currentLine.isNotEmpty()) {
            output += currentPrefix + currentLine.toString()
        }
    }

    private fun decodeHtmlEntities(text: String): String {
        var decoded = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")

        decoded = decimalEntityRegex.replace(decoded) { matchResult ->
            matchResult.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: matchResult.value
        }
        decoded = hexEntityRegex.replace(decoded) { matchResult ->
            matchResult.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: matchResult.value
        }

        return decoded
    }
}


