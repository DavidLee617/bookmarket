package com.example.demo.controller;

import com.example.demo.dto.BatchCancelRequest;
import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.dto.PayOrderRequest;
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
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "订单", description = "下单（扣库存）、查询、支付、取消")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "下单", description = "校验并扣减库存，任一图书不足则整单回滚；可选优惠码")
    public OrderResponse placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        return OrderResponse.from(orderService.placeOrder(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情")
    public OrderResponse get(@PathVariable Long id) {
        return OrderResponse.from(orderService.getOrder(id));
    }

    @GetMapping
    @Operation(summary = "分页查某用户的订单列表", description = "page 从 1 开始")
    public List<OrderResponse> listByUser(@RequestParam Long userId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return orderService.getOrdersByUser(userId, page, size).stream().map(OrderResponse::from).toList();
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "支付订单")
    public OrderResponse pay(@PathVariable Long id, @Valid @RequestBody PayOrderRequest request) {
        return OrderResponse.from(orderService.payOrder(id, request.getUserId()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消订单", description = "未取消的订单可取消，取消时恢复库存")
    public OrderResponse cancel(@PathVariable Long id) {
        return OrderResponse.from(orderService.cancelOrder(id));
    }

    @PostMapping("/batch-cancel")
    @Operation(summary = "批量取消订单")
    public Map<String, Integer> batchCancel(@Valid @RequestBody BatchCancelRequest request) {
        return Map.of("cancelled", orderService.batchCancel(request.getOrderIds()));
    }

}
