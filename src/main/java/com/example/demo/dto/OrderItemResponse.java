package com.example.demo.dto;

import com.example.demo.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class OrderItemResponse {

    private Long bookId;
    private String title;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal subtotal;

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getBook().getId(),
                item.getBook().getTitle(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }

}
