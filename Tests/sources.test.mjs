import test from 'node:test';
import assert from 'node:assert/strict';
import {parse} from 'acorn';
import {readableSource} from '../Sources/source-formatting.mjs';
test('minified JavaScript expands into readable lines without executing it',()=>{
 const raw='function example(x){const name="a;b{c}";const r=/[{};]/g;return x.map(v=>({name,value:v}));}globalThis.__mustNeverExecute=true;';
 const output=readableSource('js',raw);
 assert.ok(output.split('\n').length>5);assert.ok(output.includes('    const name'));
 assert.equal(globalThis.__mustNeverExecute,undefined);
 assert.equal(parse(output,{ecmaVersion:'latest'}).body.length,parse(raw,{ecmaVersion:'latest'}).body.length);
});
test('CSS and HTML expand while unsupported and oversized sources stay unchanged',()=>{
 assert.ok(readableSource('css','body{color:red;background:black}').split('\n').length>3);
 assert.ok(readableSource('html','<html><body><div><span>Text</span></div></body></html>').includes('\n'));
 assert.equal(readableSource('raw','a;b'),'a;b');
 const huge='a'.repeat(262145);assert.equal(readableSource('js',huge),huge);
});
