package com.solisium.core.domain

/** A selectable gear trait for catalog search (warehouse id + display name). */
data class CatalogTraitOption(
    val traitId: String,
    val label: String,
    val statKey: String = "",
)
