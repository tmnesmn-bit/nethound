# 🌐 NetHound

Wi-Fi networks around you, and devices on your own network — in plain English.
Sister app to [BlueHound](https://tmnesmn-bit.github.io/bluehound/) (Bluetooth),
[BlueHound Guard](https://github.com/tmnesmn-bit/bluehound-android), and
[RadioHound](https://github.com/tmnesmn-bit/radiohound) (cell towers and the radio dial).

**Networks tab:** every Wi-Fi network in range — name, signal, channel, security,
and who makes the router (looked up from its MAC address against the official
IEEE maker registry, 12,000+ prefixes bundled in the app). Open networks with no
password get a plain warning.

**My network tab:** scans the Wi-Fi you're on and lists everything that answers —
your router, phones, TVs, printers — with names, IPs, and response speed.

**What stays private:** everything. No account, no server, nothing leaves your phone.

## Install (2 minutes)

1. On your Android phone, open **[the latest release](../../releases/latest)** and tap **NetHound.apk**.
2. Tap the downloaded file. Android will warn about installing apps from outside
   the Play Store — allow it for your browser when asked.
3. Open NetHound and allow Location (and "Nearby devices" on newer phones) when
   asked. Android requires location permission to show Wi-Fi networks — NetHound
   never records or sends your location anywhere.

## Honest limits

- **"Private (randomized) address"** is normal, not suspicious. Modern phones
  deliberately use a fake MAC address on every network as a privacy feature.
- **Android hides other devices' MAC addresses from apps** (a privacy rule since
  Android 10), so the My network list shows names and IPs — maker lookup only
  works for the router.
- **Sleeping devices don't answer.** The My network scan can miss devices that
  are asleep or set to stay quiet; your router's own admin app sees more.
- **Network refreshes are throttled** by Android to roughly every 30 seconds.

## Fair use

Scan only networks you own or use. NetHound shows what any phone can see from
the airwaves plus what your own network answers — it does not break into
anything, crack passwords, or read anyone's traffic.
