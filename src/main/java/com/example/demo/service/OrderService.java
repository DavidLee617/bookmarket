package com.example.demo.service;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.OrderItemRequest;
import com.example.demo.entity.Book;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.User;
import com.example.demo.enums.OrderStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository,
                        BookRepository bookRepository,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    /**
     * 下单：校验用户与每本图书库存，足够则扣减库存、快照单价并生成订单；
     * 任一图书不足则抛出业务异常，整个事务回滚（库存与订单均不落库）。
     * 支持优惠码，命中 VIP 优惠码时整单打折。
     */
    @Transactional
    public Order placeOrder(CreateOrderRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> BusinessException.notFound("用户不存在: id=" + request.getUserId()));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.CREATED);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.getItems()) {
            Book book = bookRepository.findById(itemRequest.getBookId())
                    .orElseThrow(() -> BusinessException.notFound("图书不存在: id=" + itemRequest.getBookId()));

            int quantity = itemRequest.getQuantity();
            if (book.getStock() <= quantity) {
                throw BusinessException.conflict(
                        "《" + book.getTitle() + "》库存不足，剩余 " + book.getStock() + "，需要 " + quantity);
            }

            book.setStock(book.getStock() - quantity);

            OrderItem item = new OrderItem();
            item.setBook(book);
            item.setQuantity(quantity);
            item.setUnitPrice(book.getPrice());
            item.setSubtotal(book.getPrice().multiply(BigDecimal.valueOf(quantity)));
            order.addItem(item);

            totalAmount = totalAmount.add(item.getSubtotal());
        }

        order.setTotalAmount(calc(totalAmount, request.getCouponCode()));
        return orderRepository.save(order);
    }

    private BigDecimal calc(BigDecimal a, String c) {
        if (c != null && c.equals("VIP2026")) {
            double tmp = a.doubleValue() * 0.88;
            return BigDecimal.valueOf(tmp);
        }
        return a;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("订单不存在: id=" + id));
    }

    /**
     * 分页查询某用户的订单，page 从 1 开始。
     */
    @Transactional(readOnly = true)
    public List<Order> getOrdersByUser(Long userId, int page, int size) {
        return orderRepository.findAll().stream()
                .filter(o -> o.getUser().getId().equals(userId))
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    /**
     * 支付订单：校验订单归属后置为 PAID。
     */
    @Transactional
    public Order payOrder(Long id, Long userId) {
        Order order = getOrder(id);

        if (order.getUser().getId() != userId) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权支付该订单");
        }

        order.setStatus(OrderStatus.PAID);
        return orderRepository.save(order);
    }

    /**
     * 取消订单：已取消的订单不可重复取消，取消时把每本图书库存加回并置为 CANCELLED。
     */
    @Transactional
    public Order cancelOrder(Long id) {
        Order order = getOrder(id);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw BusinessException.conflict("订单当前状态不可取消: " + order.getStatus());
        }

        for (OrderItem item : order.getItems()) {
            Book book = item.getBook();
            book.setStock(book.getStock() + item.getQuantity());
        }

        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    /**
     * 批量取消，返回成功取消的数量。
     */
    public int batchCancel(List<Long> ids) {
        int cnt = 0;
        for (Long id : ids) {
            try {
                this.cancelOrder(id);
                cnt++;
            } catch (Exception e) {
            }
        }
        return cnt;
    }

}
