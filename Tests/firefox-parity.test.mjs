import test from 'node:test';
import assert from 'node:assert/strict';
import {registration,installScript,reconcileScripts,removeScript} from '../FirefoxExtension/user-scripts.js';
import {sendMedia,resourceURL} from '../FirefoxExtension/companion.js';
function api(){const state={installedScripts:[]};let registered=[];return {state,storage:{local:{get:async()=>state,set:async value=>Object.assign(state,value)}},permissions:{contains:async()=>true},userScripts:{getScripts:async()=>registered,register:async rows=>{registered.push(...rows)},unregister:async({ids})=>{registered=registered.filter(s=>!ids.includes(s.id))}}};}
const row={id:'test',name:'Test',kind:'userscript',enabled:true,hash:'123',source:'// ==UserScript==\n// @name Test\n// @match https://example.com/*\n// @grant none\n// ==/UserScript==\nwindow.test = true;'};
test('Firefox copies require installation, stop on edited source, and can be removed',async()=>{
 const mock=api();await reconcileScripts(mock,{scripts:[row]});assert.equal((await mock.userScripts.getScripts()).length,0);
 await installScript(mock,row);assert.equal((await mock.userScripts.getScripts())[0].world,'MAIN');
 await reconcileScripts(mock,{scripts:[{...row,hash:'changed'}]});assert.equal((await mock.userScripts.getScripts()).length,0);
 await reconcileScripts(mock,{scripts:[row]});assert.equal((await mock.userScripts.getScripts()).length,1);
 await removeScript(mock,'test');assert.deepEqual(mock.state.installedScripts,[]);
});
test('Firefox script validation rejects privileged grants and invalid identifiers',()=>{
 assert.throws(()=>registration({...row,id:'../path'}));assert.throws(()=>registration({...row,source:row.source.replace('@grant none','@grant GM_xmlhttpRequest')}));
 assert.equal(registration({...row,kind:'eruda',source:'return {name:"test",init(){}}'}).runAt,'document_idle');
});
test('Firefox media rejects invalid origins and subframes before requesting the companion',async()=>{
 for(const url of ['file:///tmp/a','javascript:alert(1)','https://user:pass@example.com/a'])assert.throws(()=>resourceURL(url));
 const mock=api();mock.state.mediaConnected=true;
 await assert.rejects(sendMedia(mock,{tab:{id:1},frameId:2,url:'https://example.com/'},{type:'save',url:'https://example.com/a.mp4'}),/top-level/);
 mock.state.mediaConnected=false;await assert.rejects(sendMedia(mock,{tab:{id:1},frameId:0,url:'https://example.com/'},{type:'save'}),/Enable media/);
});
