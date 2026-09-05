package com.customersupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Component
class CustomerSupportTools {

    private static final Logger log = LoggerFactory.getLogger(CustomerSupportTools.class);
    private static final String REFUNDED_STATUS = "REFUNDED";
    private static final int RETURN_WINDOW_DAYS = 30;

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    CustomerSupportTools(CustomerRepository customerRepository, OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    @Tool(description = "Look up the status and details of an order by its order id")
    String getOrderStatus(@ToolParam(description = "The order id") Long orderId) {
        return orderRepository.findById(orderId)
                .map(order -> "Order %d: status=%s, total=%s, placed on %s"
                        .formatted(order.getId(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt()))
                .orElse("No order found with id " + orderId);
    }

    @Tool(description = "List all orders placed by a customer, identified by their email address")
    String getOrdersForCustomer(@ToolParam(description = "The customer's email address") String email) {
        return customerRepository.findByEmail(email)
                .map(this::formatOrdersForCustomer)
                .orElse("No customer found with email " + email);
    }

    @Tool(description = "List all orders placed by a customer, identified by their customer id")
    String getOrdersForCustomerId(@ToolParam(description = "The customer id") Long customerId) {
        return customerRepository.findById(customerId)
                .map(this::formatOrdersForCustomer)
                .orElse("No customer found with id " + customerId);
    }

    @Tool(description = "Issue a refund for an order, if it is within the 30-day return window and not already refunded")
    @Transactional
    String issueRefund(@ToolParam(description = "The order id to refund") Long orderId,
                        @ToolParam(description = "The reason for the refund") String reason) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (REFUNDED_STATUS.equals(order.getStatus())) {
                        return "Order " + orderId + " has already been refunded.";
                    }
                    long daysSinceOrder = ChronoUnit.DAYS.between(order.getCreatedAt(), LocalDateTime.now());
                    if (daysSinceOrder > RETURN_WINDOW_DAYS) {
                        return "Order %d was placed %d days ago, which is outside the %d-day return window, so it is not eligible for a refund."
                                .formatted(orderId, daysSinceOrder, RETURN_WINDOW_DAYS);
                    }
                    String previousStatus = order.getStatus();
                    order.setStatus(REFUNDED_STATUS);
                    orderRepository.save(order);
                    log.info("Refunded order {} (was {}): {}", orderId, previousStatus, reason);
                    return "Order %d has been refunded (total %s). Reason: %s"
                            .formatted(orderId, order.getTotalAmount(), reason);
                })
                .orElse("No order found with id " + orderId);
    }

    @Tool(description = "Escalate the current conversation to a human support agent when the request cannot be resolved automatically")
    String escalateToHuman(@ToolParam(description = "A brief reason the conversation needs human attention") String reason) {
        String ticketId = "ESC-" + System.currentTimeMillis();
        log.info("Escalation created [{}]: {}", ticketId, reason);
        return "This conversation has been escalated to a human support agent. Reference: " + ticketId
                + ". They will follow up as soon as possible.";
    }

    private String formatOrdersForCustomer(Customer customer) {
        List<Order> orders = orderRepository.findByCustomerId(customer.getId());
        if (orders.isEmpty()) {
            return "Customer " + customer.getName() + " has no orders.";
        }
        return orders.stream()
                .map(order -> "Order %d: status=%s, total=%s"
                        .formatted(order.getId(), order.getStatus(), order.getTotalAmount()))
                .collect(Collectors.joining("; "));
    }
}
