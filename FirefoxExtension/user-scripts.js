const grants = new Set(['none','unsafeWindow','GM_addStyle','GM_log','GM_getValue','GM_setValue','GM_deleteValue','GM_listValues','GM_info','GM.addStyle','GM.log','GM.getValue','GM.setValue','GM.deleteValue','GM.listValues','GM.info']);
export function metadata(source) {
    const block=source.match(/\/\/\s*==UserScript==([\s\S]*?)\/\/\s*==\/UserScript==/);
    if(!block)throw new Error('A userscript metadata header is required.');
    const data={};
    for(const line of block[1].split('\n')) {const m=line.match(/^\s*\/\/\s*@([\w-]+)\s*(.*)$/);if(m)(data[m[1]]||=[]).push(m[2].trim());}
    if(!(data.match?.length||data.include?.length))throw new Error('A match or include is required.');
    if((data.include||[]).some(p=>p.startsWith('/')))throw new Error('Regular expression includes are unsupported.');
    for(const tag of ['require','resource','connect','webRequest','run-in','sandbox'])if(data[tag])throw new Error('@'+tag+' is unsupported.');
    for(const value of data.grant||['none'])if(!grants.has(value))throw new Error('Unsupported grant: '+value);
    if(!['document-start','document-end','document-idle'].includes(data['run-at']?.[0]||'document-end'))throw new Error('Unsupported run-at.');
    return data;
}
// This pure function is serialized into browser-managed user scripts; it does not evaluate strings.
function apiPrelude(row, data) {
    function glob(p,u) {if(p.length>2048)return false;return new RegExp('^'+p.split('*').map(s=>s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')).join('.*')+'$').test(u);}
    function matches(p,match) {
        const u=new URL(location.href);if(!/^https?:$/.test(u.protocol))return false;
        if(p==='<all_urls>')return true;if(!match)return glob(p,location.href);
        const m=p.match(/^(\*|https?):\/\/([^/]+)(\/.*)$/);if(!m)return false;
        const h=m[2];return (m[1]==='*'||u.protocol===m[1]+':')&&(h==='*'||(h.startsWith('*.')?(u.hostname===h.slice(2)||u.hostname.endsWith(h.slice(1))):u.hostname===h))&&glob(m[3],u.pathname+u.search);
    }
    if(!((data.match||[]).some(p=>matches(p,true))||(data.include||[]).some(p=>matches(p,false)))||(data.exclude||[]).some(p=>matches(p,false))||(data['exclude-match']||[]).some(p=>matches(p,true)))return null;
    globalThis.__DTFirefoxExecuted ||= new Set();if(__DTFirefoxExecuted.has(row.id))return null;__DTFirefoxExecuted.add(row.id);
    const granted=new Set(data.grant||['none']);const key='devtools-userscript-'+row.id;
    const values=()=>JSON.parse(localStorage.getItem(key)||'{}');
    const set=(name,value)=>{const all=values();all[name]=value;localStorage.setItem(key,JSON.stringify(all));};
    const del=name=>{const all=values();delete all[name];localStorage.setItem(key,JSON.stringify(all));};
    const style=css=>{const el=document.createElement('style');el.textContent=css;(document.head||document.documentElement).append(el);return el;};
    const funcs={GM_addStyle:style,GM_log:console.log.bind(console),GM_getValue:(name,fallback)=>values()[name]??fallback,GM_setValue:set,GM_deleteValue:del,GM_listValues:()=>Object.keys(values()),GM_info:{script:{name:data.name?.[0]||row.name,version:data.version?.[0]||''},scriptHandler:'DevTools'},unsafeWindow:window};
    const GM={};for(const [name,fn] of Object.entries(funcs)){const short=name.replace('GM_','');if(granted.has('GM.'+short))GM[short]=typeof fn==='function'&&!['addStyle','log'].includes(short)?async(...args)=>fn(...args):fn;}
    return [...Object.keys(funcs).map(name=>granted.has(name)?funcs[name]:undefined),GM];
}
export function registration(row) {
    if(!row || typeof row.id!=='string'||!/^[\w-]{1,100}$/.test(row.id)||!['eruda','userscript'].includes(row.kind)||typeof row.source!=='string'||new TextEncoder().encode(row.source).length>262144)throw new Error('Invalid script file.');
    let code, runAt='document_idle';
    if(row.kind==='userscript') {
        const data=metadata(row.source);runAt=(data['run-at']?.[0]||'document-end').replaceAll('-','_');
        code=`(()=>{const args=(${apiPrelude.toString()})(${JSON.stringify({id:row.id,name:row.name})},${JSON.stringify(data)});if(args)((GM_addStyle,GM_log,GM_getValue,GM_setValue,GM_deleteValue,GM_listValues,GM_info,unsafeWindow,GM)=>{\n${row.source}\n})(...args);})()`;
    } else {
        // Eruda plugins operate only in the website's page-world console, not the extension manager.
        code=`(()=>{const id=${JSON.stringify(row.id)};let timer;const add=()=>{const eruda=globalThis.__DTAssets?.eruda;if(!eruda?._isInit)return false;const owner=eruda.get();globalThis.__DTFirefoxPlugins||={};if(__DTFirefoxPlugins[id]?.owner===owner)return true;try{const before=new Set(Object.keys(owner._tools));const module={exports:{}};const result=((eruda,module,exports)=>{\n${row.source}\n})(eruda,module,module.exports);const tool=result||module.exports.default||module.exports;if(tool?.name&&eruda.get(tool.name))eruda.remove(tool.name);const prepare=tool=>{if(typeof tool==="function")return eruda=>prepare(tool(eruda));if(tool?.init){const init=tool.init;tool.init=function($el,...args){this._$el=$el;return init.call(this,$el,...args)}}return tool};eruda.add(prepare(tool));__DTFirefoxPlugins[id]={owner,names:Object.keys(owner._tools).filter(n=>!before.has(n)||n===tool?.name)};}catch(e){console.error('[DevTools plugin] '+e.message)}return true;};if(!add()){timer=setInterval(()=>{if(add())clearInterval(timer)},100);setTimeout(()=>clearInterval(timer),30000);addEventListener('pagehide',()=>clearInterval(timer),{once:true});}})()`;
    }
    return {id:'devtools-'+row.id,matches:['http://*/*','https://*/*'],js:[{code}],world:'MAIN',allFrames:false,runAt};
}
export async function reconcileScripts(api, profile) {
    if(!api.userScripts || !await api.permissions.contains({permissions:['userScripts']}))return;
    const {installedScripts=[]}=await api.storage.local.get('installedScripts');
    const rows=profile.scripts||[];const known=await api.userScripts.getScripts();const active=[];
    for(const copy of installedScripts) {
        const current=rows.find(s=>s.id===copy.id);
        if(current?.enabled && current.hash===copy.hash)active.push(registration(copy));
    }
    const wanted=new Set(active.map(s=>s.id));
    const remove=known.filter(s=>s.id.startsWith('devtools-')&&!wanted.has(s.id)).map(s=>s.id);
    if(remove.length)await api.userScripts.unregister({ids:remove});
    const existing=new Set(known.map(s=>s.id));const missing=active.filter(s=>!existing.has(s.id));
    if(missing.length)await api.userScripts.register(missing);
}
export async function installScript(api,row) {
    if(!api.userScripts || !await api.permissions.contains({permissions:['userScripts']}))throw new Error('Allow user scripts in Firefox first.');
    if(!row.enabled)throw new Error('Enable this script in the DevTools app first.');
    const value=registration(row);const {installedScripts=[]}=await api.storage.local.get('installedScripts');
    if((await api.userScripts.getScripts()).some(s=>s.id===value.id))await api.userScripts.unregister({ids:[value.id]});
    await api.userScripts.register([value]);
    await api.storage.local.set({installedScripts:[...installedScripts.filter(s=>s.id!==row.id),row]});
}
export async function removeScript(api,id) {
    if(api.userScripts && await api.permissions.contains({permissions:['userScripts']}))await api.userScripts.unregister({ids:['devtools-'+id]});
    const {installedScripts=[]}=await api.storage.local.get('installedScripts');
    await api.storage.local.set({installedScripts:installedScripts.filter(s=>s.id!==id)});
}
