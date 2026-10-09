return {
  name: "css-editor",
  init(element) {
    this.element = element;
    const root = element.get(0);
    const key = "devtools.css-editor.v1";
    let saved = { css: "", auto: false };
    try { saved = JSON.parse(localStorage.getItem(key)) || saved; } catch {}
    let style = document.querySelector('style[data-devtools-custom-css]');
    const box = document.createElement("div");
    box.style.cssText = "padding:12px;display:grid;gap:10px";
    const title = document.createElement("h2"); title.textContent = "Page CSS";
    const editor = document.createElement("textarea");
    editor.setAttribute("aria-label", "Custom page CSS");
    editor.placeholder = "/* Your page styles */\nbody {\n  background: #fafafa;\n}";
    editor.value = typeof saved.css === "string" ? saved.css : "";
    editor.style.cssText = "width:100%;min-height:220px;box-sizing:border-box;font:14px monospace;padding:10px;resize:vertical;color:inherit;background:transparent;border:1px solid currentColor;border-radius:8px";
    editor.spellcheck = false;
    const status = document.createElement("p"); status.setAttribute("role", "status");
    const controls = document.createElement("div"); controls.style.cssText = "display:flex;flex-wrap:wrap;gap:8px";
    const apply = () => {
      if (!style?.isConnected) { style = document.createElement("style"); style.dataset.devtoolsCustomCss = ""; document.documentElement.append(style); }
      style.textContent = editor.value;
      status.textContent = "CSS applied to this page.";
    };
    function button(label, action) { const b = document.createElement("button"); b.type = "button"; b.textContent = label; b.style.minHeight = "44px"; b.onclick = action; controls.append(b); }
    const auto = document.createElement("input"); auto.type = "checkbox"; auto.checked = !!saved.auto;
    const label = document.createElement("label"); label.style.cssText = "display:flex;align-items:center;gap:8px;min-height:44px"; label.append(auto, "Apply saved CSS when visiting this site");
    button("Apply", apply);
    button("Save for this site", () => {
      try { if (editor.value.length > 65536) throw new Error("CSS must be 64 KB or smaller."); localStorage.setItem(key, JSON.stringify({css:editor.value, auto:auto.checked})); status.textContent = "Saved for " + location.origin + "."; }
      catch (error) { status.textContent = "Could not save CSS: " + error.message; }
    });
    button("Reset page", () => { style?.remove(); style = null; status.textContent = "Custom CSS removed from this page."; });
    button("Delete saved CSS", () => { try { localStorage.removeItem(key); auto.checked = false; style?.remove(); style=null; status.textContent="Saved CSS deleted."; } catch (error) { status.textContent=error.message; } });
    const help = document.createElement("p"); help.textContent = "CSS is saved per site in page-visible browser storage. Apply previews your draft; Save remembers it. Reset removes only your custom stylesheet.";
    box.append(title, editor, controls, label, status, help); root.append(box);
    if (saved.auto && editor.value) apply();
    this.style = () => style;
  },
  show() { this.element.show(); },
  hide() { this.element.hide(); },
  destroy() { this.style?.()?.remove(); this.element.empty(); }
};
