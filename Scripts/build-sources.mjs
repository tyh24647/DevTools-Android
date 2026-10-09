import {build} from 'esbuild';
import fs from 'node:fs/promises';
await build({entryPoints:['Sources/browser.mjs'],bundle:true,format:'iife',target:'chrome100',minify:true,outfile:'app/src/main/assets/tools/source-formatting.js',legalComments:'inline'});
const notice='js-beautify '+JSON.parse(await fs.readFile('node_modules/js-beautify/package.json','utf8')).version+'\n'+await fs.readFile('node_modules/js-beautify/LICENSE','utf8');
await fs.mkdir('Licenses/js-beautify',{recursive:true});
await fs.writeFile('Licenses/js-beautify/LICENSE',notice);
await fs.copyFile('node_modules/js-beautify/package.json','Licenses/js-beautify/package.json');
await fs.writeFile('app/src/main/assets/tools/SOURCE-FORMATTING-NOTICES.txt',notice);
