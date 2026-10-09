async function modules() {
    return Promise.all([import('./settings.js'),import('./controller.js')]);
}
async function current() {
    const {profile,syncStatus} = await browser.storage.local.get(['profile','syncStatus']);
    const [{defaultProfile}] = await modules();
    return {profile:profile || defaultProfile(),status:syncStatus || 'Not paired.'};
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
        return await runCommand(browser,tabId,command,profile,decision,url);
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
    await refreshTabs();
    return result;
}
async function handle(message,sender) {
    if (sender.id !== browser.runtime.id) throw new Error("Unknown extension sender.");
    if (message.type === 'auto') {
        if (!sender.tab || sender.frameId !== 0) throw new Error('Invalid page request.');
        return applyTab(sender.tab.id,message.url);
    }
    if (sender.url && !sender.url.startsWith(browser.runtime.getURL(''))) throw new Error('Settings controls require the extension.');
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
    ['auto','command'].includes(message.type) ? handle(message,sender) : serialize(()=>handle(message,sender))
);
browser.alarms.onAlarm.addListener(alarm=>{if(alarm.name==='app-sync') serialize(sync).catch(console.error);});
browser.runtime.onInstalled.addListener(()=>{browser.alarms.create('app-sync',{periodInMinutes:1});serialize(sync).catch(console.error);});
browser.runtime.onStartup.addListener(()=>{browser.alarms.create('app-sync',{periodInMinutes:1});serialize(sync).catch(console.error);});
