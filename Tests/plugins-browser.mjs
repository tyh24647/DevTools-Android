import {chromium} from 'playwright';
import fs from 'node:fs/promises';
import http from 'node:http';
import assert from 'node:assert/strict';
const server=http.createServer((req,res)=>{
    if(req.url==='/popup.js') {res.setHeader('Content-Type','application/javascript');res.end('');return;}
    if(req.url==='/popup.css') {res.setHeader('Content-Type','text/css');fs.readFile('FirefoxExtension/popup.css','utf8').then(css=>res.end(css));return;}
    res.setHeader('Content-Type','text/html');
    fs.readFile('FirefoxExtension/popup.html','utf8').then(html=>res.end(html));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true});
try {
 const page=await browser.newPage();
 for(const width of [280,320,412,768,1920]) {
  await page.setViewportSize({width,height:800});
  await page.goto('http://127.0.0.1:'+server.address().port);
  const geometry=await page.evaluate(()=>{
   const rect=document.querySelector('main').getBoundingClientRect();
   return {overflow:document.documentElement.scrollWidth>innerWidth,left:rect.left,right:innerWidth-rect.right};
  });
  assert.equal(geometry.overflow,false,'Overflow at '+width);
  assert.ok(Math.abs(geometry.left-geometry.right)<2,'Not centered at '+width);
  if([320,1920].includes(width)) await page.screenshot({path:'/tmp/devtools-popup-'+width+'.png'});
 }
 await page.evaluate(()=>{globalThis.__DTExpectedURL=location.href});
 await page.addScriptTag({path:'FirefoxExtension/tools/eruda.js'});
 await page.addScriptTag({path:'app/src/main/assets/tools/page-runtime.js'});
 const source='return {name:"hello",init(el){this.el=el;el.text("Custom panel works");},show(){this.el.show();},hide(){this.el.hide();},destroy(){this.el.empty();}};';
 const config={enabled:true,plugins:[{name:'Hello',enabled:true,source}],console:{backend:'eruda'}};
 assert.equal((await page.evaluate(config=>__DevToolsRuntime.apply(config,{run:true},'show'),config)).active,true);
 await page.evaluate(()=>__DTAssets.eruda.show('hello'));
 assert.equal(await page.getByText('Custom panel works',{exact:true}).count(),1);
 await page.evaluate(config=>__DevToolsRuntime.apply({...config,plugins:[]},{run:true},'show'),config);
 await page.getByText('Custom panel works',{exact:true}).waitFor({state:'detached'});
 assert.equal(await page.evaluate(()=>!!__DTAssets.eruda.get('hello')),false);
 console.log('PASS: real Eruda custom panel loads/removes; popup fits and centers at 280, 320, 412, 768 and 1920px.');
} finally {await browser.close();server.close();}
