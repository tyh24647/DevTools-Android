import {parse} from 'acorn';
import {transform} from 'esbuild';
import {gzipSync} from 'node:zlib';
// Keep large, bundled Vue iframe frontend data in compressed packaged assets.
// The controller decodes these local assets before loading the unchanged adapter.
export async function compactVue(source) {
    const ast=parse(source,{ecmaVersion:'latest'});const literals=[];
    function walk(node){if(!node||typeof node!=='object')return;if(node.type==='Literal'&&typeof node.value==='string'&&node.end-node.start>1000000)literals.push(node);for(const value of Object.values(node))if(Array.isArray(value))value.forEach(walk);else if(value&&typeof value==='object')walk(value);}
    walk(ast);if(literals.length!==1)throw new Error('Expected one embedded Vue frontend.');
    const node=literals[0];source=source.slice(0,node.start)+'globalThis.__DTBundledVueFrontend'+source.slice(node.end);
    return {code:(await transform(source,{minify:true,legalComments:'inline',target:'es2020'})).code,frontend:gzipSync(node.value,{level:9})};
}
