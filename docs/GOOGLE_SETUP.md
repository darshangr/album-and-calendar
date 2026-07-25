# Google Sign-In setup

Family Hub uses **Sign in with Google** to sync **Google Calendar** and **Google Photos** from a single family account (for example `family-wall@gmail.com`).

## 1. Create a Google Cloud project

1. Open [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project (e.g. `family-hub`)
3. Enable these APIs:
   - **Google Calendar API**
   - **Google Drive API** (photos come from a shared Drive folder)

## 2. Configure OAuth consent screen

1. Go to **APIs & Services → OAuth consent screen**
2. Choose **External** (or Internal for Workspace)
3. Add scopes:
   - `.../auth/calendar.readonly`
   - `.../auth/drive.readonly`
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
| Google Drive folder | Images in one shared folder, downloaded and cached on device |

- Google data is stored locally with source `GOOGLE`
- Local events/photos you add in the app are kept
- **Sign out** removes synced Google calendar events and photos

## Photos from a shared Google Drive folder

Because Google restricted the Photos Library API in 2025 (see note below), Family
Hub gets photos from a **shared Google Drive folder** instead. This updates
automatically: add a photo to the folder and it appears on the next sync.

### One-time setup

1. In **Google Drive**, create a folder, e.g. `Family Hub Photos`.
2. Put your photos in it (or share an existing folder).
3. **Share** the folder with the family Gmail you sign into the app with
   (Viewer access is enough). If the account *owns* the folder, no sharing needed.
4. Open the folder in Drive and copy its link from the address bar, e.g.
   `https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOpQrStUvWxYz`
5. In the app: **Settings → Google account → Google Drive photo folders**, paste
   the link (or just the folder ID), then **Save settings**.
6. Tap **Sync now**. Images download, cache on the device, and start the slideshow.

### Multiple folders

If your photos live in several folders, you have two options:

- **List several folders:** paste each folder link on its **own line** in the
  Drive photo folders box. Share each one with the account.
- **Use a parent folder:** point at a single folder that *contains* subfolders —
  the app walks subfolders automatically and pulls images from all of them.

Notes:

- The whole family can drop photos into these folders; each sync picks up new
  images (up to ~2000 total across all folders) and removes ones deleted from Drive.
- Images are **downscaled to ~2560px and re-encoded as JPEG** when cached, so the
  device stays small and the slideshow won't run out of memory even with large
  libraries. Originals in Drive are untouched.
- Photos are cached locally, so the slideshow keeps running offline.
- Supported: JPG, PNG, WEBP, GIF, HEIC.

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

1. **Shared Google Drive folder (recommended):** Automatic and self-updating —
   see "Photos from a shared Google Drive folder" above.
2. **Local photos:** In the slideshow, tap **Add photo → Pick from device**.
3. **Custom cloud sync:** Host image URLs and point the app at your `/sync`
   endpoint (see `docs/cloud-api-example.json`).

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
| `NEED_REMOTE_CONSENT` (Drive) | Drive scope not yet approved for the account | The app now pops Google's approval screen automatically — tap **Allow**, then it re-syncs. Make sure `drive.readonly` is on the OAuth consent screen and your Gmail is a test user. |
| Emulator can't sign in | Image lacks Google Play | Use a **Google Play** system image and add the account in device Settings |

Note: a **Web** or **Desktop ("installed")** client ID is *not* used by the app.
Only the **Android** OAuth client matters for on-device access.

## Family account tip

Create one Gmail (e.g. `family-wall@gmail.com`), then have everyone **share calendars and albums** with that account before signing in on the tablet.
