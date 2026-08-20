package cn.interview;

import java.time.Duration;
import java.util.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class Jobs {
    private static final String STREAM = "interview:jobs";
    private static final String GROUP = "processors";
    private final JdbcTemplate db;
    private final StringRedisTemplate redis;
    private final ChatClient chat;
    private final RagService rag;
    private final DocumentExtractor extractor;
    private final String worker;
    public Jobs(JdbcTemplate db, StringRedisTemplate redis, ChatClient.Builder builder, RagService rag,DocumentExtractor extractor,
                @Value("${app.worker-name}") String worker) {
        this.db=db; this.redis=redis; this.chat=SecureChat.build(builder); this.rag=rag; this.extractor=extractor; this.worker=worker;
    }
    UUID create(UUID owner, String kind, UUID kb, String name, String text) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO job(id,owner_id,kind,kb_id,source_name,input_text) VALUES(?,?,?,?,?,?)",id,owner,kind,kb,name,text);
        return id;
    }
    UUID createUpload(UUID owner,String kind,UUID kb,String name,byte[] bytes) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO job(id,owner_id,kind,kb_id,source_name,input_text,file_data,result) VALUES(?,?,?,?,?,'',?,?)",id,owner,kind,kb,name,bytes,"文件已接收，等待正文提取与分析");
        return id;
    }
    // Transactional outbox: DB row is durable before publishing. Duplicates are idempotent.
    @Scheduled(fixedDelay=2000)
    public void dispatch() {
        try {
            var rows=db.queryForList("SELECT id FROM job WHERE published=false AND status='QUEUED' ORDER BY created_at LIMIT 30");
            for (var row:rows) {
                UUID id=(UUID)row.get("id");
                redis.opsForStream().add(STREAM,Map.of("job",id.toString()));
                db.update("UPDATE job SET published=true WHERE id=?",id);
            }
        } catch (Exception e) { /* Leave outbox rows unpublished; retry on next tick. */ }
    }
    private boolean initialized;
    @Scheduled(fixedDelay=1000)
    public void consume() {
        try {
            if (!initialized) {
                if (!Boolean.TRUE.equals(redis.hasKey(STREAM))) redis.opsForStream().add(STREAM,Map.of("init","1"));
                try { redis.opsForStream().createGroup(STREAM,ReadOffset.from("0-0"),GROUP); }
                catch (Exception e) {
                    boolean exists=false;
                    for(Throwable cause=e;cause!=null;cause=cause.getCause())
                        if(cause.getMessage()!=null && cause.getMessage().contains("BUSYGROUP")) {exists=true;break;}
                    if(!exists)throw e;
                }
                initialized=true;
            }
            // Recover own pending deliveries after restart (stable worker name).
            var pending=redis.opsForStream().read(Consumer.from(GROUP,worker),StreamReadOptions.empty().count(1),StreamOffset.create(STREAM,ReadOffset.from("0")));
            var records = pending!=null && !pending.isEmpty() ? pending : redis.opsForStream().read(Consumer.from(GROUP,worker),StreamReadOptions.empty().count(1),StreamOffset.create(STREAM,ReadOffset.lastConsumed()));
            if (records==null) return;
            for (var record:records) process(record);
        } catch (Exception e) { initialized=false; }
    }
    private void process(MapRecord<String,Object,Object> record) {
        Object raw=record.getValue().get("job");
        if (raw==null) { ack(record); return; }
        UUID id=UUID.fromString(raw.toString());
        var rows=db.queryForList("SELECT * FROM job WHERE id=?",id);
        if (rows.isEmpty()) { ack(record); return; }
        var job=rows.getFirst();
        String status=(String)job.get("status");
        if (Set.of("DONE","FAILED").contains(status)) { ack(record); return; }
        if (((Number)job.get("attempts")).intValue()>=3) {
            db.update("UPDATE job SET status='FAILED',result='多次尝试未完成，请重新提交',input_text='',file_data=null,updated_at=now() WHERE id=?",id); ack(record); return;
        }
        db.update("UPDATE job SET status='RUNNING',attempts=attempts+1,updated_at=now() WHERE id=?",id);
        try {
            String input=(String)job.get("input_text");
            if(job.get("file_data") instanceof byte[] bytes) {
                db.update("UPDATE job SET result='正在提取正文；扫描PDF会自动进行中英文OCR识别',updated_at=now() WHERE id=?",id);
                input=Guard.output(Guard.input(extractor.extract((String)job.get("source_name"),bytes),60000));
                db.update("UPDATE job SET input_text=?,file_data=null,updated_at=now() WHERE id=?",input,id);
            }
            String result;
            if ("INDEX".equals(job.get("kind"))) {
                db.update("UPDATE job SET result='正文已提取，正在切分与向量化',updated_at=now() WHERE id=?",id);
                rag.ingest(id,(UUID)job.get("owner_id"),(UUID)job.get("kb_id"),(String)job.get("source_name"),input);
                result="文档已完成切分和向量化，可在知识库中检索";
            } else {
                db.update("UPDATE job SET result='正文已提取，千问正在分析简历，请稍候',updated_at=now() WHERE id=?",id);
                result=chat.prompt().system(Guard.SYSTEM+" 分析简历：给出技能证据、岗位匹配、项目风险、改写建议和5道针对性追问。不要补造项目事实。使用清晰的中文段落。")
                    .user(input).call().content();
                if (result==null || result.isBlank()) throw new IllegalStateException("Empty model output");
            }
            db.update("UPDATE job SET status='DONE',result=?,input_text='',file_data=null,updated_at=now() WHERE id=?",Guard.output(result),id);
            ack(record);
        } catch (Exception e) {
            if(e instanceof IllegalArgumentException || e instanceof org.springframework.web.server.ResponseStatusException) {
                String message=e instanceof org.springframework.web.server.ResponseStatusException r?r.getReason():e.getMessage();
                db.update("UPDATE job SET status='FAILED',result=?,input_text='',file_data=null,updated_at=now() WHERE id=?",Guard.output(message),id);ack(record);return;
            }
            db.update("UPDATE job SET status=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'QUEUED' END,result='模型处理未完成，后台正在重试；多次失败后请重新提交',input_text=CASE WHEN attempts>=3 THEN '' ELSE input_text END,file_data=CASE WHEN attempts>=3 THEN null ELSE file_data END,updated_at=now() WHERE id=?",id);
            // Do not ACK retryable delivery: pending entry survives restart.
            if (((Number)job.get("attempts")).intValue()>=2) ack(record);
        }
    }
    private void ack(MapRecord<String,Object,Object> r) {
        redis.opsForStream().acknowledge(STREAM,GROUP,r.getId());
        redis.opsForStream().delete(STREAM,r.getId());
    }
}
