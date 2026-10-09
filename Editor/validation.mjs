import {parse} from 'acorn';
export function validateSource(source, kind) {
  if(new TextEncoder().encode(source).length>262144) return 'Source must be 256 KB or smaller.';
  if(!source.trim())return 'Enter JavaScript source.';
  try {
    const ast=parse(source,{ecmaVersion:'latest',sourceType:'script',allowReturnOutsideFunction:kind==='eruda'});
    if(kind==='userscript') {
      if(!/^\s*\/\/\s*==UserScript==/m.test(source)||!/^\s*\/\/\s*==\/UserScript==/m.test(source))return 'A userscript metadata header is required.';
      if(!/^\s*\/\/\s*@(match|include)\s+\S+/m.test(source))return 'Add at least one @match or @include rule.';
    } else {
      let exported=ast.body.some(n=>n.type==='ReturnStatement'&&n.argument&&['ObjectExpression','FunctionExpression','ArrowFunctionExpression','Identifier','CallExpression'].includes(n.argument.type));
      function walk(node) {
        if(!node||typeof node!=='object')return;
        if(node.type==='AssignmentExpression') {
          const left=node.left;
          if(left.type==='MemberExpression' && ((left.object.type==='Identifier'&&left.object.name==='exports') || (left.object.type==='Identifier'&&left.object.name==='module'&&((left.property.name||left.property.value)==='exports')) || (left.object.type==='MemberExpression'&&left.object.object?.name==='module'&&(left.object.property.name||left.object.property.value)==='exports')))exported=true;
        }
        for(const value of Object.values(node))if(Array.isArray(value))value.forEach(walk);else if(value&&typeof value==='object')walk(value);
      }
      walk(ast);
      if(!exported)return 'Eruda files must return a tool/factory or assign module.exports / exports.';
    }
    return null;
  }catch(error){return 'JavaScript: '+error.message;}
}
