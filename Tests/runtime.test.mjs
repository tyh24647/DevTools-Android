import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import vm from "node:vm";

const runtime = await fs.readFile(
    new URL("../app/src/main/assets/tools/page-runtime.js", import.meta.url),
    "utf8"
);
function harness() {
    const values = new Map();
    const calls = [];
    const makeConfig = () => ({ set: (key, value) => calls.push(["setting", key, value]) });
    const tools = new Map();
    const eruda = {
        util: {},
        init: () => calls.push(["init"]),
        destroy: () => calls.push(["destroy"]),
        hide: () => calls.push(["hide"]),
        show: () => calls.push(["show"]),
        add: (plugin) => calls.push(["add", plugin]),
        get(name = "root") {
            if (!tools.has(name)) {
                tools.set(name, {
                    config: makeConfig(),
                    hide: () => calls.push(["entryHide"]),
                    show: () => calls.push(["entryShow"]),
                    clear: () => calls.push(["settingsClear"]),
                    text: () => {}
                });
            }
            return tools.get(name);
        }
    };
    const context = vm.createContext({
        __DTAssets: { eruda },
        __DTVersions: { eruda: "3.4.3" },
        document: { head: {} },
        location: { href: "https://example.com/" },
        innerWidth: 390,
        innerHeight: 844,
        structuredClone,
        localStorage: {
            getItem: (key) => values.get(key) ?? null,
            setItem: (key, value) => values.set(key, value)
        }
    });
    vm.runInContext(runtime, context);
    return { api: context.__DevToolsRuntime, context, calls, values };
}
const base = { pro: false, console: {} };
test("repeated sync initializes once with the requested defaults", async () => {
    const h = harness();
    await h.api.apply(base, { run: true }, "", h.context.location.href);
    await h.api.apply(base, { run: true }, "", h.context.location.href);
    assert.equal(h.calls.filter((c) => c[0] === "init").length, 1);
    assert.deepEqual(JSON.parse(h.values.get("eruda-dev-tools")), {
        transparency: 0.98,
        displaySize: 55,
        theme: "Material Palenight"
    });
    assert.equal(JSON.parse(h.values.get("eruda-console")).maxLogNum, "infinite");
    assert.equal(JSON.parse(h.values.get("eruda-sources")).indentSize, 4);
    assert.equal(h.calls.filter((c) => c[0] === "add").length, 0);
});
test("Hide survives ordinary refresh; Show restores the panel and entry", async () => {
    const h = harness();
    await h.api.apply(base, { run: true }, "hide");
    const hidden = await h.api.apply(base, { run: true }, "");
    assert.equal(hidden.hidden, true);
    assert.equal(
        h.calls.some((c) => c[0] === "show"),
        false
    );
    const shown = await h.api.apply(base, { run: true }, "show");
    assert.equal(shown.hidden, false);
    assert.equal(
        h.calls.some((c) => c[0] === "entryShow"),
        true
    );
});
test("blocked and disabled pages destroy the live console", async () => {
    const h = harness();
    await h.api.apply(base, { run: true }, "");
    const state = await h.api.apply(base, { run: false, reason: "Blacklisted" }, "show");
    assert.equal(state.active, false);
    assert.equal(h.calls.filter((c) => c[0] === "destroy").length, 1);
});
test("a navigation race cannot apply the previous page decision", async () => {
    const h = harness();
    const result = await h.api.apply(base, { run: true }, "", "https://old.example/");
    assert.equal(result.stale, true);
    assert.equal(h.calls.length, 0);
});
test("Pro enables selected plugins; revocation removes them", async () => {
    const h = harness();
    h.context.__DTAssets.vue = () => {};
    h.context.__DTResourceTiming = () => {};
    await h.api.apply({ pro: true, console: { vue: true, resourceTiming: true } }, { run: true }, "");
    assert.equal(h.calls.filter((c) => c[0] === "add").length, 2);
    await h.api.apply(base, { run: true }, "");
    assert.equal(h.calls.filter((c) => c[0] === "destroy").length, 1);
    assert.equal(h.calls.filter((c) => c[0] === "settingsClear").length, 1);
});
test("remembered icon position survives and is clamped", async () => {
    const h = harness();
    h.values.set("eruda-entry-button", JSON.stringify({ rememberPos: true, pos: { x: 9999, y: -10 } }));
    await h.api.apply(base, { run: true }, "");
    assert.deepEqual(JSON.parse(h.values.get("eruda-entry-button")).pos, { x: 342, y: 0 });
});

test("custom plugins load only when enabled and reload when source changes", async () => {
    const h = harness();
    const plugin = {name:"My panel",source:'return {name:"custom-panel"};',enabled:false};
    await h.api.apply({...base,plugins:[plugin]}, {run:true}, "");
    assert.equal(h.calls.filter(c => c[0] === "add").length,0);
    await h.api.apply({...base,plugins:[{...plugin,enabled:true}]}, {run:true}, "");
    assert.equal(h.calls.filter(c => c[0] === "add").at(-1)[1].name,"custom-panel");
    await h.api.apply({...base,plugins:[{...plugin,enabled:true,source:'module.exports = {name:"updated"};'}]}, {run:true}, "");
    assert.equal(h.calls.filter(c => c[0] === "add").at(-1)[1].name,"updated");
    await h.api.apply({...base,plugins:[]}, {run:true}, "");
    assert.equal(h.calls.filter(c => c[0] === "destroy").length,3);
});
test("broken custom plugin does not prevent another panel from loading", async () => {
    const h = harness();
    const result = await h.api.apply({...base,plugins:[
        {name:"Broken",enabled:true,source:'throw new Error("Plugin failed");'},
        {name:"Working",enabled:true,source:'return {name:"working"};'}
    ]}, {run:true}, "");
    assert.equal(result.active,true);
    assert.equal(h.calls.filter(c => c[0] === "add").at(-1)[1].name,"working");
    assert.ok(result.warnings.some(warning => warning.includes("Broken")));
});
