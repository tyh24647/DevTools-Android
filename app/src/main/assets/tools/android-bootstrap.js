/* Native-owned inputs arrive as JSON. Media uses a separate bounded message listener with native save confirmation. */
(function () {
    "use strict";
    if (window.top !== window || !/^https?:$/.test(location.protocol)) {
        return;
    }
    const initial = __CONFIG__;
    const ruleSource = __RULE_SOURCE__;
    __RULE_CODE__
    const loadAssets = function () {
        globalThis.__DTExpectedURL = location.href;
        __ASSET_CODE__
    };
    __RUNTIME_CODE__
    const engine = globalThis.DevToolsRules;
    let config = initial;
    let command = __COMMAND__;
    let generation = 0;
    let lastURL = "";
    const previous = globalThis.__DTAndroidDispose;
    if (previous) {
        previous();
    }
    let timer;
    let disposed = false;
    let activeWorker;
    function decide(url) {
        const hasRegex = (config.lists || []).some((list) => list.selected &&
            (list.rules || []).some((rule) => rule.enabled && rule.kind === "regex"));
        if (!hasRegex) {
            return Promise.resolve(engine.evaluate(config, url));
        }
        return new Promise((resolve) => {
            let finished = false;
            let deadline;
            let blobURL;
            let worker;
            function end(result) {
                if (finished) {
                    return;
                }
                finished = true;
                clearTimeout(deadline);
                worker?.terminate();
                if (activeWorker === worker) {
                    activeWorker = null;
                }
                worker = null;
                if (blobURL) {
                    URL.revokeObjectURL(blobURL);
                }
                resolve(result);
            }
            try {
                blobURL = URL.createObjectURL(new Blob([ruleSource,
                    "onmessage=e=>postMessage(DevToolsRules.evaluate(e.data.config,e.data.url));"],
                    { type: "text/javascript" }));
                worker = new Worker(blobURL);
                activeWorker = worker;
                worker.onmessage = (event) => end(event.data);
                worker.onerror = () => end({ run: false, reason: "Regex worker blocked or failed; page excluded." });
                deadline = setTimeout(() => end({ run: false, reason: "Rule evaluation timed out; page excluded." }), 750);
                worker.postMessage({ config, url });
            } catch {
                end({ run: false, reason: "Regex worker unavailable; page excluded." });
            }
        });
    }
    async function apply() {
        activeWorker?.terminate();
        const token = ++generation;
        const url = location.href;
        const changedRoute = lastURL && lastURL !== url;
        lastURL = url;
        // Drop logs/hooks from the previous route before matching the new one.
        if (changedRoute) {
            globalThis.__DevToolsRuntime?.stop();
        }
        const decision = await decide(url);
        if (disposed || token !== generation || location.href !== url) {
            return;
        }
        try {
            if (decision.run) {
                loadAssets();
            }
            globalThis.__DTAndroidStatus = { ...decision,
                ...(await globalThis.__DevToolsRuntime.apply(config, decision, command, url)) };
            command = "";
        } catch (error) {
            globalThis.__DevToolsRuntime.stop();
            globalThis.__DTAndroidStatus = { run: false, error: true, reason: error.message };
        }
    }
    globalThis.__DTAndroidUpdate = function (next, action) {
        config = next;
        command = action || "";
        apply();
    };
    globalThis.__DTAndroidCommand = function (action) {
        command = action;
        const decision = globalThis.__DTAndroidStatus;
        if (!decision) {
            return;
        }
        globalThis.__DevToolsRuntime.apply(config, decision, command, location.href).then((status) => {
            globalThis.__DTAndroidStatus = { ...decision, ...status };
            command = "";
        });
    };
    globalThis.__DTAndroidDispose = function () {
        disposed = true;
        ++generation;
        clearInterval(timer);
        activeWorker?.terminate();
    };
    function start() {
        if (disposed) {
            return;
        }
        apply();
        timer = setInterval(() => {
            if (location.href !== lastURL) {
                apply();
            }
        }, 500);
    }
    if (document.documentElement && document.head) {
        start();
    } else {
        const observer = new MutationObserver(() => {
            if (document.documentElement && document.head) {
                observer.disconnect();
                start();
            }
        });
        observer.observe(document, { childList: true, subtree: true });
    }
})();
