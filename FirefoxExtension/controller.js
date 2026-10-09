export async function runCommand(api, tabId, command, profile = null, decision = {run: true}, expectedURL = null) {
    if (!Number.isInteger(tabId)) throw new Error("Open a web page in Firefox first.");
    if (!["show", "hide", "stop", "auto"].includes(command)) throw new Error("Unknown console action.");
    const execute = async (details) => {
        const results = await api.scripting.executeScript({ target: { tabId }, world: "MAIN", ...details });
        const failed = results.find((entry) => entry.error);
        if (failed) throw new Error(failed.error.message || String(failed.error));
        const top = results.find((entry) => entry.frameId === 0);
        if (!top) throw new Error("Firefox did not allow access to this page.");
        return top.result;
    };
    const context = await execute({ func: () => {
        if (!/^https?:$/.test(location.protocol)) throw new Error("Open an HTTP or HTTPS page first.");
        return { url: location.href, loaded: !!globalThis.__DevToolsRuntime && !!globalThis.__DTAssets?.eruda };
    } });
    if (expectedURL && context.url !== expectedURL) throw new Error("The page changed. Try again.");
    if (!decision.run) {
        if (context.loaded) await execute({func: () => globalThis.__DevToolsRuntime.stop()});
        return {active: false, reason: decision.reason};
    }
    if (!["show", "auto"].includes(command) && !context.loaded) return { active: false };
    if (!context.loaded) {
        await execute({ func: (url) => { globalThis.__DTExpectedURL = url; }, args: [context.url] });
        await execute({ files: ["tools/eruda.js", "tools/rule-engine.js", "tools/resource-timing.js", "tools/source-formatting.js", "tools/page-runtime.js"] });
    }
    return execute({ func: async (action, expectedURL, settings, matched) => {
        if (location.href !== expectedURL) throw new Error("The page changed. Open DevTools again.");
        if (action === "stop") {
            globalThis.__DevToolsRuntime.stop();
            return { active: false };
        }
        const config = {
            enabled: settings?.enabled ?? true, runEverywhere: settings?.runEverywhere ?? true, lists: settings?.lists ?? [], pro: true,
            console: { backend: "eruda", displaySize: 55, transparency: 0.98,
                theme: "Material Palenight", rememberPosition: true,
                wrapText: settings?.console?.wrapText !== false, entryColor: settings?.console?.entryColor || "",
                resourceTiming: true, vue: false, code: false, dom: false,
                timing: false, fps: false, features: false }
        };
        return globalThis.__DevToolsRuntime.apply(config,
            matched, action === "auto" ? "" : action, expectedURL);
    }, args: [command, context.url, profile, decision] });
}
