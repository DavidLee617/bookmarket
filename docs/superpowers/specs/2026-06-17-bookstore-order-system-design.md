# 书铺订单系统 设计文档

- 日期：2026-06-17
- 范围：在现有 Spring Boot 工程（Boot 3.5.15 / Java 26 / H2 内存库 / 已有 `User`）上，新增「书铺订单系统」最小可用版本（MVP）。
- 状态：已与用户确认，待实现。

## 1. 背景与目标

现有工程是一个可运行的 Spring Boot 脚手架，含 `User` 实体及示例 CRUD。本设计在其上增加书铺（书店）的订单能力：管理图书、下单（下单即扣库存）、查询订单、取消订单。

目标是一个**强一致、原子下单**的 MVP：下单时立即校验并扣减库存，任一图书库存不足则整单失败回滚；取消订单时把库存加回。复用现有 `User` 作为下单人，不引入支付/购物车/物流等超出 MVP 的环节。

## 2. 数据模型

复用现有 `User`，新增三张表。金额统一用 `BigDecimal`，列定义 `precision = 10, scale = 2`。

### Book（图书）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| title | String | 书名，非空 |
| author | String | 作者 |
| isbn | String | ISBN |
| price | BigDecimal(10,2) | 单价，非空，≥0 |
| stock | int | 库存，非空，≥0 |
| version | Long | `@Version` 乐观锁，防并发超卖 |

### Order（订单）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| user | User | `@ManyToOne`，下单人，非空 |
| status | OrderStatus | 枚举，`@Enumerated(STRING)` |
| totalAmount | BigDecimal(10,2) | 订单总额（各明细 subtotal 之和） |
| createdAt | LocalDateTime | 创建时间，`@PrePersist` 自动赋值 |
| items | List\<OrderItem\> | `@OneToMany(cascade = ALL, orphanRemoval = true)` |

### OrderItem（订单明细）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| order | Order | `@ManyToOne`，所属订单 |
| book | Book | `@ManyToOne`，所购图书 |
| quantity | int | 数量，>0 |
| unitPrice | BigDecimal(10,2) | **下单时单价快照**，与 Book.price 解耦 |
| subtotal | BigDecimal(10,2) | unitPrice × quantity |

### OrderStatus（枚举）
- `CREATED`：已下单（已扣库存）
- `CANCELLED`：已取消（已恢复库存）

## 3. 接口设计

### 图书 `/api/books`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/books | 新增图书 |
| GET | /api/books | 图书列表 |
| GET | /api/books/{id} | 图书详情 |
| PUT | /api/books/{id} | 更新图书（含库存） |
| DELETE | /api/books/{id} | 删除图书 |

### 订单 `/api/orders`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/orders | 下单 |
| GET | /api/orders/{id} | 订单详情（含明细） |
| GET | /api/orders?userId={id} | 查某用户订单列表 |
| POST | /api/orders/{id}/cancel | 取消订单 |

**下单请求体（CreateOrderRequest）：**
```json
{
  "userId": 1,
  "items": [
    { "bookId": 1, "quantity": 2 },
    { "bookId": 3, "quantity": 1 }
  ]
}
```

**订单响应（OrderResponse）：**
```json
{
  "id": 1,
  "userId": 1,
  "status": "CREATED",
  "totalAmount": 99.80,
  "createdAt": "2026-06-17T10:00:00",
  "items": [
    { "bookId": 1, "title": "...", "unitPrice": 39.90, "quantity": 2, "subtotal": 79.80 }
  ]
}
```

## 4. 核心业务逻辑（OrderService，方法均 `@Transactional`）

### 下单 placeOrder(CreateOrderRequest)
1. 校验 userId 对应用户存在（否则 404）。
2. 请求 items 不能为空（否则 400）。
3. 逐条明细：加载 Book（不存在→404）；校验 `book.stock >= quantity`（不足→409 业务异常，带书名与剩余库存）。
4. 扣减库存 `book.stock -= quantity`；快照 `unitPrice = book.price`；`subtotal = unitPrice × quantity`；累加 totalAmount。
5. 构建 Order（status=CREATED）+ 级联保存 OrderItem，返回 OrderResponse。
6. 任一步抛异常 → 事务整体回滚，库存与订单均不落库。
7. `Book` 的 `@Version` 提供乐观锁；并发更新冲突时抛 `OptimisticLockingFailureException`，由全局处理器转 409（MVP 不做自动重试，提示重试即可）。

### 取消 cancelOrder(orderId)
1. 加载订单（不存在→404）。
2. 仅 `CREATED` 可取消，否则→409（如已取消）。
3. 逐条明细把库存加回 `book.stock += quantity`。
4. status 置 `CANCELLED`，保存，返回更新后的 OrderResponse。

## 5. 出入参与错误处理

- 全程使用 **DTO** 收发，不直接暴露 JPA 实体（规避懒加载序列化问题、隐藏内部字段）。
  - 请求：`CreateOrderRequest`、`OrderItemRequest`、`BookRequest`
  - 响应：`OrderResponse`、`OrderItemResponse`、`BookResponse`
- `@Valid` + jakarta validation：`quantity > 0`（`@Min(1)`）、`userId @NotNull`、`title @NotBlank`、`price @PositiveOrZero`、`stock @PositiveOrZero`、items `@NotEmpty`。
- 自定义 `BusinessException`（携带 HTTP 状态与消息）。
- 全局 `@RestControllerAdvice` `GlobalExceptionHandler`，统一返回：
  ```json
  { "timestamp": "...", "status": 409, "message": "《xxx》库存不足，剩余 0，需要 2" }
  ```
  映射：业务异常→其自带状态（库存不足/不可取消→409，资源不存在→404）；参数校验失败→400。

## 6. 代码结构

沿用现有 `com.example.demo` 包风格，新增子包：
```
com.example.demo
├── entity/        Book, Order, OrderItem   (User 已存在)
├── enums/         OrderStatus
├── repository/    BookRepository, OrderRepository
├── service/       BookService, OrderService
├── controller/    BookController, OrderController
├── dto/           CreateOrderRequest, OrderItemRequest, OrderResponse,
│                  OrderItemResponse, BookRequest, BookResponse
└── exception/     BusinessException, GlobalExceptionHandler
```
- 实体继续用 Lombok（`@Data`/`@NoArgsConstructor`/`@AllArgsConstructor`），注解处理器路径已在 pom 配好。
- `OrderItem` 不单独建 Repository，靠 Order 级联读写。

## 7. 数据初始化

扩展 `src/main/resources/data.sql`：保留现有用户，新增若干图书（含 price、stock），使工程启动后即可直接下单测试。注意 H2 表名与列名（`books`、`orders`、`order_items`、`users`）。

## 8. 测试与验证

**自动化测试（OrderService 集成测试，`@SpringBootTest` 或 `@DataJpaTest` + service）：**
- 正常下单：库存按数量扣减，totalAmount 正确，明细 unitPrice 为快照值。
- 库存不足：整单抛业务异常，库存与订单均回滚（断言库存未变、无订单落库）。
- 取消订单：库存恢复，状态变 CANCELLED；重复取消报 409。

**手工端到端验证（按上次方式实跑）：**
1. `./mvnw clean package`（JAVA_HOME 指向 JDK 26）确认 BUILD SUCCESS。
2. 启动 jar，依次 curl：建书 → 下单 → 查订单 → 库存减少 → 取消 → 库存恢复 → 库存不足下单返回 409。
3. `./mvnw test` 全绿。

## 9. 明确不做（YAGNI）

支付流程、购物车、订单状态机（发货/完成等）、用户鉴权、分页/搜索、价格促销。后续如需再各自立项。
