import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
const code=await fs.readFile('app/src/main/assets/tools/userscripts.js','utf8');
function context(url='https://example.com/page'){
 const storage=new Map();
 const ctx=vm.createContext({location:{href:url},URL,console,window:{},
  localStorage:{getItem:key=>storage.get(key),setItem:(key,value)=>storage.set(key,value)},
  document:{readyState:'complete'},setTimeout:fn=>fn()});
 vm.runInContext(code,ctx);return ctx;
}
const source=(tags,body='')=>'// ==UserScript==\n'+tags.map(tag=>'// @'+tag).join('\n')+'\n// ==/UserScript==\n'+body;
test('standard matches are anchored and exclusions win',()=>{
 const c=context();const m=c.__DTUserscripts.metadata(source(['match https://*.example.com/*','exclude-match https://example.com/private*']));
 assert.equal(c.__DTUserscripts.matches(m,'https://example.com/page'),true);
 assert.equal(c.__DTUserscripts.matches(m,'https://api.example.com/page'),true);
 assert.equal(c.__DTUserscripts.matches(m,'https://example.com/private/x'),false);
 assert.equal(c.__DTUserscripts.matches(m,'https://example.com.evil.test/page'),false);
 assert.equal(c.__DTUserscripts.matches(m,'http://example.com/page'),false);
});
test('unsupported privileged APIs and external dependencies are rejected',()=>{
 const c=context();
 for(const tag of ['grant GM_xmlhttpRequest','require https://example.com/a.js','resource styles https://example.com/style.css'])
  assert.throws(()=>c.__DTUserscripts.metadata(source(['match https://example.com/*',tag])));
});
test('disabled and unmatched scripts do not execute',()=>{
 const c=context();c.__DTUserscripts.start([{id:'x',enabled:false,source:source(['match https://example.com/*'],'window.ran=true;')},
 {id:'y',enabled:true,source:source(['match https://other.test/*'],'window.ran=true;')}]);
 assert.equal(c.window.ran,undefined);
});
test('scripts run once per document even after reinjection',()=>{
 const c=context();const script={id:'x',name:'Counter',enabled:true,source:source(['match https://example.com/*'],'window.count=(window.count||0)+1;')};
 c.__DTUserscripts.start([script]);vm.runInContext(code,c);c.__DTUserscripts.start([script]);
 assert.equal(c.window.count,1);
});
test('granted script storage is isolated by script identifier',()=>{
 const c=context();
 c.__DTUserscripts.start([{id:'x',enabled:true,source:source(['match https://example.com/*','grant GM_setValue'],'GM_setValue("hello",42);')},
 {id:'y',enabled:true,source:source(['match https://example.com/*','grant GM_getValue'],'window.value=GM_getValue("hello","missing");')}]);
 assert.equal(c.window.value,'missing');
});
