package com.example.demo.controller;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "订单", description = "下单（扣库存）、查询、取消")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "下单", description = "校验并扣减库存，任一图书不足则整单回滚")
    public OrderResponse placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        return OrderResponse.from(orderService.placeOrder(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情")
    public OrderResponse get(@PathVariable Long id) {
        return OrderResponse.from(orderService.getOrder(id));
    }

    @GetMapping
    @Operation(summary = "查某用户的订单列表")
    public List<OrderResponse> listByUser(@RequestParam Long userId) {
        return orderService.getOrdersByUser(userId).stream().map(OrderResponse::from).toList();
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消订单", description = "仅 CREATED 可取消，取消时恢复库存")
    public OrderResponse cancel(@PathVariable Long id) {
        return OrderResponse.from(orderService.cancelOrder(id));
    }

}
