(function () {
    "use strict";
    if (globalThis.__DevToolsRuntime) {
        return;
    }
    const defaults = {
        console: {
            asyncRender: true,
            catchGlobalErr: true,
            jsExecution: true,
            overrideConsole: true,
            displayExtraInfo: true,
            displayUnenumerable: true,
            displayGetterVal: true,
            lazyEvaluation: true,
            displayIfErr: false,
            maxLogNum: "infinite"
        },
        "dev-tools": { transparency: 0.98, displaySize: 55, theme: "Material Palenight" },
        elements: { overrideEventTarget: true, observeElement: true },
        "entry-button": { rememberPos: true, pos: { x: 263.807642, y: 0 } },
        resources: { hideErudaSetting: false, observeElement: true },
        sources: { showLineNum: true, formatCode: true, indentSize: 4 }
    };
    const state = { eruda: null, vconsole: null, signature: "", hidden: false, warnings: [], uiCleanup: null };
    function stop() {
        state.uiCleanup?.();
        state.uiCleanup = null;
        try {
            state.eruda?.destroy();
        } catch (error) {
            state.warnings.push(error.message);
        }
        try {
            state.vconsole?.destroy();
        } catch (error) {
            state.warnings.push(error.message);
        }
        state.eruda = null;
        state.vconsole = null;
        state.signature = "";
        state.hidden = false;
    }
    function configSet(config, values) {
        if (!config) {
            return;
        }
        for (const [key, value] of Object.entries(values)) {
            try {
                config.set(key, value);
            } catch (error) {
                state.warnings.push(`${key}: ${error.message}`);
            }
        }
    }
    function seed(options, pro) {
        for (const [key, values] of Object.entries(defaults)) {
            try {
                const storageKey = `eruda-${key}`;
                // Preserve a remembered per-origin icon position; app settings own all other defaults.
                const next = structuredClone(values);
                if (key === "entry-button") {
                    const old = JSON.parse(localStorage.getItem(storageKey) || "{}");
                    next.rememberPos = pro ? options.rememberPosition : true;
                    next.pos =
                        next.rememberPos && old.pos
                            ? old.pos
                            : { x: options.positionX ?? 263.807642, y: options.positionY ?? 0 };
                    next.pos.x = Math.max(0, Math.min(innerWidth - 48, next.pos.x));
                    next.pos.y = Math.max(0, Math.min(innerHeight - 48, next.pos.y));
                }
                if (key === "dev-tools" && pro) {
                    Object.assign(next, {
                        displaySize: options.displaySize,
                        transparency: options.transparency,
                        theme: options.theme
                    });
                }
                localStorage.setItem(storageKey, JSON.stringify(next));
            } catch {
                // Safari private mode or a site's storage policy can prevent persistence.
            }
        }
    }
    function add(id, factory) {
        try {
            const plugin = factory || globalThis.__DTAssets?.[id];
            if (plugin) {
                state.eruda.add(plugin);
            }
        } catch (error) {
            state.warnings.push(`${id}: ${error.message}`);
        }
    }
    async function apply(config, decision, command, expectedURL) {
        if (expectedURL && location.href !== expectedURL) {
            return { active: !!state.eruda || !!state.vconsole, stale: true };
        }
        if (!decision.run) {
            stop();
            return { active: false, reason: decision.reason };
        }
        if (!document.head) {
            await new Promise((resolve) => {
                const observer = new MutationObserver(() => {
                    if (document.head) {
                        observer.disconnect();
                        resolve();
                    }
                });
                observer.observe(document.documentElement, { childList: true });
            });
        }
        const options = config.console || {};
        const pro = !!config.pro;
        const plugins = (config.plugins || []).filter(plugin => plugin.enabled && plugin.kind !== "userscript");
        const signature = JSON.stringify({ options: pro ? options : {wrapText: options.wrapText, entryColor: options.entryColor}, pro, plugins });
        if (state.signature !== signature) {
            stop();
            state.warnings = [];
            seed(options, pro);
            const assets = globalThis.__DTAssets || {};
            if (pro && options.backend === "vconsole") {
                if (!assets.vconsole) {
                    throw new Error("vConsole package is unavailable.");
                }
                state.vconsole = new assets.vconsole({ theme: "dark" });
                if (options.vue && assets.vueVConsole) {
                    try {
                        assets.vueVConsole.initPlugin(state.vconsole);
                    } catch (error) {
                        state.warnings.push(error.message);
                    }
                }
            } else {
                if (!assets.eruda?.init) {
                    throw new Error("Eruda package is unavailable.");
                }
                state.eruda = assets.eruda;
                // eruda-fps 2.0.0 still expects the pre-3.x now helper.
                state.eruda.util.now ||= Date.now;
                state.eruda.init({
                    useShadowDom: true,
                    defaults: pro
                        ? {
                              transparency: options.transparency,
                              displaySize: options.displaySize,
                              theme: options.theme
                          }
                        : defaults["dev-tools"]
                });
                for (const [name, values] of Object.entries(defaults)) {
                    if (name === "entry-button") {
                        continue;
                    }
                    const tool = name === "dev-tools" ? state.eruda.get() : state.eruda.get(name);
                    configSet(
                        tool?.config,
                        name === "dev-tools" && pro
                            ? {
                                  transparency: options.transparency,
                                  displaySize: options.displaySize,
                                  theme: options.theme
                              }
                            : values
                    );
                }
                configSet(state.eruda.get("entryBtn")?.config, {
                    rememberPos: pro ? options.rememberPosition : true
                });
                // NATIVE_PLUGINS_START
                for (const plugin of plugins) {
                    try {
                        const tool = new Function("eruda", "module", "exports", plugin.source);
                        const module = { exports: {} };
                        const result = tool(state.eruda, module, module.exports);
                        const exported = result || module.exports.default || module.exports;
                        if (!exported || (typeof exported !== "function" && !exported.name)) {
                            throw new Error("Return an Eruda tool or export a plugin factory.");
                        }
                        state.eruda.add(exported);
                    } catch (error) {
                        state.warnings.push(plugin.name + ": " + error.message);
                    }
                }
                // NATIVE_PLUGINS_END
                if (pro) {
                    if (options.vue) {
                        if (options.vueAdapter === "legacy") {
                            try {
                                assets.vueLegacy?.initPlugin(state.eruda);
                            } catch (error) {
                                state.warnings.push(error.message);
                            }
                        } else {
                            add("vue");
                        }
                    }
                    for (const id of ["code", "dom", "timing", "fps", "features"]) {
                        if (options[id]) {
                            add(id);
                        }
                    }
                    if (options.resourceTiming) {
                        add("resource timing", globalThis.__DTResourceTiming);
                    }
                } else {
                    // Remove UI controls for customization while leaving the requested defaults active.
                    const settings = state.eruda.get("settings");
                    settings?.clear();
                    settings?.text(
                        "DevTools defaults are active. Unlock Pro in the DevTools app for customization and extra plugins."
                    );
                }
            }
            if (state.eruda) {
                globalThis.__DTSourceFormatting?.(state.eruda);
                state.uiCleanup = globalThis.__DTConsoleUI?.(state.eruda, options);
            }
            state.signature = signature;
        }
        if (command === "hide") {
            state.hidden = true;
            state.eruda?.hide();
            state.eruda?.get("entryBtn")?.hide();
            state.vconsole?.hide();
            state.vconsole?.hideSwitch();
        } else if (command === "show") {
            state.hidden = false;
            state.eruda?.get("entryBtn")?.show();
            state.eruda?.show();
            state.vconsole?.showSwitch();
            state.vconsole?.show();
        }
        return {
            active: !!state.eruda || !!state.vconsole,
            hidden: state.hidden,
            versions: globalThis.__DTVersions || {},
            warnings: state.warnings.slice(0, 8)
        };
    }
    globalThis.__DevToolsRuntime = { apply, stop, defaults };
})();
