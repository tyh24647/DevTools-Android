# ⚙︎ DevTools for Android

A native Android developer browser with Eruda debugging tools, a Firefox companion add-on, and six browser share actions. Built with Kotlin and Jetpack Compose, alongside the [DevTools for iOS](https://github.com/tyh24647/DevTools-2-master) project.

Inspect webpages, run custom plugins and userscripts, apply page CSS, and save images or videos into a local media library.

DevTools defaults to **enabled**, **run on every webpage**, and **automatic tool updates**. Selected blacklists take priority over AllowLists. The app opens on **Browsers**, where the native browser and Firefox have separate settings.

## Open and run

1. Open this repository in Android Studio with support for Android Gradle Plugin **9.4.1**.
2. Install Android SDK Platform **36** and use the compatible JDK bundled with Android Studio. Java/Kotlin compilation targets Java 17; Gradle uses the included **9.6.0** wrapper.
3. Select **app / debug** and run on Android **8.0 (API 26)** or newer.
4. Keep Android System WebView updated. Document-start injection is used when supported; older providers fall back to injection after page load.
5. Open **Browser** and enter a URL, or share a page URL into DevTools.
6. Use **Browsers** to configure automatic activation, AllowLists, and blacklists.

```sh
./gradlew :app:assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Local packaged builds may also be available in `Build/`; that directory is excluded from Git. The debug package is `com.tyh24647.devtools.debug`; release is `com.tyh24647.devtools`.

JavaScript tools and the code editor are bundled, so npm is only needed when rebuilding those assets or running JavaScript tests. Debug builds include a switchable **Pro preview** for development; release builds do not.

## Included behavior

| Feature | Implementation |
| --- | --- |
| Run everywhere | Enabled by default; selected blacklists exclude matching pages |
| Allow-only mode | Turn off Run on every webpage; selected AllowLists permit matching pages |
| Named lists | Create, rename, change type, select, and delete lists per browser |
| Rules | Domain/subdomains, exact URL, wildcard, and bounded JavaScript regex |
| Precedence | Disabled → blacklist deny → run everywhere → AllowList match → deny |
| Browser profiles | Independent native-browser and Firefox settings |
| Show / Hide | Open or hide the console without discarding the active console session |
| Tabbed browser | Up to twelve tabs, back/forward, reload, close, uploads, and URL sharing |
| Restored tabs | Saved URLs load when opened; live page state does not survive process death |
| Camera and microphone | Website consent plus Android runtime permissions on secure pages |
| Eruda tools | Console, Elements, Sources, Resources, and additional inspection panels |
| Resource timing | Filters, DNS/connect/TLS/TTFB/download timings, bytes, and waterfall |
| Plugins and userscripts | Create, import, edit, enable, export, delete, and organize source files |
| Code editor | Highlighting, completion, formatting, themes, invisibles, and bracket preferences |
| CSS editor | Apply/reset page styles and optionally save/reapply CSS per site |
| Saved resources | Thumbnail file browser with folders, previews, sharing, and export |
| Video downloads | Direct media downloads and supported HLS-to-MP4 remuxing |
| Live recording | Record a supported live HLS stream until Stop or source/page unload |
| Appearance | System/light/dark modes, accent presets, and native cards/navigation |

The six Android share targets are:

- ⚙︎ DevTools - Add to Blacklist
- ⚙︎ DevTools - Add to AllowList
- ⚙︎ DevTools - Show
- ⚙︎ DevTools - Hide
- ⚙︎ DevTools - Enable
- ⚙︎ DevTools - Disable

Android controls their ordering, grouping, and displayed labels. Sharing a URL opens it in DevTools; the original browser's cookies, DOM, and JavaScript session are separate.

## Firefox companion

The Firefox preview runs Eruda inside Firefox and pairs with **Browsers → Firefox** in the Android app. Its responsive menu includes Show/Hide and synchronized Enabled/Run on every page toggles. Manage per-browser lists in the app.

```sh
npm run build:firefox
```

This creates `Build/DevTools-Firefox-preview.xpi`. Temporary add-ons disappear when Firefox restarts; persistent installation requires Mozilla signing. Firefox internal pages and protected sites remain inaccessible. The companion currently includes Eruda and resource timing; native plugins, userscripts, and the saved-media bridge are not included.

See [Firefox setup and limitations](FirefoxExtension/README.md) for pairing, installation, permissions, and preview status. Chrome URL sharing opens the native browser; it does not install a Chrome extension.

## Plugins, userscripts, and page CSS

Open **Plugins** to create a script or import a local JavaScript file or direct JavaScript URL. Imports are parsed for review without executing them. New and edited scripts remain disabled until enabled; reload the target page after changes.

- **Eruda panels:** return a tool/factory or export it through `module.exports`. Bundle ES modules first.
- **Userscripts:** new scripts include a metadata-header template. Supported features include HTTP(S) matching/exclusions, document start/end/idle, styles, logging, and per-script/per-origin GM storage.
- **Organization:** nested folders and colored tags; removing a folder keeps its scripts in the root.
- **Editor:** JavaScript highlighting, completion for variables declared above the cursor, Prettier formatting, light/dark themes, invisibles, matching/closing brackets, and indentation preferences through the settings cog.
- **CSS panel:** the bundled CSS editor is enabled on first installation. Apply a draft, reset page styles, save CSS for the origin, or automatically reapply saved CSS. Saved CSS uses page-visible localStorage.

Up to 20 scripts of 256 KB each can be stored. Scripts run with the inspected page's access. Userscripts implement a subset of Tampermonkey; external dependencies, privileged cross-origin networking, and arbitrary grants are unsupported. These features currently apply to the native browser.

## Saved images, videos, and live streams

**Resources contains only explicitly saved files.** Discovered URLs stay in Eruda. Save from Eruda's media preview, long-press a page image, or use a video's download button.

The library shows image/video thumbnails, supports folders and hidden-file visibility, and offers previews, sharing, export, and system Files access. Unsupported thumbnail codecs fall back to a file icon. Choose a default app folder and optionally an additional export destination.

Direct media is copied; supported HLS is remuxed into MP4 using Android MediaExtractor/MediaMuxer without transcoding. Live recordings finalize on Stop or source/page unload. Transfers show progress and completed files appear after finalization. Optional resolution selection defaults to off, choosing the highest-resolution HLS rendition.

Extractor/codec support varies by device. DRM, encrypted fragmented MP4, and changing track formats are unsupported. See the [test coverage and remaining media checks](Documentation/TESTING.md).

## Bundled tools and defaults

| Package | Version | Role |
| --- | --- | --- |
| eruda | 3.4.3 | Core console |
| eruda-vue | 1.1.1 | Current Vue adapter |
| eruda-vue-devtools | 1.0.1 | Legacy Vue adapter |
| vconsole | 3.15.1 | Alternative console |
| vue-vconsole-devtools | 1.0.9 | Vue adapter for vConsole |
| eruda-code | 2.2.0 | Code panel |
| eruda-dom | 2.0.0 | DOM explorer |
| eruda-timing | 2.0.1 | Navigation timing |
| eruda-fps | 2.0.0 | Frame-rate monitor |
| eruda-features | 2.1.0 | Browser feature detection |

Defaults follow the iOS project: asynchronous console rendering, global-error capture, console override, detailed object inspection, a 55% panel at 0.98 opacity, Material Palenight, observed elements/resources, and a remembered viewport-clamped entry-button position.

Only selected tools initialize. Vue inspection depends on compatible page hooks, which production builds may omit. Cross-origin timing details require the page server's `Timing-Allow-Origin` header.

Automatic updates check npm metadata and fetch version-pinned known bundles through jsDelivr. SHA-256 checks and atomic cache installation guard against incomplete/corrupt downloads. Bundled tools remain available offline; clearing downloaded packages restores them on new page loads. Existing pages keep their loaded tools until reload. Android runtime updates are not governed by the iOS project's Debug/Release distinction; review distribution requirements before release.

## Purchases and ads

| Product ID | Type | Intended US price |
| --- | --- | --- |
| `devtools_pro_monthly` | Monthly subscription | $2.99/month |
| `devtools_pro_lifetime` | Non-consumable purchase | $19.99 |

Pro unlocks additional tool and appearance controls and suppresses ads. Google Play supplies actual localized prices. Purchases are platform-specific; App Store purchases do not transfer automatically. Buying lifetime does not cancel an existing subscription.

Configure Play products and the licensing public key before purchase testing. Local signature verification, pending purchases, acknowledgement, refresh, and restore are implemented; authoritative server-side verification and real Play-track testing remain release work.

Ads default to **off**. Optional AdMob/UMP integration uses test IDs unless configured otherwise:

```sh
./gradlew :app:assembleDebug -PdevtoolsAds=true
```

Disable Debug Pro preview to test ads. See [release configuration](Documentation/RELEASE.md) before configuring production billing, signing, consent, or ad IDs. Keep private signing keys and service credentials outside Git.

## Development commands

```sh
npm ci --ignore-scripts
npm test
npx playwright install chromium
npm run test:browser
npm run test:firefox-browser
npm run test:media-browser
npm run test:editor-browser
npm run test:css-browser
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Rebuild editor assets with `npm run build:editor`; rebuild the Firefox package with `npm run build:firefox`. Instrumentation tests live in `app/src/androidTest` and require a connected Android device.

## Structure and boundaries

- `app/`: native UI, browser, configuration, billing, permissions, media library, and bundled tools.
- `FirefoxExtension/`: companion add-on, popup, settings sync, and page runtime.
- `Editor/`: local code-editor source and validation.
- `Scripts/`: asset and extension build scripts.
- `Tests/`: JavaScript unit tests and browser checks.
- `Documentation/`: release setup and verification details.
- `Licenses/` and `Shared/`: upstream notices and package metadata.

This is page-context debugging. Site CSP, browser permissions, protected pages, and cross-origin restrictions can limit inspection. The app does not share Chrome's profile or provide an OS-level protocol debugger.

Incoming URLs are restricted to HTTP(S) without embedded credentials. TLS errors are canceled; HTTP is available for local development and mixed content remains blocked. Third-party cookies and automatic popups are disabled. Camera/microphone require consent; Android privacy controls remain authoritative. Clearing browser data closes tabs and clears cookies, cache, and WebStorage.

Visited pages receive no unrestricted Android JavaScript interface. A bounded media listener handles explicit, native-confirmed saves and related media actions. Regex rules run in a worker with a deadline; worker or policy failures exclude the page.

## Validation

Builds, JVM/Node tests, browser smoke checks, and selected physical-device workflows have passed, including real camera/microphone capture, editor rendering/recovery, and HLS VOD/live finalization. This does not establish complete coverage across Android versions, codecs, Google Play, or AdMob.

See [completed checks and remaining device tests](Documentation/TESTING.md).

## References

- [DevTools for iOS](https://github.com/tyh24647/DevTools-2-master)
- [Eruda](https://github.com/liriliri/eruda)
- [CodeMirror](https://codemirror.net/)
- [Prettier](https://prettier.io/)
- [Mozilla Extension Workshop](https://extensionworkshop.com/)
