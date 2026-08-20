package cn.interview;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class Api {
    private final JdbcTemplate db;
    private final Jobs jobs;
    public Api(JdbcTemplate db, Jobs jobs) {this.db=db;this.jobs=jobs;}
    @PostMapping("/auth/register")
    Map<String,Object> register() {
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id=UUID.randomUUID(); db.update("INSERT INTO account(id,token_hash) VALUES(?,?)",id,AuthFilter.hash(token));
        return Map.of("token",token,"userId",id);
    }
    @GetMapping("/skills") Map<String,String> skills() {return Skills.ALL;}
    @GetMapping("/knowledge-bases") List<Map<String,Object>> bases(HttpServletRequest r) {
        return db.queryForList("SELECT id,name,created_at FROM knowledge_base WHERE owner_id=? ORDER BY created_at DESC",AuthFilter.owner(r));
    }
    record NewBase(@NotBlank @Size(max=100) String name) {}
    @PostMapping("/knowledge-bases") Map<String,UUID> base(HttpServletRequest r,@Valid @RequestBody NewBase body) {
        UUID id=UUID.randomUUID(); db.update("INSERT INTO knowledge_base(id,owner_id,name) VALUES(?,?,?)",id,AuthFilter.owner(r),body.name());
        return Map.of("id",id);
    }
    record TextJob(@NotBlank @Size(max=60000) String text, UUID kbId) {}
    @PostMapping("/jobs/{kind}") @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String,UUID> textJob(HttpServletRequest r,@PathVariable String kind,@Valid @RequestBody TextJob b) {
        return enqueue(AuthFilter.owner(r),kind,b.kbId(),"粘贴文档",b.text());
    }
    @PostMapping("/uploads/{kind}") @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String,UUID> upload(HttpServletRequest r,@PathVariable String kind,@RequestParam MultipartFile file,@RequestParam(required=false) UUID kbId) {
        String name=Objects.requireNonNullElse(file.getOriginalFilename(),"document").replaceAll("[\\\\/\\r\\n]","_");
        if (!name.toLowerCase(Locale.ROOT).matches(".*\\.(pdf|docx|txt|md)$")) throw bad("仅支持 PDF、DOCX、TXT、MD");
        if (file.isEmpty() || file.getSize()>5*1024*1024) throw bad("文件为空或超过5MB");
        if (!Set.of("resume","index").contains(kind)) throw bad("未知任务类型");
        if (kind.equals("index")) requireBase(AuthFilter.owner(r),kbId);
        try {
            UUID id=jobs.createUpload(AuthFilter.owner(r),kind.equals("index")?"INDEX":"RESUME",kind.equals("index")?kbId:null,name.substring(0,Math.min(200,name.length())),file.getBytes());
            return Map.of("id",id);
        }catch(java.io.IOException e){throw bad("文件读取失败，请重新上传。");}
    }
    private Map<String,UUID> enqueue(UUID owner,String kind,UUID kb,String name,String text) {
        if (!Set.of("resume","index").contains(kind)) throw bad("未知任务类型");
        if (kind.equals("index")) requireBase(owner,kb);
        return Map.of("id",jobs.create(owner,kind.equals("index")?"INDEX":"RESUME",kind.equals("index")?kb:null,name,Guard.output(Guard.input(text,60000))));
    }
    void requireBase(UUID owner,UUID id) {
        if(id==null || db.queryForObject("SELECT count(*) FROM knowledge_base WHERE id=? AND owner_id=?",Integer.class,id,owner)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"知识库不存在");
    }
    @GetMapping("/jobs") List<Map<String,Object>> jobs(HttpServletRequest r) {
        return db.queryForList("SELECT id,kind,source_name,status,result,attempts,created_at FROM job WHERE owner_id=? ORDER BY created_at DESC LIMIT 50",AuthFilter.owner(r));
    }
    @GetMapping("/interviews") List<Map<String,Object>> interviews(HttpServletRequest r) {
        return db.queryForList("SELECT id,direction,level,status,turn,report,created_at FROM interview WHERE owner_id=? ORDER BY created_at DESC LIMIT 50",AuthFilter.owner(r));
    }
    @GetMapping("/interviews/{id}") Map<String,Object> interview(HttpServletRequest r,@PathVariable UUID id) {
        var rows=db.queryForList("SELECT id,direction,level,status,turn,report FROM interview WHERE id=? AND owner_id=?",id,AuthFilter.owner(r));
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"面试不存在");
        var result=rows.getFirst(); result.put("messages",db.queryForList("SELECT role,content FROM message WHERE session_id=? ORDER BY id",id)); return result;
    }
    static ResponseStatusException bad(String m) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
