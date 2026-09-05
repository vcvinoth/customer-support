package com.customersupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test against the real /api/chat endpoint: real tool calling, real RAG
 * retrieval, and real conversation memory, all together. Uses Testcontainers for
 * Postgres/pgvector but calls the real local Ollama, so it needs Ollama running with
 * llama3.2 and nomic-embed-text pulled - same requirement as running the app.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ChatIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Long orderId;

    @BeforeEach
    void seedOrder() {
        Customer customer = new Customer();
        customer.setName("Integration Test Customer");
        customer.setEmail("integration-" + UUID.randomUUID() + "@example.com");
        customer = customerRepository.save(customer);

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus("SHIPPED");
        order.setTotalAmount(new BigDecimal("77.50"));
        order = orderRepository.save(order);
        orderId = order.getId();
    }

    @Test
    void toolCallingReturnsRealOrderData() {
        ChatController.Output output = chat("What is the status of order " + orderId + "?", "it-tools");

        assertThat(output.content()).containsIgnoringCase("shipped");
        assertThat(output.content()).contains("77.50");
    }

    @Test
    void ragReturnsRetrievedPolicyFacts() {
        ChatController.Output output = chat("How many days do I have to return an item?", "it-rag");

        assertThat(output.content()).contains("30");
    }

    @Test
    void conversationMemoryCarriesContextAcrossTurns() {
        String conversationId = "it-memory";
        chat("What is the status of order " + orderId + "?", conversationId);

        ChatController.Output followUp = chat("What was its total amount again?", conversationId);

        assertThat(followUp.content()).contains("77.50");
    }

    private ChatController.Output chat(String prompt, String conversationId) {
        ResponseEntity<ChatController.Output> response = restTemplate.postForEntity(
                "/api/chat",
                new ChatController.Input(prompt, conversationId),
                ChatController.Output.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }
}
