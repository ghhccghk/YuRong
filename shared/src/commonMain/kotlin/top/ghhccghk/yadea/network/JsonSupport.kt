package top.ghhccghk.yadea.network

/** 只基于索引扫描的轻量 JSON 字段提取，用于固定 API 响应。 */
internal object JsonSupport {

    /** Read one root-object scalar without searching nested metadata or truncating numbers. */
    fun topLevelScalarText(json: String, key: String): String? {
        fun stringEnd(start: Int): Int? {
            var escaped = false
            for (i in start + 1 until json.length) {
                val c = json[i]
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') return i + 1
            }
            return null
        }
        var i = 0
        fun skipWhitespace() { while (i < json.length && json[i].isWhitespace()) i++ }
        skipWhitespace()
        if (json.getOrNull(i++) != '{') return null
        while (i < json.length) {
            skipWhitespace()
            if (json.getOrNull(i) != '"') return null
            val keyEnd = stringEnd(i) ?: return null
            val currentKey = unescape(json.substring(i + 1, keyEnd - 1))
            i = keyEnd
            skipWhitespace()
            if (json.getOrNull(i++) != ':') return null
            skipWhitespace()
            val start = i
            when (json.getOrNull(i)) {
                '"' -> i = stringEnd(i) ?: return null
                '{' -> i += (readBalanced(json, i, '{', '}') ?: return null).length
                '[' -> i += (readBalanced(json, i, '[', ']') ?: return null).length
                else -> while (i < json.length && json[i] != ',' && json[i] != '}') i++
            }
            val literal = json.substring(start, i).trim()
            skipWhitespace()
            if (json.getOrNull(i) != ',' && json.getOrNull(i) != '}') return null
            if (currentKey == key) {
                return when {
                    literal.startsWith('"') -> unescape(literal.substring(1, literal.length - 1))
                    literal == "true" || literal == "false" -> literal
                    Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").matches(literal) -> literal
                    else -> null
                }
            }
            if (json[i++] == '}') return null
        }
        return null
    }

    fun objectField(json: String, key: String): String? {
        val index = findKey(json, key) ?: return null
        val start = json.indexOf('{', index)
        if (start < 0) return null
        return readBalanced(json, start, '{', '}')
    }

    fun firstArrayObject(json: String, key: String): String? {
        val index = findKey(json, key) ?: return null
        val arrayStart = json.indexOf('[', index)
        if (arrayStart < 0) return null
        val objectStart = json.indexOf('{', arrayStart)
        if (objectStart < 0) return null
        return readBalanced(json, objectStart, '{', '}')
    }

    fun arrayObjects(json: String, key: String): List<String> {
        val index = findKey(json, key) ?: return emptyList()
        val arrayStart = json.indexOf('[', index)
        if (arrayStart < 0) return emptyList()
        val result = mutableListOf<String>()
        var i = arrayStart + 1
        while (i < json.length) {
            while (i < json.length && (json[i].isWhitespace() || json[i] == ',')) i++
            if (i >= json.length || json[i] == ']') break
            if (json[i] != '{') {
                i++
                continue
            }
            val item = readBalanced(json, i, '{', '}') ?: break
            result += item
            i += item.length
        }
        return result
    }

    fun string(json: String, key: String): String? {
        val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            .find(json) ?: return null
        return unescape(match.groupValues[1])
    }

    fun int(json: String, key: String): Int? =
        numberText(json, key)?.toIntOrNull()

    fun long(json: String, key: String): Long? =
        numberText(json, key)?.toLongOrNull()

    fun boolean(json: String, key: String): Boolean? =
        Regex("\"${Regex.escape(key)}\"\\s*:\\s*(true|false)")
            .find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()

    fun numberText(json: String, key: String): String? =
        Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
            .find(json)?.groupValues?.get(1)

    private fun findKey(json: String, key: String): Int? {
        val index = json.indexOf("\"$key\"")
        return index.takeIf { it >= 0 }
    }

    private fun readBalanced(json: String, start: Int, open: Char, close: Char): String? {
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < json.length) {
            val c = json[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return json.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    private fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c != '\\' || i >= value.length) {
                out.append(c)
                continue
            }
            when (val escaped = value[i++]) {
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                '/' -> out.append('/')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (i + 4 <= value.length) {
                        value.substring(i, i + 4).toIntOrNull(16)?.let { out.append(it.toChar()) }
                        i += 4
                    }
                }
                else -> out.append(escaped)
            }
        }
        return out.toString()
    }
}
