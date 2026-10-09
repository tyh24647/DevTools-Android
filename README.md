# ⚙︎ DevTools — Android

A native Kotlin / Jetpack Compose app with a tabbed Android WebView browser. Based on `tyh24647/DevTools-2-master` commit `9df942d8387ebe168d3279e31404e5e80f1a6716` (October 7, 2026).

## Run

A prebuilt debug APK is included at **Build/DevTools-debug.apk**. Unzip the download and install that APK on Android, or build the source below.

1. Open this directory in Android Studio (an AGP 8.13-compatible version or newer).
2. Use JDK 17 and install Android SDK Platform 36. Android Studio supplies your own `local.properties`.
3. Sync Gradle, select the **app / debug** variant, and Run on Android 8.0 (API 26) or newer.
4. Keep Android System WebView up to date for document-start injection. The app detects unsupported versions and falls back to injection after page load.
5. Paste a URL, or share a page URL from Chrome to one of the six DevTools actions.

Command-line build:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug package ID is `com.tyh24647.devtools.debug`; release is `com.tyh24647.devtools`. The debug build uses Android's standard debug signing certificate and enables Pro preview by default. It does not represent a paid purchase. Release has no preview entitlement.

## Browser boundary

Chrome for Android does not provide an extension installation mechanism. This app controls its **own** WebViews. Sharing from Chrome transfers a URL; it does not transfer Chrome's cookies, DOM, history or active JavaScript state. Show/Hide controls the page opened in DevTools, not the original Chrome tab. The app can be chosen as a browser for HTTP/HTTPS links.

A Firefox-for-Android preview add-on is included at `Build/DevTools-Firefox-preview.xpi`. It operates inside Firefox and pairs with the Android app for browser-specific settings. Open **Browsers** in the app for separate DevTools and Firefox tabs, each with its own Enabled, Run on every page, AllowLists and blacklists. See [Firefox setup and limitations](FirefoxExtension/README.md). The preview is unsigned and requires temporary developer installation until Mozilla signing is completed.

## Custom Eruda plugins

The app opens on Browsers. Use the Plugins tab to import a local JavaScript file, paste/edit source, or try the example panel. Saving or editing leaves a plugin disabled; enable it when ready. Select Eruda in Tools. Plugins apply only to the built-in DevTools browser and obey its page rules.

A plugin file returns an Eruda tool or factory, or exports one through module.exports. The eruda parameter is provided. ES modules must be bundled first. Up to 20 plugins, 256 KB each, can be stored. Code runs with the inspected page's access; only import trusted code. Plugins should clean up listeners/timers in destroy. Site CSP can block evaluation. Errors appear in runtime warnings. The Plugins page links to the official writing guide, API reference and starter template.

## Features

- Enabled, run-everywhere and automatic package updates default to on.
- Selected blacklists always override run-everywhere and selected AllowLists.
- Allow-only mode combines selected AllowLists; unmatched pages are excluded.
- Create, rename, change type, select/deselect and delete named lists.
- Create, edit, enable/disable and delete domain, exact-URL, wildcard and regex rules.
- The same JavaScript rule engine and requested six groups of Eruda defaults as iOS.
- Regex runs in a dedicated Worker with a 750 ms deadline. Patterns are bounded to 512 characters, URLs to 4096, i/m/u flags; backreferences, lookarounds and repeated complex groups are excluded. Worker failures or site CSP restrictions exclude the page.
- Route polling every 500 ms detects SPA URL changes. Settings updates replace the document-start script and refresh open pages. Matching can occur after initial page scripts, especially with asynchronous regex rules; it does not promise capture of every request from the first byte of navigation.
- Twelve live tabs, back/forward/reload, user-initiated popups, document picker for file uploads, URL sharing and reopening existing shared URLs.
- Tab URLs persist; process restarts reload them. Live JavaScript state is not restored after process death.
- Pro controls: Eruda/vConsole, modern/legacy Vue adapter, code, DOM, resource timing, navigation timing, FPS and feature detection. Size, opacity, theme and remembered icon position.
- Resource timing includes a buffered resource observer, filters, DNS/connect/TLS/TTFB/download/start/duration/bytes and waterfall. Cross-origin details depend on Timing-Allow-Origin. This is page-level instrumentation, not Chrome's full protocol debugger or packet capture.
- System/light/dark appearance and four accents, adapting the existing RegexDojo-inspired icon and card style.

The six Android activity-alias share targets are:

- ⚙︎ DevTools - Add to Blacklist
- ⚙︎ DevTools - Add to AllowList
- ⚙︎ DevTools - Show
- ⚙︎ DevTools - Hide
- ⚙︎ DevTools - Enable
- ⚙︎ DevTools - Disable

Android and browser share-sheet implementations control ordering, truncation and grouping; some show several DevTools targets behind an expanded app entry. Enable/Disable changes the app-wide setting. Adding to an inactive list preserves its inactive state.

## Bundled tools and updates

Ten npm bundles from the updated iOS repository are included: Eruda 3.4.3, eruda-vue 1.1.1, eruda-vue-devtools 1.0.1, vConsole 3.15.1, vue-vconsole-devtools 1.0.9, eruda-code 2.2.0, eruda-dom 2.0.0, eruda-timing 2.0.1, eruda-fps 2.0.0 and eruda-features 2.1.0. Their wrappers keep package exports separate from page-owned globals. Only selected libraries execute. The FPS compatibility shim remains outside upstream code.

When automatic updates are enabled, each app opening/foreground checks npm latest metadata, with jsDelivr resolution fallback. Version-pinned jsDelivr files are checked against the service's SHA-256 metadata before atomic cache installation. Cache bytes are verified again before use. Network failures and corrupt caches retain the bundled installation. Turning automatic updates off preserves already-installed versions; Clear downloaded packages restores the bundled versions on new page loads. Already-loaded assets remain in their page until reload. A newer adapter may introduce incompatibilities; clear the cache and reload to recover.

Android does not run npm inside the app. Development-time `npm run update:tools` refreshes offline bundles. Runtime JS updates do not download APK, dex, JAR or native-library code. No visited page receives an Android JavaScript interface. Store review requirements still apply to interpreted downloaded code and app behavior.

```sh
npm ci --ignore-scripts
npm test
npx playwright install chromium
npm run test:browser
npm run update:tools
```

`DEVTOOLS_BROWSER_PATH` can select an existing Chromium executable for browser tests. Upstream license notices are included in the app and `Licenses/`; update/review notices when changing dependencies.

## Purchases

Create these products in your Google Play Console for the release package:

| Product | Type | Intended US price |
| --- | --- | --- |
| devtools_pro_monthly | Subscription with a monthly base plan | $2.99/month |
| devtools_pro_lifetime | Non-consumable one-time product | $19.99 |

Purchases are platform-specific; existing App Store purchases do not automatically transfer to Android. The UI displays Google Play's actual localized prices, not fabricated purchase prices. Add your licensing **public** RSA key via the `billingPublicKey` Gradle property. Without a configured key the app will not launch real purchases or trust purchase signatures. Do not add private signing keys or service-account credentials to the repository.

The implementation queries active purchases at connection, foreground and every three minutes while resumed, verifies Play purchase signatures locally, handles pending/cancelled/owned states, acknowledges valid purchases and supports restore. Entitlements are not accepted from pages or configuration files. Subscription availability is based on successful Play queries; a failed refresh preserves in-process access for at most 24 hours after the last successful query. Access is not persisted across process restarts. A purchase backend using the Play Developer API and RTDN is recommended before production to defend against replay and maintain authoritative revocation/expiry state. Buying lifetime does not cancel a monthly subscription.

Use a Play internal-testing track and license testers to test real purchase flows. Debug Pro preview can be switched off from the Pro screen to inspect the free experience; release builds cannot enable it.

## Ads

The updated repository disables ads by default, so this port also defaults to **ads off**. To enable the implemented native AdMob integration:

```sh
./gradlew :app:assembleDebug -PdevtoolsAds=true
```

Also switch off Debug Pro preview to see ads. Google test app/banner/interstitial/app-open IDs are the defaults. Configure `admobAppId`, `adBanner`, `adInterstitial` and `adOpen` Gradle properties for your account before release. UMP handles consent and privacy options; requests are non-personalized. Ad requests do not include browsing URLs, rules, console logs or timing records. Ad SDKs can still process device/network/ad interaction information.

Native banners appear at the bottom of the app shell. Completing a rule save refreshes the banner and makes an interstitial eligible. Three minutes makes an interstitial eligible at the next completed edit or app navigation. App-open ads can display on returning from the background, excluding initial launch, the Pro screen and active shared-rule dialogs. Full-screen ads are at least 30 seconds apart. No fill or unavailable consent never blocks a rule save. Pro suppresses ads. Test consent, ad availability and lifecycle behavior on a device before enabling production ads.

## Browser privacy and limits

- No JavascriptInterface or purchase/settings bridge is exposed to visited pages. A bounded media message listener accepts discovery, share, stop and native-confirmed save requests.
- Native settings are private, atomically saved, and malformed storage fails closed until reset.
- Incoming URLs are restricted to HTTP(S), without embedded credentials. File/content/intent/javascript schemes are rejected.
- Third-party cookies, camera/microphone and automatic popups are disabled; file uploads require the document picker.
- TLS certificate errors are cancelled. HTTP navigation is deliberately available for local development; mixed content remains blocked.
- Clear browser data closes tabs and clears cookies, cache and WebStorage. Android backup is disabled.
- Download links can be handled by another browser using Open externally; there is no background download manager in this version.
- This developer browser is not a complete Chrome replacement: Chrome profile sync, passkeys, DRM media and every site-specific authentication flow are not guaranteed. Production Vue builds may omit the hooks needed for inspection. Cross-origin iframe inspection is not implemented.

## Validation

See `Documentation/TESTING.md` for the completed build/test evidence and the device checks still needed. Automated Chromium results prove the JavaScript runtime, not the Android WebView provider, Google Play, share-sheet rendering or AdMob delivery on a physical phone.


## Userscripts and saved media (native browser)

Plugins includes Eruda panels and userscripts. Create or import JavaScript, then enable it and reload the inspected page. Userscripts support HTTP(S) match/include/exclude rules, document start/end/idle and a limited GM API for styles, logging and per-script, per-origin storage. This is a subset of Tampermonkey: external dependencies, cross-origin privileged requests and arbitrary grants are rejected. Scripts execute in the page context and their storage is page-visible.

Resources is a saved-file browser for private app media, with image/video thumbnails, folder creation/deletion, hidden-file controls, previews, sharing, export and system Files access. It contains only explicitly saved files. Discovered page URLs stay in Eruda; save there, long-press a page image, or use a video download button. Set a default app folder and optionally an additional export folder. Video quality selection is off by default, choosing the highest-resolution HLS rendition.

Visible video elements have save/record controls. Eruda Resources shows discovered page media before Images with type-correct image/audio/video previews and share/save actions. Native downloads copy direct media or remux HLS into MP4 using Android MediaExtractor/MediaMuxer without transcoding. Live recording ends on source/page unload or Stop and save. Foreground transfers show progress; completed files become available only after finalization. Supported clear/AES-128 HLS depends on device extractors/codecs; DRM, encrypted fragmented MP4 and changing track formats are rejected. No DRM bypass is provided. Media3 supplies optional HLS preview (Apache 2.0, https://github.com/androidx/media).

These userscript/media features currently apply to the native browser. The Firefox add-on does not yet provide this media/library integration.


Script management includes direct JavaScript URL import, parse validation without running imported code, editable/exportable source, nested folders and colored tags. Removing a script folder keeps its scripts in the root. The local CodeMirror editor provides JavaScript highlighting, completion including declarations above the cursor, Prettier formatting, light/dark themes, invisibles, bracket matching/closing and indentation preferences behind the settings cog.

A bundled CSS editor Eruda panel is installed and enabled once. It applies a draft stylesheet to the current page, resets it, and optionally saves/reapplies CSS per origin using page-visible localStorage. Delete saved CSS removes the stored stylesheet. The Plugins tab can edit, disable or delete the panel, and offers it again for manual installation after deletion.

### Webpage camera and microphone

The native browser supports camera and microphone capture on secure webpages. Each request shows the requesting origin and asks for website consent, followed by Android permissions when needed. Unknown WebView resources are denied. Navigating away, closing the tab or losing its renderer cancels pending requests. Android privacy toggles still apply.
