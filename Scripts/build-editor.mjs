import {build} from 'esbuild';
import fs from 'node:fs/promises';
await fs.mkdir('app/src/main/assets/editor',{recursive:true});
await build({entryPoints:['Editor/editor.mjs'],bundle:true,format:'iife',target:'chrome100',minify:true,outfile:'app/src/main/assets/editor/editor.js',legalComments:'linked'});
const html=await fs.readFile('Editor/index.html','utf8'),code=await fs.readFile('app/src/main/assets/editor/editor.js','utf8');
await fs.writeFile('app/src/main/assets/editor/index.html',html.replace('/*EDITOR_BUNDLE*/',()=>code.replace(/<\/script/gi,'<\\/script')));

const packages=['@codemirror/autocomplete','@codemirror/commands','@codemirror/lang-javascript','@codemirror/language','@codemirror/lint','@codemirror/search','@codemirror/state','@codemirror/theme-one-dark','@codemirror/view','@lezer/common','@lezer/highlight','@lezer/javascript','@lezer/lr','acorn','prettier','style-mod','w3c-keyname','crelt'];
let notices='JavaScript editor: CodeMirror, Acorn and Prettier, bundled locally.\n';
for(const name of packages){const dir='node_modules/'+name;const packageJson=JSON.parse(await fs.readFile(dir+'/package.json','utf8'));let license;for(const file of ['LICENSE','LICENSE.txt','LICENSE.md']){try{license=await fs.readFile(dir+'/'+file,'utf8');break;}catch{}}if(!license)throw new Error('Missing license for '+name);notices+='\n'+name+' '+packageJson.version+'\n'+license+'\n';}
await fs.writeFile('app/src/main/assets/editor/THIRD-PARTY-NOTICES.txt',notices);
