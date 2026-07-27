# Family Hub

A wall-mounted tablet app inspired by [Skylight Calendar](https://www.skylightcal.com/) — one app that turns any Android tablet into a shared **family calendar** and **photo frame**, with automatic switching between modes.

Built for **Samsung Galaxy Tab S11** (11" display) and designed to scale gracefully across other Android tablets.

## Features

### Family calendar
- **Weekly** (day columns, default) and **Monthly** views; switch in the top bar, default configurable in Settings
- **Family members**: add members in Settings; assign each event to a member (color-coded) or leave it as a general/family event
- Selected-day panel groups events **by family member**
- Event types: birthdays, parties, events, kids recurring classes, and other reminders
- Recurring events: none, weekly, yearly
- Notes on any event
- Add and edit events locally on the device
- Pull events from Google Calendar or a remote cloud API

### Display & kiosk
- **Night sleep**: screen dims to black between configurable hours (default 21:00–07:00); tap to wake
- **Immersive fullscreen**: hides status/navigation bars; optional screen pinning to lock the tablet to the app

### Photo slideshow
- Full-screen auto-advancing photo frame
- Per-photo display duration (3–60 seconds)
- Smooth crossfade transitions
- Local photos (gallery picker) or cloud URLs
- Caption overlay

### Display modes
- **Calendar** is the default home screen
- After configurable inactivity (default 5 minutes), automatically switches to **Photos**
- **Double tap anywhere** on the photo screen returns to the calendar
- Screen stays awake while plugged in (configurable)

## Getting started

### Requirements
- Android Studio Ladybug (2024.2) or newer
- JDK 17
- Android SDK 35
- A tablet running Android 8.0+ (API 26+)

### Build and run

```bash
./gradlew assembleDebug
```

Install on a connected tablet:

```bash
./gradlew installDebug
```

Or open the project in Android Studio and run on your Samsung Tab S11.

### First launch
The app seeds sample events and demo photos so you can explore immediately. Replace them with your own content via **Add event** or cloud sync.

## Google Sign-In (Calendar + Photos)

The app supports **Sign in with Google** to pull events and photos directly — no custom backend required.

1. Open **Settings → Sign in with Google**
2. Use your family Gmail account (see setup guide below)
3. Tap **Sync now** to refresh

Setup requires a Google Cloud project with an **Android OAuth client** (package + SHA-1) — no client ID or secret is baked into the app. See **[docs/GOOGLE_SETUP.md](docs/GOOGLE_SETUP.md)** for step-by-step instructions (enable APIs, add test user, register SHA-1).

When signed in, sync pulls:
- **Google Calendar** — all readable calendars (365 days ahead)
- **Photos from shared Google Drive folders** — one or more folders (subfolders
  included); images are downloaded and cached on device, and new photos appear on
  the next sync

> **Why Drive for photos?** Google restricted the Photos Library API in 2025, so
> third-party library-wide Photos access no longer works. Family Hub uses a shared
> **Google Drive folder** instead: share one folder with the family account, paste
> its link in **Settings → Google Drive photo folder**, and the slideshow stays
> updated automatically. See [docs/GOOGLE_SETUP.md](docs/GOOGLE_SETUP.md).

## Custom cloud sync (optional)

Configure in **Settings → Cloud sync**:

| Setting | Description |
|---------|-------------|
| Cloud API base URL | e.g. `https://api.example.com/v1/` |
| API key | Sent as `Authorization: Bearer <key>` |
| Sync interval | Background sync frequency (hours) |

The app calls `GET {baseUrl}sync` and expects JSON matching `docs/cloud-api-example.json`.

Supported event `type` values: `BIRTHDAY`, `PARTY`, `EVENT`, `KIDS_CLASS`, `OTHER`.

Supported `recurrence` values: `NONE`, `WEEKLY`, `YEARLY`.

Cloud-sourced items replace previous cloud items on each sync; local items are preserved.

You can host your own simple backend, or wire this to Google Calendar / iCloud via a small sync service (not included).

## Samsung Tab S11 setup tips

1. **Orientation**: Use landscape for wall mounting; the layout adapts with a calendar + sidebar split on wide screens.
2. **Battery**: Disable battery optimization for Family Hub so idle timeout and slideshow keep running.
3. **Kiosk mode**: For a dedicated wall display, enable Samsung **Knox / Multi-window** restrictions or a launcher like **Fully Kiosk Browser** to lock to this app.
4. **Brightness**: Set adaptive brightness off and pick a fixed level for hallway viewing.

## Optional: wall mount and power

### Cases and mounts (buy or DIY)

| Option | Notes |
|--------|-------|
| **VESA tablet wall mount** | Search for "universal tablet wall mount VESA" — works with an 11" Tab in a slim case. Look for mounts rated for 11–13" and 1–2 lb. |
| **Magnetic kitchen mount** | 3M Command strips + a magnetic tablet case (e.g. MoKo, Fintie) — easy to remove for charging. |
| **DIY shadow box frame** | Build a thin wooden frame slightly larger than the tablet; route a cable channel in the back; use velcro or a spring clip to hold the tablet. |
| **3D-printed dock** | Print a cradle with 75×75 mm VESA holes; add a USB-C passthrough for hidden cable routing. |

For the **Galaxy Tab S11 (~7.3 × 11.1 in)**, choose a mount with **landscape** support and leave ~10 mm clearance for a thin TPU/slim case.

### External battery / always-on power

| Option | Capacity | Notes |
|--------|----------|-------|
| **Anker PowerCore 20K + USB-C PD** | ~20,000 mAh | ~1–2 days of mixed calendar/photo use; good for testing away from an outlet. |
| **Baseus 65W power bank with pass-through** | Varies | Pass-through charging lets the tablet run while the bank recharges overnight. |
| **Permanent**: recessed outlet + short USB-C cable | — | Best for a true wall display; use a **right-angle USB-C** adapter to keep the profile flat. |
| **UPS battery backup** | — | A small desktop UPS (APC Back-UPS) avoids reboots during brief power blips. |

For 24/7 wall use, a **hidden outlet** behind the mount beats relying on a battery. Use a battery only where wiring is impractical.

## Project structure

```
app/src/main/java/com/familyhub/display/
├── data/           # Room DB, repositories, cloud sync
├── ui/
│   ├── calendar/   # Month grid and event editor
│   ├── photos/     # Slideshow and photo picker
│   ├── settings/   # Timeouts, sync, screen-on
│   └── viewmodel/  # State management
└── util/           # Formatting, double-tap detection
```

## Tech stack

- Kotlin + Jetpack Compose + Material 3
- Room (local persistence)
- DataStore (settings)
- Coil (image loading)
- Retrofit + Moshi (cloud sync)

## License

MIT — use freely for your family hub.
