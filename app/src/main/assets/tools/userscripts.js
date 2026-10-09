(function () {
    "use strict";
    if(globalThis.__DTUserscripts) return;
    const executed=new Set();
    const supportedGrants = new Set(["none","GM_addStyle","GM_log","GM_getValue","GM_setValue","GM_deleteValue","GM_listValues","GM_info",
        "GM.addStyle","GM.log","GM.getValue","GM.setValue","GM.deleteValue","GM.listValues","GM.info","unsafeWindow"]);
    function metadata(source) {
        const block=source.match(/\/\/\s*==UserScript==([\s\S]*?)\/\/\s*==\/UserScript==/);
        if(!block) throw new Error("Include a UserScript metadata header with @match or @include.");
        const data={};
        for(const line of block[1].split("\n")) {
            const match=line.match(/^\s*\/\/\s*@([\w-]+)\s*(.*)$/);
            if(match) (data[match[1]] ||= []).push(match[2].trim());
        }
        if(!(data.match?.length || data.include?.length)) throw new Error("Add @match or @include before enabling this script.");
        for(const tag of ["require","resource","connect","webRequest","run-in","sandbox"]) if(data[tag]) throw new Error("@"+tag+" is not supported.");
        for(const grant of data.grant || ["none"]) if(!supportedGrants.has(grant)) throw new Error("Unsupported grant: "+grant);
        const at=data["run-at"]?.[0] || "document-end";
        if(!["document-start","document-end","document-idle"].includes(at)) throw new Error("Unsupported @run-at.");
        return data;
    }
    function glob(pattern,url) {return new RegExp("^"+pattern.split("*").map(p=>p.replace(/[.*+?^${}()|[\]\\]/g,"\\$&")).join(".*")+"$").test(url);}
    function wildcard(pattern,url,match=false) {
        if(pattern==="<all_urls>") return /^https?:/.test(url);
        if(pattern.length>2048 || pattern.startsWith("/")) throw new Error("Regex includes are not supported; use URL wildcards.");
        if(match && !/^(\*|https?):\/\/(\*|\*\.[^/]+|[^/*]+)\//.test(pattern)) throw new Error("Invalid @match pattern.");
        if(!match) return glob(pattern,url);
        const u=new URL(url); const parts=pattern.match(/^(\*|https?):\/\/([^/]+)(\/.*)$/);
        if(!parts || !/^https?:$/.test(u.protocol)) return false;
        if(parts[1]!=="*" && u.protocol!==parts[1]+":") return false;
        const host=parts[2];
        if(host!=="*" && (host.startsWith("*.") ? !(u.hostname===host.slice(2) || u.hostname.endsWith(host.slice(1))) : u.hostname!==host)) return false;
        return glob(parts[3],u.pathname+u.search);
    }
    function matches(data,url) {
        return ((data.match || []).some(p=>wildcard(p,url,true)) || (data.include || []).some(p=>wildcard(p,url))) &&
            !(data.exclude || []).some(p=>wildcard(p,url)) && !(data["exclude-match"] || []).some(p=>wildcard(p,url,true));
    }
    function start(scripts) {
        function run(row) {
            if(!row.enabled || executed.has(row.id)) return;
            try {
                const data=metadata(row.source); if(!matches(data,location.href)) return;
                executed.add(row.id);
                const grants=new Set(data.grant || ["none"]);
                const key="devtools-userscript-"+row.id;
                const values=()=>JSON.parse(localStorage.getItem(key)||"{}");
                const set=(name,value)=>{const all=values();all[name]=value;localStorage.setItem(key,JSON.stringify(all));};
                const del=name=>{const all=values();delete all[name];localStorage.setItem(key,JSON.stringify(all));};
                const style=css=>{const el=document.createElement("style");el.textContent=css;(document.head||document.documentElement).append(el);return el;};
                const functions={GM_addStyle:style,GM_log:console.log.bind(console),GM_getValue:(name,fallback)=>values()[name]??fallback,
                    GM_setValue:set,GM_deleteValue:del,GM_listValues:()=>Object.keys(values()),
                    GM_info:{script:{name:data.name?.[0]||row.name,version:data.version?.[0]||"",grants:[...grants]},scriptHandler:"DevTools"},
                    unsafeWindow:window};
                const GM={};
                for(const [name,fn] of Object.entries(functions)) {
                    const short=name.replace("GM_","");
                    if(grants.has("GM."+short)) GM[short]=typeof fn==="function" && !["addStyle","log"].includes(short) ? async(...args)=>fn(...args) : fn;
                }
                const names=Object.keys(functions);
                new Function(...names,"GM",row.source)(...names.map(name=>grants.has(name)?functions[name]:undefined),GM);
            } catch(error) {console.error("[DevTools userscript] "+row.name+": "+error.message);}
        }
        for(const row of scripts||[]) {
            if(!row.enabled) continue;
            try {
                const data=metadata(row.source), at=data["run-at"]?.[0]||"document-end";
                if(at==="document-start") run(row);
                else if(document.readyState!=="loading") at==="document-idle"?setTimeout(()=>run(row),0):run(row);
                else document.addEventListener("DOMContentLoaded",()=>at==="document-idle"?setTimeout(()=>run(row),0):run(row),{once:true});
            } catch(error) {console.error("[DevTools userscript] "+row.name+": "+error.message);}
        }
    }
    globalThis.__DTUserscripts={metadata,matches,start};
})();
