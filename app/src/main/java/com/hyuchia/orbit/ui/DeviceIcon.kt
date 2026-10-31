package com.hyuchia.orbit.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.hyuchia.orbit.data.FolderDevice
import com.hyuchia.orbit.data.deviceKind

/** A device of this kind (see [FolderDevice.kind]) as an icon: a phone, a tablet or a computer. */
fun deviceIcon(kind: String): ImageVector = when (kind) {
    FolderDevice.TABLET -> Icons.Outlined.TabletAndroid
    FolderDevice.COMPUTER -> Icons.Outlined.Computer
    else -> Icons.Outlined.PhoneAndroid
}

/** This device as an icon, so "this device" shows which one: a phone, a tablet or a Chromebook. */
@Composable
fun thisDeviceIcon(): ImageVector {
    val context = LocalContext.current
    val smallestWidth = LocalConfiguration.current.smallestScreenWidthDp
    return remember(smallestWidth) { deviceIcon(deviceKind(context, smallestWidth)) }
}
