# GAT 2027 — mobile app (WebView wrapper)

A thin native Android/iOS shell (via [Capacitor](https://capacitorjs.com)) that loads the live
GAT 2027 site (the Spring Boot app in the parent directory, deployed at
`https://gat2027.org.ng`) inside a native app. Every feature — registration, login, member
dashboard, coordination centre, events, podcast, the breaking-news marquee — works exactly as on
the website, because it *is* the website; this project adds no new backend or UI code of its own.

Verified working (built + installed on an Android emulator): the app launches straight into the
live home page, nav, hero, and video widget all render identically to a mobile browser.

## What's in here

- `capacitor.config.json` — points the app at the live server URL and lists the external domains
  (YouTube, Facebook, WhatsApp, X, TikTok, Instagram, Google Maps/Gmail) the site links out to, so
  the in-app WebView is allowed to follow those links instead of blocking them.
- `www/` — an unused placeholder page. Capacitor requires a local web root even when the app's
  real content comes from a remote `server.url`; this only shows if that URL is unreachable.
- `android/` — the generated native Android Studio project (gitignored build output only; the
  project files themselves are committed).

No `ios/` folder yet — building for iOS needs a Mac with Xcode. Run `npx cap add ios` there once
you have one; the same `capacitor.config.json` applies to both platforms.

## Building an APK

Requires Node.js and the Android SDK (Android Studio's default install location works — this was
tested against `%LOCALAPPDATA%\Android\Sdk` with JDK 21).

```bash
cd mobile
npm install
npx cap sync android
cd android
./gradlew assembleDebug
```

The debug APK lands at `android/app/build/outputs/apk/debug/app-debug.apk` — install it on a
connected device/emulator with `adb install -r <path>`, or open the `android/` folder directly in
Android Studio and hit Run.

For a release build (signed, for the Play Store), open `android/` in Android Studio and use
**Build > Generate Signed Bundle / APK** — you'll need to create a signing keystore first.

## Changing the target server

Edit `server.url` in `capacitor.config.json`, then re-run `npx cap sync android`. Useful values:

- Production (default): `https://gat2027.org.ng`
- Local dev server: use your machine's LAN IP, not `localhost` (the phone/emulator is a separate
  device) — e.g. `http://192.168.1.23:8080`. An Android emulator specifically can reach the host
  machine's `localhost` via the special address `http://10.0.2.2:8080`.

## App identity

- App ID (package name): `ng.gat2027.grassroot`
- App name: `GAT 2027`

Change these in `capacitor.config.json` before your first real release build if you want
something different — the package name in particular can't be changed after publishing to the
Play Store.

## Icon & splash screen

Not customised yet — the app currently ships with Capacitor's default icon/splash. To set the
real GAT 2027 logo, install `@capacitor/assets`, drop a 1024×1024 icon and a splash image into
`resources/`, and run `npx capacitor-assets generate` before your next `cap sync`.
