package com.solisium.core.meta

import com.solisium.core.domain.CommunityHit
import com.solisium.core.domain.CommunitySnapshot
import com.solisium.core.domain.DisplayName
import com.solisium.core.query.CatalogQuery

object CommunityOverlay {
    fun bind(snapshot: CommunitySnapshot, query: CatalogQuery, snapshotId: String): CommunitySnapshot {
        val names = (
            query.items(snapshotId).mapNotNull { DisplayName.of(it.name, it.sourceRowId) } +
                query.weapons(snapshotId).mapNotNull { DisplayName.of(it.name, it.sourceRowId) } +
                query.skills(snapshotId).mapNotNull { DisplayName.of(it.name, it.sourceRowId) }
            ).distinct()
        fun bindHits(hits: List<CommunityHit>): List<CommunityHit> = hits.map { hit ->
            val match = names.firstOrNull { TextNorm.likelySame(it, hit.name) }
            hit.copy(catalogName = match)
        }
        return snapshot.copy(
            items = bindHits(snapshot.items),
            skills = bindHits(snapshot.skills),
            builds = bindHits(snapshot.builds),
        )
    }
}
