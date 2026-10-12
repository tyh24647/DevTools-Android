export async function companionRequest(api, path, body, fetcher=fetch) {
    if(!['/firefox/script','/firefox/media','/firefox/media-state'].includes(path))throw new Error('Invalid companion action.');
    const {token}=await api.storage.local.get('token');
    if(!token)throw new Error('Pair with DevTools first.');
    const response=await fetcher('http://127.0.0.1:18746'+path,{
        method:body?'POST':'GET',headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{})},
        body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(5000),cache:'no-store'
    });
    if(response.status!==200)throw new Error(response.status===401?'Pairing was rejected. Pair again.':'Open DevTools and confirm any pending download first.');
    return response.json();
}
export function resourceURL(value) {
    const url=new URL(value);
    if(!['http:','https:'].includes(url.protocol)||url.username||url.password||value.length>4096)throw new Error('Invalid media URL.');
    return url.href;
}
export async function sendMedia(api, sender, data) {
    const {mediaConnected,includeMediaCookies}=await api.storage.local.get(['mediaConnected','includeMediaCookies']);
    if(!mediaConnected)throw new Error('Enable media integration in the extension menu.');
    if(!sender.tab || sender.frameId!==0)throw new Error('Media requires a top-level webpage.');
    if(!['save','stop','unload','leave'].includes(data.type))throw new Error('Unknown media action.');
    const page=resourceURL(sender.url||sender.tab.url);
    const body={type:data.type,tabId:sender.tab.id,page,title:String(data.title||sender.tab.title||'').slice(0,160),hls:data.hls===true,mime:String(data.mime||'').slice(0,80)};
    if(data.type!=='leave')body.url=resourceURL(data.url);
    if(data.type==='save' && includeMediaCookies && await api.permissions.contains({permissions:['cookies']})) {
        const cookies=await api.cookies.getAll({url:body.url});
        body.cookie=cookies.map(c=>c.name+'='+c.value).join('; ').slice(0,16384);
    }
    return companionRequest(api,'/firefox/media',body);
}
