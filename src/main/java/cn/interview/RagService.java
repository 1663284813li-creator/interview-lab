package cn.interview;

import java.util.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class RagService {
    private final JdbcTemplate db;
    private final EmbeddingModel embeddings;
    private final ChatClient chat;
    private final TransactionTemplate tx;
    public RagService(JdbcTemplate db, EmbeddingModel embeddings, ChatClient.Builder builder, PlatformTransactionManager manager) {
        this.db = db; this.embeddings = embeddings; this.chat = SecureChat.build(builder); this.tx = new TransactionTemplate(manager);
    }
    static String vector(float[] v) {
        if (v.length != 1024) throw new IllegalStateException("Embedding 模型必须输出 1024 维，请检查配置");
        var join = new StringJoiner(",", "[", "]");
        for (float f : v) { if (!Float.isFinite(f)) throw new IllegalStateException("Invalid embedding"); join.add(Float.toString(f)); }
        return join.toString();
    }
    void ingest(UUID job, UUID owner, UUID kb, String name, String text) {
        var pieces = TokenTextSplitter.builder().withChunkSize(500).withMinChunkSizeChars(100)
            .withMinChunkLengthToEmbed(20).withMaxNumChunks(200).build().apply(List.of(new Document(text)));
        if (pieces.isEmpty()) throw new IllegalArgumentException("文档没有可索引的正文");
        var vectors = pieces.stream().map(p -> vector(embeddings.embed(p.getText()))).toList();
        tx.executeWithoutResult(status -> {
            db.update("DELETE FROM chunk WHERE job_id=? AND owner_id=?", job, owner);
            for (int i=0; i<pieces.size(); i++) db.update("INSERT INTO chunk(id,owner_id,kb_id,job_id,source_name,ordinal,content,embedding) VALUES(?,?,?,?,?,?,?,?::vector)", UUID.randomUUID(),owner,kb,job,name,i,pieces.get(i).getText(),vectors.get(i));
        });
    }
    public record Source(String id, String source, String text, double similarity) {}
    List<Source> search(UUID owner, UUID kb, String question, String history) {
        String rewritten = chat.prompt().system(Guard.SYSTEM + " 将用户问题改写成独立的知识库检索问题，保留技术实体；只输出检索问题，最多200字。")
            .user("历史数据：" + history + "\n当前问题：" + question).call().content();
        if (rewritten == null || rewritten.isBlank()) rewritten = question;
        rewritten = rewritten.substring(0, Math.min(400, rewritten.length()));
        String v = vector(embeddings.embed(rewritten));
        int topK = question.codePointCount(0,question.length()) < 12 ? 8 : 5;
        double threshold = question.length() < 12 ? .55 : .35;
        // Scope first, then exact distance ordering: isolation is guaranteed even on small filtered datasets.
        // HNSW is present for future large-corpus tuning; planner may prefer this scope index.
        return db.query("SELECT id,source_name,content,1-(embedding <=> ?::vector) AS score FROM chunk WHERE owner_id=? AND kb_id=? AND 1-(embedding <=> ?::vector)>=? ORDER BY embedding <=> ?::vector LIMIT ?",
            (rs,n)->new Source(rs.getString("id"),rs.getString("source_name"),rs.getString("content"),rs.getDouble("score")), v,owner,kb,v,threshold,v,topK);
    }
}
