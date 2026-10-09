import beautifier from 'js-beautify/js/lib/beautifier.js';

// Formatting changes only the inspected presentation. It never evaluates source.
export function readableSource(type, source, indentSize = 4) {
    if (typeof source !== 'string' || source.length > 262144) return source;
    const options = {indent_size: Number(indentSize) || 4, preserve_newlines: true, max_preserve_newlines: 2, wrap_line_length: 100, end_with_newline: true};
    try {
        if (type === 'js') return beautifier.js(source, options);
        if (type === 'css') return beautifier.css(source, options);
        if (type === 'html') return beautifier.html(source, {...options, indent_inner_html: true});
    } catch { /* Keep the original available if formatting fails. */ }
    return source;
}
export function installSourceFormatting(eruda) {
    const sources = eruda?.get('sources');
    if (!sources || typeof sources._renderCode !== 'function' || sources.__dtReadableSource) return;
    sources.__dtReadableSource = true;
    const render = sources._renderCode;
    let cached;
    sources._renderCode = function (...args) {
        const raw = this._data;
        if (!raw || this.config?.get('formatCode') === false) return render.apply(this, args);
        const indent = this.config?.get('indentSize') || 4;
        if (!cached || cached.type !== raw.type || cached.raw !== raw.val || cached.indent !== indent) {
            cached = {type: raw.type, raw: raw.val, indent, formatted: readableSource(raw.type, raw.val, indent)};
        }
        this._data = {...raw, val: cached.formatted};
        try { return render.apply(this, args); }
        finally { this._data = raw; }
    };
    sources.config?.set('formatCode', true);
    sources.config?.on('change', name => {
        if ((name === 'formatCode' || name === 'indentSize') && ['js', 'css', 'html'].includes(sources._data?.type)) sources._renderCode();
    });
    eruda.get('settings')?.switch(sources.config, 'formatCode', 'Beautify source code');
}
