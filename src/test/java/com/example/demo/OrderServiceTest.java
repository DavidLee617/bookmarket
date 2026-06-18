package com.example.demo;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.OrderItemRequest;
import com.example.demo.entity.Book;
import com.example.demo.entity.Order;
import com.example.demo.entity.User;
import com.example.demo.enums.OrderStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;

    private User newUser() {
        return userRepository.save(new User(null, "Tester", "tester@example.com"));
    }

    private Book newBook(String title, double price, int stock) {
        Book book = new Book();
        book.setTitle(title);
        book.setPrice(BigDecimal.valueOf(price));
        book.setStock(stock);
        return bookRepository.save(book);
    }

    private CreateOrderRequest orderReq(Long userId, Long bookId, int qty) {
        OrderItemRequest item = new OrderItemRequest();
        item.setBookId(bookId);
        item.setQuantity(qty);
        CreateOrderRequest req = new CreateOrderRequest();
        req.setUserId(userId);
        req.setItems(List.of(item));
        return req;
    }

    @Test
    void placeOrder_deductsStock_andComputesTotal() {
        User user = newUser();
        Book book = newBook("下单测试书", 30.00, 10);

        Order order = orderService.placeOrder(orderReq(user.getId(), book.getId(), 3));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("90.00");
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo("30.00");
        // 库存已扣减并落库
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(7);
    }

    @Test
    void placeOrder_insufficientStock_rollsBackEntireOrder() {
        User user = newUser();
        Book enough = newBook("库存充足书", 20.00, 10);
        Book scarce = newBook("库存不足书", 50.00, 1);

        OrderItemRequest i1 = new OrderItemRequest();
        i1.setBookId(enough.getId());
        i1.setQuantity(2);
        OrderItemRequest i2 = new OrderItemRequest();
        i2.setBookId(scarce.getId());
        i2.setQuantity(5);
        CreateOrderRequest req = new CreateOrderRequest();
        req.setUserId(user.getId());
        req.setItems(List.of(i1, i2));

        long ordersBefore = orderRepository.count();

        assertThatThrownBy(() -> orderService.placeOrder(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足");

        // 整单回滚：两本书库存都不变，且没有订单落库
        assertThat(bookRepository.findById(enough.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(bookRepository.findById(scarce.getId()).orElseThrow().getStock()).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    void cancelOrder_restoresStock_andSetsCancelled() {
        User user = newUser();
        Book book = newBook("可取消书", 40.00, 8);

        Order order = orderService.placeOrder(orderReq(user.getId(), book.getId(), 5));
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(3);

        Order cancelled = orderService.cancelOrder(order.getId());

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(8);
    }

    @Test
    void cancelOrder_alreadyCancelled_throwsConflict() {
        User user = newUser();
        Book book = newBook("重复取消书", 10.00, 5);
        Order order = orderService.placeOrder(orderReq(user.getId(), book.getId(), 1));
        orderService.cancelOrder(order.getId());

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可取消");
    }

}
