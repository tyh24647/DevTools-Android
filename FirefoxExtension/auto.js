(() => {
    let previous='';
    let busy=false;
    async function refresh() {
        if (busy) return;
        busy=true;
        previous=location.href;
        try {await browser.runtime.sendMessage({type:'auto',url:location.href});} catch { /* Protected page, navigation or background restarting. */ }
        finally {busy=false;}
    }
    browser.runtime.onMessage.addListener(message=>{if(message.type==='refresh') return refresh();});
    refresh();
    let timer;
    function watch() {clearInterval(timer);timer=setInterval(()=>{if(location.href!==previous) refresh();},500);}
    watch();
    addEventListener('pagehide',()=>clearInterval(timer));
    addEventListener('pageshow',event=>{if(event.persisted) {watch();refresh();}});
})();
// The isolated script validates transport; the background owns credentials and native requests.
addEventListener('message',async event=>{
    if(event.source!==window||event.origin!==location.origin||event.data?.channel!=='devtools-media'||typeof event.data.payload!=='string'||event.data.payload.length>8192)return;
    try {
        const data=JSON.parse(event.data.payload);
        if(data.type==='save' && !navigator.userActivation?.isActive)return;
        const result=await browser.runtime.sendMessage({type:'media',data});
        if(data.type==='save') {
            const notice=document.createElement('div');notice.textContent=result.status;
            notice.style.cssText='position:fixed;bottom:16px;left:16px;right:16px;padding:12px;border-radius:12px;background:#111;color:white;z-index:2147483647';
            document.documentElement.append(notice);setTimeout(()=>notice.remove(),6000);
        }
    }catch(error){if(event.data.payload.includes('"save"'))console.warn('[DevTools media] '+error.message);}
});
let mediaTimer=setInterval(()=>browser.runtime.sendMessage({type:'media-status'}).catch(()=>{}),2000);
addEventListener('pagehide',()=>{clearInterval(mediaTimer);browser.runtime.sendMessage({type:'media',data:{type:'leave'}}).catch(()=>{});});
addEventListener('pageshow',event=>{if(event.persisted)mediaTimer=setInterval(()=>browser.runtime.sendMessage({type:'media-status'}).catch(()=>{}),2000);});
