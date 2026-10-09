export const defaultProfile = () => ({schema:1, revision:'unpaired', enabled:false, runEverywhere:true, lists:[]});
export function validateProfile(value) {
    if (!value || value.schema !== 1 || typeof value.enabled !== 'boolean' || typeof value.runEverywhere !== 'boolean' || !Array.isArray(value.lists) || typeof value.revision !== 'string') throw new Error('Invalid browser settings.');
    for (const list of value.lists) {
        if (!['allow','block'].includes(list.kind) || typeof list.selected !== 'boolean' || !Array.isArray(list.rules)) throw new Error('Invalid browser list.');
        for (const rule of list.rules) if (!['domain','url','wildcard','regex'].includes(rule.kind) || typeof rule.pattern !== 'string' || rule.pattern.length > 512 || typeof rule.enabled !== 'boolean') throw new Error('Invalid browser rule.');
    }
    return value;
}
export function parsePairingCode(code) {
    const match = /^18746\.([A-Za-z0-9_-]{43})$/.exec(code.trim());
    if (!match) throw new Error('Copy the pairing code from DevTools → Browser settings → Firefox.');
    return match[1];
}
export async function syncSettings(api, fetcher = fetch) {
    const saved = await api.storage.local.get(['token','profile','pending']);
    if (!saved.token) return {profile: saved.profile || defaultProfile(), status:'Not paired. Copy a code from the Android app.'};
    const request = async (patch) => {
        const response = await fetcher('http://127.0.0.1:18746/firefox', {
            method: patch ? 'POST' : 'GET', headers:{Authorization:`Bearer ${saved.token}`,...(patch?{'Content-Type':'application/json'}:{})},
            body: patch ? JSON.stringify(patch) : undefined, signal:AbortSignal.timeout(3000), cache:'no-store'
        });
        if (![200,409].includes(response.status)) throw new Error(response.status===401?'Pairing code rejected. Pair again.':'App settings unavailable.');
        return {conflict:response.status===409, profile:validateProfile(await response.json())};
    };
    try {
        const result = await request(saved.pending);
        await api.storage.local.set({profile:result.profile, pending:null, syncStatus:result.conflict?'The app changed these settings. Loaded its latest version; change the toggle again.':'Synced with the DevTools app.'});
        return {profile:result.profile,status:result.conflict?'The app changed these settings. Loaded its latest version; change the toggle again.':'Synced with the DevTools app.'};
    } catch(error) {
        const status=`${error.message} Firefox is using saved settings; reopen the app to sync.`;
        await api.storage.local.set({syncStatus:status});
        return {profile:saved.profile || defaultProfile(),status};
    }
}
