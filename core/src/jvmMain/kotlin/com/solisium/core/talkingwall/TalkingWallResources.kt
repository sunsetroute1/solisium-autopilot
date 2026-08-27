package com.solisium.core.talkingwall

object TalkingWallResources {
    fun communityJson(): String =
        TalkingWallResources::class.java.getResourceAsStream("/talking-wall-community.json")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("missing classpath resource /talking-wall-community.json")
}
