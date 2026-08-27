package com.solisium.core.json

sealed class JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data object Null : JsonValue()

    fun child(key: String): JsonValue? = (this as? Obj)?.fields?.get(key)

    fun str(key: String): String? = when (val value = child(key)) {
        is Str -> value.value
        else -> null
    }

    fun long(key: String): Long? = when (val value = child(key)) {
        is Num -> value.value.toLong()
        is Str -> value.value.toLongOrNull()
        else -> null
    }

    fun double(key: String): Double? = when (val value = child(key)) {
        is Num -> value.value
        is Str -> value.value.toDoubleOrNull()
        else -> null
    }

    fun bool(key: String): Boolean? = when (val value = child(key)) {
        is Bool -> value.value
        is Num -> value.value != 0.0
        else -> null
    }

    fun arr(key: String): List<JsonValue> = when (val value = child(key)) {
        is Arr -> value.items
        else -> emptyList()
    }

    fun obj(key: String): Obj? = child(key) as? Obj

    /** Numeric fields of this object in document order; non-objects yield nothing. */
    fun numbers(): List<Pair<String, Double>> = (this as? Obj)?.fields
        ?.mapNotNull { (key, value) -> (value as? Num)?.let { key to it.value } }
        ?: emptyList()

    fun asObj(): Obj = this as? Obj ?: throw JsonParseException("expected object")

    fun strAny(vararg keys: String): String? {
        for (key in keys) {
            str(key)?.let { return it }
        }
        return null
    }

    fun longAny(vararg keys: String): Long? {
        for (key in keys) {
            long(key)?.let { return it }
        }
        return null
    }

    fun boolAny(vararg keys: String): Boolean? {
        for (key in keys) {
            bool(key)?.let { return it }
        }
        return null
    }
}

class JsonParseException(message: String) : IllegalArgumentException(message)

object JsonParser {
    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWs()
        if (!parser.done()) throw JsonParseException("trailing content at ${parser.index}")
        return value
    }

    private class Parser(private val text: String) {
        var index: Int = 0

        fun done(): Boolean = index >= text.length

        fun skipWs() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun parseValue(): JsonValue {
            skipWs()
            if (done()) throw JsonParseException("unexpected end")
            return when (val c = text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't', 'f' -> parseBool()
                'n' -> parseNull()
                '-' -> parseNumber()
                in '0'..'9' -> parseNumber()
                else -> throw JsonParseException("unexpected '$c' at $index")
            }
        }

        private fun parseObject(): JsonValue.Obj {
            expect('{')
            val fields = linkedMapOf<String, JsonValue>()
            skipWs()
            if (peek() == '}') {
                index++
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                fields[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return JsonValue.Obj(fields)
                    }
                    else -> throw JsonParseException("expected ',' or '}' at $index")
                }
            }
        }

        private fun parseArray(): JsonValue.Arr {
            expect('[')
            val items = ArrayList<JsonValue>()
            skipWs()
            if (peek() == ']') {
                index++
                return JsonValue.Arr(items)
            }
            while (true) {
                items.add(parseValue())
                skipWs()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return JsonValue.Arr(items)
                    }
                    else -> throw JsonParseException("expected ',' or ']' at $index")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (!done()) {
                when (val c = text[index++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (done()) throw JsonParseException("unterminated escape")
                        when (val e = text[index++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) throw JsonParseException("bad unicode escape")
                                val hex = text.substring(index, index + 4)
                                out.append(hex.toInt(16).toChar())
                                index += 4
                            }
                            else -> throw JsonParseException("bad escape \\$e")
                        }
                    }
                    else -> out.append(c)
                }
            }
            throw JsonParseException("unterminated string")
        }

        private fun parseNumber(): JsonValue.Num {
            val start = index
            if (peek() == '-') index++
            while (index < text.length && text[index] in '0'..'9') index++
            if (peek() == '.') {
                index++
                while (index < text.length && text[index] in '0'..'9') index++
            }
            val raw = text.substring(start, index)
            return JsonValue.Num(raw.toDoubleOrNull() ?: throw JsonParseException("bad number $raw"))
        }

        private fun parseBool(): JsonValue.Bool {
            return when {
                match("true") -> JsonValue.Bool(true)
                match("false") -> JsonValue.Bool(false)
                else -> throw JsonParseException("expected true/false at $index")
            }
        }

        private fun parseNull(): JsonValue.Null {
            if (!match("null")) throw JsonParseException("expected null at $index")
            return JsonValue.Null
        }

        private fun match(token: String): Boolean {
            if (text.startsWith(token, index)) {
                index += token.length
                return true
            }
            return false
        }

        private fun expect(c: Char) {
            skipWs()
            if (peek() != c) throw JsonParseException("expected '$c' at $index")
            index++
        }

        private fun peek(): Char? = if (done()) null else text[index]
    }
}
