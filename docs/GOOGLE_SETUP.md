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

## 3. Create OAuth credentials

You need **two** OAuth client IDs:

### Android client (required)

1. **APIs & Services → Credentials → Create credentials → OAuth client ID**
2. Type: **Android**
3. Package name: `com.familyhub.display`
4. SHA-1 fingerprint:

```bash
# Debug keystore (development)
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Copy the **SHA-1** line into the Android OAuth client.

### Web client (recommended)

1. Create another OAuth client ID
2. Type: **Web application**
3. Copy the **Client ID** (ends with `.apps.googleusercontent.com`)

## 4. Add the Web client ID to the app

Edit `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"YOUR_WEB_CLIENT_ID.apps.googleusercontent.com\"")
```

Rebuild the app after changing this value.

## 5. Sign in on the tablet

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

## Google Photos API note

Google restricts Photos Library API access for new apps. For a personal/family app:

- Keep the OAuth app in **Testing** mode and add family accounts as test users, or
- Submit for verification if you distribute widely

If Photos sync fails but Calendar works, check that **Photos Library API** is enabled and the scope is approved on the consent screen.

## Family account tip

Create one Gmail (e.g. `family-wall@gmail.com`), then have everyone **share calendars and albums** with that account before signing in on the tablet.
