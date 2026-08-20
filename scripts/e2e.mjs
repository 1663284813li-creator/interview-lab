import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { SseParser } from '../src/main/resources/static/sse.js';
const base=process.env.APP_URL||'http://127.0.0.1:8080';
const results=[];
async function api(token,path,method='GET',body){
  const r=await fetch(base+'/api'+path,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(180000)});
  const data=await r.json().catch(()=>({}));if(!r.ok)throw new Error(path+' HTTP '+r.status+' '+(data.message||''));return data;
}
async function stream(token,path,body){
  const r=await fetch(base+'/api'+path,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(180000)});
  if(!r.ok)throw new Error('SSE HTTP '+r.status);const events=[];const p=new SseParser((event,data)=>{if(event==='error')throw new Error(data.message);events.push({event,data});});p.push(await r.text());assert(events.some(e=>e.event==='done'));assert(events.some(e=>e.event==='delta'));return events;
}
async function waitJob(token,id){
  const limit=Date.now()+180000;
  while(Date.now()<limit){const job=(await api(token,'/jobs')).find(j=>j.id===id);if(job?.status==='FAILED')throw new Error('job failed: '+job.result);if(job?.status==='DONE')return job;await new Promise(r=>setTimeout(r,5000));}
  throw new Error('job timeout');
}
function pass(name){results.push({name,passed:true});console.log('PASS',name);}
try{
  const health=await fetch(base+'/actuator/health').then(r=>r.json());assert.equal(health.status,'UP');pass('服务健康');
  const a=await api(null,'/auth/register','POST',{});const b=await api(null,'/auth/register','POST',{});
  assert.equal(Object.keys(await api(a.token,'/skills')).length,12);pass('认证与12个Skill');
  const kb=await api(a.token,'/knowledge-bases','POST',{name:'自动验收 · Java事务'});
  const job=await api(a.token,'/jobs/index','POST',{kbId:kb.id,text:'Java Spring事务知识笔记。Spring声明式事务基于AOP代理实现。默认情况下，未检查异常RuntimeException和Error会触发事务回滚，受检查异常默认不会触发回滚，可通过rollbackFor配置。事务方法在同一个对象内通过this调用另一个事务方法时绕过代理，导致事务注解失效。解决方式包括将事务边界拆分到另一个Bean，通过代理对象调用，或使用TransactionTemplate显式管理事务。REQUIRED传播行为加入已有事务，不存在时创建事务。REQUIRES_NEW挂起当前事务并创建新的独立事务。数据库MVCC允许不同事务读取不同版本，减少读写锁竞争。'});
  await waitJob(a.token,job.id);pass('Redis Stream消费与pgvector向量入库');
  const rag=await stream(a.token,'/rag/chat',{kbId:kb.id,question:'Spring事务为什么会在同一个对象内的this调用时失效？',history:[]});
  const sources=rag.find(e=>e.event==='sources')?.data;assert(sources?.length>0);pass('查询改写、向量检索和带来源SSE回答');
  const resume=await api(a.token,'/jobs/resume','POST',{text:'求职方向：Java后端开发。技能：Java 21、Spring Boot、PostgreSQL、Redis。项目：实现订单服务，用数据库事务保证订单写入与库存扣减一致；使用Redis缓存商品详情，实现过期失效策略。负责接口开发、JUnit测试和Docker部署。请仅基于以上事实分析。'});
  await waitJob(a.token,resume.id);pass('异步简历分析');
  const interview=await api(a.token,'/interviews','POST',{direction:'Java 后端',level:'中级',resumeJobId:resume.id});
  await stream(a.token,'/interviews/'+interview.id+'/messages',{text:''});
  await stream(a.token,'/interviews/'+interview.id+'/messages',{text:'我通过Spring的声明式事务管理订单和库存写入，事务方法放在单独的Service中，避免this调用绕过代理。对于需要回滚的受检查异常配置rollbackFor。并发扣库存使用带库存大于零条件的更新语句，检查更新行数，失败时回滚。'});
  const report=await api(a.token,'/interviews/'+interview.id+'/report','POST',{});assert(report.score>=0&&report.score<=100);assert(Array.isArray(report.plan));pass('关联简历面试、多轮SSE、结构化评估');
  const history=await api(a.token,'/interviews/'+interview.id);assert.equal(history.status,'COMPLETED');assert.equal(history.messages.length,3);pass('会话和报告持久化');
  for(const path of ['/interviews/'+interview.id]){const r=await fetch(base+'/api'+path,{headers:{Authorization:'Bearer '+b.token}});assert.equal(r.status,404);}
  const foreignRag=await fetch(base+'/api/rag/chat',{method:'POST',headers:{Authorization:'Bearer '+b.token,'Content-Type':'application/json'},body:JSON.stringify({kbId:kb.id,question:'Spring事务'})});assert.equal(foreignRag.status,404);
  assert.equal((await api(b.token,'/jobs')).length,0);pass('真实数据库跨用户隔离');
}catch(e){console.error('FAIL',e.message);results.push({name:e.message,passed:false});process.exitCode=1;}
await mkdir('tmp',{recursive:true});await writeFile('tmp/e2e-results.json',JSON.stringify({checkedAt:new Date().toISOString(),results},null,2));
