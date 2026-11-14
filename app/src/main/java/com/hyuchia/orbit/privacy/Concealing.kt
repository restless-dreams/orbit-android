package com.hyuchia.orbit.privacy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.ui.Hint

/**
 * Whether private maps hide altogether now, rather than showing locked: Settings hides them while
 * they're locked (`hiding`), and they are. Then nothing shows they exist, wherever maps are listed,
 * named or counted, until they're unlocked.
 */
fun PrivateView.conceals(hiding: Boolean): Boolean = hiding && locked && lockable

/**
 * Whether what takes every map out of Orbit, private ones included (Export all, a folder to keep maps in), asks for
 * the phone's screen lock first: while one of `maps` is private and locked, it would leave without its lock. While
 * private maps hide (`hiding`), it asks whether any is there or not, so the question gives nothing away.
 */
fun PrivateView.asksBeforeEveryMap(maps: List<MapSummary>, hiding: Boolean): Boolean = conceals(hiding) || maps.any { hides(it) }

/**
 * How many maps already here to mention after importing `alreadyHere` maps, `privateAlreadyHere` of
 * them private: while private maps hide (`concealing`), the private ones aren't counted.
 */
fun mentionedAlreadyHere(alreadyHere: Int, privateAlreadyHere: Int, concealing: Boolean): Int =
    if (concealing) (alreadyHere - privateAlreadyHere).coerceAtLeast(0) else alreadyHere

/**
 * How many files to mention that an import brought into maps already here, `filledIn`, `privateFilledIn`
 * of them into private ones, or left out of them: while private maps hide (`concealing`), those aren't counted.
 */
fun mentionedFilledIn(filledIn: Int, privateFilledIn: Int, concealing: Boolean): Int =
    if (concealing) (filledIn - privateFilledIn).coerceAtLeast(0) else filledIn

/**
 * The way back to private maps while they hide: an open lock, as in Home's bar, that asks for the
 * phone's screen lock and shows them again. Wherever it's offered, it's offered whether any private
 * map exists or not, so it gives nothing away.
 */
@Composable
fun ShowPrivateMapsButton(onShow: () -> Unit) {
    val name = stringResource(R.string.private_show_private_maps)

    Hint(name) {
        IconButton(onClick = onShow) { Icon(Icons.Outlined.LockOpen, contentDescription = name) }
    }
}
