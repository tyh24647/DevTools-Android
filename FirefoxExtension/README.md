# DevTools Firefox preview

Firefox desktop/Android 142+ add-on with a bundled Eruda console and resource timing. This preview supports automatic activation on ordinary HTTP/HTTPS pages, browser-specific rules, and paired settings synchronization with the Android app.

## Configure and pair

1. Install the updated Android app. Open **Browsers → Firefox** to configure Enabled, Run on every page, AllowLists and blacklists. The DevTools tab has its own settings and lists.
2. Copy the Firefox pairing code from that screen.
3. In Firefox, open **Extensions → DevTools → Pair with DevTools app** and paste the code.
4. The extension panel shows Enabled and Run on every page. Changes made there save to the Firefox profile in the Android app. Manage lists in the app.

With Enabled and Run on every page on, the console icon appears automatically on accessible webpages. Selected blacklists take precedence. With Run on every page off, only pages matching selected AllowLists activate. Show opens the console panel; Hide conceals it; Stop destroys the current instance until the next automatic refresh or navigation. Close the extension panel to interact with the console.

Synchronization uses an authenticated loopback-only settings endpoint while the Android app is running. Firefox checks when its panel opens and approximately once a minute. Keep the app running to synchronize; when it is unavailable, Firefox continues with its saved profile and queues toggle changes. If settings changed in the app before a queued change arrives, the app's newer profile wins and the panel asks you to repeat the change. Pairing is local to this device. Firefox desktop can run the console, but this Android pairing flow requires the app on the same device.

## Permissions and limits

The add-on needs HTTP/HTTPS host access for automatic activation, scripting for the page-world console, storage for settings, tabs to refresh open pages, and alarms to synchronize. Pairing credentials remain in extension-private storage and Android-private preferences. Only settings travel over the local endpoint; visited URLs, console messages and timing records are not sent to it. There is no telemetry or downloaded executable code.

Firefox internal pages and Mozilla-protected sites cannot be inspected. The page can see and affect the page-world console. Requests/errors before activation are not captured; scripts start after document load. SPA URL changes are checked every 500 ms. Rules run in an extension Worker with a 750 ms timeout. This preview includes Eruda and resource timing; it does not mirror the native app's additional tool adapters, purchases or appearance controls.

## Build and install

From the project root:

    node Scripts/build-firefox.mjs
    npm test
    npx web-ext lint --source-dir FirefoxExtension

The output `Build/DevTools-Firefox-preview.xpi` is unsigned. On desktop, load `FirefoxExtension/manifest.json` from about:debugging → This Firefox → Load Temporary Add-on. On Android, enable Remote debugging via USB in Firefox Settings → Advanced, connect the device, and run:

    npx web-ext run --source-dir FirefoxExtension --target firefox-android --adb-device DEVICE_SERIAL --firefox-apk org.mozilla.firefox

Temporary installation ends when Firefox restarts. Normal distribution requires Mozilla signing. Eruda's MIT license is in ERUDA-LICENSE.txt. Shared tools are copied from the Android assets by Scripts/build-firefox.mjs.

## Validation

The initial console prototype was visibly exercised on Firefox for Android 157.0.1: console, Elements and Resource Timing on example.com; protected-site rejection on Mozilla's add-on website. The updated Android build passes Gradle compilation/unit checks. Thirty-seven Node tests cover the shared rule engine, injection lifecycle, pairing validation, authenticated settings synchronization, offline changes and revision conflicts. A device bridge test verifies unauthorized access rejection, profile reads/writes, conflict handling and isolation from the built-in browser.

The Chromium lifecycle test covers automatic activation, reload, disabling, allow-only mode and blacklist precedence, plus Show/Hide/Stop/restart. Run `npm run test:firefox-browser` after `npx playwright install chromium`. This checks shared page runtime behavior; pairing and automatic activation in actual Firefox still require device verification. Mozilla's validator reports zero errors/notices and twelve warnings in the bundled Eruda evaluator/HTML rendering code, which require review before public submission.

Local 0.3.0 centers the panel on wider devices and uses responsive controls without horizontal scrolling. The previously submitted 0.2.0 remains pending Mozilla review. Native custom-plugin loading is stripped from this extension build.
