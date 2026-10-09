import { chromium } from 'playwright';
import fs from 'node:fs/promises';
import http from 'node:http';
import assert from 'node:assert/strict';
import { runCommand } from '../FirefoxExtension/controller.js';
import '../FirefoxExtension/tools/rule-engine.js';
const server=http.createServer((request,response)=>{
    response.setHeader('Content-Type','text/html');
    response.end('<!doctype html><html><head><title>Firefox console fixture</title></head><body><h1>Console fixture</h1></body></html>');
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,executablePath:process.env.DEVTOOLS_BROWSER_PATH});
try {
    const page=await browser.newPage({viewport:{width:412,height:915}});
    await page.goto(`http://127.0.0.1:${server.address().port}`);
    const api={scripting:{executeScript:async({func,args=[],files})=>{
        try {
            let result;
            if(files) {
                for(const file of files) await page.addScriptTag({content:await fs.readFile(`FirefoxExtension/${file}`,'utf8')});
            } else result=await page.evaluate(({source,args})=>(0,eval)(`(${source})`)(...args),{source:func.toString(),args});
            return [{frameId:0,result}];
        } catch(error) {return [{frameId:0,error:{message:error.message}}];}
    }}};
    assert.equal((await runCommand(api,1,'show')).active,true);
    assert.equal(await page.evaluate(()=>__DTVersions.eruda),'3.4.3');
    assert.ok(await page.locator('#eruda').count());
    assert.equal((await runCommand(api,1,'hide')).hidden,true);
    assert.equal((await runCommand(api,1,'show')).hidden,false);
    assert.equal((await runCommand(api,1,'stop')).active,false);
    assert.equal((await runCommand(api,1,'show')).active,true);
    await page.reload();
    assert.equal(await page.evaluate(()=>!!globalThis.__DevToolsRuntime),false);
    assert.equal((await runCommand(api,1,'show')).active,true);
    const url=page.url();
    const profile={enabled:true,runEverywhere:true,lists:[]};
    const apply=async settings=>runCommand(api,1,'auto',settings,globalThis.DevToolsRules.evaluate(settings,url),url);
    assert.equal((await apply(profile)).active,true);
    assert.equal((await apply({...profile,enabled:false})).active,false);
    const allow={kind:'allow',selected:true,rules:[{kind:'domain',pattern:'127.0.0.1',enabled:true}]};
    assert.equal((await apply({...profile,runEverywhere:false})).active,false);
    assert.equal((await apply({...profile,runEverywhere:false,lists:[allow]})).active,true);
    assert.equal((await apply({...profile,lists:[allow,{...allow,kind:'block'}]})).active,false);
    await page.reload();
    assert.equal((await apply(profile)).active,true);
    console.log('PASS: console lifecycle, automatic activation/reload, disable, allow-only and blocklist precedence in a real browser. Firefox permission/API validation is tested separately on device.');
} finally {await browser.close();server.close();}
