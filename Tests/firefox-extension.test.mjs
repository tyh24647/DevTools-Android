import test from 'node:test';
import assert from 'node:assert/strict';
import { runCommand } from '../FirefoxExtension/controller.js';
function api(results) {
    const calls=[];
    return { calls, scripting: { executeScript: async (details) => {
        calls.push(details);
        const result=results.shift();
        if(result instanceof Error) throw result;
        return result;
    } } };
}
const ok=(result)=>[{frameId:0,result}];
test('Firefox Show injects bundled files into the page world and applies the console',async()=>{
    const browser=api([ok({url:'https://example.com/',loaded:false}),ok(),ok(),ok({active:true,hidden:false})]);
    assert.deepEqual(await runCommand(browser,7,'show'),{active:true,hidden:false});
    assert.equal(browser.calls.length,4);
    assert.ok(browser.calls.every(call=>call.world==='MAIN'&&call.target.tabId===7));
    assert.ok(browser.calls[2].files.includes('tools/eruda.js'));
    assert.deepEqual(browser.calls[3].args.slice(0,2),['show','https://example.com/']);
});
test('Hide on a fresh page never loads the console',async()=>{
    const browser=api([ok({url:'https://example.com/',loaded:false})]);
    assert.deepEqual(await runCommand(browser,7,'hide'),{active:false});
    assert.equal(browser.calls.length,1);
});
test('Stop reuses the existing runtime without loading bundles',async()=>{
    const browser=api([ok({url:'https://example.com/',loaded:true}),ok({active:false})]);
    assert.deepEqual(await runCommand(browser,7,'stop'),{active:false});
    assert.equal(browser.calls.length,2);
});
test('Firefox per-frame injection exceptions are surfaced',async()=>{
    const browser=api([[{frameId:0,error:{message:'Protected page'}}]]);
    await assert.rejects(runCommand(browser,7,'show'),/Protected page/);
});
test('Invalid commands and missing tabs are rejected before injection',async()=>{
    const browser=api([]);
    await assert.rejects(runCommand(browser,undefined,'show'),/Open a web page/);
    await assert.rejects(runCommand(browser,7,'unknown'),/Unknown/);
    assert.equal(browser.calls.length,0);
});
