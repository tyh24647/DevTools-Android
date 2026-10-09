import { chromium } from 'playwright';
import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import assert from 'node:assert/strict';

const tools = path.resolve('app/src/main/assets/tools');
const template = await fs.readFile(path.join(tools,'android-bootstrap.js'),'utf8');
const engine = await fs.readFile(path.join(tools,'rule-engine.js'),'utf8');
const runtime = await fs.readFile(path.join(tools,'page-runtime.js'),'utf8');
const assets = (await Promise.all(['eruda','vue','code','dom','timing','fps','features'].map(id => fs.readFile(path.join(tools,`vendor/${id}.js`),'utf8')))).join('\n') + '\n' + await fs.readFile(path.join(tools,'resource-timing.js'),'utf8');
const config = {enabled:true,runEverywhere:true,pro:true,lists:[],console:{displaySize:55,transparency:0.98,theme:'Material Palenight',rememberPosition:true,backend:'eruda',vueAdapter:'modern',vue:true,resourceTiming:true,code:true,dom:true,timing:true,fps:true,features:true}};
function script(configuration) {
    return template.replace('__CONFIG__',()=>JSON.stringify(configuration)).replace('__RULE_SOURCE__',()=>JSON.stringify(engine))
        .replace('__RULE_CODE__',()=>engine).replace('__ASSET_CODE__',()=>assets).replace('__RUNTIME_CODE__',()=>runtime).replace('__COMMAND__','""');
}
const server=http.createServer((req,res)=>{
    if(req.url==='/data.json'){res.setHeader('Content-Type','application/json');res.end('{"ok":true}');return;}
    if(req.url==='/csp'){res.setHeader('Content-Security-Policy',"worker-src 'none'");}
    res.setHeader('Content-Type','text/html');
    res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><h1>Android DevTools fixture</h1><p>Console and resource timing on a mobile viewport.</p></body></html>');
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const origin=`http://127.0.0.1:${server.address().port}`;
const browser=await chromium.launch({headless:true,executablePath:process.env.DEVTOOLS_BROWSER_PATH,args:['--no-sandbox']});
const errors=[];
try{
    const context=await browser.newContext({viewport:{width:412,height:915},deviceScaleFactor:1});
    await context.addInitScript({content:script(config)});
    const page=await context.newPage();
    page.on('pageerror',error=>errors.push(error.message));
    await page.goto(origin+'/');
    await page.waitForFunction(()=>globalThis.__DTAndroidStatus?.active===true);
    assert.equal(await page.evaluate(()=>__DTVersions.eruda),'3.4.3');
    console.log('PASS: document-start bootstrap initializes real Eruda and Pro plugins');
    await page.evaluate(async()=>{await fetch('/data.json'); console.log('Android smoke fixture');});
    await page.evaluate(()=>__DTAssets.eruda.show('resource timing'));
    await page.waitForFunction(()=>document.querySelector('#eruda')?.shadowRoot?.textContent.includes('data.json'));
    await page.evaluate(()=>__DTAndroidCommand('hide'));
    await page.waitForFunction(()=>__DTAndroidStatus.hidden===true);
    await page.evaluate(()=>__DTAndroidCommand('show'));
    await page.waitForFunction(()=>__DTAndroidStatus.hidden===false);
    console.log('PASS: Hide/Show and real fetch resource timing');
    await page.evaluate(()=>__DTAssets.eruda.show('resource timing'));

    await page.evaluate(cfg=>__DTAndroidUpdate({...cfg,lists:[{kind:'block',name:'Private',selected:true,rules:[{kind:'domain',pattern:'127.0.0.1',enabled:true}]}]},''),config);
    await page.waitForFunction(()=>__DTAndroidStatus?.active===false);
    assert.match(await page.evaluate(()=>__DTAndroidStatus.reason),/Blacklisted/);
    console.log('PASS: blacklist settings revoke a running console');
    await page.evaluate(({cfg,url})=>__DTAndroidUpdate({...cfg,runEverywhere:false,lists:[{kind:'allow',name:'Home',selected:true,rules:[{kind:'url',pattern:url,enabled:true}]}]},''),{cfg:config,url:origin+'/'});
    await page.waitForFunction(()=>__DTAndroidStatus?.active===true);
    await page.evaluate(()=>history.pushState({},'', '/private'));
    await page.waitForFunction(()=>__DTAndroidStatus?.active===false);
    console.log('PASS: SPA route change tears down excluded-page tools');
    const regexConfig={...config,runEverywhere:false,lists:[{kind:'allow',name:'Regex',selected:true,rules:[{kind:'regex',pattern:'127\\.0\\.0\\.1',enabled:true}]}]};
    await page.evaluate(cfg=>__DTAndroidUpdate(cfg,''),regexConfig);
    await page.waitForFunction(()=>__DTAndroidStatus?.active===true);
    console.log('PASS: regex matches through a real dedicated Worker');
    const cspContext=await browser.newContext();
    await cspContext.addInitScript({content:script(regexConfig)});
    const blocked=await cspContext.newPage();
    await blocked.goto(origin+'/csp');
    await blocked.waitForFunction(()=>__DTAndroidStatus?.run===false);
    assert.equal(await blocked.evaluate(()=>!!globalThis.__DTAssets),false);
    console.log('PASS: worker CSP restriction excludes page without running assets');
    await cspContext.close();
    assert.deepEqual(errors,[]);
    console.log('6 Android browser scenarios passed; no uncaught page errors.');
}finally{
    await browser.close();
    await new Promise(resolve=>server.close(resolve));
}
