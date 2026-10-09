(function () {
    "use strict";
    if(globalThis.__DTMedia || window.top!==window || !/^https?:$/.test(location.protocol)) return;
    const entries=new Map(), videoButtons=new Map();
    function message(type,url) {
        try {globalThis.devtoolsMedia?.postMessage(JSON.stringify({type,url,title:document.title.slice(0,160),hls:entries.get(url)?.hls||false,mime:entries.get(url)?.mime||""}));}catch{}
    }
    function normalized(raw) {try {const url=new URL(raw,location.href);return /^https?:$/.test(url.protocol)?url.href:null;}catch{return null;}}
    function isMedia(url) {return /\.(m3u8|mp4|webm|m4v|mov|mkv|mp3|m4a|wav|ogg|png|jpe?g|gif|webp|avif)(?:[?#]|$)/i.test(url);}
    function add(raw,force=false,hls=false,mime="") {
        const url=normalized(raw);if(!url || (!force && !isMedia(url)))return;
        if(entries.has(url)){const entry=entries.get(url);if(mime)entry.mime=mime;if(hls)entry.hls=true;return;}
        if(entries.size>=200)entries.delete(entries.keys().next().value);
        entries.set(url,{url,status:"",progress:null,mime,hls:hls||/\.m3u8(?:[?#]|$)/i.test(url)});

    }
    function request(url) {
        const entry=entries.get(url);if(!entry)return;
        if(entry.busy && entry.live)message("stop",url);
        else if(!entry.busy)message("save",url);
    }
    function mediaType(url) {
        const entry=entries.get(url);
        if(entry?.mime.startsWith("image/") || /\.(png|jpe?g|gif|webp|avif|svg|bmp)(?:[?#]|$)/i.test(url))return "img";
        if(entry?.mime.startsWith("audio/") || /\.(mp3|m4a|wav|ogg)(?:[?#]|$)/i.test(url))return "audio";
        return "video";
    }
    function preview(url) {
        const dialog=document.createElement("dialog");dialog.dataset.devtoolsPreview="";
        dialog.style.cssText="max-width:min(90vw,640px);width:90vw;border:0;border-radius:16px;padding:20px;z-index:2147483647";
        const media=document.createElement(mediaType(url));
        media.style.cssText="width:100%;max-height:50vh;object-fit:contain";
        if(media.tagName!=="IMG"){media.controls=true;media.preload="metadata";if(media.tagName==="VIDEO"){media.width=480;media.height=270;}}
        media.src=url;
        const status=document.createElement("p");status.textContent=entries.get(url)?.status || "Resource preview";
        const save=document.createElement("button");save.textContent=media.tagName==="VIDEO"?"Save / start recording":"Save resource";save.style.minHeight="44px";save.onclick=()=>{request(url);dialog.close();};
        const share=document.createElement("button");share.textContent="Share";share.style.minHeight="44px";share.onclick=async()=>{
            try{if(globalThis.devtoolsMedia){message("share",url);return;}if(navigator.share)await navigator.share({title:document.title,url});else {await navigator.clipboard.writeText(url);status.textContent="Link copied.";}}catch(error){status.textContent=error.message;}
        };
        const close=document.createElement("button");close.textContent="Close";close.style.minHeight="44px";close.onclick=()=>dialog.close();
        dialog.append(media,status,save,share,close);document.body.append(dialog);
        dialog.addEventListener("close",()=>{if(media.tagName!=="IMG"){media.pause();media.removeAttribute("src");media.load();}dialog.remove();});
        dialog.showModal();
    }
    function resourceSection() {
        const resources=globalThis.__DTAssets?.eruda?.get("resources");
        const root=resources?._$el?.get?.(0) || resources?._$el?.[0];
        if(!root || !entries.size)return;
        let section=root.querySelector("[data-devtools-videos]");
        if(!section) {
            section=document.createElement("section");section.dataset.devtoolsVideos="";section.style.cssText="padding:12px;display:grid;gap:8px;background:transparent";
            const heading=document.createElement("h2");heading.textContent="Video and audio";
            section.append(heading);
            const images=root.querySelector(".eruda-image");
            if(images)root.insertBefore(section,images);else root.append(section);
        }
        for(const image of root.querySelectorAll(".eruda-image img")) {
            if("devtoolsSave" in image.dataset)continue;
            image.dataset.devtoolsSave="";
            image.addEventListener("click",event=>{const url=normalized(image.currentSrc||image.src);if(url){event.preventDefault();event.stopImmediatePropagation();add(url,true,false,"image/unknown");preview(url);}},{capture:true});
        }
        const playable=[...entries].filter(([url])=>mediaType(url)!=="img");
        section.hidden=playable.length===0;
        for(const row of section.querySelectorAll("[data-media-url]"))if(mediaType(row.dataset.mediaUrl)==="img" || !entries.has(row.dataset.mediaUrl))row.remove();
        for(const [url,entry] of playable) {
            let row=[...section.querySelectorAll("[data-media-url]")].find(node=>node.dataset.mediaUrl===url);
            if(!row) {
                row=document.createElement("div");row.dataset.mediaUrl=url;
                const button=document.createElement("button");button.style.cssText="min-height:44px;max-width:100%;overflow-wrap:anywhere;background:transparent;border:0;padding:0;color:inherit;text-align:left";button.onclick=()=>preview(url);
                const video=document.createElement(mediaType(url));if(video.tagName!=="IMG")video.preload="metadata";video.width=160;video.height=90;if(video.tagName==="IMG")video.alt="Image resource";
                video.style.cssText="display:block;width:160px;height:90px;object-fit:contain;background:transparent";video.src=url;
                const label=document.createElement("span");button.append(video,label);
                row.append(button);section.append(row);
            }
            const button=row.firstElementChild;
            button.querySelector("span").textContent=(entry.status?entry.status+" · ":"")+url.split("/").pop().slice(0,100);
        }
    }
    function scan() {
        for(const entry of performance.getEntriesByType("resource"))add(entry.name);
        for(const node of document.querySelectorAll("video,audio,video source,audio source,img")) {
            if(node.closest("#eruda,dialog[data-devtools-preview]"))continue;
            const raw=node.currentSrc||node.src;add(raw,true,false,node.tagName==="IMG"?"image/unknown":node.tagName==="AUDIO"?"audio/unknown":"");
        }
        const manifests=[...entries.keys()].filter(url=>entries.get(url).hls);
        for(const video of document.querySelectorAll("video")) {
            if(video.closest("#eruda,dialog[data-devtools-preview]"))continue;
            const src=normalized(video.currentSrc||video.src)||manifests.at(-1);
            if(!src)continue;
            let record=videoButtons.get(video);
            if(!record) {
                const button=document.createElement("button");button.type="button";button.setAttribute("aria-label","Download or record this video");
                button.style.cssText="position:fixed;z-index:2147483646;min-width:44px;min-height:44px;border-radius:50%;background:#7344b5;color:white;border:2px solid white";
                button.onclick=()=>request(record.url);
                record={button,url:src};videoButtons.set(video,record);document.documentElement.append(button);
                video.addEventListener("emptied",()=>message("unload",record.url));
            }
            if(record.url!==src){message("unload",record.url);record.url=src;}
            const rect=video.getBoundingClientRect(),visible=rect.width>0&&rect.height>0&&rect.bottom>0&&rect.top<innerHeight&&rect.right>0&&rect.left<innerWidth;
            record.button.hidden=!visible;
            record.button.style.left=Math.max(4,Math.min(innerWidth-48,rect.right-48))+"px";
            record.button.style.top=Math.max(4,Math.min(innerHeight-48,rect.top+4))+"px";
            const entry=entries.get(src);
            record.button.textContent=entry?.busy?(entry.live?"■":"◌"):entry?.status==="Saved"?"✓":"↓";
            record.button.title=entry?.status||"Save / record video";
            record.button.style.animation=entry?.busy&&!entry.live&&!matchMedia("(prefers-reduced-motion: reduce)").matches?"devtools-media-spin 1s linear infinite":"none";
        }
        for(const [video,record] of videoButtons)if(!video.isConnected){message("unload",record.url);record.button.remove();videoButtons.delete(video);}
        resourceSection();
    }
    const originalFetch=window.fetch;
    const wrappedFetch=async function(...args) {
        const response=await originalFetch.apply(this,args);
        const type=response.headers.get("Content-Type")||"";
        if(/mpegurl/i.test(type))add(response.url,true,true);
        else if(/^(video|audio|image)\//i.test(type))add(response.url,true,false,type.split(";")[0]);
        return response;
    };
    window.fetch=wrappedFetch;
    const originalOpen=XMLHttpRequest.prototype.open;
    const wrappedOpen=function(...args) {
        this.addEventListener("load",()=>{
            try {
                const type=this.getResponseHeader("Content-Type")||"";
                if(/mpegurl/i.test(type))add(this.responseURL,true,true);
                else if(/^(video|audio|image)\//i.test(type))add(this.responseURL,true,false,type.split(";")[0]);
            }catch{}
        },{once:true});
        return originalOpen.apply(this,args);
    };
    XMLHttpRequest.prototype.open=wrappedOpen;
    const style=document.createElement("style");style.textContent="@keyframes devtools-media-spin {to{transform:rotate(360deg)}}";document.documentElement.append(style);
    const timer=setInterval(scan,1000);
    addEventListener("pagehide",()=>{for(const url of entries.keys())message("unload",url);clearInterval(timer);if(window.fetch===wrappedFetch)window.fetch=originalFetch;if(XMLHttpRequest.prototype.open===wrappedOpen)XMLHttpRequest.prototype.open=originalOpen;for(const record of videoButtons.values())record.button.remove();});
    globalThis.__DTMedia={
        add,
        status(url,status,progress,busy,live){add(url,true);Object.assign(entries.get(url),{status,progress,busy,live});scan();},
        refresh:scan
    };
    scan();
})();
