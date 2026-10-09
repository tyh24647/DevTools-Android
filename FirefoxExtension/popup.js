const status = document.querySelector('#status');
const settings = document.querySelector('#settings');
const controls = [...document.querySelectorAll('button')];
function display(result) {
    for (const key of ['enabled','runEverywhere']) document.getElementById(key).checked=result.profile[key];
    settings.disabled=result.profile.revision==='unpaired';
    const selected=result.profile.lists.filter(list=>list.selected);
    document.querySelector('#lists').textContent=`${selected.filter(list=>list.kind==='allow').length} AllowList(s), ${selected.filter(list=>list.kind==='block').length} Blacklist(s) selected. Edit them in DevTools → Browsers → Firefox.`;
    status.textContent=result.status;
}
async function perform(task) {
    controls.forEach(control=>{control.disabled=true;});
    settings.disabled=true;
    try {await task();} catch(error) {status.textContent=error.message.includes('Missing host permission')?'Firefox protects this page. Try an ordinary website.':error.message;}
    finally {
        controls.forEach(control=>{control.disabled=false;});
        const {profile}=await browser.storage.local.get('profile');
        settings.disabled=!profile;
    }
}
for(const key of ['enabled','runEverywhere']) {
    document.getElementById(key).addEventListener('change',()=>perform(async()=>{
        display(await browser.runtime.sendMessage({type:'set-settings',patch:{[key]:document.getElementById(key).checked}}));
    }));
}
for(const button of document.querySelectorAll('[data-command]')) {
    button.addEventListener('click',()=>perform(async()=>{
        const [tab]=await browser.tabs.query({active:true,currentWindow:true});
        const result=await browser.runtime.sendMessage({type:'command',command:button.dataset.command,tabId:tab?.id});
        status.textContent=result.reason || (!result.active?'Console stopped.':result.hidden?'Console hidden.':'Console is ready. Close this panel to use it.');
    }));
}
document.querySelector('#sync').addEventListener('click',()=>perform(async()=>display(await browser.runtime.sendMessage({type:'get-settings'}))));
document.querySelector('#pair-form').addEventListener('submit',event=>{
    event.preventDefault();
    perform(async()=>{
        const code=document.querySelector('#pair-code');
        display(await browser.runtime.sendMessage({type:'pair',code:code.value}));
        code.value='';
    });
});
perform(async()=>display(await browser.runtime.sendMessage({type:'get-settings'})));
