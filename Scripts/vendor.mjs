import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";

const root = path.resolve(import.meta.dirname, "..");
const specs = [
    ["eruda", "eruda", "eruda.js", "eruda"],
    ["vue", "eruda-vue", "eruda-vue.js", "erudaVue"],
    ["vueLegacy", "eruda-vue-devtools", "dist/eruda_vue_devtools.js", "eruda_vue_devtools"],
    ["vconsole", "vconsole", "dist/vconsole.min.js", "VConsole"],
    ["vueVConsole", "vue-vconsole-devtools", "dist/vue_plugin.js", "vueVconsoleDevtools"],
    ["code", "eruda-code", "eruda-code.js", "erudaCode"],
    ["dom", "eruda-dom", "eruda-dom.js", "erudaDom"],
    ["timing", "eruda-timing", "eruda-timing.js", "erudaTiming"],
    ["fps", "eruda-fps", "eruda-fps.min.js", "erudaFps"],
    ["features", "eruda-features", "eruda-features.js", "erudaFeatures"]
];
await fs.mkdir(path.join(root, "SafariExtension/Resources/vendor"), { recursive: true });
await fs.mkdir(path.join(root, "Shared"), { recursive: true });
const assets = [];
for (const [id, name, entry, global] of specs) {
    const dir = path.join(root, "node_modules", name);
    const pkg = JSON.parse(await fs.readFile(path.join(dir, "package.json"), "utf8"));
    const raw = await fs.readFile(path.join(dir, entry), "utf8");
    // A local CommonJS scope keeps UMD packages from replacing page-owned globals.
    const wrapped = `/* ${name}@${pkg.version}; bundled from npm. */\n(function () {\nif (globalThis.__DTExpectedURL !== location.href || globalThis.__DTAssets?.[${JSON.stringify(id)}]) { return; }\nvar module = { exports: {} }; var exports = module.exports; var define; var process = { env: { NODE_ENV: "production" } };\n${raw}\n;globalThis.__DTAssets = globalThis.__DTAssets || {};\nglobalThis.__DTAssets[${JSON.stringify(id)}] = module.exports.default || module.exports;\nglobalThis.__DTVersions = globalThis.__DTVersions || {};\nglobalThis.__DTVersions[${JSON.stringify(id)}] = ${JSON.stringify(pkg.version)};\n}).call(globalThis);\n`;
    const file = `vendor/${id}.js`;
    await fs.writeFile(path.join(root, "SafariExtension/Resources", file), wrapped);
    const licenseFiles = (await fs.readdir(dir)).filter((f) => /^licen[sc]e/i.test(f));
    const licenseDir = path.join(root, "Licenses", name);
    await fs.mkdir(licenseDir, { recursive: true });
    for (const license of licenseFiles) {
        await fs.copyFile(path.join(dir, license), path.join(licenseDir, license));
    }
    const distLicense = path.join(dir, "dist/vue_plugin.js.LICENSE.txt");
    try {
        await fs.copyFile(distLicense, path.join(licenseDir, "THIRD-PARTY.txt"));
    } catch (error) {
        if (error.code !== "ENOENT") {
            throw error;
        }
    }
    await fs.copyFile(path.join(dir, "package.json"), path.join(licenseDir, "package.json"));
    assets.push({
        id,
        package: name,
        version: pkg.version,
        path: entry,
        file,
        global,
        sha256: crypto.createHash("sha256").update(raw).digest("base64"),
        license: pkg.license || "MIT"
    });
}
const catalog = { generatedAt: new Date().toISOString(), assets };
const json = JSON.stringify(catalog, null, 2) + "\n";
await fs.writeFile(path.join(root, "Shared/tool-catalog.json"), json);
await fs.writeFile(path.join(root, "SafariExtension/Resources/tool-catalog.json"), json);
console.log(assets.map((a) => `${a.package}@${a.version}`).join("\n"));

let notices = "Third-party JavaScript libraries bundled by DevTools Android\n\n";
for (const asset of assets) {
    notices += `${asset.package}@${asset.version} — declared license: ${asset.license}\nhttps://www.npmjs.com/package/${asset.package}\n`;
    const directory = path.join(root, "Licenses", asset.package);
    const files = (await fs.readdir(directory)).filter(name => /^licen[sc]e|^THIRD-PARTY/i.test(name));
    if (!files.length) {
        notices += "No standalone license file was shipped by this npm package; see included package metadata.\n";
    }
    for (const name of files) {
        notices += `\n${name}\n${await fs.readFile(path.join(directory, name), "utf8")}\n`;
    }
    notices += "\n";
}
await fs.writeFile(path.join(root, "Shared/THIRD-PARTY-NOTICES.txt"), notices);
