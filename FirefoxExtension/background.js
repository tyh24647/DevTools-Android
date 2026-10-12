const companionModule = () => import('./companion.js');
const scriptsModule = () => import('./user-scripts.js');
async function modules() {
    return Promise.all([import('./settings.js'),import('./controller.js')]);
}
async function current() {
    const {profile,syncStatus,mediaConnected} = await browser.storage.local.get(['profile','syncStatus','mediaConnected']);
    const [{defaultProfile}] = await modules();
    return {profile:{...(profile || defaultProfile()),mediaConnected:mediaConnected===true},status:syncStatus || 'Not paired.'};
}
function decide(profile,url) {
    return new Promise(resolve => {
        const worker = new Worker(browser.runtime.getURL('rule-worker.js'));
        const finish = decision => {clearTimeout(timer);worker.terminate();resolve(decision);};
        const timer=setTimeout(()=>finish({run:false,reason:'Rule matching timed out.'}),750);
        worker.onmessage=event=>finish(event.data);
        worker.onerror=()=>finish({run:false,reason:'Rules could not be checked.'});
        worker.postMessage({profile,url});
    });
}
const inFlight = new Set();
async function applyTab(tabId,url,command='auto') {
    if (!Number.isInteger(tabId) || !/^https?:\/\//.test(url || '')) return {active:false,reason:'Open an ordinary webpage first.'};
    if (inFlight.has(tabId)) return {active:false,reason:'Console update in progress.'};
    inFlight.add(tabId);
    try {
        const {profile}=await current();
        const decision=await decide(profile,url);
        const [,{runCommand}]=await modules();
        const result = await runCommand(browser,tabId,command,profile,decision,url);
        if(result.active && browser.userScripts?.execute && await browser.permissions.contains({permissions:['userScripts']})) {
            const {installedScripts=[]}=await browser.storage.local.get('installedScripts');
            const {registration}=await scriptsModule();
            for(const row of installedScripts.filter(s=>s.kind==='eruda' && profile.scripts?.some(current=>current.id===s.id&&current.enabled&&current.hash===s.hash))) {
                await browser.userScripts.execute({target:{tabId},world:'MAIN',js:registration(row).js});
            }
        }
        return result;
    } finally {inFlight.delete(tabId);}
}
async function refreshTabs() {
    for (const tab of await browser.tabs.query({})) {
        if (/^https?:\/\//.test(tab.url || '')) {
            try {await browser.tabs.sendMessage(tab.id,{type:'refresh'});} catch { /* Protected or unloaded page. */ }
        }
    }
}
async function sync() {
    const [{syncSettings}]=await modules();
    const result=await syncSettings(browser);
    const {reconcileScripts}=await scriptsModule();
    await reconcileScripts(browser,result.profile);
    await refreshTabs();
    return result;
}
async function handle(message,sender) {
    if (sender.id !== browser.runtime.id) throw new Error("Unknown extension sender.");
    if (message.type === 'auto') {
        if (!sender.tab || sender.frameId !== 0) throw new Error('Invalid page request.');
        return applyTab(sender.tab.id,message.url);
    }
    if (message.type === 'media') {
        const {sendMedia}=await companionModule();return sendMedia(browser,sender,message.data||{});
    }
    if (message.type === 'media-status') {
        if(!sender.tab || sender.frameId!==0)throw new Error('Invalid page.');
        const {mediaConnected,token}=await browser.storage.local.get(['mediaConnected','token']);
        if(!mediaConnected||!token)return;
        const {companionRequest}=await companionModule();
        const result=await companionRequest(browser,'/firefox/media-state');
        const states=(result.tasks||[]).filter(s=>s.tab===String(sender.tab.id));
        if(states.length)await browser.scripting.executeScript({target:{tabId:sender.tab.id},world:'MAIN',func:states=>{for(const s of states)globalThis.__DTMedia?.status(s.url,s.status,s.progress,s.busy,s.live);},args:[states]});
        return;
    }
    if (!sender.url || !sender.url.startsWith(browser.runtime.getURL(''))) throw new Error('Settings controls require the extension.');
    if (message.type === 'script-list') {
        const result=await sync();const {installedScripts=[]}=await browser.storage.local.get('installedScripts');
        return {profile:result.profile,installed:installedScripts.map(({id,hash})=>({id,hash}))};
    }
    if (message.type === 'review-script') {const {companionRequest}=await companionModule();return companionRequest(browser,'/firefox/script',{id:message.id});}
    if (message.type === 'install-script') {
        const {companionRequest}=await companionModule();const row=await companionRequest(browser,'/firefox/script',{id:message.id});
        if(row.hash!==message.hash)throw new Error('Source changed. Refresh and review it again.');
        const {installScript}=await scriptsModule();await installScript(browser,row);return {status:'Installed in Firefox. Reload the target webpage.'};
    }
    if (message.type === 'remove-script') {const {removeScript}=await scriptsModule();await removeScript(browser,message.id);return {status:'Removed from Firefox. Reload pages to discard existing effects.'};}
    if (message.type === 'connect-media') {
        await browser.storage.local.set({mediaConnected:message.enabled===true});await refreshTabs();return {status:message.enabled?'Media saves will ask for confirmation in DevTools.':'Media integration disabled.'};
    }
    if (message.type === 'get-settings') return sync();
    if (message.type === 'pair') {
        const [{parsePairingCode}]=await modules();
        const token=parsePairingCode(message.code);
        await browser.storage.local.set({token,pending:null});
        return sync();
    }
    if (message.type === 'set-settings') {
        const snapshot=await browser.storage.local.get(['profile','pending','token']);
        if (!snapshot.token) throw new Error('Pair with the app first.');
        const profile=snapshot.profile;
        if (!profile) throw new Error('Open the app to finish pairing first.');
        const patch={...(snapshot.pending || {revision:profile.revision})};
        for (const key of ['enabled','runEverywhere']) if (key in message.patch) {
            if (typeof message.patch[key] !== 'boolean') throw new Error('Invalid setting.');
            patch[key]=message.patch[key];
        }
        await browser.storage.local.set({profile:{...profile,...message.patch,revision:profile.revision},pending:patch});
        return sync();
    }
    if (message.type === 'command') {
        const tab=await browser.tabs.get(message.tabId);
        return applyTab(tab.id,tab.url,message.command);
    }
    throw new Error('Unknown extension action.');
}
let settingsQueue=Promise.resolve();
function serialize(action) {
    const result=settingsQueue.then(action,action);
    settingsQueue=result.catch(()=>{});
    return result;
}
browser.runtime.onMessage.addListener((message,sender)=>
    ['auto','command','media','media-status'].includes(message.type) ? handle(message,sender) : serialize(()=>handle(message,sender))
);
browser.alarms.onAlarm.addListener(alarm=>{if(alarm.name==='app-sync') serialize(sync).catch(console.error);});
browser.runtime.onInstalled.addListener(()=>{browser.alarms.create('app-sync',{periodInMinutes:1});serialize(sync).catch(console.error);});
browser.runtime.onStartup.addListener(()=>{browser.alarms.create('app-sync',{periodInMinutes:1});serialize(sync).catch(console.error);});

browser.tabs.onRemoved.addListener(tabId=>{companionModule().then(({companionRequest})=>companionRequest(browser,'/firefox/media',{type:'leave',tabId})).catch(()=>{});});
browser.tabs.onUpdated.addListener((tabId,change)=>{if(change.status==='loading')companionModule().then(({companionRequest})=>companionRequest(browser,'/firefox/media',{type:'leave',tabId})).catch(()=>{});});
