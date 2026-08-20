import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
const base=process.env.APP_URL||'http://127.0.0.1:8080';
const pause=ms=>new Promise(r=>setTimeout(r,ms));
let healthy=false;
for(let i=0;i<25;i++){try{const r=await fetch(base+'/actuator/health');healthy=(await r.json()).status==='UP';if(healthy)break;}catch{}await pause(2000);}
assert(healthy,'应用重启后未恢复健康');
async function api(token,path,body){const r=await fetch(base+'/api'+path,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined});assert(r.ok,'请求失败 HTTP '+r.status);return r.json();}
const {token}=await api(null,'/auth/register',{});
const {id:kbId}=await api(token,'/knowledge-bases',{name:'自动验收 · 重启后消费'});
const {id}=await api(token,'/jobs/index',{kbId,text:'Redis Stream通过消费组管理任务投递。消息读取后进入pending列表，处理完成之后ACK。应用重启时，已有消费组会返回BUSYGROUP错误，该错误应作为已存在正常处理，而不是阻止后续消费。同名消费者可以读取自身pending消息，恢复中断任务。终态任务应幂等跳过，避免重复向量入库。'});
let completed=false;
for(let i=0;i<30;i++){const job=(await api(token,'/jobs')).find(j=>j.id===id);assert(job?.status!=='FAILED','重启后任务失败');if(job?.status==='DONE'){completed=true;break;}await pause(3000);}
assert(completed,'重启后消费组未正常处理新任务');
await writeFile('tmp/recovery-check.json',JSON.stringify({checkedAt:new Date().toISOString(),health:true,existingGroupConsumption:true},null,2));
console.log('PASS 应用重启恢复健康，复用既有Redis消费组并完成新文档向量化');
