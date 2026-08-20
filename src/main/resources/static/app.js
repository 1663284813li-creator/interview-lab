import { SseParser } from './sse.js';
const $ = (id) => document.getElementById(id);
const names = { interview:'模拟面试',resume:'简历分析',knowledge:'知识库问答',history:'训练记录' };
const state = { token:localStorage.getItem('interview-token'),session:null,busy:false,ragBusy:false,jobs:[],bases:[],ragHistory:[] };
const skills = ['Java 后端','AI Agent','RAG 工程','前端开发','Python 开发','数据工程','算法工程','数据库','云原生','测试开发','产品经理','系统设计'];
function el(tag, text, cls) { const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e; }
function notice(text,success=false) { $('notice').textContent=text;$('notice').className='notice'+(success?' success':''); }
function view(id) {document.querySelectorAll('.view').forEach(v=>v.classList.toggle('hidden',v.id!==id));document.querySelectorAll('.nav').forEach(n=>n.classList.toggle('active',n.dataset.view===id));$('breadcrumb').textContent=names[id];}
document.querySelectorAll('.nav').forEach(n=>n.addEventListener('click',()=>{view(n.dataset.view);if(n.dataset.view==='history')safe(loadHistory);}));
function headers() {return {Authorization:'Bearer '+state.token};}
async function api(path,method='GET',body) {
  const opts={method,headers:headers()};
  if(body instanceof FormData)opts.body=body;
  else if(body!==undefined){opts.headers['Content-Type']='application/json';opts.body=JSON.stringify(body);}
  opts.signal=AbortSignal.timeout(30000);
  const r=await fetch('/api'+path,opts);if(!r.ok){let text='请求失败（'+r.status+'）';try{const j=await r.json();text=j.message||text;}catch{}if(r.status===401)text='身份凭证失效，请备份现有凭证后重新创建训练空间';throw new Error(text);}return r.json();
}
function safe(fn) {return Promise.resolve().then(fn).catch(e=>notice(e.message));}
function setBusy(b) {state.busy=b;$('start').disabled=b;$('finish').disabled=b||!state.session;$('answer').disabled=b||!state.session;$('send-answer').disabled=b||!state.session;}
function bubble(container,role,text) {
  container.querySelector('.empty-chat')?.remove();const box=el('div',undefined,'bubble '+role);box.append(el('div',role==='user'?'你':'AI 面试官','bubble-label'));const body=el('div',text);box.append(body);container.append(box);container.scrollTop=container.scrollHeight;return {box,body};
}
async function stream(path,body,container) {
  const response=await fetch('/api'+path,{method:'POST',headers:{...headers(),'Content-Type':'application/json'},body:JSON.stringify(body)});
  if(!response.ok){let message='生成失败（'+response.status+'）';try{message=(await response.json()).message||message;}catch{}throw new Error(message);}
  const item=bubble(container,'assistant','正在思考…');let answer='',done=false;
  const parser=new SseParser((event,data)=>{
    if(event==='delta'){answer+=data.text;item.body.textContent=answer;container.scrollTop=container.scrollHeight;}
    if(event==='sources'&&data.length){const list=el('div',undefined,'sources');data.forEach((s,i)=>{const details=el('details');details.append(el('summary',`[${i+1}] ${s.source} · 相关度 ${(s.similarity*100).toFixed(0)}%`),el('p',s.text));list.append(details);});item.box.append(list);}
    if(event==='error')throw new Error(data.message);
    if(event==='done')done=true;
  });
  const reader=response.body.getReader(),decoder=new TextDecoder();
  try {while(true){const {value,done:end}=await reader.read();if(end)break;parser.push(decoder.decode(value,{stream:true}));}parser.push(decoder.decode());if(!done)throw new Error('连接中断，本轮未完成；请重试');return answer;}
  catch(e){await reader.cancel();item.body.textContent=(answer?answer+'\n\n':'')+'[本轮未完成]';throw e;}
}
skills.forEach((name,i)=>{const b=el('button',name,'skill-btn'+(i===0?' selected':''));b.type='button';b.setAttribute('aria-pressed',i===0?'true':'false');b.onclick=()=>{$('direction').value=name;document.querySelectorAll('.skill-btn').forEach(x=>{const selected=x===b;x.classList.toggle('selected',selected);x.setAttribute('aria-pressed',String(selected));});};$('skill-grid').append(b);$('direction').append(new Option(name,name));});
$('start').onclick=()=>safe(async()=>{
  setBusy(true);try{const result=await api('/interviews','POST',{direction:$('direction').value,level:$('level').value,resumeJobId:$('resume-job').value||null});state.session=result.id;$('chat').replaceChildren();$('report').classList.add('hidden');$('session-state').textContent=$('direction').value+' · '+$('level').value;await stream('/interviews/'+state.session+'/messages',{text:''},$('chat'));}finally{setBusy(false);}
});
$('answer-form').onsubmit=e=>{e.preventDefault();safe(async()=>{if(state.busy||!state.session)return;const text=$('answer').value.trim();if(!text)return;setBusy(true);bubble($('chat'),'user',text);try{await stream('/interviews/'+state.session+'/messages',{text},$('chat'));$('answer').value='';}finally{setBusy(false);}});};
$('answer').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();$('answer-form').requestSubmit();}};
function showReport(report) {
  const box=$('report');box.replaceChildren();box.classList.remove('hidden');box.append(el('h2','面试评估 · 你的下一步'),el('div',report.score+' / 100','score'),el('p',report.summary));const columns=el('div',undefined,'report-columns');[['回答亮点',report.strengths],['改善空间',report.improvements],['学习计划',report.plan]].forEach(([title,items])=>{const col=el('div');col.append(el('h3',title));const list=el('ul');items.forEach(s=>list.append(el('li',s)));col.append(list);columns.append(col);});box.append(columns,el('p','AI 辅助评价，仅用于个人训练。','muted'));box.scrollIntoView({behavior:'smooth',block:'start'});
}
$('finish').onclick=()=>safe(async()=>{if(!state.session)return;setBusy(true);try{const report=await api('/interviews/'+state.session+'/report','POST',{});showReport(report);state.session=null;$('session-state').textContent='面试已完成 · 报告已保存';}finally{setBusy(false);}});
async function submitDocument(kind,textId,fileId) {
  const file=$(fileId).files[0],text=$(textId).value.trim();const kbId=$('kb').value;
  if(kind==='index'&&!kbId)throw new Error('请先创建或选择知识库');if(!file&&!text)throw new Error('请粘贴正文或选择文件');
  if(file){if(file.size>5*1024*1024)throw new Error('文件超过 5 MB');const form=new FormData();form.append('file',file);if(kind==='index')form.append('kbId',kbId);return api('/uploads/'+kind,'POST',form);}
  return api('/jobs/'+kind,'POST',{text,kbId:kind==='index'?kbId:null});
}
[['resume-form','resume','resume-text','resume-file'],['index-form','index','knowledge-text','knowledge-file']].forEach(([formId,kind,textId,fileId])=>{
  const form=$(formId),feedback=el('p','','form-feedback hidden');feedback.id=formId+'-feedback';feedback.setAttribute('role','status');feedback.setAttribute('aria-live','polite');form.append(feedback);
  form.onsubmit=async e=>{
    e.preventDefault();const b=form.querySelector('button');if(b.disabled)return;
    const label=b.textContent;b.disabled=true;b.textContent='正在上传，请稍候…';feedback.textContent='正在接收文件，上传后会自动提取正文并分析。';feedback.className='form-feedback';
    try{
      await submitDocument(kind,textId,fileId);
      feedback.textContent='已创建任务。扫描PDF会自动OCR识别，请查看右侧任务进度，无需重复提交。';feedback.className='form-feedback success';notice('已提交，后台正在处理，任务结果将自动刷新。',true);
      $(textId).value='';$(fileId).value='';await loadJobs();
    }catch(e){feedback.textContent=e.name==='TimeoutError'?'上传响应超时，请点击右上角刷新确认任务是否已提交，再决定是否重试。':e.message;feedback.className='form-feedback error';notice(feedback.textContent);}
    finally{b.disabled=false;b.textContent=label;}
  };
});
const statusNames={QUEUED:'排队中',RUNNING:'处理中',DONE:'已完成',FAILED:'失败'};
async function loadJobs() {
  const jobs=await api('/jobs');const signature=JSON.stringify(jobs);if(signature===state.jobsSignature)return;state.jobsSignature=signature;state.jobs=jobs;const selected=$('resume-job').value;$('resume-job').replaceChildren(new Option('通用技术面试',''));['resume-tasks','index-tasks'].forEach(id=>$(id).replaceChildren());
  jobs.forEach(j=>{const task=el('div',undefined,'task'+(j.status==='FAILED'?' failed':''));task.dataset.jobId=j.id;const head=el('div',undefined,'task-title');head.append(el('span',j.source_name),el('span',statusNames[j.status]||j.status,'status'));task.append(head);if(j.result)task.append(el('pre',j.result));else if(j.status==='QUEUED')task.append(el('p','文件已接收，正在等待后台处理。','muted'));else if(j.status==='RUNNING')task.append(el('p','后台正在识别文档并生成分析，请稍候。','muted'));if(['QUEUED','RUNNING'].includes(j.status))task.append(el('p','每5秒自动更新 · 无需重复提交','subnote'));$(j.kind==='RESUME'?'resume-tasks':'index-tasks').append(task);if(j.kind==='RESUME'&&j.status==='DONE')$('resume-job').append(new Option(j.source_name+' · '+new Date(j.created_at).toLocaleString('zh-CN'),j.id));});
  if([...$('resume-job').options].some(o=>o.value===selected))$('resume-job').value=selected;
  if(!$('resume-tasks').children.length)$('resume-tasks').append(el('p','还没有分析任务。提交一份简历开始。','muted'));
}
async function loadBases() {const selected=$('kb').value;state.bases=await api('/knowledge-bases');$('kb').replaceChildren(new Option('请选择知识库',''));state.bases.forEach(b=>$('kb').append(new Option(b.name,b.id)));if(state.bases.some(b=>b.id===selected))$('kb').value=selected;else if(state.bases.length)$('kb').value=state.bases[0].id;}
$('kb-form').onsubmit=e=>{e.preventDefault();safe(async()=>{const b=$('kb-form').querySelector('button');b.disabled=true;try{const result=await api('/knowledge-bases','POST',{name:$('kb-name').value.trim()});await loadBases();$('kb').value=result.id;$('kb-name').value='';notice('知识库已创建，添加资料后即可问答。',true);}finally{b.disabled=false;}});};
$('kb').onchange=()=>{state.ragHistory=[];$('rag-chat').replaceChildren();};
$('rag-form').onsubmit=e=>{e.preventDefault();safe(async()=>{if(state.ragBusy)return;const question=$('question').value.trim();if(!question)return;if(!$('kb').value)throw new Error('请先选择知识库');state.ragBusy=true;const b=$('rag-form').querySelector('button');b.disabled=true;$('kb').disabled=true;bubble($('rag-chat'),'user',question);try{const answer=await stream('/rag/chat',{kbId:$('kb').value,question,history:state.ragHistory.slice(-6)},$('rag-chat'));state.ragHistory.push('用户：'+question,'助手：'+answer.slice(0,1900));$('question').value='';}finally{state.ragBusy=false;b.disabled=false;$('kb').disabled=false;}});};
async function loadHistory() {
  const rows=await api('/interviews');$('history-list').replaceChildren();if(!rows.length)$('history-list').append(el('p','还没有训练记录，开始你的第一次面试吧。','muted'));
  rows.forEach(row=>{const card=el('div',undefined,'panel history-card');card.append(el('h3',row.direction),el('span',row.status==='COMPLETED'?'已完成':'进行中','status'),el('p',row.level+' · '+row.turn+' 轮提问'),el('p',new Date(row.created_at).toLocaleString('zh-CN')));const b=el('button',row.report?'查看报告':'继续面试','secondary');b.onclick=()=>safe(async()=>{if(state.busy)throw new Error('请等待当前回答完成');view('interview');const data=await api('/interviews/'+row.id);$('chat').replaceChildren();data.messages.forEach(m=>bubble($('chat'),m.role,m.content));$('session-state').textContent=data.direction+' · '+data.level;if(data.report){state.session=null;showReport(JSON.parse(data.report));}else{state.session=row.id;$('report').classList.add('hidden');if(data.turn===0){setBusy(true);try{await stream('/interviews/'+row.id+'/messages',{text:''},$('chat'));}finally{setBusy(false);}}}setBusy(false);});card.append(b);$('history-list').append(card);});
}
async function connect() {
  if(!state.token){const result=await api('/auth/register','POST',{});state.token=result.token;localStorage.setItem('interview-token',result.token);}
  await Promise.all([loadJobs(),loadBases(),api('/skills')]);$('connection').textContent='服务已连接';$('notice').classList.add('hidden');
}
$('refresh').onclick=()=>safe(connect);
safe(async()=>{try{await connect();}catch(e){$('connection').textContent='服务未连接';throw new Error('后端尚未就绪。请按 README 配置模型密钥并启动 Docker 服务，再点击右上角刷新。');}});
setInterval(()=>{if(state.token&&(state.jobs.some(j=>['QUEUED','RUNNING'].includes(j.status))||!$('resume').classList.contains('hidden')||!$('knowledge').classList.contains('hidden')))safe(loadJobs);},5000);
