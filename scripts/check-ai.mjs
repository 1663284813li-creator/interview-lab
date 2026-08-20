import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const values=Object.fromEntries((await readFile(resolve(root,'.env'),'utf8')).split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>{const i=l.indexOf('=');return [l.slice(0,i),l.slice(i+1).trim().replace(/^(["'])(.*)\1$/,'$2')];}));
const base=values.AI_BASE_URL?.replace(/\/+$/,'');
if(!base||!values.AI_API_KEY)throw new Error('请填写 AI_BASE_URL 和 AI_API_KEY');
const redact=s=>String(s).replaceAll(values.AI_API_KEY,'[REDACTED]').slice(0,250);
const results={checkedAt:new Date().toISOString(),host:new URL(base).host,chatModel:values.AI_CHAT_MODEL,embeddingModel:values.AI_EMBED_MODEL};
async function request(path,body){
  const started=Date.now();
  try{
    const r=await fetch(base+path,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+values.AI_API_KEY,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(45000)});
    const raw=await r.text();let j;try{j=JSON.parse(raw);}catch{j={error:{message:'服务未返回 JSON'}};}
    return {status:r.status,ms:Date.now()-started,json:j};
  }catch(e){return {status:0,ms:Date.now()-started,json:{error:{message:redact(e.message)}}};}
}
const models=await request('/models');
results.models={status:models.status,available:(models.json.data||[]).map(x=>x.id).filter(x=>/qwen|embedding/i.test(x)).slice(0,100)};
console.log('模型目录:',JSON.stringify(results.models));
const chat=await request('/chat/completions',{model:values.AI_CHAT_MODEL,messages:[{role:'user',content:'请用一句话提出一道 Java 面试题。'}],max_tokens:100,temperature:.2});
results.chat={status:chat.status,ms:chat.ms,ok:chat.status===200&&!!chat.json.choices?.[0]?.message?.content,error:chat.status!==200?redact(chat.json.error?.message||chat.json.message||'请求失败'):undefined};
console.log('聊天连通性:',JSON.stringify(results.chat));
const embedding=await request('/embeddings',{model:values.AI_EMBED_MODEL,input:['Java 面试训练'],dimensions:1024,encoding_format:'float'});
const vector=embedding.json.data?.[0]?.embedding;
results.embedding={status:embedding.status,ms:embedding.ms,dimensions:Array.isArray(vector)?vector.length:null,ok:embedding.status===200&&Array.isArray(vector)&&vector.length===1024&&vector.every(Number.isFinite),error:embedding.status!==200?redact(embedding.json.error?.message||embedding.json.message||'请求失败'):undefined};
console.log('向量连通性:',JSON.stringify(results.embedding));
await mkdir(resolve(root,'tmp'),{recursive:true});await writeFile(resolve(root,'tmp/ai-check.json'),JSON.stringify(results,null,2));
if(!results.chat.ok||!results.embedding.ok)process.exitCode=1;
