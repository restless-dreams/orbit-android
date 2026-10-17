package com.hyuchia.orbit.nearby

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hyuchia.orbit.Logs

/**
 * Another device with Orbit open on the same network, where to reach it,
 * and the key maps sent to it are sealed to, when its advertisement has one.
 */
data class NearbyDevice(val name: String, val address: String, val key: String? = null)

/** A resolved advertisement as a device to send maps to, or null when it's this launch of Orbit or has no address. */
internal fun nearbyDevice(name: String, attributes: Map<String, ByteArray?>, host: String?, port: Int, selfId: String): NearbyDevice? {
    if (host == null || attributes[SERVICE_ID]?.decodeToString() == selfId) {
        return null
    }

    return NearbyDevice(name, "$host:$port", attributes[SERVICE_KEY]?.decodeToString())
}

/**
 * The devices with Orbit open on this network, found with network service
 * discovery (no Google services needed), other than this one, `selfId`.
 * They come and go as Orbit opens and closes on them.
 */
@Composable
fun rememberNearbyDevices(selfId: String): List<NearbyDevice> {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(NsdManager::class.java) }
    var devices by remember { mutableStateOf(emptyMap<String, NearbyDevice>()) }

    DisposableEffect(manager) {
        val main = Handler(Looper.getMainLooper())
        // One resolution at a time: the manager refuses a second while one runs.
        val unresolved = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        var stopped = false

        fun resolveNext() {
            if (resolving || stopped) {
                return
            }

            val next = unresolved.removeFirstOrNull() ?: return
            resolving = true
            @Suppress("DEPRECATION")
            manager.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        main.post {
                            resolving = false
                            val device = nearbyDevice(info.serviceName, info.attributes, info.host?.hostAddress, info.port, selfId)

                            if (!stopped && device != null) {
                                devices = devices + (info.serviceName to device)
                            }

                            resolveNext()
                        }
                    }

                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                        main.post {
                            resolving = false
                            resolveNext()
                        }
                    }
                },
            )
        }

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post {
                    unresolved.addLast(info)
                    resolveNext()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { devices = devices - info.serviceName }
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, error: Int) {
                Logs.warn(TAG, "Couldn't look for nearby devices ($error)")
            }

            override fun onStopDiscoveryFailed(serviceType: String, error: Int) = Unit
        }

        val started = runCatching { manager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }.isSuccess

        onDispose {
            stopped = true

            if (started) {
                runCatching { manager?.stopServiceDiscovery(discovery) }
            }
        }
    }

    return devices.values.sortedBy { it.name.lowercase() }
}

private const val TAG = "NearbyDevices"
