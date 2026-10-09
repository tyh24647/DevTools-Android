import {chromium} from 'playwright';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const browser=await chromium.launch({headless:true});
try{
 const page=await browser.newPage();
 await page.route('https://fixture.test/**',route=>route.fulfill({contentType:'text/html',body:'<h1>CSS fixture</h1>'}));
 await page.goto('https://fixture.test/');
 await page.evaluate(()=>{globalThis.__DTExpectedURL=location.href;});
 await page.addScriptTag({path:'FirefoxExtension/tools/eruda.js'});
 await page.evaluate(code=>{const eruda=__DTAssets.eruda;eruda.init();const plugin=new Function('eruda','module','exports',code)(eruda,{exports:{}},{});eruda.add(plugin);eruda.show();eruda.show('css-editor');},await fs.readFile('app/src/main/assets/tools/css-editor-plugin.js','utf8'));
 await page.getByRole('textbox',{name:'Custom page CSS'}).fill('h1 { color: rgb(100, 10, 200); }');
 await page.getByRole('button',{name:'Apply',exact:true}).click();
 assert.equal(await page.locator('h1').evaluate(el=>getComputedStyle(el).color),'rgb(100, 10, 200)');
 await page.getByRole('checkbox',{name:'Apply saved CSS when visiting this site'}).check();
 await page.getByRole('button',{name:'Save for this site'}).click();
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('devtools.css-editor.v1')).auto),true);
 await page.getByRole('button',{name:'Reset page',exact:true}).click();
 assert.equal(await page.locator('style[data-devtools-custom-css]').count(),0);
 await page.reload();
 await page.evaluate(()=>{globalThis.__DTExpectedURL=location.href;});
 await page.addScriptTag({path:'FirefoxExtension/tools/eruda.js'});
 await page.evaluate(code=>{const eruda=__DTAssets.eruda;eruda.init();eruda.add(new Function('eruda','module','exports',code)(eruda,{exports:{}},{}));eruda.show();eruda.show('css-editor');},await fs.readFile('app/src/main/assets/tools/css-editor-plugin.js','utf8'));
 assert.equal(await page.locator('h1').evaluate(el=>getComputedStyle(el).color),'rgb(100, 10, 200)');
 await page.getByRole('button',{name:'Delete saved CSS'}).click();
 assert.equal(await page.evaluate(()=>localStorage.getItem('devtools.css-editor.v1')),null);
 console.log('PASS: real Eruda CSS panel apply/reset, site saving, automatic reapply and deletion.');
}finally{await browser.close();}
