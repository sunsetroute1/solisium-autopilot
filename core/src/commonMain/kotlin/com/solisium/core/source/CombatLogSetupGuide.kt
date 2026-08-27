package com.solisium.core.source

/**
 * In-game steps for T&L's Combat Analyzer / detailed combat log (update 3.11+).
 * Logging is configured through the Ring Menu, not Gameplay settings.
 */
object CombatLogSetupGuide {
    const val title = "Enable Combat Analyzer & logging"

    val steps: List<String> = listOf(
        "Esc → Settings → Shortcuts → Ring Menu Settings → Quick Menu tab",
        "In the left list, select QuickMenu2 (or free a slot on QuickMenu1 — all 8 slots full means click a slot to replace it)",
        "Click a ring slot → pick from the list: Turn On Combat Meter, Record Combat Log, and Combat Analyzer (three separate entries)",
        "Save, then Edit Complete — in-game Tab → Ring Menu → switch pages with mouse wheel until you see those icons",
        "Before fighting: tap Record Combat Log to start (not Combat Analyzer — that only shows the breakdown UI)",
        "After fighting: leave combat or tap Record Combat Log again to stop — that commits the .txt file",
        "Return here and tap Scan & import",
    )

    const val guildRaidNote =
        "Guild raid pulls may reset the Combat Analyzer HUD but still not produce log files. " +
            "Test on a practice dummy first; if that works, the guild raid content type is likely unsupported."

    const val resetNote =
        "Reset on the Combat Meter / Combat Analyzer clears the on-screen encounter. " +
            "Keep Record Combat Log toggled on separately for new .txt files."

    val logFolderNote =
        "Logs save to %LOCALAPPDATA%\\TL\\Saved\\CombatLogs"
}
