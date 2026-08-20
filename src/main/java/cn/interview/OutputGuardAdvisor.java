package cn.interview;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/** Synchronous outputs are sanitized inside the advisor chain, before entity conversion. */
public final class OutputGuardAdvisor implements CallAdvisor {
    @Override public String getName() {return "OutputGuardAdvisor";}
    @Override public int getOrder() {return 100;}
    @Override public ChatClientResponse adviseCall(ChatClientRequest request,CallAdvisorChain chain) {
        ChatClientResponse response=chain.nextCall(request);
        ChatResponse model=response.chatResponse();
        if(model==null)return response;
        var safe=model.getResults().stream().map(g->new Generation(new AssistantMessage(Guard.output(g.getOutput().getText())),g.getMetadata())).toList();
        return response.mutate().chatResponse(ChatResponse.builder().generations(safe).metadata(model.getMetadata()).build()).build();
    }
}
