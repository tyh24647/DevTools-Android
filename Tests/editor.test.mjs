import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validateSource} from '../Editor/validation.mjs';
test('URL import validation rejects HTML, broken JS and plain scripts masquerading as Eruda plugins',()=>{
 for(const source of ['<!doctype html><html>hello</html>','return { name:','alert("hello")','const value=1;'])assert.ok(validateSource(source,'eruda'));
 assert.equal(validateSource('return { name:"demo", init(element) {} };','eruda'),null);
 assert.equal(validateSource('module.exports = function(eruda) {};','eruda'),null);
});
test('userscripts require a functional header and valid JavaScript',()=>{
 assert.ok(validateSource('const x=1;','userscript'));
 assert.ok(validateSource('// ==UserScript==\n// @name Demo\n// ==/UserScript==\nconst x=1;','userscript'));
 assert.equal(validateSource('// ==UserScript==\n// @name Demo\n// @match https://example.com/*\n// ==/UserScript==\nconst x=1;','userscript'),null);
});
