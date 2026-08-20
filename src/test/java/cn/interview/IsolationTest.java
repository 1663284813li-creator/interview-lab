package cn.interview;

import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IsolationTest {
    @Test void knownBaseIdDoesNotBypassOwnerCheck() {
        JdbcTemplate db=mock(JdbcTemplate.class);UUID otherBase=UUID.randomUUID(),owner=UUID.randomUUID();
        when(db.queryForObject("SELECT count(*) FROM knowledge_base WHERE id=? AND owner_id=?",Integer.class,otherBase,owner)).thenReturn(0);
        var e=assertThrows(ResponseStatusException.class,()->new Api(db,mock(Jobs.class)).requireBase(owner,otherBase));
        assertEquals(404,e.getStatusCode().value());
    }
    @Test void historyRejectsForeignSessionBeforeReadingMessages() {
        JdbcTemplate db=mock(JdbcTemplate.class);UUID otherSession=UUID.randomUUID(),owner=UUID.randomUUID();
        MockHttpServletRequest request=new MockHttpServletRequest();request.setAttribute("owner",owner);
        when(db.queryForList("SELECT id,direction,level,status,turn,report FROM interview WHERE id=? AND owner_id=?",otherSession,owner)).thenReturn(List.of());
        var e=assertThrows(ResponseStatusException.class,()->new Api(db,mock(Jobs.class)).interview(request,otherSession));
        assertEquals(404,e.getStatusCode().value());
        verify(db,never()).queryForList("SELECT role,content FROM message WHERE session_id=? ORDER BY id",otherSession);
    }
}
