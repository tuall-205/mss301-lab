# Xây dựng Order Service
> **Database:** MySQL | **Port:** 8081 | **Migration:** Flyway | **Artifact:** `order-service`

---

## 0. Checklist tổng quan

- [ ] Tạo project Spring Boot tại start.spring.io
- [ ] Cài MySQL bằng Docker Compose (dùng chung container với inventory-service)
- [ ] Tạo `init.sql` để tạo database `order_service`
- [ ] Cấu hình `application.properties`
- [ ] Viết Flyway migration `V1__init.sql`
- [ ] Tạo Model `Order`
- [ ] Tạo Repository `OrderRepository`
- [ ] Tạo DTO `OrderRequest`
- [ ] Tạo Service `OrderService`
- [ ] Tạo Controller `OrderController`
- [ ] Test thủ công bằng Postman
- [ ] Viết Integration Test (TestContainers + RestAssured)
- [ ] Chạy `mvn test` thành công

---

## Bước 1 – Tạo project tại start.spring.io

Cấu hình:
- **Group:** `com.fudn` 
- **Artifact:** `order-service`
- **Java:** 21, **Maven**

Dependencies:
- `Spring Web`
- `Lombok`
- `Spring Data JPA`
- `MySQL Driver`
- `Flyway Migration`
- `Testcontainers`

### TODO
- [ ] Generate project với Group = `com.fudn`, Artifact = `order-service`
- [ ] Mở project trong IDE
- [ ] `mvn clean verify` chạy không lỗi

---

## Bước 2 – Cài MySQL bằng Docker Compose

Thêm service MySQL vào `docker-compose.yml` đặt tại thư mục gốc order-service:

```yaml
version: '4'
services:
  mysql:
    image: mysql:8.3.0
    container_name: mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: mysql
    volumes:
      - ./mysql/init.sql:/docker-entrypoint-initdb.d/init.sql
      - ./docker/mysql/data:/var/lib/mysql
```

Tạo thư mục mysql tại thư mục gốc order-service, thêm mới file `init.sql` có nội dung sau:

```sql
CREATE DATABASE IF NOT EXISTS order_service;
```

> ⚠️ Lưu ý: Khi làm tới Inventory Service, file `init.sql` này sẽ cần cập nhật thêm dòng tạo `inventory_service` 
Khởi động:
```bash
docker compose up -d mysql

Kết quả: Container mysql started là ok

⚠️ Container MySQL phải đang chạy trước thì Test Connection mới thành công. Nếu chưa chạy, mở terminal tại thư mục order-service:
docker compose up -d mysql

Cấu hình kết nối database connection trong Intellij: 
Các bước điền
Driver: đang là "MySQL – Not downloaded" → bấm link Download bên phải để IntelliJ tải driver MySQL về (chỉ cần làm 1 lần).
Host: localhost (giữ nguyên, đã đúng)
Port: 3306 (giữ nguyên, đã đúng — đúng port map trong docker-compose.yml)
Authentication: giữ User & Password
User: gõ root
Password: gõ mysql
Save: để Forever cho khỏi phải nhập lại
Database: gõ order_service
(để trống cũng được, nhưng gõ vào sẽ giúp IntelliJ chỉ hiện schema này thay vì tất cả)
Kiểm tra ô URL phía dưới tự động cập nhật thành:
   jdbc:mysql://localhost:3306/order_service

→ khớp với spring.datasource.url trong application.properties.

Bấm Test Connection (góc dưới trái) để kiểm tra kết nối OK.
Bấm Apply → OK.

Kết quả: Successed
```

### TODO
- [ ] Tạo `docker-compose.yml` với service `mysql`
- [ ] Tạo `mysql/init.sql` với `CREATE DATABASE IF NOT EXISTS order_service;`
- [ ] `docker compose up -d mysql` chạy thành công
- [ ] Kiểm tra container: `docker ps`

---

## Bước 3 – Cấu hình `application.properties`

```properties
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost:3306/order_service
spring.datasource.username=root
spring.datasource.password=mysql

# Tắt auto-create schema vì dùng Flyway
spring.jpa.hibernate.ddl-auto=none

# Port riêng vì 8080 đã dùng cho product-service
server.port=8081
```

### TODO
- [ ] Thêm cấu hình datasource
- [ ] `spring.jpa.hibernate.ddl-auto=none`
- [ ] `server.port=8081`

---

## Bước 4 – Database Migration với Flyway

Quy tắc đặt tên: `V{số}__{mô tả}.sql`, đặt tại `src/main/resources/db/migration/`.

`V1__init.sql`:

```sql
CREATE TABLE `t_orders`
(
    `id`           bigint(20)    NOT NULL AUTO_INCREMENT,
    `order_number` varchar(255)  DEFAULT NULL,
    `sku_code`     varchar(255),
    `price`        decimal(19,2),
    `quantity`     int(11),
    PRIMARY KEY (`id`)
);
```

### TODO
- [ ] Tạo thư mục `src/main/resources/db/migration/`
- [ ] Tạo file `V1__init.sql` với bảng `t_orders`
- [ ] Khởi động app, xác nhận log Flyway apply thành công

---

## Bước 5 – Tạo Model `Order.java`

`src/main/java/com/fudn/orderservice/model/Order.java`:

```java
package com.fudn.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "t_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderNumber;
    private String skuCode;
    private BigDecimal price;
    private Integer quantity;
}
```

### TODO
- [ ] Tạo package `com.fudn.orderservice.model`
- [ ] Tạo entity `Order` ánh xạ bảng `t_orders`

---

## Bước 6 – Tạo Repository `OrderRepository.java`

`src/main/java/com/fudn/orderservice/repository/OrderRepository.java`:

```java
package com.fudn.orderservice.repository;

import com.fudn.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
```

### TODO
- [ ] Tạo interface `OrderRepository extends JpaRepository<Order, Long>`

---

## Bước 7 – Tạo DTO `OrderRequest.java`

`src/main/java/com/fudn/orderservice/dto/OrderRequest.java`:

```java
package com.fudn.orderservice.dto;

import java.math.BigDecimal;

public record OrderRequest(Long id, String skuCode, BigDecimal price, Integer quantity) {
}
```

### TODO
- [ ] Tạo record `OrderRequest`

---

## Bước 8 – Tạo Service `OrderService.java`

`src/main/java/com/fudn/orderservice/service/OrderService.java`:

```java
package com.fudn.orderservice.service;

import com.fudn.orderservice.dto.OrderRequest;
import com.fudn.orderservice.model.Order;
import com.fudn.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    public void placeOrder(OrderRequest orderRequest) {
        var order = mapToOrder(orderRequest);
        orderRepository.save(order);
    }

    private static Order mapToOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setPrice(orderRequest.price());
        order.setQuantity(orderRequest.quantity());
        order.setSkuCode(orderRequest.skuCode());
        return order;
    }
}
```

### TODO
- [ ] Tạo class `OrderService` (`placeOrder`)
- [ ] Kiểm tra `orderNumber` sinh ngẫu nhiên bằng `UUID.randomUUID()`

---

## Bước 9 – Tạo Controller `OrderController.java`

`src/main/java/com/fudn/orderservice/controller/OrderController.java`:

```java
package com.fudn.orderservice.controller;

import com.fudn.orderservice.dto.OrderRequest;
import com.fudn.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String placeOrder(@RequestBody OrderRequest orderRequest) {
        orderService.placeOrder(orderRequest);
        return "Order Placed Successfully";
    }
}
```

### TODO
- [ ] Tạo class `OrderController`
- [ ] Endpoint `POST /api/order` → 201, trả về text "Order Placed Successfully"

---

## Bước 10 – Kiểm tra bằng Postman

```
POST http://localhost:8081/api/order
Content-Type: application/json

{
  "skuCode": "iphone_15",
  "price": 1000,
  "quantity": 1
}
```
→ Kỳ vọng: **201**, body: `"Order Placed Successfully"`

### TODO / Checklist Postman
- [ ] `mvn spring-boot:run` chạy service thành công trên port 8081
- [ ] POST `/api/order` trả về 201 và đúng message
- [ ] Kiểm tra dữ liệu đã lưu vào bảng `t_orders` (MySQL client / DBeaver)

---

## Bước 11 – Viết Integration Test

`src/test/java/com/fudn/orderservice/OrderServiceApplicationTests.java`:

```java
package com.fudn.orderservice;

import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

import static org.hamcrest.MatcherAssert.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderServiceApplicationTests {

    @ServiceConnection
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.3.0");

    @LocalServerPort
    private Integer port;

    @BeforeEach
    void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    static {
        mySQLContainer.start();
    }

    @Test
    void shouldSubmitOrder() {
        String submitOrderJson = """
                {
                     "skuCode": "iphone_15",
                     "price": 1000,
                     "quantity": 1
                }
                """;

        var responseBodyString = RestAssured.given()
                .contentType("application/json")
                .body(submitOrderJson)
                .when()
                .post("/api/order")
                .then()
                .log().all()
                .statusCode(201)
                .extract()
                .body().asString();

        assertThat(responseBodyString, Matchers.is("Order Placed Successfully"));
    }
}
```

Chạy test:
```bash
mvn test
```

### TODO
- [ ] Tạo `OrderServiceApplicationTests` (package `com.fudn.orderservice`)
- [ ] Docker Desktop đang chạy (Testcontainers cần Docker)
- [ ] `mvn test` PASS

---

## ✅ Checklist hoàn thành Order Service

- [ ] Cấu trúc thư mục đúng package `com.fudn.orderservice.*`
- [ ] `docker compose up -d mysql` chạy ổn định, database `order_service` đã tồn tại
- [ ] `application.properties` trỏ đúng datasource, port 8081
- [ ] Flyway migration `V1__init.sql` chạy thành công (log "Successfully applied")
- [ ] `Order`, `OrderRepository`, `OrderRequest`, `OrderService`, `OrderController` đã tạo đủ
- [ ] Postman test POST `/api/order` đúng expected status code + message
- [ ] `mvn test` chạy Integration Test PASS

---

## Ghi chú thay đổi package

| Class | Package gốc | Package mới |
|---|---|---|
| Order | `com.programmingtechie.orderservice.model` | `com.fudn.orderservice.model` |
| OrderRepository | `com.programmingtechie.orderservice.repository` | `com.fudn.orderservice.repository` |
| OrderRequest | `com.programmingtechie.orderservice.dto` | `com.fudn.orderservice.dto` |
| OrderService | `com.programmingtechie.orderservice.service` | `com.fudn.orderservice.service` |
| OrderController | `com.programmingtechie.orderservice.controller` | `com.fudn.orderservice.controller` |
| Test class | `com.programmingtechie.orderservice` | `com.fudn.orderservice` |

> **Lưu ý:** Đặt Group = `com.fudn` ngay từ khi generate project tại start.spring.io để thư mục package tự sinh đúng, tránh phải đổi thủ công.
