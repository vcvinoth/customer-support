package com.customersupport;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Answers.RETURNS_SELF;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(ChatControllerTest.StubChatClientConfig.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void chatReturnsModelResponse() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"prompt\":\"Hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello back!"));
    }

    @Test
    void chatRejectsBlankPrompt() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"prompt\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class StubChatClientConfig {

        @Bean
        ChatClient.Builder chatClientBuilder() {
            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
            ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("Hello back!");

            ChatClient chatClient = mock(ChatClient.class);
            when(chatClient.prompt(anyString())).thenReturn(requestSpec);

            ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_SELF);
            when(builder.build()).thenReturn(chatClient);
            return builder;
        }

        @Bean
        CustomerSupportTools customerSupportTools() {
            return mock(CustomerSupportTools.class);
        }

        @Bean
        VectorStore vectorStore() {
            return mock(VectorStore.class);
        }

        @Bean
        ChatMemory chatMemory() {
            return mock(ChatMemory.class);
        }
    }
}
