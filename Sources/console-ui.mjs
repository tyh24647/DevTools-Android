export function installConsoleUI(eruda, options = {}) {
    const panel = eruda?.get();
    const element = panel?._$el?.get(0);
    const root = eruda?._shadowRoot || eruda?._container;
    if (!element || !root) return () => {};
    const abort = new AbortController();
    const style = document.createElement('style');
    root.appendChild(style);
    const color = /^#[0-9a-f]{6}$/i.test(options.entryColor || '') ? options.entryColor : '';
    function updateWrap() {
        const wrap = panel.config.get('wrapText') !== false;
        style.textContent = `
        .eruda-entry-btn {background:${color || 'linear-gradient(135deg,#3b82f6,#8b5cf6)'} !important;color:#fff !important;border:1px solid rgba(255,255,255,.5) !important;box-shadow:0 3px 14px rgba(99,102,241,.4) !important}
        .eruda-code .luna-text-viewer-text,.eruda-code .luna-text-viewer-line-text,.eruda-raw pre,.luna-console-log-item .luna-console-log-content {white-space:${wrap ? 'pre-wrap' : 'pre'} !important;overflow-wrap:${wrap ? 'anywhere' : 'normal'} !important;word-break:${wrap ? 'break-word' : 'normal'} !important}
        .dt-resize-grip {position:absolute;top:-14px;left:0;width:100%;height:24px;border:0;padding:0;z-index:121;background:transparent;touch-action:none;cursor:ns-resize;display:flex;justify-content:center;align-items:flex-start}
        .dt-resize-grip::after {content:'';display:block;width:48px;height:4px;margin-top:5px;border-radius:4px;background:#fff;box-shadow:0 1px 4px #0008}
        .dt-resize-grip:focus-visible {outline:2px solid #60a5fa;outline-offset:-2px}
        `;
    }
    panel.config.set('wrapText', options.wrapText !== false);
    const changed = name => {if (name === 'wrapText') updateWrap();};
    panel.config.on('change', changed);
    updateWrap();
    eruda.get('settings')?.switch(panel.config, 'wrapText', 'Wrap text');
    const grip = document.createElement('button');
    grip.className = 'dt-resize-grip';
    grip.setAttribute('aria-label', 'Resize developer console');
    grip.setAttribute('title', 'Drag to resize; use arrow keys when focused');
    element.appendChild(grip);
    let drag;
    const clamp = value => Math.max(25, Math.min(95, value));
    function resized(value) {panel.config.set('displaySize', clamp(value));}
    grip.addEventListener('pointerdown', event => {
        if (event.button !== 0) return;
        event.preventDefault();event.stopPropagation();
        drag = {id:event.pointerId, y:event.clientY, size:Number(panel.config.get('displaySize')) || 55};
        grip.setPointerCapture(event.pointerId);
    }, {signal:abort.signal});
    grip.addEventListener('pointermove', event => {
        if (!drag || event.pointerId !== drag.id) return;
        event.preventDefault();event.stopPropagation();
        resized(drag.size + (drag.y - event.clientY) / Math.max(1, innerHeight) * 100);
    }, {signal:abort.signal});
    function end(event) {if (drag?.id === event.pointerId) {drag = null;if (grip.hasPointerCapture(event.pointerId)) grip.releasePointerCapture(event.pointerId);}}
    grip.addEventListener('pointerup',end,{signal:abort.signal});
    grip.addEventListener('pointercancel',end,{signal:abort.signal});
    grip.addEventListener('lostpointercapture',()=>{drag=null;},{signal:abort.signal});
    grip.addEventListener('keydown', event => {
        if (['ArrowUp','ArrowDown','Home','End'].includes(event.key)) {
            event.preventDefault();event.stopPropagation();
            resized(event.key==='Home' ? 25 : event.key==='End' ? 95 : Number(panel.config.get('displaySize')) + (event.key==='ArrowUp' ? 5 : -5));
        }
    },{signal:abort.signal});
    return () => {abort.abort();panel.config.off('change',changed);grip.remove();style.remove();};
}
