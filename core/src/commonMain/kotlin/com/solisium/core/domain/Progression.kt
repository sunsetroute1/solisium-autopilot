package com.solisium.core.domain

/** Reset cadence for a progression task. User marks completion manually in v0. */
enum class ProgressionCadence(val label: String) {
    Daily("Daily"),
    Weekly("Weekly"),
    Monthly("Monthly"),
    Always("Ongoing"),
    OneTime("One-time"),
}

/** How easy the task is relative to your current sheet — green / yellow / red. */
enum class ProgressionEase(val label: String) {
    Easy("Easy"),
    Moderate("Moderate"),
    Hard("Hard"),
}

/** Rough effort or coordination required, 1 = quick solo, 3 = demanding. */
enum class ProgressionDifficulty(val stars: Int, val label: String) {
    Light(1, "Light"),
    Standard(2, "Standard"),
    Heavy(3, "Heavy"),
}

data class ProgressionTaskTemplate(
    val id: String,
    val title: String,
    val detail: String,
    val cadence: ProgressionCadence,
    val category: String,
    val baseEase: ProgressionEase,
    val difficulty: ProgressionDifficulty,
    val progressionValue: Int,
    val minLevel: Int = 1,
    val tokenTags: Set<String> = emptySet(),
)

data class ProgressionRecommendation(
    val id: String,
    val title: String,
    val detail: String,
    val cadence: ProgressionCadence,
    val category: String,
    val ease: ProgressionEase,
    val difficulty: ProgressionDifficulty,
    val progressionValue: Int,
    val priorityScore: Int,
    val completed: Boolean,
    val source: String,
    val reasons: List<String> = emptyList(),
)

data class ProgressionCharacterSnapshot(
    val id: String?,
    val name: String?,
    val level: Long?,
    val combatPower: Long?,
    val gearScore: Long?,
    val buildGoalLabel: String?,
    val classLabel: String?,
)

data class ProgressionPlan(
    val character: ProgressionCharacterSnapshot,
    val recommendations: List<ProgressionRecommendation>,
    val completedCount: Int,
    val openCount: Int,
    val notes: List<String>,
    val live: LiveProgressionSnapshot? = null,
)
