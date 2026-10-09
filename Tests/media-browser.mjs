import {chromium} from 'playwright';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const browser=await chromium.launch({headless:true});
try {
 const page=await browser.newPage({viewport:{width:412,height:915}});
 await page.route('https://fixture.test/**',async route=>{
  if(route.request().url().endsWith('/'))await route.fulfill({contentType:'text/html',body:'<!doctype html><title>Media fixture</title><h1>Media fixture</h1><video src="/vod.m3u8" width="320" height="180" controls></video><img src="/photo.png" alt="Page image" width="100" height="100">'});
  else await route.fulfill({status:404,body:''});
 });
 await page.goto('https://fixture.test/');
 await page.evaluate(()=>{globalThis.messages=[];globalThis.devtoolsMedia={postMessage:data=>messages.push(JSON.parse(data))};globalThis.__DTExpectedURL=location.href;});
 await page.addScriptTag({path:'FirefoxExtension/tools/eruda.js'});
 await page.addScriptTag({path:'app/src/main/assets/tools/page-runtime.js'});
 await page.evaluate(()=>__DevToolsRuntime.apply({enabled:true,console:{}},{run:true},'show'));
 await page.addScriptTag({path:'app/src/main/assets/tools/media-resources.js'});
 assert.equal(await page.evaluate(()=>messages.some(m=>m.type==='discover'||m.type==='save')),false,'Discovery must not save or populate the native library');
 await page.getByRole('button',{name:'Download or record this video'}).click();
 assert.ok(await page.evaluate(()=>messages.some(m=>m.type==='save')));
 await page.evaluate(()=>{__DTAssets.eruda.show('resources');__DTMedia.refresh();});
 await page.locator('[data-devtools-videos]').waitFor({state:'attached'});
 const beforeImages=await page.locator('[data-devtools-videos]').evaluate(section=>{
  const images=section.parentElement.querySelector('.eruda-image');
  return !!images && !!(section.compareDocumentPosition(images)&Node.DOCUMENT_POSITION_FOLLOWING);
 });
 assert.equal(beforeImages,true);
 await page.locator('[data-media-url="https://fixture.test/vod.m3u8"] button').click();
 assert.equal(await page.locator('dialog[open] video').count(),1);
 await page.getByRole('button',{name:'Close',exact:true}).click();
 assert.equal(await page.locator('[data-devtools-videos] img').count(),0);
 assert.equal(await page.locator('[data-media-url="https://fixture.test/photo.png"]').count(),0);
 assert.equal(await page.locator('[data-media-url="https://fixture.test/vod.m3u8"] button').evaluate(el=>getComputedStyle(el).backgroundColor),'rgba(0, 0, 0, 0)');
 await page.locator('#eruda .eruda-image img').click();
 assert.equal(await page.locator('dialog[open] img').count(),1);
 assert.equal(await page.locator('dialog[open] video').count(),0);
 assert.equal(await page.locator('[data-media-url="https://fixture.test/photo.png"] video').count(),0);
 await page.getByRole('button',{name:'Save resource',exact:true}).click();
 assert.ok(await page.evaluate(()=>messages.some(m=>m.type==='save'&&m.url.endsWith('photo.png'))));
 await page.evaluate(()=>{__DTMedia.status('https://fixture.test/vod.m3u8','Recording',null,true,true);document.querySelector('body > video').remove();__DTMedia.refresh();});
 assert.ok(await page.evaluate(()=>messages.some(m=>m.type==='unload')));
 console.log('PASS: visible-video download button, native save request, Eruda video section before images, modal preview and source-unload signal.');
} finally {await browser.close();}
