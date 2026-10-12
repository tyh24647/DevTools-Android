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
        return { url: location.href, loaded: !!globalThis.__DevToolsRuntime && !!globalThis.__DTAssets?.eruda, assets: Object.keys(globalThis.__DTAssets || {}), media: !!globalThis.__DTMedia };
    } });
    if (expectedURL && context.url !== expectedURL) throw new Error("The page changed. Try again.");
    if (!decision.run) {
        if (context.loaded) await execute({func: () => globalThis.__DevToolsRuntime.stop()});
        return {active: false, reason: decision.reason};
    }
    if (!["show", "auto"].includes(command) && !context.loaded) return { active: false };
    const options = profile?.console || {};
    const ids = new Set();
    if (profile?.pro) {
        if (options.backend === 'vconsole') {ids.add('vconsole');if(options.vue)ids.add('vueVConsole');}
        else {
            if(options.vue)ids.add(options.vueAdapter==='legacy'?'vueLegacy':'vue');
            for(const id of ['code','dom','timing','fps','features'])if(options[id])ids.add(id);
        }
    }
    const extra=[...ids].filter(id=>!context.assets?.includes(id)&&!['vue','vueLegacy','vueVConsole'].includes(id)).map(id=>'tools/'+id+'.js');
    if(profile?.mediaConnected && !context.media)extra.push('tools/firefox-media.js','tools/media-resources.js');
    if (!context.loaded || extra.length || [...ids].some(id=>!context.assets?.includes(id))) {
        await execute({ func: (url) => { globalThis.__DTExpectedURL = url; }, args: [context.url] });
        for(const id of ['vue','vueLegacy','vueVConsole'].filter(id=>ids.has(id)&&!context.assets?.includes(id))) {
            const response=await fetch(api.runtime.getURL('tools/frontends/'+id+'.gz'));
            if(!response.ok)throw new Error('Bundled Vue frontend unavailable.');
            const frontend=await new Response(response.body.pipeThrough(new DecompressionStream('gzip'))).text();
            await execute({func:frontend=>{globalThis.__DTBundledVueFrontend=frontend;},args:[frontend]});
            await execute({files:['tools/'+id+'.js']});
            await execute({func:()=>{delete globalThis.__DTBundledVueFrontend;}});
        }
        await execute({ files: [...(!context.loaded ? ["tools/eruda.js", "tools/rule-engine.js", "tools/resource-timing.js", "tools/source-formatting.js", "tools/css-editor.js", "tools/page-runtime.js"] : []), ...extra] });
    }
    return execute({ func: async (action, expectedURL, settings, matched) => {
        if (location.href !== expectedURL) throw new Error("The page changed. Open DevTools again.");
        if (action === "stop") {
            globalThis.__DevToolsRuntime.stop();
            return { active: false };
        }
        const config = {
            enabled: settings?.enabled ?? true, runEverywhere: settings?.runEverywhere ?? true, lists: settings?.lists ?? [], pro: settings?.pro ?? false,
            console: { backend: "eruda", displaySize: 55, transparency: 0.98,
                theme: "Material Palenight", rememberPosition: true,
                resourceTiming: true, vue: false, code: false, dom: false,
                timing: false, fps: false, features: false, cssEditor: true,
                ...(settings?.console || {}) }

        };
        return globalThis.__DevToolsRuntime.apply(config,
            matched, action === "auto" ? "" : action, expectedURL);
    }, args: [command, context.url, profile, decision] });
}
