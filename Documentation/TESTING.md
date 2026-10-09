# Verification — October 8, 2026

## Completed

- Gradle assembled the Debug APK with JDK 17 / SDK 36.
- Gradle assembled the minified Release APK (unsigned; production signing is intentionally account-owned).
- Five JVM/JUnit tests passed: bare-domain HTTPS normalization, local HTTP development URLs, privileged scheme/credential rejection, control-character/length rejection and safe share-text extraction.
- Twenty-five Node tests passed: shared rule precedence and normalization, runtime lifecycle/defaults/Pro gating, Android bootstrap navigation, blacklist revocation, Worker failure handling and a pending Hide command during initialization.
- Six real headless Chromium 134 scenarios passed on a mobile-sized viewport: document-start Eruda 3.4.3/Pro plugin initialization, Hide/Show and actual fetch resource timing, blacklist teardown, SPA route exclusion, real regex Worker matching and CSP worker rejection. No uncaught page errors were observed.
- Android lint completed with zero errors. Advisory findings remain for feature-guard recognition, deliberate JavaScript enablement, dependency updates, icon variants, backup-policy details and Kotlin/API style suggestions. RequiresFeature calls are guarded by the captured System WebView feature check; no unsupported document-start API is deliberately invoked.
- Debug APK signature verified with Android's debug certificate.
- Packaged APK assets checked: the final Android bootstrap is present along with all ten npm libraries. The manifest declares six share targets. Generated Release BuildConfig disables Pro preview.

## Not completed here

No Android emulator or physical-device run was available. Chromium testing validates the JavaScript core; it is not a substitute for Android WebView, native UI, Play Billing or AdMob testing.

Before production, test:

1. Launch, native navigation, tab creation/closure, keyboard/file picker and default-browser links on a phone.
2. All six share entries from Chrome, Firefox and other apps; confirm labels/grouping and that Chrome's original tab remains a separate session.
3. Selected lists, inactive lists, regex Worker/CSP failure, private routes, live settings and process-restart behavior.
4. Offline bundled tools, fresh updates, metadata errors, corrupted cache recovery and plugin compatibility after cache updates.
5. Actual Play-track transactions: purchase, pending, cancellation, lifetime, subscription expiration/revocation, restore and loss of connectivity. Configure the licensing public key and production verification backend.
6. Optional ads: UMP consent/denial, privacy options, banner refresh, no-fill, foreground app-open, three-minute opportunity timing and Pro suppression.

## Reproduce

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease :app:lintDebug
npm ci --ignore-scripts
npm test
npx playwright install chromium
npm run test:browser
```

The delivered APK is `Build/DevTools-debug.apk`. It enables Debug Pro preview and leaves ads disabled, following the updated source's local-use defaults. Unzip the project before installing or opening it in Android Studio.

## Browser profiles and Firefox automatic activation (October 9, 2026)

- Gradle assembleDebug and testDebugUnitTest pass; updated APK installed on the connected device.
- 37 Node tests pass: rules, controller, pairing validation, authenticated sync, offline edits and stale revision rejection.
- Firefox browser smoke test passes automatic activation/reload, disable, allow-only mode, blacklist precedence and console lifecycle in Chromium.
- Scripts/test-firefox-sync.py passes against the installed Android app: unauthorized requests rejected, profile reads/writes persisted, native browser profile isolated, stale revisions and malformed toggles rejected. Original toggle restored.
- Firefox 0.2.0 temporarily installed in regular Firefox 157.0.1. Actual popup pairing, two-way UI toggle synchronization and automatic activation remain device checks to complete.

## Browser controls and custom plugins

Gradle build/unit checks pass; 39 Node tests pass including custom plugin updates and isolated failures. Real Eruda panel load/removal and responsive popup layout at 280, 320, 412, 768 and 1920px pass. Updated APK installed on the connected device. Local Firefox 0.3.0 has not been submitted for signing.


## Native userscripts and media

44 Node tests pass, including userscript matching/exclusions, grants, isolated storage and once-per-document execution. JVM tests cover userscript validation and HLS master/media parsing, sequences, byte ranges, keys, live detection and unsupported encryption. The Chromium media smoke test verifies actual Eruda section placement, detection, overlay save requests, modal preview and source unload.

MediaWorkflowTest on the connected Samsung device verifies a clear MPEG-TS HLS VOD and a live stream remux to playable MP4, including recording finalization on tab leave. The fixture uses Apple's public HLS sample via a local server/ADB reverse. AES-128, fragmented MP4, alternate audio and changing live servers have not been verified end to end on the device. Export destination permissions and unsupported device codecs may require user action.


## Saved resources, script editor and CSS panel

46 Node tests pass. Browser checks cover image resources rendering as images (no video player), discovery never posting native save/library events, explicit video/image save requests, editor highlighting, declared-variable completion, formatting/themes, and the real Eruda CSS panel's Apply, Reset, Save, automatic per-origin reapply and deletion. Android JVM tests also cover UTF-8 import validation, size limits, rejecting webpages/binary inputs and enforcing userscript metadata/type.

ScriptOrganizationTest uses an isolated configuration directory to check persisted folder/tag assignment, preserved metadata after source edits, and non-destructive folder removal. ScriptEditorDeviceTest checks the actual bundled editor in the device WebView with the production origin and bounded message interface.

Both ScriptOrganizationTest and ScriptEditorDeviceTest pass on the connected Samsung device. Gradle debug build/JVM tests and lint pass (0 errors; existing warnings remain). Updated APK installed.

RendererRecoveryTest also passes on the connected device: deliberate shared WebView renderer termination leaves the activity running and recreates the editor with its draft preserved. All app-owned WebViews handle renderer loss; restored browser tabs load only when opened. The final debug APK is installed.

Manual device verification confirms the editor renders syntax-highlighted source in its Compose dialog. The editor uses viewport positioning to avoid zero-height percentage layouts in Android WebView. Editor browser checks pass after this fix.

## Webpage camera and microphone

Debug build, JVM tests and Android lint pass. WebMediaPermissionsTest passes on the connected Samsung device: the native browser presents website consent, starts real audio and video tracks, and stops both immediately without recording a file. The test pregrants Android runtime permissions; the Android permission dialog itself remains a manual UI check. WebView audio capture requires MODIFY_AUDIO_SETTINGS in addition to RECORD_AUDIO.
