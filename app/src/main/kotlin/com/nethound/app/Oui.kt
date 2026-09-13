package com.nethound.app

import android.content.Context

/**
 * MAC address -> maker name, using the official IEEE OUI registry
 * (trimmed to common consumer brands, bundled as assets/oui.txt).
 */
object Oui {
    private var map: HashMap<String, String>? = null

    fun load(ctx: Context) {
        if (map != null) return
        val m = HashMap<String, String>(16000)
        try {
            ctx.assets.open("oui.txt").bufferedReader().forEachLine { line ->
                val i = line.indexOf(',')
                if (i > 0) m[line.substring(0, i)] = line.substring(i + 1)
            }
        } catch (e: Exception) { /* lookup just returns null */ }
        map = m
    }

    /** true if the device is hiding behind a per-network random address */
    fun isRandomized(mac: String): Boolean {
        if (mac.length < 2) return false
        return mac[1].uppercaseChar() in charArrayOf('2', '6', 'A', 'E')
    }

    fun vendor(mac: String?): String? {
        if (mac.isNullOrBlank()) return null
        val clean = mac.replace(":", "").replace("-", "").uppercase()
        if (clean.length < 6) return null
        if (isRandomized(mac.replace(":", "").replace("-", ""))) return "Private (randomized) address"
        return map?.get(clean.substring(0, 6))
    }
}
