import fs from 'node:fs/promises';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..');
const target=path.join(root,'FirefoxExtension','tools');
await fs.mkdir(target,{recursive:true});
for(const file of ['rule-engine.js','page-runtime.js','resource-timing.js']) {
    const source = await fs.readFile(path.join(root,'app/src/main/assets/tools',file),'utf8');
    // User-supplied executable plugins are a native browser feature.
    await fs.writeFile(path.join(target,file),source.replace(/\s*\/\/ NATIVE_PLUGINS_START[\s\S]*?\/\/ NATIVE_PLUGINS_END/g, ''));
}
await fs.copyFile(path.join(root,'app/src/main/assets/tools/vendor/eruda.js'),path.join(target,'eruda.js'));
await fs.copyFile(path.join(root,'Licenses/eruda/LICENSE'),path.join(root,'FirefoxExtension/ERUDA-LICENSE.txt'));
const artifact=path.join(root,'Build/DevTools-Firefox-preview.xpi');
// zip updates existing entries; remove the prior artifact to avoid obsolete files.
await fs.rm(artifact,{force:true});
execFileSync('/usr/bin/zip',['-q','-r',artifact,'.'],{cwd:path.join(root,'FirefoxExtension')});
console.log(artifact);
