import {readFile,writeFile} from 'node:fs/promises';
const file=process.argv[2]||'tmp/synthetic-scanned.pdf';
if(!file.startsWith('tmp/'))throw new Error('本诊断脚本只接受 tmp/ 下的合成测试资料');
const base='http://127.0.0.1:8080';
const user=await fetch(base+'/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'}).then(r=>r.json());
const form=new FormData();form.append('file',new Blob([await readFile(file)],{type:'application/pdf'}),'synthetic-scanned.pdf');
const start=Date.now();const response=await fetch(base+'/api/uploads/resume',{method:'POST',headers:{Authorization:'Bearer '+user.token},body:form,signal:AbortSignal.timeout(150000)});
const result=await response.json();console.log('UPLOAD',response.status,JSON.stringify(result),'ms='+String(Date.now()-start));
if(response.ok){
  for(let i=0;i<36;i++){
    const jobs=await fetch(base+'/api/jobs',{headers:{Authorization:'Bearer '+user.token}}).then(r=>r.json());
    const job=jobs.find(j=>j.id===result.id);
    if(job?.status==='DONE'){console.log('PASS 扫描版PDF识别、入队和真实模型简历分析，结果长度='+job.result.length);await writeFile('tmp/upload-results.json',JSON.stringify({status:'DONE',resultLength:job.result.length,jobId:job.id},null,2));process.exit(0);}
    if(job?.status==='FAILED')throw new Error(job.result);
    await new Promise(r=>setTimeout(r,5000));
  }
  throw new Error('任务等待超时');
}
process.exitCode=1;
