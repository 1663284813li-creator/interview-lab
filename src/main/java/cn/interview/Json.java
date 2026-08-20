package cn.interview;

import com.fasterxml.jackson.databind.ObjectMapper;

final class Json {
    static final ObjectMapper MAPPER = new ObjectMapper();
    static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("JSON encoding failed", e); }
    }
}
