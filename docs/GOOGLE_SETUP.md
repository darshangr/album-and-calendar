# Google Sign-In setup

Family Hub uses **Sign in with Google** to sync **Google Calendar** and **Google Photos** from a single family account (for example `family-wall@gmail.com`).

## 1. Create a Google Cloud project

1. Open [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project (e.g. `family-hub`)
3. Enable these APIs:
   - **Google Calendar API**
   - **Photos Library API**

## 2. Configure OAuth consent screen

1. Go to **APIs & Services → OAuth consent screen**
2. Choose **External** (or Internal for Workspace)
3. Add scopes:
   - `.../auth/calendar.readonly`
   - `.../auth/photoslibrary.readonly`
4. Add your Gmail as a **test user** while the app is in testing mode

## 3. Create the Android OAuth client (the only client the app needs)

The app accesses Calendar and Photos **on-device**, so it does **not** need a Web
or Desktop client ID baked in. It only needs an **Android OAuth client** that
matches your app's package name and signing certificate.

1. **APIs & Services → Credentials → Create credentials → OAuth client ID**
2. Type: **Android**
3. Package name: `com.familyhub.display`
4. SHA-1 fingerprint:

```bash
# Debug keystore (development)
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Copy the **SHA-1** line into the Android OAuth client and save.

> If you use a shared debug keystore across machines, register that keystore's
> SHA-1 instead so every machine authenticates without re-registering.

No changes to `app/build.gradle.kts` are required — there is no client ID or
secret to paste into the app.

## 4. Sign in on the tablet

1. Open **Family Hub → Settings**
2. Tap **Sign in with Google**
3. Choose your family Gmail account
4. Accept Calendar and Photos permissions
5. The app syncs automatically after sign-in

Use **Sync now** anytime to refresh events and photos.

## How sync works

| Source | What is synced |
|--------|----------------|
| Google Calendar | All calendars the account can read (except free/busy-only) |
| Google Photos | Up to 100 recent photos from the library |

- Google data is stored locally with source `GOOGLE`
- Local events/photos you add in the app are kept
- **Sign out** removes synced Google calendar events and photos

## Google Photos: important 2025 restriction

**Google changed the Photos Library API on March 31, 2025.** Third-party apps can
**no longer read a user's whole photo library** via `mediaItems:search` /
`photoslibrary.readonly`. That call now returns **403**. Google's replacement is
the **Google Photos Picker API** (the user picks specific photos/albums), or
access limited to media the app itself created.

What this means for Family Hub:

- **Calendar sync is unaffected** and works normally.
- **Library-wide Photos sync no longer works** — you'll see a 403 with a message
  explaining the restriction. The app now keeps Calendar working even when Photos
  fails, and reports the reason on screen (full detail in Logcat, tag
  `GooglePhotosSync`).

### Photo options that work today

1. **Local photos (recommended, simplest):** In the slideshow, tap **Add photo →
   Pick from device**. On the tablet, put family photos in a folder (or let
   Google Photos "Back up & sync" onto the device) and add them locally.
2. **Custom cloud sync:** Host image URLs and point the app at your `/sync`
   endpoint (see `docs/cloud-api-example.json`).
3. **Google Photos Picker API (future):** A larger feature — the user selects
   photos in a Google-hosted picker and the app caches them locally. Ask if you
   want this built.

### If the 403 says the API is disabled

If the on-screen/Logcat message mentions *accessNotConfigured* or *has not been
used in project*, simply enable **Photos Library API** in Google Cloud Console —
that's a different, easily fixed cause.

## Troubleshooting sign-in

The app shows the failure reason on screen (and status code). Common cases:

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `code 10` (DEVELOPER_ERROR) | Android OAuth client missing or wrong SHA-1/package | Re-check the Android client: package `com.familyhub.display` + the SHA-1 you actually build with (`./gradlew signingReport`) |
| `BAD_AUTHENTICATION` / "Long live credential not available" | Account not a test user, or APIs not enabled | Add the Gmail as a **test user**; enable Calendar + Photos APIs |
| `code 12501` | Sign-in cancelled | Try again and pick the account |
| Emulator can't sign in | Image lacks Google Play | Use a **Google Play** system image and add the account in device Settings |

Note: a **Web** or **Desktop ("installed")** client ID is *not* used by the app.
Only the **Android** OAuth client matters for on-device access.

## Family account tip

Create one Gmail (e.g. `family-wall@gmail.com`), then have everyone **share calendars and albums** with that account before signing in on the tablet.
