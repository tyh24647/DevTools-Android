import {EditorView,keymap,highlightWhitespace,highlightSpecialChars,lineNumbers,drawSelection,highlightActiveLine,highlightActiveLineGutter} from '@codemirror/view';
import {EditorState,Compartment} from '@codemirror/state';
import {defaultKeymap,history,historyKeymap,indentWithTab} from '@codemirror/commands';
import {javascript,javascriptLanguage} from '@codemirror/lang-javascript';
import {oneDark} from '@codemirror/theme-one-dark';
import {autocompletion,startCompletion,completionKeymap} from '@codemirror/autocomplete';
import {bracketMatching,syntaxTree,indentUnit,foldGutter,indentOnInput,syntaxHighlighting,defaultHighlightStyle} from '@codemirror/language';
import {closeBrackets} from '@codemirror/autocomplete';
import * as prettier from 'prettier/standalone';
import babel from 'prettier/plugins/babel';
import estree from 'prettier/plugins/estree';
import {validateSource} from './validation.mjs';
let view,kind='eruda',settings={};
const prefs=new Compartment();
function declarations(context) {
 const word=context.matchBefore(/[\w$]*/);if(!word||(!context.explicit&&word.from===word.to))return null;
 const names=new Map();
 syntaxTree(context.state).iterate({to:context.pos,enter(node){
   if(['VariableDefinition','PropertyDefinition'].includes(node.name)) {
     const label=context.state.sliceDoc(node.from,node.to);if(/^[\w$]+$/.test(label))names.set(label,{label,type:'variable'});
   }
 }});
 return {from:word.from,options:[...names.values()],validFor:/^[\w$]*$/};
}
function extensions() {
 return [settings.theme==='dark'?oneDark:[],settings.invisibles?[highlightWhitespace(),highlightSpecialChars()]:[],settings.matchBrackets?bracketMatching():[],settings.closeBrackets?closeBrackets():[],indentUnit.of(settings.indent==='tabs'?'\t':' '.repeat(settings.indentSize||2)),settings.wrap?EditorView.lineWrapping:[],EditorView.theme({'&':{height:'100%',fontSize:(settings.fontSize||14)+'px'},'.cm-scroller':{overflow:'auto',fontFamily:'monospace'},'.cm-content':{minHeight:'220px'}})];
}
function publish() {
 const source=view.state.doc.toString();const error=validateSource(source,kind);
 window.editorBridge?.postMessage(JSON.stringify({source,error}));
 document.querySelector('#status').textContent=error||'JavaScript valid';
 return {source,error};
}
window.DevToolsEditor={
 init(source,type,options){
  if(view)view.destroy();kind=type;settings=options;
  view=new EditorView({parent:document.querySelector('#editor'),state:EditorState.create({doc:source,extensions:[lineNumbers(),highlightActiveLine(),highlightActiveLineGutter(),drawSelection(),history(),foldGutter(),indentOnInput(),syntaxHighlighting(defaultHighlightStyle,{fallback:true}),keymap.of([...defaultKeymap,...historyKeymap,...completionKeymap,indentWithTab]),javascript(),javascriptLanguage.data.of({autocomplete:declarations}),autocompletion(),prefs.of(extensions()),keymap.of([{key:'Ctrl-Space',run:startCompletion}]),EditorView.updateListener.of(update=>{if(update.docChanged)publish();})]})});
  this.configure(options);publish();
 },
 configure(options){settings=options;document.documentElement.dataset.theme=settings.theme;view.dispatch({effects:prefs.reconfigure(extensions())});},
 snapshot:publish,
 complete(){startCompletion(view);view.focus();},
 async format(){try{const result=await prettier.format(view.state.doc.toString(),{parser:'babel',plugins:[babel,estree],tabWidth:settings.indentSize||2,useTabs:settings.indent==='tabs',singleQuote:!!settings.singleQuote,semi:settings.semicolons!==false});view.dispatch({changes:{from:0,to:view.state.doc.length,insert:result}});}catch(error){document.querySelector('#status').textContent=error.message;}},
 validate:validateSource,
 view(){return view;}
};
