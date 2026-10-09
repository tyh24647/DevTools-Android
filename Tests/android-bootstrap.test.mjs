import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';

const source = fs.readFileSync('app/src/main/assets/tools/android-bootstrap.js', 'utf8');
const engine = fs.readFileSync('app/src/main/assets/tools/rule-engine.js', 'utf8');
const base = () => ({ enabled: true, runEverywhere: true, lists: [], pro: true, console: {} });
function page(config = base(), extras = {}) {
    const calls = { load: 0, stop: 0, apply: [], timers: [] };
    const context = vm.createContext({
        URL, Blob, Promise, structuredClone,
        location: { href: 'https://example.com/page', protocol: 'https:' },
        document: { documentElement: {}, head: {} },
        setInterval: fn => { calls.timers.push(fn); return calls.timers.length; },
        clearInterval() {}, setTimeout, clearTimeout,
        ...extras,
        calls,
    });
    context.window = context;
    context.top = context;
    const code = source.replace('__CONFIG__', () => JSON.stringify(config))
        .replace('__RULE_SOURCE__', () => JSON.stringify(engine)).replace('__RULE_CODE__', () => engine)
        .replace('__ASSET_CODE__', 'calls.load++;')
        .replace('__RUNTIME_CODE__', `globalThis.__DevToolsRuntime ||= {
            stop(){calls.stop++;},
            async apply(config,decision,command,url){calls.apply.push({config,decision,command,url});return {active:decision.run,hidden:command==='hide'};}
        };`).replace('__COMMAND__', '""');
    vm.runInContext(code, context);
    return { context, calls };
}
const settle = () => new Promise(resolve => setImmediate(resolve));

test('eligible page loads tools and evaluates its current URL', async () => {
    const { context, calls } = page();
    await settle();
    assert.equal(calls.load, 1);
    assert.equal(context.__DTAndroidStatus.active, true);
    assert.equal(calls.apply[0].url, context.location.href);
});
test('blacklisted page never executes bundled assets', async () => {
    const config = base();
    config.lists.push({name:'Private',kind:'block',selected:true,rules:[{kind:'domain',pattern:'example.com',enabled:true}]});
    const { context, calls } = page(config);
    await settle();
    assert.equal(calls.load, 0);
    assert.equal(context.__DTAndroidStatus.active, false);
});
test('config updates revoke eligibility on an existing document', async () => {
    const { context, calls } = page();
    await settle();
    context.__DTAndroidUpdate({...base(), enabled:false}, '');
    await settle();
    assert.equal(calls.load, 1);
    assert.equal(context.__DTAndroidStatus.active, false);
});
test('SPA navigation stops the old runtime and applies the new route', async () => {
    const config = {...base(),runEverywhere:false,lists:[{name:'Dev',kind:'allow',selected:true,rules:[{kind:'url',pattern:'https://example.com/page',enabled:true}]}]};
    const { context, calls } = page(config);
    await settle();
    context.location.href = 'https://example.com/private';
    calls.timers[0]();
    await settle();
    assert.equal(calls.stop, 1);
    assert.equal(context.__DTAndroidStatus.active, false);
});
test('Show and Hide call the page runtime without a native bridge', async () => {
    const { context, calls } = page();
    await settle();
    context.__DTAndroidCommand('hide');
    await settle();
    assert.equal(calls.apply.at(-1).command, 'hide');
    assert.equal(context.__DTAndroidStatus.hidden, true);
    assert.equal(context.Android, undefined);
});
test('regex worker rejection fails closed and never runs regex on the main thread', async () => {
    const config = base();
    config.lists.push({name:'Regex',kind:'allow',selected:true,rules:[{kind:'regex',pattern:'/example/',enabled:true}]});
    const { context, calls } = page(config);
    await settle();
    assert.equal(calls.load, 0);
    assert.equal(context.__DTAndroidStatus.run, false);
    assert.match(context.__DTAndroidStatus.reason, /worker unavailable/);
});
test('script reinjection disposes the prior observer loop', async () => {
    const { context, calls } = page();
    await settle();
    context.__DTAndroidDispose();
    context.location.href = 'https://example.com/other';
    calls.timers[0]();
    await settle();
    assert.equal(calls.apply.length, 1);
});

test('Hide requested before matching completes is retained for initialization', async () => {
    const {context, calls} = page();
    context.__DTAndroidCommand('hide');
    await settle();
    assert.equal(calls.apply.at(-1).command, 'hide');
    assert.equal(context.__DTAndroidStatus.hidden, true);
});
