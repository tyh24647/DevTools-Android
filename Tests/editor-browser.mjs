import {chromium} from 'playwright';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const browser=await chromium.launch({headless:true});
try {
 const page=await browser.newPage({viewport:{width:412,height:750}});
 await page.setContent(await fs.readFile('app/src/main/assets/editor/index.html','utf8'));
 await page.evaluate(()=>{globalThis.updates=[];globalThis.editorBridge={postMessage:data=>updates.push(JSON.parse(data))};DevToolsEditor.init('const userDefinedValue = 42;\nuserD','userscript',{theme:'dark',closeBrackets:true,matchBrackets:true,invisibles:true,wrap:true});});
 await page.evaluate(()=>{const view=DevToolsEditor.view();view.dispatch({selection:{anchor:view.state.doc.length}});view.focus();DevToolsEditor.complete();});
 await page.getByRole('option',{name:'userDefinedValue',exact:true}).waitFor();
 await page.keyboard.press('Enter');
 assert.match(await page.evaluate(()=>DevToolsEditor.snapshot().source),/\nuserDefinedValue$/);
 await page.evaluate(()=>DevToolsEditor.configure({theme:'light',closeBrackets:false,matchBrackets:false,invisibles:false,wrap:true,indentSize:4}));
 assert.equal(await page.locator('html').getAttribute('data-theme'),'light');
 await page.evaluate(async()=>{DevToolsEditor.init('return {name:"sample",init(el){const a=1;}}','eruda',{theme:'light',indentSize:4,closeBrackets:true,matchBrackets:true});await DevToolsEditor.format();});
 assert.equal(await page.evaluate(()=>DevToolsEditor.snapshot().error),null);
 assert.match(await page.evaluate(()=>DevToolsEditor.snapshot().source),/\n    name:/);
 assert.ok(await page.locator('.cm-line span').count()>0,'JavaScript syntax highlighting must render');
 console.log('PASS: editor highlighting, custom variable completion, themes, formatting and syntax validation.');
}finally{await browser.close();}
