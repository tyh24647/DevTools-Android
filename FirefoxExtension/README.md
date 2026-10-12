# DevTools Firefox preview

Firefox desktop/Android 142+ add-on with a bundled Eruda console and resource timing. This preview supports automatic activation on ordinary HTTP/HTTPS pages, browser-specific rules, and paired settings synchronization with the Android app.

## Configure and pair

1. Install the updated Android app. Open **Browser settings → Firefox** to configure Enabled, Run on every page, AllowLists and blacklists. The DevTools tab has its own settings and lists.
2. Copy the Firefox pairing code from that screen.
3. In Firefox, open **Extensions → DevTools → Pair with DevTools app** and paste the code.
4. The extension panel shows Enabled and Run on every page. Changes made there save to the Firefox profile in the Android app. Manage lists in the app.

With Enabled and Run on every page on, the console icon appears automatically on accessible webpages. Selected blacklists take precedence. With Run on every page off, only pages matching selected AllowLists activate. Show opens the console panel; Hide conceals it; Stop destroys the current instance until the next automatic refresh or navigation. Close the extension panel to interact with the console.

Synchronization uses an authenticated loopback-only settings endpoint while the Android app is running. Firefox checks when its panel opens and approximately once a minute. Keep the app running to synchronize; when it is unavailable, Firefox continues with its saved profile and queues toggle changes. If settings changed in the app before a queued change arrives, the app's newer profile wins and the panel asks you to repeat the change. Pairing is local to this device. Firefox desktop can run the console, but this Android pairing flow requires the app on the same device.

## Permissions and limits

The add-on needs HTTP/HTTPS host access for automatic activation, scripting for the page-world console, storage for settings, tabs to refresh open pages, and alarms to synchronize. Pairing credentials remain in extension-private storage and Android-private preferences. Settings normally travel over the local endpoint. Explicit script review/install retrieves user-authored source from the paired app. Explicit media saves send the resource URL and page URL to the native app for confirmation; optional cookie access can attach cookies scoped to the resource origin. Cookies are not exposed to page scripts. Console messages and timing records are not sent to the app; there is no telemetry.

Firefox internal pages and Mozilla-protected sites cannot be inspected. The page can see and affect the page-world console. Requests/errors before activation are not captured; scripts start after document load. SPA URL changes are checked every 500 ms. Rules run in an extension Worker with a 750 ms timeout. Version 0.4.0 includes the app-selected console adapters and inspection plugins, source beautification, console handle, wrapping, icon colors and code fonts. Purchases remain in the native app. The built-in CSS editor is bundled. Custom plugins and userscripts need explicit review/install in the extension's script manager, using Firefox's optional userScripts permission. Changes or deletion in the app stop the old copy at the next sync; reinstall edited source after reviewing it. Reload pages to clear existing script effects. Native editor/folder/tag management remains in the app.

## Build and install

From the project root:

    node Scripts/build-firefox.mjs
    npm test
    npx web-ext lint --source-dir FirefoxExtension

The output `Build/DevTools-Firefox-preview.xpi` is unsigned. On desktop, load `FirefoxExtension/manifest.json` from about:debugging → This Firefox → Load Temporary Add-on. On Android, enable Remote debugging via USB in Firefox Settings → Advanced, connect the device, and run:

    npx web-ext run --source-dir FirefoxExtension --target firefox-android --adb-device DEVICE_SERIAL --firefox-apk org.mozilla.firefox

Temporary installation ends when Firefox restarts. Normal distribution requires Mozilla signing. Eruda's MIT license is in ERUDA-LICENSE.txt. Shared tools are copied from the Android assets by Scripts/build-firefox.mjs.

## Validation

The initial console prototype was visibly exercised on Firefox for Android 157.0.1: console, Elements and Resource Timing on example.com; protected-site rejection on Mozilla's add-on website. The updated Android build passes Gradle compilation/unit checks. Fifty-one Node tests cover the shared rule engine, injection lifecycle, pairing validation, authenticated settings synchronization, offline changes and revision conflicts. A device bridge test verifies unauthorized access rejection, profile reads/writes, conflict handling and isolation from the built-in browser.

The Chromium lifecycle test covers automatic activation, reload, disabling, allow-only mode and blacklist precedence, plus Show/Hide/Stop/restart. Run `npm run test:firefox-browser` after `npx playwright install chromium`. This checks shared page runtime behavior; pairing and automatic activation in actual Firefox still require device verification. Mozilla’s validator reports zero errors/notices. Warnings remain for bundled inspection libraries and the feature-guarded userScripts.execute API (Firefox 153+); earlier supported versions use registered plugins after page reload. These warnings require review before submission.

Version 0.4.0 is a new unsigned update. The user reports the earlier submitted version was approved; that approval does not cover this package. Submit this update for Mozilla signing to make it persistent. Temporary loading is for testing and disappears after Firefox restarts.

Enable **Save media into DevTools** in the popup for Eruda image/media saves, video download buttons, HLS quality selection and live recording through the app. Bring DevTools forward to confirm each download; progress is reflected back into the Firefox page. Leaving a resource/page requests recording finalization. The app must remain running for this local bridge. Optional **Include site cookies** supports authenticated media; cross-origin segment authentication and DRM-protected streams may remain unsupported.

Large Vue iframe frontends are stored as compressed local package assets and decoded by the extension before their static adapters load. The source archive includes the original npm vendor bundles and reproducible extraction script; no frontend code is fetched from an external service.
