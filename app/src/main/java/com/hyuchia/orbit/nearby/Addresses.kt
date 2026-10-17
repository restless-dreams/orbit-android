package com.hyuchia.orbit.nearby

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/**
 * The beginnings of the names of the network interfaces people in a room
 * share, in the order to offer them: Wi-Fi, a hotspot this device shares,
 * a cable, USB tethering. Mobile data's interfaces (rmnet, ccmni and the
 * like) and VPNs aren't among them.
 */
private val ROOM_INTERFACES = listOf("wlan", "swlan", "softap", "ap", "eth", "rndis", "ncm", "usb")

/** How long the interface each address is on is trusted before it's looked up again. */
private const val INTERFACES_LIFETIME_MILLISECONDS = 10_000L
/** An address no interface has is looked up again at most this often. */
private const val INTERFACES_RETRY_MILLISECONDS = 1_000L

/** Whether a network interface, by its name, is one a room shares: Wi-Fi, a hotspot, a cable or USB tethering. */
internal fun isRoomInterface(name: String?): Boolean = name != null && ROOM_INTERFACES.any { name.startsWith(it) }

/**
 * Whether an address is one a device in the room can have: a private range,
 * as Wi-Fi and hotspots hand out, or link-local. Never a loopback address,
 * which any app on this device can use, nor the internet, nor mobile
 * carriers' shared range (100.64.0.0/10).
 */
fun isRoomAddress(address: InetAddress?): Boolean {
    if (address == null) {
        return false
    }

    val plain = unmapped(address)

    if (plain.isLoopbackAddress || plain.isAnyLocalAddress || plain.isMulticastAddress) {
        return false
    }

    if (plain.isLinkLocalAddress || plain.isSiteLocalAddress) {
        return true
    }

    // Unique local addresses, fc00::/7, are IPv6's private range.
    return plain is Inet6Address && (plain.address[0].toInt() and 0xFE) == 0xFC
}

/** An IPv4 address carried in IPv6 (::ffff:192.168.1.5) as itself; any other address as it is. */
internal fun unmapped(address: InetAddress): InetAddress {
    val bytes = address.address
    val mapped = address is Inet6Address && bytes.take(10).all { it.toInt() == 0 } && bytes[10].toInt() == -1 && bytes[11].toInt() == -1
    return if (mapped) InetAddress.getByAddress(bytes.copyOfRange(12, 16)) else address
}

/** Which connections a server takes: by the address on this device each arrived at, and the address it came from. */
fun interface ConnectionPolicy {
    fun admits(local: InetAddress, remote: InetAddress): Boolean
}

/**
 * Connections from the room only: arrived on Wi-Fi, this device's hotspot,
 * a cable or USB tethering, never mobile data; from an address in the room;
 * and neither end this device's loopback. `interfaceOf` names the interface
 * an address of this device is on.
 */
class RoomNetworks(private val interfaceOf: (InetAddress) -> String? = InterfaceNames()::of) : ConnectionPolicy {
    override fun admits(local: InetAddress, remote: InetAddress): Boolean {
        val here = unmapped(local)
        return !here.isLoopbackAddress && isRoomAddress(remote) && isRoomInterface(interfaceOf(here))
    }
}

/**
 * The interface each of this device's addresses is on, kept for a few
 * seconds: a connection is checked as it arrives, and listing the
 * interfaces for each one would slow a busy room down.
 */
class InterfaceNames(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val lookUp: () -> Map<String, String> = ::interfacesByAddress,
) {
    @Volatile private var names: Map<String, String> = emptyMap()
    @Volatile private var lookedUp: Long? = null

    fun of(address: InetAddress): String? {
        val key = addressKey(address)
        val now = clock()
        val last = lookedUp
        val known = names[key]
        val stale = last == null || now - last >= INTERFACES_LIFETIME_MILLISECONDS
        val retry = known == null && (last == null || now - last >= INTERFACES_RETRY_MILLISECONDS)

        if (stale || retry) {
            synchronized(this) {
                names = lookUp()
                lookedUp = now
            }

            return names[key]
        }

        return known
    }
}

/** Every address of this device, by the interface it's on. */
private fun interfacesByAddress(): Map<String, String> {
    val interfaces = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
    } catch (unavailable: SocketException) {
        return emptyMap()
    }

    return interfaces.flatMap { network -> network.inetAddresses.toList().map { addressKey(it) to network.name } }.toMap()
}

/** An address's bytes, which name it whatever its scope or the way it's written. */
private fun addressKey(address: InetAddress): String = unmapped(address).address.joinToString(".") { (it.toInt() and 0xFF).toString() }

/**
 * The address other devices in the room reach this one at: its own on the
 * Wi-Fi, on the hotspot it's sharing, or on a cable or USB tethering. Null
 * on mobile data alone, which doesn't take connections from other devices.
 */
fun localAddress(): String? {
    val interfaces = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
    } catch (unavailable: SocketException) {
        return null
    }

    val candidates = interfaces
        .filter { network -> isRoomInterface(network.name) && runCatching { network.isUp && !network.isLoopback && !network.isVirtual }.getOrDefault(false) }
        .flatMap { network ->
            network.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.map { network.name to it.hostAddress }
        }

    // Wi-Fi first, then a hotspot, then a cable or USB: the networks people in a room share.
    return candidates
        .sortedBy { (name, _) -> ROOM_INTERFACES.indexOfFirst { name.startsWith(it) } }
        .firstOrNull()?.second
}

private val IPV4 = Regex("(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(\\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}")

/**
 * An IP address written out, as in a URL's host: 192.168.1.5, or IPv6
 * with or without its brackets and zone. Null for anything else, names
 * included: nothing is ever looked up.
 */
internal fun ipLiteral(text: String): InetAddress? {
    val bare = text.removePrefix("[").removeSuffix("]").substringBefore('%')

    val bytes = when {
        IPV4.matches(bare) -> bare.split('.').map { it.toInt().toByte() }.toByteArray()
        ':' in bare -> ipv6Bytes(bare)
        else -> null
    } ?: return null

    return InetAddress.getByAddress(bytes)
}

/** The 16 bytes of an IPv6 address written out, with :: and a final IPv4 part allowed; null when it isn't one. */
private fun ipv6Bytes(text: String): ByteArray? {
    val halves = text.split("::")

    if (halves.size > 2) {
        return null
    }

    val compressed = halves.size == 2
    val left = ipv6Groups(halves[0], lastMayBeIpv4 = !compressed) ?: return null
    val right = if (compressed) ipv6Groups(halves[1], lastMayBeIpv4 = true) ?: return null else emptyList()
    val count = left.size + right.size

    if ((compressed && count > 7) || (!compressed && count != 8)) {
        return null
    }

    val groups = left + List(8 - count) { 0 } + right
    val bytes = ByteArray(16)

    groups.forEachIndexed { index, group ->
        bytes[index * 2] = (group shr 8).toByte()
        bytes[index * 2 + 1] = group.toByte()
    }

    return bytes
}

private fun ipv6Groups(part: String, lastMayBeIpv4: Boolean): List<Int>? {
    if (part.isEmpty()) {
        return emptyList()
    }

    val pieces = part.split(':')
    val groups = ArrayList<Int>()

    pieces.forEachIndexed { index, piece ->
        if (lastMayBeIpv4 && index == pieces.lastIndex && '.' in piece) {
            if (!IPV4.matches(piece)) {
                return null
            }

            val octets = piece.split('.').map { it.toInt() }
            groups += (octets[0] shl 8) or octets[1]
            groups += (octets[2] shl 8) or octets[3]
        } else {
            if (piece.length !in 1..4 || !piece.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                return null
            }

            groups += piece.toInt(16)
        }
    }

    return groups
}

/**
 * Whether a request's Host names the address and port its connection
 * arrived at, as an IP literal. A website whose name was pointed at this
 * device (DNS rebinding) sends its own name, so it never matches.
 */
internal fun hostMatches(host: String?, local: InetAddress, port: Int): Boolean {
    if (host.isNullOrEmpty()) {
        return false
    }

    val colon = if (host.startsWith("[")) {
        host.indexOf(']').takeIf { it > 0 && host.getOrNull(it + 1) == ':' }?.plus(1)
    } else {
        host.lastIndexOf(':').takeIf { it > 0 }
    } ?: return false

    val portText = host.substring(colon + 1)

    if (portText.isEmpty() || portText.length > 5 || !portText.all { it in '0'..'9' } || portText.toInt() != port) {
        return false
    }

    val named = ipLiteral(host.substring(0, colon)) ?: return false
    return named.address.contentEquals(unmapped(local).address)
}
