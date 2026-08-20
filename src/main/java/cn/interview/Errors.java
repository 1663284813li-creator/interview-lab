package cn.interview;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> known(ResponseStatusException e) {return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"请求失败":e.getReason()));}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(Exception e) {return ResponseEntity.badRequest().body(Map.of("message","请检查必填项和内容长度"));}
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unknown(Exception e) {return ResponseEntity.status(500).body(Map.of("message","服务暂时不可用，请检查后台日志和配置"));}
}
