package cn.interview;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutputGuardAdvisorTest {
    @Test void filtersModelResponseBeforeReturningToCaller() {
        CallAdvisorChain chain=mock(CallAdvisorChain.class);
        when(chain.nextCall(null)).thenReturn(new ChatClientResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("联系 test@example.com")))),Map.of()));
        var result=new OutputGuardAdvisor().adviseCall(null,chain);
        assertEquals("联系 [已脱敏]",result.chatResponse().getResult().getOutput().getText());
    }
}
