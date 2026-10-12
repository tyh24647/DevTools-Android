// Page-visible transport contains no pairing credentials. Native saves require confirmation.
if (!globalThis.devtoolsMedia) globalThis.devtoolsMedia = {
    firefox: true,
    postMessage(payload) {if(typeof payload==='string' && payload.length<=8192)window.postMessage({channel:'devtools-media',payload},location.origin);}
};
