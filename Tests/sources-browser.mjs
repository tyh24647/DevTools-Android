import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
const browser=await chromium.launch({headless:true});
try {
 const page=await browser.newPage({viewport:{width:412,height:915}});
 await page.route('https://sources.test/**',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><h1>Sources fixture</h1>'}));
 await page.goto('https://sources.test/');
 await page.evaluate(()=>{globalThis.__DTExpectedURL=location.href});
 await page.addScriptTag({path:'FirefoxExtension/tools/eruda.js'});
 await page.addScriptTag({path:'FirefoxExtension/tools/source-formatting.js'});
 await page.addScriptTag({path:'FirefoxExtension/tools/page-runtime.js'});
 const config={enabled:true,runEverywhere:true,pro:false,lists:[],console:{}};
 await page.evaluate(async config=>{await __DevToolsRuntime.apply(config,{run:true},'show');__DTAssets.eruda.show('sources');},config);
 await page.waitForFunction(()=>!__DTAssets.eruda.get('sources')._isGettingHtml);
 const raw='function minified(x){const label="<img src=x onerror=globalThis.bad=1>";return x.map(v=>({label,value:v}));}';
 await page.evaluate(raw=>__DTAssets.eruda.get('sources').set('js',raw),raw);
 const rendered=await page.evaluate(()=>{
  const s=__DTAssets.eruda.get('sources');return {text:s._$el.get(0).innerText,lines:s._$el.get(0).querySelectorAll('.luna-text-viewer-line').length,raw:s._data.val};
 });
 assert.equal(rendered.raw,raw);assert.ok(rendered.text.includes('    const label'));assert.ok(rendered.text.includes('\n'));assert.equal(await page.evaluate(()=>globalThis.bad),undefined);
 await page.evaluate(()=>__DTAssets.eruda.get('sources').config.set('formatCode',false));
 assert.equal(await page.evaluate(()=>__DTAssets.eruda.get('sources')._$el.get(0).innerText.includes('    const label')),false);
 await page.evaluate(()=>__DTAssets.eruda.get('sources').config.set('formatCode',true));
 assert.ok(await page.evaluate(()=>__DTAssets.eruda.get('sources')._$el.get(0).innerText.includes('    const label')));
 for(const [type,code] of [['css','body{color:red;background:black}'],['html','<html><body><div><span>Text</span></div></body></html>']]){
  await page.evaluate(({type,code})=>__DTAssets.eruda.get('sources').set(type,code),{type,code});
  assert.ok(await page.evaluate(()=>__DTAssets.eruda.get('sources')._$el.get(0).innerText.includes('\n')));
 }
 await page.evaluate(async config=>{await __DevToolsRuntime.apply(config,{run:false});await __DevToolsRuntime.apply(config,{run:true},'show');__DTAssets.eruda.show('sources');__DTAssets.eruda.get('sources').set('js','function again(){return 1;}');},config);
 assert.ok(await page.evaluate(()=>__DTAssets.eruda.get('sources')._$el.get(0).innerText.includes('\n')));
 console.log('PASS: actual Eruda Sources expands JS/CSS/HTML, preserves original source, toggles formatting, survives restart and never executes displayed source.');
}finally{await browser.close();}
