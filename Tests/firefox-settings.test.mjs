import test from 'node:test';
import assert from 'node:assert/strict';
import {defaultProfile,validateProfile,parsePairingCode,syncSettings} from '../FirefoxExtension/settings.js';
import {runCommand} from '../FirefoxExtension/controller.js';
function storage(initial) {
    const state={...initial};
    return {state,storage:{local:{get:async()=>({...state}),set:async patch=>Object.assign(state,patch)}}};
}
const profile={...defaultProfile(),revision:'a',enabled:true};
test('pairing accepts only the local protocol and opaque secret',()=>{
    assert.equal(parsePairingCode('18746.'+'a'.repeat(43)),'a'.repeat(43));
    assert.throws(()=>parsePairingCode('https://remote.example/token'));
    assert.throws(()=>parsePairingCode('18746.short'));
});
test('sync stores the app profile without sending browsing URLs',async()=>{
    const api=storage({token:'secret'});
    const result=await syncSettings(api,async(url,options)=>{
        assert.equal(url,'http://127.0.0.1:18746/firefox');
        assert.equal(options.headers.Authorization,'Bearer secret');
        assert.equal(options.method,'GET');
        assert.equal(options.body,undefined);
        return {status:200,json:async()=>profile};
    });
    assert.equal(result.profile.enabled,true);
    assert.deepEqual(api.state.profile,profile);
});
test('offline settings changes are retained and queued for the app',async()=>{
    const api=storage({token:'secret',profile,pending:{revision:'a',runEverywhere:false}});
    const result=await syncSettings(api,async()=>{throw new Error('Offline');});
    assert.deepEqual(api.state.pending,{revision:'a',runEverywhere:false});
    assert.equal(result.profile.enabled,true);
    assert.match(result.status,/saved settings/);
});
test('conflicting changes use the newer app profile instead of overwriting it',async()=>{
    const api=storage({token:'secret',profile,pending:{revision:'a',enabled:false}});
    const newer={...profile,revision:'b',runEverywhere:false};
    const result=await syncSettings(api,async(url,options)=>{
        assert.deepEqual(JSON.parse(options.body),{revision:'a',enabled:false});
        return {status:409,json:async()=>newer};
    });
    assert.deepEqual(api.state.profile,newer);
    assert.equal(api.state.pending,null);
    assert.match(result.status,/change the toggle again/);
});
test('invalid list data cannot replace a valid saved profile',async()=>{
    const api=storage({token:'secret',profile});
    await syncSettings(api,async()=>({status:200,json:async()=>({...profile,lists:[{kind:'evil'}]})}));
    assert.deepEqual(api.state.profile,profile);
    assert.throws(()=>validateProfile({...profile,enabled:'true'}));
});
test('excluded pages never load the console bundle',async()=>{
    const calls=[];
    const api={scripting:{executeScript:async details=>{
        calls.push(details);
        return [{frameId:0,result:{url:'https://blocked.example/',loaded:false}}];
    }}};
    const result=await runCommand(api,1,'auto',profile,{run:false,reason:'Blacklisted'});
    assert.equal(result.active,false);
    assert.equal(calls.length,1);
    assert.ok(calls.every(call=>!call.files));
});
test('new exclusion stops an already active console',async()=>{
    const calls=[];
    const api={scripting:{executeScript:async details=>{
        calls.push(details);
        return [{frameId:0,result:calls.length===1?{url:'https://blocked.example/',loaded:true}:undefined}];
    }}};
    await runCommand(api,1,'auto',profile,{run:false,reason:'Disabled'});
    assert.equal(calls.length,2);
    assert.match(calls[1].func.toString(),/stop/);
});
