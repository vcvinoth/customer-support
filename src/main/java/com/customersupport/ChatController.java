package com.customersupport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class ChatController {

    private static final String SYSTEM_PROMPT = """
            You are a helpful customer support assistant for our company.
            Be concise, polite, and professional. General policy questions
            (e.g. "what is your return policy", "how long does shipping
            take") do not require an order id or customer email - answer
            them directly from the retrieved policy documents. Only ask for
            an order id or email when the customer is asking about a
            SPECIFIC order's status, or wants an action taken (like a
            refund) on a specific order. Use the available tools to look up
            real order and customer information when asked. If a customer
            requests a refund and it looks eligible, use the refund tool
            rather than just describing the policy; the tool itself
            enforces eligibility, so trust its result. If a request is
            something you cannot resolve yourself, escalate it to a human
            agent rather than guessing. Never make up order statuses,
            totals, customer details, or policy information. If you don't
            know the answer, say so rather than guessing.
            """;

    private final ChatClient chatClient;

    ChatController(ChatClient.Builder builder, CustomerSupportTools customerSupportTools,
                    VectorStore vectorStore, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(customerSupportTools)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @PostMapping("/api/chat")
    Output chat(@RequestBody @Valid Input input) {
        String conversationId = input.conversationId() == null || input.conversationId().isBlank()
                ? ChatMemory.DEFAULT_CONVERSATION_ID
                : input.conversationId();
        String response = chatClient.prompt(input.prompt())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call().content();
        return new Output(response);
    }

    record Input(@NotBlank String prompt, String conversationId) {}
    record Output(String content) {}
}
