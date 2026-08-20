import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const root=resolve('src/main/resources/static');
const allowed=new Map([['/','index.html'],['/index.html','index.html'],['/app.js','app.js'],['/sse.js','sse.js'],['/styles.css','styles.css'],['/favicon.svg','favicon.svg']]);
http.createServer(async(req,res)=>{
  const file=allowed.get(new URL(req.url,'http://localhost').pathname);
  if(!file){res.writeHead(503,{'Content-Type':'application/json'});res.end(JSON.stringify({message:'静态预览没有后端，请使用 Docker 启动完整服务'}));return;}
  const type=file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'text/html';
  try{res.writeHead(200,{'Content-Type':type+'; charset=utf-8'});res.end(await readFile(resolve(root,file)));}catch{res.writeHead(404);res.end();}
}).listen(4173,'127.0.0.1',()=>console.log('UI preview: http://127.0.0.1:4173 (backend unavailable)'));
