(() => {
    const existing = document.getElementById('__dt_reader');
    if (existing) {existing.remove();return false;}
    const candidates = [...document.querySelectorAll('article,main,[role="main"]')];
    const article = candidates.sort((a,b)=>b.innerText.length-a.innerText.length)[0];
    if (!article || article.innerText.trim().length < 200) return null;
    const host = document.createElement('div');host.id='__dt_reader';
    host.style.cssText='position:fixed;inset:0;z-index:2147483000;overflow:auto;background:Canvas;color:CanvasText;color-scheme:light dark;';
    const root = host.attachShadow({mode:'open'});
    const style=document.createElement('style');style.textContent='section{max-width:42rem;margin:auto;padding:2rem 1.25rem;font:1.15rem/1.7 system-ui}img{max-width:100%;height:auto}a{color:LinkText}pre{white-space:pre-wrap;overflow-wrap:anywhere}button{font:inherit;margin-bottom:1rem}';root.append(style);
    const section=document.createElement('section');const close=document.createElement('button');close.textContent='Exit reader mode';close.onclick=()=>{host.hidden=true};section.append(close);
    const title=document.createElement('h1');title.textContent=document.title;section.append(title);
    // Rebuild an inert readable subset, never copying scripts, event handlers or embedded documents.
    for (const node of article.querySelectorAll('h1,h2,h3,h4,p,pre,blockquote,li,figure img')) {
        if (node.closest('[hidden],nav,aside,script,style')) continue;
        if (node.tagName==='IMG') {const url=node.currentSrc || node.src;if (/^https?:/.test(url)){const image=document.createElement('img');image.src=url;image.alt=node.alt;section.append(image);}continue;}
        const copy=document.createElement(node.tagName==='LI'?'p':node.tagName.toLowerCase());copy.textContent=node.innerText;section.append(copy);
    }
    root.append(section);document.documentElement.append(host);return true;
})()
