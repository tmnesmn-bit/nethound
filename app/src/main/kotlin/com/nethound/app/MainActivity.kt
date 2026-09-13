package com.nethound.app

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val BG = Color.parseColor("#0b1220")
    private val PANEL = Color.parseColor("#141d31")
    private val TEXT = Color.parseColor("#e8eefc")
    private val DIM = Color.parseColor("#8fa0c4")
    private val BLUE = Color.parseColor("#4da3ff")
    private val GREEN = Color.parseColor("#3ddc84")
    private val AMBER = Color.parseColor("#ffb347")
    private val RED = Color.parseColor("#ff5c6c")

    private lateinit var wifi: WifiManager
    private lateinit var status: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var actionBtn: Button
    private lateinit var tabNets: Button
    private lateinit var tabLan: Button

    private var mode = "nets" // "nets" | "lan"
    private var lanHosts: List<Host> = emptyList()
    private var sweeping = AtomicBoolean(false)
    private val ui = Handler(Looper.getMainLooper())
    private val autoRefresh = object : Runnable {
        override fun run() {
            if (mode == "nets") { tryStartWifiScan(); showNetworks() }
            ui.postDelayed(this, 30_000)
        }
    }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { if (mode == "nets") showNetworks() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        Oui.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(16), dp(40), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "🌐 NetHound"
            textSize = 24f; setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Wi-Fi networks around you · devices on your network"
            textSize = 13f; setTextColor(DIM); gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(14))
        })

        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabNets = tabBtn("📶 Networks") { switchMode("nets") }
        tabLan = tabBtn("🖧 My network") { switchMode("lan") }
        tabRow.addView(tabNets, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabRow.addView(tabLan, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
        root.addView(tabRow)

        status = TextView(this).apply {
            textSize = 13f; setTextColor(DIM); gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(PANEL)
        }
        root.addView(status, lpMargin())

        actionBtn = Button(this).apply {
            textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(BLUE); setTextColor(Color.parseColor("#04101f"))
            setOnClickListener { onAction() }
        }
        root.addView(actionBtn, lpMargin())

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(listBox) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(TextView(this).apply {
            text = "Only scan networks you own or use. Modern phones hide behind random addresses on purpose — " +
                "\"Private (randomized) address\" is a privacy feature, not something suspicious. " +
                "Android also hides other devices' addresses from apps, so the My network list shows names and IPs, not MACs."
            textSize = 11f; setTextColor(DIM); setPadding(0, dp(10), 0, 0)
        })

        setContentView(root)
        switchMode("nets")
    }

    private fun tabBtn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; setOnClickListener { onClick() }
    }

    private fun lpMargin() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(8); bottomMargin = dp(4) }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        ui.post(autoRefresh)
        if (!hasPerms()) requestPermissions(neededPerms().toTypedArray(), 1)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(scanReceiver) } catch (e: Exception) { }
        ui.removeCallbacks(autoRefresh)
    }

    private fun neededPerms(): List<String> {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
        return p
    }

    private fun hasPerms() = neededPerms().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        if (hasPerms()) { tryStartWifiScan(); showNetworks() }
        else status.text = "Location permission is required to see Wi-Fi networks (an Android rule — NetHound never records your location)."
    }

    private fun switchMode(m: String) {
        mode = m
        tabNets.setBackgroundColor(if (m == "nets") BLUE else PANEL)
        tabNets.setTextColor(if (m == "nets") Color.parseColor("#04101f") else DIM)
        tabLan.setBackgroundColor(if (m == "lan") BLUE else PANEL)
        tabLan.setTextColor(if (m == "lan") Color.parseColor("#04101f") else DIM)
        if (m == "nets") {
            actionBtn.text = "Refresh"
            tryStartWifiScan()
            showNetworks()
        } else {
            actionBtn.text = if (sweeping.get()) "Scanning…" else "Scan my network"
            showLan()
        }
    }

    private fun onAction() {
        if (mode == "nets") { tryStartWifiScan(); showNetworks() }
        else startSweep()
    }

    private fun tryStartWifiScan() {
        if (!hasPerms()) return
        try { wifi.startScan() } catch (e: Exception) { }
    }

    // ---------- Networks tab ----------
    private fun showNetworks() {
        if (mode != "nets") return
        if (!hasPerms()) { status.text = "Waiting for permission…"; return }
        val results: List<ScanResult> = try { wifi.scanResults } catch (e: SecurityException) { emptyList() }
        val myBssid = try { wifi.connectionInfo?.bssid } catch (e: Exception) { null }
        val sorted = results.sortedByDescending { it.level }

        status.text = if (sorted.isEmpty())
            "Listening… (Android limits refreshes to every ~30 seconds)"
        else
            "${sorted.size} networks in range · strongest first"

        listBox.removeAllViews()
        for (r in sorted.take(60)) {
            val ssid = if (r.SSID.isNullOrBlank()) "(hidden network)" else r.SSID
            val mine = myBssid != null && myBssid.equals(r.BSSID, ignoreCase = true)
            val sec = security(r.capabilities ?: "")
            val vendor = Oui.vendor(r.BSSID)
            val row = panel()
            row.addView(TextView(this).apply {
                text = ssid + if (mine) "   ✓ your network" else ""
                textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (mine) GREEN else TEXT)
            })
            val sub = buildList {
                vendor?.let { add("router by $it") }
                add(band(r.frequency))
                add("${r.level} dBm ${bars(r.level)}")
                add(sec.first)
            }.joinToString(" · ")
            row.addView(TextView(this).apply {
                text = sub; textSize = 12f; setTextColor(DIM)
            })
            if (sec.second != null) {
                row.addView(TextView(this).apply {
                    text = sec.second
                    textSize = 12f; setTextColor(AMBER); typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(3), 0, 0)
                })
            }
            listBox.addView(row, lpMargin())
        }
    }

    /** returns (label, warning-or-null) */
    private fun security(caps: String): Pair<String, String?> = when {
        caps.contains("SAE") || caps.contains("WPA3") -> "WPA3 🔒" to null
        caps.contains("WPA2") -> "WPA2 🔒" to null
        caps.contains("WPA") -> "WPA (old) 🔒" to null
        caps.contains("OWE") -> "Enhanced Open 🔒" to null
        caps.contains("WEP") -> "WEP 🔓" to
            "⚠️ outdated WEP security — easily broken; if this is your network, upgrade the router"
        else -> "OPEN" to
            "⚠️ no password — others nearby can see which sites you visit; avoid sensitive logins or use a VPN"
    }

    private fun band(freq: Int): String = when {
        freq == 2484 -> "2.4 GHz ch 14"
        freq in 2400..2500 -> "2.4 GHz ch ${(freq - 2407) / 5}"
        freq in 4900..5900 -> "5 GHz ch ${(freq - 5000) / 5}"
        freq > 5900 -> "6 GHz"
        else -> "$freq MHz"
    }

    private fun bars(level: Int): String = when {
        level >= -55 -> "▂▄▆█"
        level >= -67 -> "▂▄▆"
        level >= -78 -> "▂▄"
        else -> "▂"
    }

    // ---------- My network tab ----------
    private fun startSweep() {
        if (sweeping.get()) return
        val info = try { wifi.connectionInfo } catch (e: Exception) { null }
        val dhcp = try { wifi.dhcpInfo } catch (e: Exception) { null }
        if (info == null || dhcp == null || dhcp.ipAddress == 0) {
            status.text = "Connect to Wi-Fi first, then scan."
            return
        }
        val selfIp = intToIp(dhcp.ipAddress)
        val gw = intToIp(dhcp.gateway)
        sweeping.set(true)
        actionBtn.text = "Scanning…"
        val cancelled = AtomicBoolean(false)
        thread {
            val hosts = NetScanner.sweep(selfIp, gw, cancelled) { done, found ->
                ui.post {
                    if (mode == "lan") status.text = "Checking addresses… $done of 254 · $found found"
                }
            }
            ui.post {
                lanHosts = hosts
                sweeping.set(false)
                if (mode == "lan") { actionBtn.text = "Scan my network"; showLan() }
            }
        }
    }

    private fun showLan() {
        if (mode != "lan") return
        val gwVendor = try { Oui.vendor(wifi.connectionInfo?.bssid) } catch (e: Exception) { null }
        status.text = when {
            sweeping.get() -> "Scanning…"
            lanHosts.isEmpty() -> "Tap Scan — takes about a minute. Finds phones, TVs, printers, anything answering on your Wi-Fi."
            else -> "${lanHosts.size} devices answered" + (gwVendor?.let { " · router by $it" } ?: "")
        }
        listBox.removeAllViews()
        for (h in lanHosts) {
            val row = panel()
            val label = when {
                h.isSelf -> "📱 This phone"
                h.isGateway -> "📡 Your router" + (gwVendor?.let { " ($it)" } ?: "")
                h.hostname != null -> "💻 ${h.hostname}"
                else -> "❓ Device (no name given)"
            }
            row.addView(TextView(this).apply {
                text = label; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (h.isSelf || h.isGateway) GREEN else TEXT)
            })
            row.addView(TextView(this).apply {
                text = "${h.ip} · answered in ${h.millis} ms"
                textSize = 12f; setTextColor(DIM)
            })
            listBox.addView(row, lpMargin())
        }
        if (lanHosts.isNotEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "Devices asleep or set to stay quiet won't answer — this list can be shorter than what your router's own app shows."
                textSize = 11f; setTextColor(DIM); setPadding(dp(4), dp(6), dp(4), 0)
            })
        }
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(PANEL)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun intToIp(v: Int): String =
        "${v and 0xff}.${v shr 8 and 0xff}.${v shr 16 and 0xff}.${v shr 24 and 0xff}"

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
