(function () {
    "use strict";
    globalThis.__DTResourceTiming = function (eruda) {
        const cap = 1000;
        let records = [];
        let observer;
        let timer;
        let root;
        let tbody;
        let filter;
        let count;
        let dirty = true;
        let active = false;
        const seen = new Set();
        function ingest(entries) {
            for (const entry of entries) {
                const key = `${entry.name}|${entry.startTime}|${entry.duration}`;
                if (seen.has(key)) {
                    continue;
                }
                seen.add(key);
                records.push({
                    name: entry.name,
                    type: entry.initiatorType || entry.entryType,
                    start: entry.startTime,
                    duration: entry.duration,
                    dns: Math.max(0, entry.domainLookupEnd - entry.domainLookupStart),
                    connect: Math.max(0, entry.connectEnd - entry.connectStart),
                    tls:
                        entry.secureConnectionStart > 0
                            ? Math.max(0, entry.connectEnd - entry.secureConnectionStart)
                            : 0,
                    ttfb: entry.requestStart > 0 ? Math.max(0, entry.responseStart - entry.requestStart) : 0,
                    download:
                        entry.responseStart > 0 ? Math.max(0, entry.responseEnd - entry.responseStart) : 0,
                    bytes: entry.transferSize || 0
                });
            }
            if (records.length > cap) {
                records = records.slice(-cap);
                seen.clear();
            }
            dirty = true;
        }
        function render() {
            if (!dirty || !active) {
                return;
            }
            dirty = false;
            const query = filter.value.trim().toLowerCase();
            const visible = records.filter((r) => `${r.name} ${r.type}`.toLowerCase().includes(query));
            count.textContent = `${visible.length} / ${records.length} resources · latest ${cap} retained`;
            tbody.replaceChildren();
            const max = Math.max(1, ...records.map((r) => r.start + r.duration));
            for (const record of visible.slice().reverse()) {
                const row = document.createElement("tr");
                const label = document.createElement("td");
                label.textContent = record.name;
                label.title = record.name;
                label.style.cssText =
                    "max-width:250px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap";
                row.append(label);
                for (const value of [
                    record.type,
                    record.start.toFixed(1),
                    record.duration.toFixed(1),
                    record.dns.toFixed(1),
                    record.connect.toFixed(1),
                    record.tls.toFixed(1),
                    record.ttfb.toFixed(1),
                    record.download.toFixed(1),
                    record.bytes
                ]) {
                    const cell = document.createElement("td");
                    cell.textContent = String(value);
                    cell.style.padding = "6px";
                    row.append(cell);
                }
                const chart = document.createElement("td");
                chart.style.minWidth = "180px";
                const bar = document.createElement("div");
                bar.style.cssText = `background:#82aaff;height:9px;border-radius:3px;margin-left:${(record.start / max) * 100}%;width:${Math.max(0.5, (record.duration / max) * 100)}%`;
                chart.append(bar);
                row.append(chart);
                tbody.append(row);
            }
        }
        return {
            name: "resource timing",
            init($el) {
                root = $el.get(0);
                root.style.cssText =
                    "height:100%;overflow:auto;padding:12px;box-sizing:border-box;font:12px ui-monospace,monospace";
                const toolbar = document.createElement("div");
                toolbar.style.cssText =
                    "display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:12px";
                filter = document.createElement("input");
                filter.placeholder = "Filter URL or type: fetch, xmlhttprequest…";
                filter.style.cssText = "min-width:230px;padding:8px";
                filter.addEventListener("input", () => {
                    dirty = true;
                    render();
                });
                const clear = document.createElement("button");
                clear.textContent = "Clear";
                clear.addEventListener("click", () => {
                    records = [];
                    seen.clear();
                    dirty = true;
                    render();
                });
                count = document.createElement("span");
                toolbar.append(filter, clear, count);
                const note = document.createElement("p");
                note.textContent =
                    "Times are milliseconds since navigation. Cross-origin phase details and sizes require Timing-Allow-Origin; zero can also mean cache. Buffered entries include early resources, not early request bodies.";
                const table = document.createElement("table");
                table.style.cssText = "border-collapse:collapse;width:100%;text-align:left";
                const head = document.createElement("thead");
                const headings = document.createElement("tr");
                for (const title of [
                    "Resource",
                    "Type",
                    "Start",
                    "Total",
                    "DNS",
                    "Connect",
                    "TLS",
                    "TTFB",
                    "Download",
                    "Bytes",
                    "Waterfall"
                ]) {
                    const cell = document.createElement("th");
                    cell.textContent = title;
                    cell.style.padding = "6px";
                    headings.append(cell);
                }
                head.append(headings);
                tbody = document.createElement("tbody");
                table.append(head, tbody);
                root.append(toolbar, note, table);
                ingest(performance.getEntriesByType("navigation"));
                ingest(performance.getEntriesByType("resource"));
                if (typeof PerformanceObserver === "function") {
                    observer = new PerformanceObserver((list) => ingest(list.getEntries()));
                    try {
                        observer.observe({ type: "resource", buffered: true });
                    } catch {
                        observer.observe({ entryTypes: ["resource"] });
                    }
                }
                timer = setInterval(render, 750);
            },
            show() {
                active = true;
                root.style.display = "block";
                dirty = true;
                render();
            },
            hide() {
                active = false;
                root.style.display = "none";
            },
            destroy() {
                observer?.disconnect();
                clearInterval(timer);
                records = [];
                seen.clear();
                root?.replaceChildren();
            }
        };
    };
})();
