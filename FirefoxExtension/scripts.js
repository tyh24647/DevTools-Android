const status=document.querySelector('#status');const files=document.querySelector('#files');
async function action(fn){try{await fn();}catch(error){status.textContent=error.message;}}
async function refresh(){
    const {profile,installed}=await browser.runtime.sendMessage({type:'script-list'});files.replaceChildren();
    for(const row of profile.scripts||[]) {
        const section=document.createElement('section');const title=document.createElement('h2');title.textContent=row.name;section.append(title);
        const description=document.createElement('p');const current=installed.find(s=>s.id===row.id);description.textContent=`${row.kind} · ${row.enabled?'Enabled':'Disabled in app'} · ${current?.hash===row.hash?'Installed':current?'Updated source available':'Not installed'}`;section.append(description);
        const folder=(profile.scriptFolders||[]).find(folder=>folder.id===row.folder);if(folder){const text=document.createElement('p');text.textContent='Folder: '+folder.name;section.append(text);}
        for(const id of row.tags||[]){const tag=(profile.scriptTags||[]).find(tag=>tag.id===id);if(tag){const label=document.createElement('span');label.textContent=tag.name+' ';if(/^#[0-9a-f]{6}$/i.test(tag.color))label.style.color=tag.color;section.append(label);}}
        const review=document.createElement('button');review.textContent='Review source';
        const install=document.createElement('button');install.textContent='Install reviewed file';install.disabled=true;
        review.onclick=()=>action(async()=>{const source=await browser.runtime.sendMessage({type:'review-script',id:row.id});if(source.hash!==row.hash)throw new Error('Source changed. Refresh the list.');document.querySelector('#source').textContent=source.source;document.querySelector('#review').open=true;install.disabled=!row.enabled;});
        install.onclick=async()=>{try{if(!await browser.permissions.request({permissions:['userScripts']}))throw new Error('User script permission was declined.');status.textContent=(await browser.runtime.sendMessage({type:'install-script',id:row.id,hash:row.hash})).status;install.disabled=true;}catch(error){status.textContent=error.message;}};
        section.append(review,install);
        if(current){const remove=document.createElement('button');remove.textContent='Remove Firefox copy';remove.onclick=()=>action(async()=>{status.textContent=(await browser.runtime.sendMessage({type:'remove-script',id:row.id})).status;await refresh();});section.append(remove);}
        files.append(section);
    }
    if(!files.children.length)status.textContent='No scripts found. Pair the app and add files in its Plugins tab.';
}
document.querySelector('#refresh').onclick=()=>action(refresh);action(refresh);
