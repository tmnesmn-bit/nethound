package com.nethound.app

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class Host(
    val ip: String,
    val hostname: String?,
    val millis: Long,
    val isGateway: Boolean,
    val isSelf: Boolean
)

/**
 * Finds devices on the local Wi-Fi network with a ping/connect sweep of the /24.
 * Android hides other devices' MAC addresses from apps (privacy rule since
 * Android 10), so results are IP + name + speed — vendor lookup only works for
 * the router, whose MAC the system does expose.
 */
object NetScanner {

    private val PROBE_PORTS = intArrayOf(80, 443, 445, 22, 8009, 62078, 8080)

    fun sweep(
        selfIp: String,
        gatewayIp: String,
        cancelled: AtomicBoolean,
        onProgress: (done: Int, found: Int) -> Unit
    ): List<Host> {
        val base = selfIp.substringBeforeLast('.')
        val found = ConcurrentLinkedQueue<Host>()
        val pool = Executors.newFixedThreadPool(40)
        var done = 0

        for (i in 1..254) {
            val ip = "$base.$i"
            pool.execute {
                if (cancelled.get()) return@execute
                val t0 = System.currentTimeMillis()
                if (alive(ip)) {
                    val ms = System.currentTimeMillis() - t0
                    val name = try {
                        val h = InetAddress.getByName(ip).canonicalHostName
                        if (h == ip) null else h
                    } catch (e: Exception) { null }
                    found.add(Host(ip, name, ms, ip == gatewayIp, ip == selfIp))
                }
                synchronized(this) { done++ }
                onProgress(done, found.size)
            }
        }
        pool.shutdown()
        pool.awaitTermination(90, TimeUnit.SECONDS)
        return found.sortedBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 999 }
    }

    private fun alive(ip: String): Boolean {
        try {
            if (InetAddress.getByName(ip).isReachable(300)) return true
        } catch (e: Exception) { }
        for (port in PROBE_PORTS) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), 200)
                    return true
                }
            } catch (e: Exception) { }
        }
        return false
    }
}
