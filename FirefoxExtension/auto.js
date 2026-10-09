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
