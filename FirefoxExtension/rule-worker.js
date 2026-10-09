importScripts('tools/rule-engine.js');
onmessage = ({data}) => postMessage(DevToolsRules.evaluate(data.profile,data.url));
