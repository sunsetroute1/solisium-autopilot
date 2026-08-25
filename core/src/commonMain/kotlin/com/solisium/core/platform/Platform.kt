package com.solisium.core.platform

expect fun randomUuid(): String

expect fun sha256Hex(text: String): String
