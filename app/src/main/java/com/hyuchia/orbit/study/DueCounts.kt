package com.hyuchia.orbit.study

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.page.MapNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps Home's count of a map's cards due to the cards it has, while the map
 * is open: as its page reads it whole (`ideas`; null while it hasn't, or
 * can't, as with a map Orbit can't fully read), the ideas with answers, here
 * or on another device, that aren't cards now are noted in its details
 * (`details`, as they are now), by `note`, given the map's cards. Home then
 * leaves them out (`MapMetadata.studyNotCards`); their answers stay. They're
 * noted again only when they change.
 */
fun noteCards(scope: CoroutineScope, ideas: Flow<List<MapNode>?>, details: Flow<MapMetadata?>, note: suspend (cards: Set<String>) -> Unit): Job = scope.launch {
    val cards = ideas.distinctUntilChanged().map { nodes -> nodes?.let(::mapCards) }.distinctUntilChanged()

    combine(cards, details) { now, metadata -> now to metadata }.collect { (now, metadata) ->
        if (now != null && metadata != null && notCards(metadata.studyProgress, now) != metadata.studyNotCards.orEmpty().toSet()) {
            note(now)
        }
    }
}
