# Part 3 & Part 4 – OpenFeign · WireMock · API Gateway · Keycloak/OAuth2

> **Series:** Spring Boot Microservices Tutorial – MSS301
> **Code nền:** thư mục `Part1/` (product-service, order-service, inventory-service – package `com.fudn.*`)
> **Phiên bản áp dụng:** **Spring Boot 4.1.0** · **Spring Cloud 2025.1.3 (Oakwood)** · Java **21** – xem mục 0.3 và 0.4
> **File test đi kèm:** `part3-4_Test.md`

---

## Mục lục
- [PHẦN 3 – OpenFeign, WireMock, API Gateway](#phần-3--openfeign-wiremock-api-gateway)
  - [3.A Lý thuyết chính](#3a-lý-thuyết-chính)
  - [3.B Danh sách yêu cầu TODO](#3b-danh-sách-yêu-cầu-todo)
  - [3.C Hướng dẫn step-by-step + code đầy đủ](#3c-hướng-dẫn-step-by-step--code-đầy-đủ)
- [PHẦN 4 – Bảo mật API Gateway với Keycloak & OAuth2](#phần-4--bảo-mật-api-gateway-với-keycloak--oauth2)
  - [4.A Lý thuyết chính](#4a-lý-thuyết-chính)
  - [4.B Danh sách yêu cầu TODO](#4b-danh-sách-yêu-cầu-todo)
  - [4.C Hướng dẫn step-by-step + code đầy đủ](#4c-hướng-dẫn-step-by-step--code-đầy-đủ)
- [5. Cấu trúc thư mục cuối cùng](#5-cấu-trúc-thư-mục-cuối-cùng)
- [6. Lỗi thường gặp & cách xử lý](#6-lỗi-thường-gặp--cách-xử-lý)
- [7. Câu hỏi ôn tập](#7-câu-hỏi-ôn-tập)

---

## Mục tiêu

| Phần | Bài toán | Giải pháp |
|---|---|---|
| **3A** | Order Service cần biết hàng còn trong kho không trước khi lưu đơn | **OpenFeign** gọi đồng bộ (HTTP) sang Inventory Service |
| **3B** | Integration test của Order Service bị hỏng vì không có Inventory Service thật | **WireMock** giả lập Inventory Service trong test |
| **3C** | Client phải nhớ 3 địa chỉ (8080, 8081, 8082) | **API Gateway** (Spring Cloud Gateway Server Web MVC) – 1 cổng duy nhất `9000` |
| **4** | Ai cũng gọi được API qua Gateway | **Keycloak** cấp JWT (Client Credentials), **Gateway = OAuth2 Resource Server** kiểm tra JWT |

## 0.2 Kiến trúc sau khi hoàn thành

```
                 (1) POST /token  client_id + client_secret
   +---------+  ------------------------------------------->  +----------------------+
   | Client  |                                                 |  Keycloak  (:8181)   |
   | Postman |  <-------------------------------------------   |  realm:              |
   +---------+           (2) access_token (JWT)                |  spring-microservices|
        |                                                      |  -realm              |
        | (3) GET/POST /api/...  Authorization: Bearer <JWT>   +----------+-----------+
        v                                                                 ^
   +--------------------------------------------------+                   | (4) tải JWKS (public key)
   |           API GATEWAY (:9000)                    | ------------------+     để verify chữ ký JWT
   |   Gateway Server Web MVC + Resource Server       |
   |   /api/products/** -> http://localhost:8080      |
   |   /api/order/**    -> http://localhost:8081      |
   |   /api/inventory/**-> http://localhost:8082      |
   +--------+-------------------+----------------------+
            |                   |                   |
            v                   v                   v
     Product Service      Order Service  --OpenFeign-->  Inventory Service
     (:8080, MongoDB)     (:8081, MySQL)   GET /api/inventory   (:8082, MySQL)
```
---

# PHẦN 3 – OpenFeign, WireMock, API Gateway

## 3.A Lý thuyết chính

### 3.A.1 Microservices & các service trong project

**Microservices**: ứng dụng được tách thành nhiều service nhỏ, độc lập, mỗi service một nghiệp vụ, deploy/scale riêng, có DB riêng, giao tiếp qua mạng.

| Service | Port | DB | Endpoint chính |
|---|---|---|---|
| Product Service | 8080 | MongoDB | `GET/POST /api/products` |
| Order Service | 8081 | MySQL `order_service` | `POST /api/order` |
| Inventory Service | 8082 | MySQL `inventory_service` | `GET /api/inventory?skuCode=&quantity=` |

### 3.A.2 Hai kiểu giao tiếp giữa các service

| | **Synchronous (đồng bộ)** | **Asynchronous (bất đồng bộ)** |
|---|---|---|
| Cách hoạt động | Gửi request và **chờ** response | Gửi message vào **queue/topic**, không chờ |
| Công nghệ | HTTP/REST, gRPC | Kafka, RabbitMQ, ActiveMQ |
| Ưu điểm | Đơn giản, có kết quả ngay | Loose coupling, chịu lỗi tốt |
| Nhược điểm | **Temporal coupling**: service được gọi chậm/chết → service gọi cũng chậm/lỗi | Phức tạp, eventual consistency |
| Dùng khi | Cần câu trả lời ngay để quyết định (còn hàng không?) | Thông báo sự kiện (đơn đã tạo → gửi email) |

Order → Inventory là bài toán **cần câu trả lời ngay** (true/false) → chọn **đồng bộ**.

### 3.A.3 OpenFeign – Declarative HTTP Client

**Ý tưởng:** bạn chỉ **khai báo interface** mô tả HTTP request; Spring Cloud OpenFeign **sinh implementation ở runtime** (dynamic proxy). Gọi method Java = gửi HTTP request.

```java
@FeignClient(value = "inventory", url = "${inventory.url}")
public interface InventoryClient {
    @RequestMapping(method = RequestMethod.GET, value = "/api/inventory")
    boolean isInStock(@RequestParam String skuCode, @RequestParam Integer quantity);
}
// inventoryClient.isInStock("iphone_15", 1)
//   ==> GET http://localhost:8082/api/inventory?skuCode=iphone_15&quantity=1
```

| Tiêu chí | RestTemplate | WebClient | **OpenFeign** |
|---|---|---|---|
| Cú pháp | Imperative, dài dòng | Fluent / Reactive | **Declarative** (gọn nhất) |
| Tích hợp Spring Cloud (Discovery, LoadBalancer, CircuitBreaker) | Thủ công | Thủ công | **Có sẵn** |
| Trạng thái | Maintenance mode | Hiện đại | Phổ biến cho microservices |

**Các annotation cần nhớ**

| Annotation | Vị trí | Ý nghĩa |
|---|---|---|
| `@FeignClient(value, url)` | Interface | `value`: tên logic (dùng cho Eureka ở phần sau); `url`: địa chỉ thật, lấy từ properties |
| `@RequestMapping / @GetMapping` | Method | Mô tả request **được gửi đi** (method + path) |
| `@RequestParam` | Tham số | Biến thành query param `?skuCode=...&quantity=...` |
| `@EnableFeignClients` | Main class | **Bắt buộc** – bật cơ chế quét và tạo bean cho interface `@FeignClient`. Thiếu → `NoSuchBeanDefinitionException` |

**BOM (Bill of Materials):** `spring-cloud-dependencies` import vào `<dependencyManagement>` để quản lý version của **toàn bộ** thư viện Spring Cloud ở 1 chỗ → các dependency Spring Cloud không cần ghi `<version>`.

**Externalize URL:** `inventory.url` đặt trong `application.properties` → dev/test/prod (Docker, K8s) đổi URL không cần build lại. Trong test ta trỏ nó sang WireMock.

### 3.A.4 WireMock – giả lập HTTP server trong test

Sau khi thêm OpenFeign, test cũ gọi `POST /api/order` → OrderService gọi `http://localhost:8082` → **Connection refused** → test fail.

**WireMock** dựng một HTTP server giả (port ngẫu nhiên) và trả response theo "stub" bạn định nghĩa:

```
Test --POST /api/order--> Order Service --GET /api/inventory?...--> WireMock (stub: 200 "true")
```

| Khái niệm | Ý nghĩa |
|---|---|
| `org.wiremock.integrations:wiremock-spring-boot` | Thư viện tích hợp WireMock **chính thức** cho Spring Boot (thay Spring Cloud Contract WireMock – đã bị gỡ ở Spring Cloud 2025.1) |
| `@EnableWireMock(@ConfigureWireMock(baseUrlProperties = "inventory.url"))` | Khởi động WireMock ở port ngẫu nhiên, **ghi URL của nó vào property `inventory.url`** (và `wiremock.server.port`), reset stub sau mỗi test |
| `stubFor(get(urlEqualTo(...)).willReturn(aResponse()...))` | Định nghĩa: request khớp URL → trả response định sẵn |
| `urlEqualTo` vs `urlPathEqualTo` | `urlEqualTo` so **cả path + query string** (đúng thứ tự); `urlPathEqualTo` chỉ so path, query kiểm tra bằng `withQueryParam` |
| `verify(...)` | Kiểm tra Order Service có thực sự gửi request đúng URL/params |

Ưu điểm: test nhanh, không cần hạ tầng thật, test được cả case **hết hàng / lỗi / timeout**.

### 3.A.5 API Gateway Pattern

**Vấn đề khi không có Gateway:** client phụ thuộc địa chỉ nội bộ từng service; bảo mật/CORS/log phải làm ở từng service; đổi port 1 service → sửa mọi client.

**API Gateway** = **single entry point**, hoạt động như **reverse proxy**: nhận request, dựa vào quy tắc định tuyến chuyển tiếp tới service phù hợp. Ngoài routing, Gateway xử lý **cross-cutting concerns**: Authentication/Authorization (Phần 4), Rate limiting, Load balancing, Circuit breaker (Phần 5), biến đổi request/response, logging/monitoring tập trung.

### 3.A.6 Spring Cloud Gateway Server Web MVC

| | Spring Cloud Gateway Server WebFlux (gốc) | **Spring Cloud Gateway Server Web MVC** |
|---|---|---|
| Nền tảng | WebFlux (Reactive, Netty) | **Web MVC (Servlet, Tomcat)** |
| Lập trình | Non-blocking | Imperative / Blocking |
| Thread model | Event loop | Thread-per-request (hỗ trợ Virtual Threads) |
| Cách khai báo route | `RouteLocator` | **`RouterFunction<ServerResponse>`** |

**3 khái niệm cốt lõi**

| Khái niệm | Ý nghĩa | Ví dụ |
|---|---|---|
| **Route** | Đơn vị định tuyến: ID + URI đích + Predicate + Filter | `route("product_service")` |
| **Predicate** | Điều kiện để request khớp route | `path("/api/products/**")`, `method(GET)`, header, query |
| **Filter** | Biến đổi request/response trước/sau khi forward | `AddRequestHeader`, `RewritePath`, `CircuitBreaker`, `Retry` |

```java
route("product_service")                                   // (1) ID route
    .route(RequestPredicates.path("/api/products/**"),     // (2) Predicate
           http())                                         // (3) Handler: proxy request
    .before(uri("http://localhost:8080"))                  // (4) Filter: đặt URI đích
    .build();                                              // (5) tạo RouterFunction
```

> Tên gọi: từ Spring Cloud 2025.0 bản Servlet được đổi tên thành **Gateway Server Web MVC**, starter là `spring-cloud-starter-gateway-server-webmvc`.

> `path("/api/products")` chỉ khớp **đúng** `/api/products`. `path("/api/products/**")` khớp cả `/api/products` và `/api/products/abc` (PathPattern `**` = 0 hoặc nhiều segment).

---

## 3.B Danh sách yêu cầu TODO

### Phần A – OpenFeign (order-service)

| TODO | Nội dung | File |
|---|---|---|
| **TODO 3.1** | Thêm dependency `spring-cloud-starter-openfeign` + BOM `spring-cloud-dependencies` + property `spring-cloud.version` | `order-service/pom.xml` |
| **TODO 3.2** | Tạo interface `InventoryClient` với `@FeignClient`, method `isInStock(skuCode, quantity)` | `client/InventoryClient.java` |
| **TODO 3.3** | Khai báo `inventory.url=http://localhost:8082` | `application.properties` |
| **TODO 3.4** | Inject `InventoryClient` vào `OrderService`, kiểm tra tồn kho trước khi lưu; hết hàng → ném `RuntimeException` | `service/OrderService.java` |
| **TODO 3.5** | Thêm `@EnableFeignClients` vào main class | `OrderServiceApplication.java` |
| **TODO 3.6** | Test thủ công: qty=100 → `201`; qty=101 → `500` | Postman |

### Phần B – WireMock (order-service test)

| TODO | Nội dung | File |
|---|---|---|
| **TODO 3.7** | Thêm dependency `org.wiremock.integrations:wiremock-spring-boot` (scope test) | `pom.xml` |
| **TODO 3.8** | Tạo `InventoryStubs` (stub còn hàng / hết hàng) | `src/test/.../stub/InventoryStubs.java` |
| **TODO 3.9** | Cập nhật `OrderServiceApplicationTests`: `@EnableWireMock` + `@ConfigureWireMock(baseUrlProperties="inventory.url")`, test 2 case | `OrderServiceApplicationTests.java` |

### Phần C – API Gateway (project mới `api-gateway`)

| TODO | Nội dung | File |
|---|---|---|
| **TODO 3.10** | Tạo project `api-gateway` (Gateway Server Web MVC, Actuator, Resilience4J) | start.spring.io |
| **TODO 3.11** | Hoàn thiện `pom.xml` (Boot 4.1.0, Cloud 2025.1.3) | `api-gateway/pom.xml` |
| **TODO 3.12** | Cấu hình `server.port=9000` + URL 3 service | `application.properties` |
| **TODO 3.13** | Tạo `Routes.java` với 3 route: products / order / inventory | `routes/Routes.java` |
| **TODO 3.14** | Test: gọi cả 3 service qua `localhost:9000` | Postman |

---

## 3.C Hướng dẫn step-by-step + code đầy đủ

> Chuẩn bị: copy (hoặc tiếp tục trên) 3 service ở `Part1/`. Docker Desktop đang chạy. MySQL (`order-service/docker-compose.yml`) và MongoDB (`product-service/docker-compose.yml`) đã lên.

### Bước 1 (TODO 3.1) – Thêm OpenFeign + Spring Cloud BOM vào `order-service/pom.xml`


File **`order-service/pom.xml`** (đầy đủ – Spring Boot 4.1.0):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>
    <groupId>com.fudn</groupId>
    <artifactId>order-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>order-service</name>
    <description>Order Service - MSS301</description>

    <properties>
        <java.version>21</java.version>
        <!-- TODO 3.1: Spring Cloud release train khop voi Spring Boot 4.1.x -->
        <spring-cloud.version>2025.1.3</spring-cloud.version>
    </properties>

    <dependencies>
        <!-- Boot 4: spring-boot-starter-web da doi ten thanh spring-boot-starter-webmvc -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <!-- Boot 4: bat buoc dung starter thi Flyway moi duoc auto-config -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- TODO 3.1: OpenFeign - khong ghi version, BOM quan ly -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>

        <!-- ===== TEST ===== -->
        <!-- Boot 4: test starter theo module (da gom spring-boot-starter-test) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <!-- Testcontainers 2.x: artifact co tien to testcontainers- (version do Boot quan ly) -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-mysql</artifactId>
            <scope>test</scope>
        </dependency>
        <!-- REST Assured 6.x: Groovy 5 + Jackson 3 (tuong thich Boot 4) -->
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <version>6.0.1</version>
            <scope>test</scope>
        </dependency>
        <!-- TODO 3.7: WireMock chinh thuc cho Spring Boot (thay spring-cloud-starter-contract-stub-runner) -->
        <dependency>
            <groupId>org.wiremock.integrations</groupId>
            <artifactId>wiremock-spring-boot</artifactId>
            <version>4.2.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- TODO 3.1: Spring Cloud BOM -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <!-- Khai bao ro Lombok la annotation processor (start.spring.io Boot 4 sinh san) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Giải thích:** `<scope>import</scope>` + `<type>pom</type>` nghĩa là "nhập toàn bộ bảng version" từ BOM Spring Cloud. Hai dependency **phải ghi version** vì Spring Boot/Spring Cloud không quản lý: `rest-assured` (6.0.1) và `wiremock-spring-boot` (4.2.3). Sau khi sửa: IntelliJ → **Maven → Reload project** (hoặc `mvn -q dependency:resolve`).

---

### Bước 2 (TODO 3.2) – Tạo `InventoryClient`

Tạo package `client`:

```
order-service/src/main/java/com/fudn/orderservice/
├── client/
│   └── InventoryClient.java      <-- TẠO MỚI
├── controller/OrderController.java
├── dto/OrderRequest.java
├── model/Order.java
├── repository/OrderRepository.java
├── service/OrderService.java
└── OrderServiceApplication.java
```

File **`client/InventoryClient.java`**:

```java
package com.fudn.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Declarative HTTP client goi sang Inventory Service.
 * Spring Cloud OpenFeign tu sinh implementation luc runtime.
 *
 * isInStock("iphone_15", 1)
 *   ==> GET ${inventory.url}/api/inventory?skuCode=iphone_15&quantity=1
 */
@FeignClient(value = "inventory", url = "${inventory.url}")
public interface InventoryClient {

    @RequestMapping(method = RequestMethod.GET, value = "/api/inventory")
    boolean isInStock(@RequestParam("skuCode") String skuCode,
                      @RequestParam("quantity") Integer quantity);
}
```

**Giải thích:**
- Signature phải **khớp** với `InventoryController` phía Inventory Service: `GET /api/inventory`, 2 query param `skuCode`, `quantity`, trả `boolean`.
- Ghi rõ tên `@RequestParam("skuCode")` để không phụ thuộc cờ compiler `-parameters` (tránh lỗi `RequestParam.value() was empty on parameter 0`).

---

### Bước 3 (TODO 3.3) – Cấu hình `inventory.url`

File **`order-service/src/main/resources/application.properties`** (đầy đủ):

```properties
spring.application.name=order-service

spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost:3306/order_service
spring.datasource.username=root
spring.datasource.password=mysql

# Tat auto-create schema cua Hibernate vi chung ta dung Flyway
spring.jpa.hibernate.ddl-auto=none

# Chay tren port 8081 vi 8080 da dung cho product-service
server.port=8081

# TODO 3.3: URL cua Inventory Service (externalize de doi theo moi truong)
inventory.url=http://localhost:8082

# (Tuy chon) Xem log request/response cua Feign khi debug
# logging.level.com.fudn.orderservice.client=DEBUG
# spring.cloud.openfeign.client.config.inventory.logger-level=full
```

---

### Bước 4 (TODO 3.4) – Cập nhật `OrderService`

File **`service/OrderService.java`**:

```java
package com.fudn.orderservice.service;

import com.fudn.orderservice.client.InventoryClient;
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
    private final InventoryClient inventoryClient;   // TODO 3.4: inject FeignClient

    public void placeOrder(OrderRequest orderRequest) {
        // 1. Goi dong bo sang Inventory Service
        boolean inStock = inventoryClient.isInStock(
                orderRequest.skuCode(), orderRequest.quantity());

        // 2. Con hang -> luu don; het hang -> nem exception
        if (inStock) {
            Order order = mapToOrder(orderRequest);
            orderRepository.save(order);
        } else {
            throw new RuntimeException(
                    "Product with SkuCode " + orderRequest.skuCode() + " is not in stock");
        }
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

**Luồng xử lý:**

```
POST /api/order ─► OrderController ─► OrderService.placeOrder()
                                        │
                                        ├─► InventoryClient.isInStock(sku, qty)
                                        │        └─► GET http://localhost:8082/api/inventory?skuCode=..&quantity=..
                                        │                 └─► Inventory Service: SELECT ... quantity >= ?
                                        │
                                        ├─ true  ─► orderRepository.save() ─► 201 "Order Placed Successfully"
                                        └─ false ─► RuntimeException        ─► 500 Internal Server Error
```

> **Vì sao 500 mà không phải 400?** `RuntimeException` không được handle → Spring trả 500. Dự án thật nên tạo `@RestControllerAdvice` map sang `400/409` (tùy chọn – xem mục Mở rộng ở cuối phần 3).

---

### Bước 5 (TODO 3.5) – Bật OpenFeign

File **`OrderServiceApplication.java`**:

```java
package com.fudn.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients   // TODO 3.5: BAT BUOC - quet va tao bean cho cac @FeignClient
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

---

### Bước 6 (TODO 3.6) – Chạy & test thủ công

```bash
# Terminal 1 – MySQL (dùng chung cho order + inventory)
cd order-service && docker compose up -d

# Terminal 2
cd inventory-service && mvn spring-boot:run      # port 8082

# Terminal 3
cd order-service && mvn spring-boot:run          # port 8081
```

| Request | Kết quả mong đợi |
|---|---|
| `POST :8081/api/order` `{"skuCode":"iphone_15","price":1000,"quantity":100}` | `201` – `Order Placed Successfully` |
| `POST :8081/api/order` `{"skuCode":"iphone_15","price":1000,"quantity":101}` | `500` – log: `Product with SkuCode iphone_15 is not in stock` |

> Inventory seed sẵn `iphone_15 = 100` (file `V2__add_inventory.sql`), điều kiện `quantity >= ?` → 100 còn hàng, 101 hết hàng. Chi tiết test: `part3-4_Test.md` mục T3.

---

### Bước 7 (TODO 3.7) – Dependency WireMock

Đã thêm ở Bước 1 (`org.wiremock.integrations:wiremock-spring-boot:4.2.3`, scope `test`). Reload Maven.

> **Vì sao không dùng `spring-cloud-starter-contract-stub-runner` như tài liệu gốc?** Từ Spring Cloud Contract 5.0 (Spring Cloud 2025.1, dành cho Boot 4), annotation `@AutoConfigureWireMock` **đã bị gỡ**; nhóm Spring khuyến nghị chuyển sang thư viện tích hợp chính thức của WireMock. Các hàm DSL (`stubFor`, `get`, `urlEqualTo`, `aResponse`, `verify`…) **giữ nguyên** package `com.github.tomakehurst.wiremock.client.WireMock`.

---

### Bước 8 (TODO 3.8) – Tạo `InventoryStubs`

```
order-service/src/test/java/com/fudn/orderservice/
├── stub/
│   └── InventoryStubs.java                <-- TẠO MỚI
└── OrderServiceApplicationTests.java      <-- SỬA
```

File **`src/test/java/com/fudn/orderservice/stub/InventoryStubs.java`**:

```java
package com.fudn.orderservice.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Cac kich ban gia lap Inventory Service cho integration test.
 */
public final class InventoryStubs {

    private InventoryStubs() {
    }

    /** Inventory tra ve true (con hang). */
    public static void stubInventoryCall(String skuCode, Integer quantity) {
        stubInventory(skuCode, quantity, true);
    }

    /** Inventory tra ve false (het hang). */
    public static void stubInventoryOutOfStock(String skuCode, Integer quantity) {
        stubInventory(skuCode, quantity, false);
    }

    private static void stubInventory(String skuCode, Integer quantity, boolean inStock) {
        stubFor(get(urlEqualTo("/api/inventory?skuCode=" + skuCode + "&quantity=" + quantity))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.valueOf(inStock))));
    }
}
```

**Giải thích DSL:** `stubFor(...)` đăng ký stub vào WireMock server mặc định (server tên `wiremock` mà `@EnableWireMock` khởi động); `get(urlEqualTo(...))` – khớp GET đúng path + query; `willReturn(aResponse()...)` – response trả về. Thứ tự query `skuCode` rồi `quantity` phải đúng thứ tự Feign sinh ra (theo thứ tự tham số của method).

---

### Bước 9 (TODO 3.9) – Cập nhật Integration Test

File **`src/test/java/com/fudn/orderservice/OrderServiceApplicationTests.java`**:

```java
package com.fudn.orderservice;

import com.fudn.orderservice.stub.InventoryStubs;
import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// TODO 3.9: khoi dong WireMock (port ngau nhien) va ghi URL cua no vao property inventory.url
@EnableWireMock(@ConfigureWireMock(baseUrlProperties = "inventory.url"))
class OrderServiceApplicationTests {

    // Testcontainers 2.x: package org.testcontainers.mysql, khong con generic <?>
    @ServiceConnection
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.3.0");

    static {
        mySQLContainer.start();
    }

    @LocalServerPort
    private Integer port;

    @BeforeEach
    void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
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
        InventoryStubs.stubInventoryCall("iphone_15", 1);

        String responseBody = RestAssured.given()
                .contentType("application/json")
                .body(submitOrderJson)
                .when()
                .post("/api/order")
                .then()
                .log().all()
                .statusCode(201)
                .extract().body().asString();

        assertThat(responseBody, Matchers.is("Order Placed Successfully"));
        // Xac nhan Order Service da goi dung URL sang Inventory
        verify(getRequestedFor(urlEqualTo("/api/inventory?skuCode=iphone_15&quantity=1")));
    }

    @Test
    void shouldFailOrderWhenProductIsNotInStock() {
        String submitOrderJson = """
                {
                     "skuCode": "iphone_15",
                     "price": 1000,
                     "quantity": 1000
                }
                """;
        InventoryStubs.stubInventoryOutOfStock("iphone_15", 1000);

        RestAssured.given()
                .contentType("application/json")
                .body(submitOrderJson)
                .when()
                .post("/api/order")
                .then()
                .log().all()
                .statusCode(500);
    }
}
```

**Giải thích annotation:**

| Annotation | Ý nghĩa |
|---|---|
| `@SpringBootTest(RANDOM_PORT)` | Khởi động toàn bộ context trên port ngẫu nhiên |
| `@EnableWireMock` | Khởi động WireMock (mặc định tên `wiremock`, port ngẫu nhiên) trước khi tạo Spring context, cấu hình sẵn static DSL `stubFor/verify`, reset stub sau mỗi test |
| `@ConfigureWireMock(baseUrlProperties = "inventory.url")` | Ghi `http://localhost:<port-wiremock>` vào property `inventory.url` **chỉ trong test** → Feign gọi sang WireMock. Mặc định vẫn set thêm `wiremock.server.port` |
| `@ServiceConnection` | Testcontainers tự cấu hình datasource từ container MySQL |
| `@LocalServerPort` | Inject port mà app test đang chạy |

> ⚠️ Không nên tạo `src/test/resources/application.properties` chỉ với 1 dòng `inventory.url=...`: file này **che hoàn toàn** file `application.properties` của main (cùng tên trên classpath). Dùng `baseUrlProperties` như trên (hoặc `@SpringBootTest(properties = "inventory.url=http://localhost:${wiremock.server.port}")`) an toàn hơn.

Chạy: `cd order-service && mvn test` → **2 tests PASS** (cần Docker để Testcontainers chạy MySQL).

---

### Bước 10 (TODO 3.10) – Tạo project `api-gateway`

Vào https://start.spring.io:

| Trường | Giá trị |
|---|---|
| Project / Language | Maven / Java |
| Spring Boot | **4.1.0** (cùng phiên bản với các service) |
| Group | `com.fudn` |
| Artifact / Name | `api-gateway` |
| Package name | `com.fudn.gateway` |
| Java | 21 |
| Dependencies | **Gateway** (nhóm Spring Cloud Routing – bản **Server Web MVC**, sinh `spring-cloud-starter-gateway-server-webmvc`; **KHÔNG** chọn *Reactive Gateway*), **Spring Boot Actuator**, **Resilience4J** (Spring Cloud Circuit Breaker) |

Generate → giải nén vào thư mục cùng cấp với 3 service.

---

### Bước 11 (TODO 3.11) – `api-gateway/pom.xml` (trạng thái cuối Phần 3)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>
    <groupId>com.fudn</groupId>
    <artifactId>api-gateway</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>api-gateway</name>
    <description>API Gateway - MSS301</description>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2025.1.3</spring-cloud.version>
    </properties>

    <dependencies>
        <!-- Actuator: /actuator/health, metrics -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <!-- Gateway Server Web MVC (Servlet-based) - ten moi tu Spring Cloud 2025.x -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
        </dependency>
        <!-- Resilience4J: Circuit Breaker (dung o Phan 5) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

> Phần 4 sẽ bổ sung dependency bảo mật + test – xem pom hoàn chỉnh ở Bước 4.8.

---

### Bước 12 (TODO 3.12) – `api-gateway/src/main/resources/application.properties` (Phần 3)

```properties
spring.application.name=api-gateway

# API Gateway chay port 9000 (8080/8081/8082 da duoc cac service dung)
server.port=9000

# URL cac service dich (co the doi theo moi truong, khong can sua code)
services.product.url=http://localhost:8080
services.order.url=http://localhost:8081
services.inventory.url=http://localhost:8082
```

---

### Bước 13 (TODO 3.13) – Tạo `Routes.java`

```
api-gateway/src/main/java/com/fudn/gateway/
├── ApiGatewayApplication.java
└── routes/
    └── Routes.java     <-- TẠO MỚI
```

File **`routes/Routes.java`**:

```java
package com.fudn.gateway.routes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * Dinh nghia routing rules cua API Gateway (Spring Cloud Gateway Server Web MVC).
 * Moi route = ID + Predicate (dieu kien) + Handler http() + Filter uri(...) (URI dich).
 */
@Configuration(proxyBeanMethods = false)
public class Routes {

    @Value("${services.product.url:http://localhost:8080}")
    private String productServiceUrl;

    @Value("${services.order.url:http://localhost:8081}")
    private String orderServiceUrl;

    @Value("${services.inventory.url:http://localhost:8082}")
    private String inventoryServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> productServiceRoute() {
        return route("product_service")
                .route(RequestPredicates.path("/api/products/**"), http())
                .before(uri(productServiceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> orderServiceRoute() {
        return route("order_service")
                .route(RequestPredicates.path("/api/order/**"), http())
                .before(uri(orderServiceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> inventoryServiceRoute() {
        return route("inventory_service")
                .route(RequestPredicates.path("/api/inventory/**"), http())
                .before(uri(inventoryServiceUrl))
                .build();
    }
}
```

File **`ApiGatewayApplication.java`** (start.spring.io đã sinh, giữ nguyên):

```java
package com.fudn.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

**Giải thích cú pháp:**

| # | Thành phần | Ý nghĩa |
|---|---|---|
| 1 | `route("product_service")` | ID route – dùng khi debug/log/actuator |
| 2 | `RequestPredicates.path("/api/products/**")` | Predicate – request có path khớp thì dùng route này |
| 3 | `http()` | HandlerFunction – proxy nguyên request (method, header, body, query) tới URI đích, giữ nguyên path |
| 4 | `.before(uri(productServiceUrl))` | Before-filter – đặt URI đích (scheme/host/port) cho các route của builder này |
| 5 | `.build()` | Kết thúc builder, tạo `RouterFunction<ServerResponse>` |

> ⚠️ Tài liệu gốc dùng `http("http://localhost:8080")`. Từ Spring Cloud Gateway 4.1.7, `HandlerFunctions.http(String)` / `http(URI)` **deprecated**; ở Gateway 5.0 (Spring Cloud 2025.1) cách chuẩn là `http()` + `BeforeFilterFunctions.uri(...)` như trên.

---

### Bước 14 (TODO 3.14) – Chạy & test qua Gateway

Thứ tự khởi động: **Inventory (8082) → Product (8080) → Order (8081) → API Gateway (9000)**.

| Trước (không Gateway) | Sau (qua Gateway) |
|---|---|
| `http://localhost:8080/api/products` | `http://localhost:9000/api/products` |
| `http://localhost:8081/api/order` | `http://localhost:9000/api/order` |
| `http://localhost:8082/api/inventory?skuCode=iphone_15&quantity=1` | `http://localhost:9000/api/inventory?skuCode=iphone_15&quantity=1` |

Client chỉ cần biết **một địa chỉ**: `localhost:9000`. Test chi tiết: `part3-4_Test.md` mục T5.

### Mở rộng Phần 3 (không bắt buộc) – trả 409 thay vì 500 khi hết hàng

```java
package com.fudn.orderservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
```

> Nếu thêm handler này, đổi kỳ vọng trong test `shouldFailOrderWhenProductIsNotInStock` từ `500` thành `409`.

---

# PHẦN 4 – Bảo mật API Gateway với Keycloak & OAuth2

## 4.A Lý thuyết chính

### 4.A.1 Vì sao cần bảo mật?

Sau Phần 3, **bất kỳ ai** gọi `http://localhost:9000/api/order` cũng tạo được đơn: không biết ai gọi, không phân quyền được.

| Lớp traffic | Mô tả | Giải pháp thường dùng |
|---|---|---|
| **North–South** (Client ↔ Gateway) | Bên ngoài gọi vào hệ thống | **OAuth2 + JWT tại API Gateway** ← Phần 4 |
| **East–West** (Service ↔ Service) | Các service gọi nhau bên trong (Order → Inventory) | mTLS, service mesh, network policy |

### 4.A.2 OAuth2 (RFC 6749)

**OAuth2 là framework *ủy quyền* (authorization)** – cho phép một ứng dụng truy cập tài nguyên được bảo vệ **mà không cần chia sẻ mật khẩu** (VD: "Đăng nhập với Google").

| Vai trò | Ý nghĩa | Trong project |
|---|---|---|
| Resource Owner | Chủ tài nguyên | Ứng dụng/dev (M2M không có user thật) |
| Client | Ứng dụng muốn truy cập tài nguyên | Postman / frontend |
| **Authorization Server** | Xác thực và **cấp token** | **Keycloak** |
| **Resource Server** | Giữ tài nguyên, **kiểm tra token** | **API Gateway** |

**Access Token** – chuỗi Authorization Server cấp; client gửi kèm mọi request:

```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 4.A.3 JWT – JSON Web Token

`HEADER.PAYLOAD.SIGNATURE` (mỗi phần Base64URL):

| Phần | Nội dung |
|---|---|
| Header | Thuật toán ký (`RS256`), `kid` (id của key) |
| Payload (claims) | `iss` (issuer), `sub`, `exp` (hết hạn), `iat`, `scope`, `azp`, `preferred_username`… |
| Signature | Keycloak ký bằng **private key**; Gateway verify bằng **public key** (JWKS) |

```json
{
  "iss": "http://localhost:8181/realms/spring-microservices-realm",
  "sub": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "exp": 1714000000,
  "iat": 1713996400,
  "scope": "email profile",
  "azp": "spring-microservices-client",
  "preferred_username": "service-account-spring-microservices-client"
}
```

> JWT **chỉ được ký, không mã hóa** → ai cũng decode được (jwt.io). Không đặt dữ liệu nhạy cảm trong payload. Tính toàn vẹn nhờ chữ ký: sửa 1 ký tự payload → chữ ký sai → 401.

**Resource Server kiểm tra gì?** (1) chữ ký hợp lệ với public key của Keycloak, (2) `exp` chưa quá hạn, (3) `iss` **trùng khớp** `issuer-uri` cấu hình.

### 4.A.4 OpenID Connect (OIDC)

| | OAuth2 | OIDC |
|---|---|---|
| Mục đích | **Ủy quyền** – "được làm gì?" | **Xác thực** – "là ai?" |
| Token | Access Token | Access Token + **ID Token** |
| Endpoint thêm | — | `/userinfo`, `/.well-known/openid-configuration` |

OIDC là lớp xây trên OAuth2. Project này dùng **Client Credentials** (M2M, không có user) nên không cần ID Token, nhưng vẫn tận dụng **discovery document** của OIDC để Gateway tự tìm JWKS.

### 4.A.5 Các Grant Type

| Grant | Dùng cho | Luồng |
|---|---|---|
| Authorization Code (+PKCE) | Web/mobile có **người dùng** | User → login ở Keycloak → code → đổi lấy token |
| **Client Credentials** ✅ | **Machine-to-machine**, script, Postman | `client_id + client_secret` → token trực tiếp |
| Implicit | SPA cũ | **Deprecated** |
| Password (ROPC) | App cực kỳ tin cậy | User đưa user/pass cho app – không khuyến nghị |

### 4.A.6 Keycloak

Authorization Server mã nguồn mở (Red Hat): SSO, MFA, social login, LDAP/AD federation, hỗ trợ OAuth2/OIDC/SAML 2.0, Admin Console. Lý do dùng: **đừng tự viết** hệ thống auth (hash mật khẩu, refresh/revoke token, chống brute force… rất dễ sai).

- **Realm** – không gian cô lập chứa clients, users, roles. **Không dùng realm `master`** cho ứng dụng.
- **Client** – một ứng dụng được phép xin token trong realm. Bật **Client authentication = ON** → client *confidential* → có **Client Secret**. Bật **Service accounts roles** → cho phép **Client Credentials Grant**.

### 4.A.7 Resource Server & `issuer-uri`

Khi cấu hình `spring.security.oauth2.resourceserver.jwt.issuer-uri`, Spring Security (lần đầu cần verify token):
1. Gọi `{issuer-uri}/.well-known/openid-configuration`
2. Lấy `jwks_uri` từ kết quả
3. Tải public key (JWKS) và cache lại
4. Với mỗi request: đọc header `Authorization: Bearer ...` → verify chữ ký, `exp`, `iss` → hợp lệ thì đưa `Authentication` vào `SecurityContext` và cho request đi tiếp tới route; không hợp lệ → **401** + header `WWW-Authenticate: Bearer error="invalid_token"`.

**`SecurityFilterChain`** (Spring Security 7 đi kèm Boot 4 – lambda DSL):
- `authorizeHttpRequests(...)`: quy tắc phân quyền theo URL (`permitAll`, `authenticated`, `hasRole`…)
- `oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))`: bật xác thực Bearer JWT, decoder cấu hình từ `issuer-uri`
- CSRF: với request mang Bearer token, Resource Server tự bỏ qua CSRF → POST qua Gateway vẫn chạy. Ta vẫn `disable` CSRF + `STATELESS` session cho rõ ràng vì Gateway là REST API không dùng cookie.

---

## 4.B Danh sách yêu cầu TODO

| TODO | Nội dung | Nơi làm |
|---|---|---|
| **TODO 4.1** | Tạo `docker-compose.yml` chạy Keycloak 24 + MySQL (đúng biến môi trường `KC_DB_*`) | `api-gateway/docker-compose.yml` |
| **TODO 4.2** | Khởi động Keycloak, truy cập `http://localhost:8181` | Terminal |
| **TODO 4.3** | Đăng nhập Admin Console (`admin/admin`) | Trình duyệt |
| **TODO 4.4** | Tạo realm `spring-microservices-realm` | Keycloak |
| **TODO 4.5** | Tạo client `spring-microservices-client` (Client authentication ON, chỉ Service accounts roles) | Keycloak |
| **TODO 4.6** | Lấy Client Secret | Keycloak → Credentials |
| **TODO 4.7** | Xác định Token Endpoint & JWKS URI | `.well-known/openid-configuration` |
| **TODO 4.8** | Thêm `spring-boot-starter-security-oauth2-resource-server` (+ test deps) | `api-gateway/pom.xml` |
| **TODO 4.9** | Cấu hình `issuer-uri` | `application.properties` |
| **TODO 4.10** | Tạo `SecurityConfig` – mọi request `/api/**` phải có JWT hợp lệ | `config/SecurityConfig.java` |
| **TODO 4.11** | Postman: lấy token bằng Client Credentials | Postman |
| **TODO 4.12** | Gọi API qua Gateway có token → 200/201; không token / token sai / hết hạn → 401 | Postman |
| **TODO 4.13** *(khuyến khích)* | Viết test tự động cho Gateway (401 / 200 với JWT giả lập) | `ApiGatewaySecurityTests.java` |

---

## 4.C Hướng dẫn step-by-step + code đầy đủ

### Bước 4.1 (TODO 4.1) – Docker Compose cho Keycloak

Cấu trúc:

```
api-gateway/
├── docker/
│   └── keycloak/
│       └── realms/                 <-- tạo thư mục (có thể để trống, hoặc đặt file realm JSON – Cách B ở Bước 4.5)
├── volume-data/
│   └── mysql_keycloak_data/        <-- tự sinh khi chạy
├── docker-compose.yml              <-- TẠO MỚI
└── src/
```

File **`api-gateway/docker-compose.yml`**:

```yaml
services:
  # MySQL luu tru cau hinh cua Keycloak (realm, client, user...)
  keycloak-mysql:
    container_name: keycloak-mysql
    image: mysql:8
    volumes:
      - ./volume-data/mysql_keycloak_data:/var/lib/mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: keycloak
      MYSQL_USER: keycloak
      MYSQL_PASSWORD: password
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot"]
      interval: 10s
      timeout: 5s
      retries: 10
    # Khong publish port 3306 ra host de tranh dung do voi MySQL cua order/inventory

  # Keycloak Authorization Server
  keycloak:
    container_name: keycloak
    image: quay.io/keycloak/keycloak:24.0.1
    command: ["start-dev", "--import-realm"]
    environment:
      # Keycloak >= 17 (Quarkus) dung bien KC_*
      KC_DB: mysql
      KC_DB_URL: jdbc:mysql://keycloak-mysql:3306/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: password
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    ports:
      - "8181:8080"
    volumes:
      - ./docker/keycloak/realms/:/opt/keycloak/data/import/
    depends_on:
      keycloak-mysql:
        condition: service_healthy
```

| Cấu hình | Ý nghĩa |
|---|---|
| `start-dev` | Chế độ development: HTTP, không cần hostname/TLS |
| `--import-realm` | Import file `*.json` trong `/opt/keycloak/data/import/` khi khởi động (bỏ qua nếu realm đã tồn tại) |
| `KC_DB`, `KC_DB_URL`… | Dùng MySQL `keycloak-mysql` (tên service trong compose = hostname) |
| `KEYCLOAK_ADMIN/_PASSWORD` | Tài khoản admin lần đầu |
| `8181:8080` | Keycloak trong container chạy 8080, ra host qua 8181 (8080 của host là Product Service) |
| `depends_on … service_healthy` | Chỉ start Keycloak khi MySQL đã sẵn sàng (tránh Keycloak crash vì DB chưa lên) |

> ⚠️ `start-dev` và `admin/admin` **chỉ dùng cho dev**. Dòng `version: "3.8"` trong tài liệu gốc đã lỗi thời với Docker Compose v2 (chỉ gây warning) nên bỏ đi.

### Bước 4.2 (TODO 4.2) – Khởi động Keycloak

```bash
cd api-gateway
docker compose up -d
docker compose ps
docker compose logs -f keycloak      # chờ dòng: "Keycloak 24.0.1 on JVM ... started in ..."
```

Mở http://localhost:8181 (chờ 30–60 giây lần đầu).

### Bước 4.3 (TODO 4.3) – Đăng nhập Admin Console

Username `admin`, Password `admin`.

### Bước 4.4 (TODO 4.4) – Tạo realm

1. Góc trên trái, click dropdown realm (**Keycloak / master**) → **Create realm**
2. **Realm name**: `spring-microservices-realm` → **Create**
3. Góc trên trái giờ hiển thị `spring-microservices-realm`

### Bước 4.5 (TODO 4.5) – Tạo client

**Cách A – thao tác UI (bắt buộc biết làm):**

1. Sidebar **Clients** → **Create client**
2. *General settings*: Client type **OpenID Connect**, Client ID **`spring-microservices-client`** → **Next**
3. *Capability config*:
   - **Client authentication: ON** (tạo Client Secret – confidential client)
   - **Authorization: OFF**
   - **Authentication flow**: chỉ tick **Service accounts roles**; bỏ tick *Standard flow*, *Direct access grants*, *Implicit flow*, *OAuth 2.0 Device Authorization Grant*
   - **Next**
4. *Login settings*: để trống → **Save**

**Cách B – import tự động (tùy chọn, tiện khi làm lại nhiều lần):** tạo file **`api-gateway/docker/keycloak/realms/spring-microservices-realm.json`** trước khi `docker compose up`:

```json
{
  "realm": "spring-microservices-realm",
  "enabled": true,
  "clients": [
    {
      "clientId": "spring-microservices-client",
      "enabled": true,
      "protocol": "openid-connect",
      "publicClient": false,
      "clientAuthenticatorType": "client-secret",
      "secret": "mss301-dev-secret-change-me",
      "serviceAccountsEnabled": true,
      "standardFlowEnabled": false,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false
    }
  ]
}
```

> Import chỉ chạy khi realm **chưa tồn tại**. Nếu đã tạo bằng tay, xóa realm (hoặc xóa `volume-data/`) rồi `docker compose up -d` lại.

### Bước 4.6 (TODO 4.6) – Lấy Client Secret

Clients → `spring-microservices-client` → tab **Credentials** → copy **Client Secret**. (*Regenerate* sẽ vô hiệu hóa secret cũ ngay.)

### Bước 4.7 (TODO 4.7) – Token Endpoint & JWKS

Mở: http://localhost:8181/realms/spring-microservices-realm/.well-known/openid-configuration

```json
{
  "issuer": "http://localhost:8181/realms/spring-microservices-realm",
  "token_endpoint": "http://localhost:8181/realms/spring-microservices-realm/protocol/openid-connect/token",
  "jwks_uri": "http://localhost:8181/realms/spring-microservices-realm/protocol/openid-connect/certs"
}
```

- `token_endpoint` → Postman dùng để lấy token
- `jwks_uri` → Gateway dùng để lấy public key verify chữ ký

---

### Bước 4.8 (TODO 4.8) – `api-gateway/pom.xml` hoàn chỉnh

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>
    <groupId>com.fudn</groupId>
    <artifactId>api-gateway</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>api-gateway</name>
    <description>API Gateway - MSS301</description>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2025.1.3</spring-cloud.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
        </dependency>

        <!-- TODO 4.8: OAuth2 Resource Server - Boot 4 doi ten starter (keo theo spring-security) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
        </dependency>

        <!-- ===== TEST ===== -->
        <!-- MockMvc + @AutoConfigureMockMvc (Boot 4, da gom spring-boot-starter-test) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <!-- spring-security-test: jwt() post-processor cho MockMvc -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <!-- WireMock gia lap service dich khi test Gateway -->
        <dependency>
            <groupId>org.wiremock.integrations</groupId>
            <artifactId>wiremock-spring-boot</artifactId>
            <version>4.2.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Bước 4.9 (TODO 4.9) – `application.properties` hoàn chỉnh

```properties
spring.application.name=api-gateway
server.port=9000

# URL cac service dich
services.product.url=http://localhost:8080
services.order.url=http://localhost:8081
services.inventory.url=http://localhost:8082

# TODO 4.9: Issuer URI cua Keycloak realm.
# Spring tu goi {issuer-uri}/.well-known/openid-configuration -> jwks_uri -> tai public key
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8181/realms/spring-microservices-realm

# Actuator: chi mo health
management.endpoints.web.exposure.include=health

# (Tuy chon) debug bao mat khi gap 401
# logging.level.org.springframework.security=DEBUG
```

> ⚠️ `issuer-uri` phải **khớp tuyệt đối** với claim `iss` trong token (cùng `http`, cùng `localhost`, cùng port `8181`, cùng tên realm, không có `/` cuối). Lấy token qua `127.0.0.1:8181` thì `iss` sẽ là `http://127.0.0.1:8181/...` → Gateway trả 401.

### Bước 4.10 (TODO 4.10) – `SecurityConfig`

```
api-gateway/src/main/java/com/fudn/gateway/
├── ApiGatewayApplication.java
├── config/
│   └── SecurityConfig.java     <-- TẠO MỚI
└── routes/
    └── Routes.java
```

File **`config/SecurityConfig.java`**:

```java
package com.fudn.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // REST API dung Bearer token, khong dung cookie/session -> tat CSRF, stateless
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // TODO 4.10: quy tac phan quyen
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll() // health check mo
                        .anyRequest().authenticated()                       // con lai phai co JWT
                )

                // TODO 4.10: Gateway la OAuth2 Resource Server, xac thuc bang JWT
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
```

**Giải thích:**

| Phần | Ý nghĩa |
|---|---|
| `.authorizeHttpRequests(...)` | `/actuator/health` công khai để giám sát; **mọi request khác** (gồm `/api/**`) phải xác thực. Muốn đúng 100% tài liệu gốc thì bỏ dòng `permitAll` |
| `.oauth2ResourceServer(o -> o.jwt(withDefaults()))` | Đọc `Authorization: Bearer`, verify JWT bằng decoder tạo từ `issuer-uri` |
| `Customizer.withDefaults()` | Dùng cấu hình mặc định (JwtDecoder từ properties) |
| `.csrf(disable)` + `STATELESS` | Không lưu session; mỗi request tự mang token |

Ví dụ mở rộng (không áp dụng bây giờ):

```java
authorize -> authorize
    .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()   // xem SP khong can token
    .requestMatchers("/api/admin/**").hasRole("ADMIN")                 // can map role Keycloak -> GrantedAuthority
    .anyRequest().authenticated()
```

### Bước 4.11 (TODO 4.11) – Lấy Access Token bằng Postman

Tạo request → tab **Authorization** → **Type: OAuth 2.0** → **Configure New Token**:

| Trường | Giá trị |
|---|---|
| Token Name | `keycloak-token` |
| Grant Type | **Client Credentials** |
| Access Token URL | `http://localhost:8181/realms/spring-microservices-realm/protocol/openid-connect/token` |
| Client ID | `spring-microservices-client` |
| Client Secret | *(secret ở Bước 4.6)* |
| Scope | *(để trống)* |
| Client Authentication | **Send as Basic Auth header** |

→ **Get New Access Token** → **Use Token**. Token mặc định sống **300 giây (5 phút)**.

Lấy token bằng lệnh (Windows dùng `curl.exe`):

```bash
curl -s -X POST "http://localhost:8181/realms/spring-microservices-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=spring-microservices-client" \
  -d "client_secret=<SECRET>"
```

### Bước 4.12 (TODO 4.12) – Gọi API qua Gateway

```
GET http://localhost:9000/api/products
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

| Kịch bản | Status |
|---|---|
| Không có header `Authorization` | **401** |
| Token giả / sai định dạng / sửa payload | **401** (`invalid_token`) |
| Token hết hạn (> 5 phút) | **401** (`Jwt expired at ...`) |
| Token hợp lệ | **200** (GET) / **201** (POST order) |

Chi tiết từng test case: `part3-4_Test.md` mục T6–T8.

### Bước 4.13 (TODO 4.13, khuyến khích) – Test tự động cho Gateway

Test này **không cần Keycloak lẫn các service thật**: JWT được giả lập bằng `jwt()` của `spring-security-test`, service đích được giả lập bằng WireMock.

File **`api-gateway/src/test/java/com/fudn/gateway/ApiGatewaySecurityTests.java`**:

```java
package com.fudn.gateway;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// Tro ca 3 route sang cung 1 WireMock server (port ngau nhien)
@EnableWireMock(@ConfigureWireMock(baseUrlProperties = {
        "services.product.url", "services.order.url", "services.inventory.url"}))
class ApiGatewaySecurityTests {

    @Autowired
    private MockMvc mockMvc;

    // Thay JwtDecoder that (can Keycloak) bang mock -> test khong phu thuoc Keycloak
    // Boot 4: @MockBean da bi xoa, dung @MockitoBean
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void requestWithValidJwtShouldBeRoutedToProductService() throws Exception {
        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/api/products"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"1\",\"name\":\"iPhone 15\",\"description\":\"Apple\",\"price\":1000}]")));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/products").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("iPhone 15")));

        WireMock.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/api/products")));
    }

    @Test
    void postOrderWithValidJwtShouldBeRoutedToOrderService() throws Exception {
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/api/order"))
                .willReturn(WireMock.aResponse()
                        .withStatus(201)
                        .withBody("Order Placed Successfully")));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/order")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"iphone_15\",\"price\":1000,\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(content().string("Order Placed Successfully"));
    }

    @Test
    void inventoryRouteShouldForwardQueryParams() throws Exception {
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/inventory"))
                .withQueryParam("skuCode", WireMock.equalTo("iphone_15"))
                .withQueryParam("quantity", WireMock.equalTo("1"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/inventory")
                        .param("skuCode", "iphone_15")
                        .param("quantity", "1")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }
}
```

**Giải thích:**
- `jwt()` tạo sẵn `JwtAuthenticationToken` trong SecurityContext → bỏ qua bước verify thật.
- `@MockitoBean JwtDecoder` → context không cần tải JWKS từ Keycloak.
- WireMock (`@EnableWireMock` + `baseUrlProperties`) đóng vai Product/Order/Inventory; `verify` chứng minh Gateway **thật sự forward** request.

Chạy: `cd api-gateway && mvn test` → **5 tests PASS** (không cần Docker).

---

# 5. Cấu trúc thư mục cuối cùng

```
BaiTap/ (hoặc workspace của bạn)
├── product-service/                 (Part1 – không đổi; endpoint /api/products)
├── inventory-service/               (Part1 – không đổi; GET /api/inventory)
├── order-service/
│   ├── pom.xml                                  (Boot 4.1.0, + openfeign, + wiremock-spring-boot, + BOM 2025.1.3)
│   └── src/
│       ├── main/java/com/fudn/orderservice/
│       │   ├── client/InventoryClient.java      (MỚI)
│       │   ├── service/OrderService.java        (SỬA)
│       │   └── OrderServiceApplication.java     (SỬA: @EnableFeignClients)
│       ├── main/resources/application.properties (+ inventory.url)
│       └── test/java/com/fudn/orderservice/
│           ├── stub/InventoryStubs.java         (MỚI)
│           └── OrderServiceApplicationTests.java (SỬA)
└── api-gateway/                                 (PROJECT MỚI)
    ├── docker-compose.yml                       (Keycloak + MySQL)
    ├── docker/keycloak/realms/                  (realm JSON – tùy chọn)
    ├── pom.xml                                  (Boot 4.1.0, gateway-server-webmvc, security-oauth2-resource-server)
    └── src/
        ├── main/java/com/fudn/gateway/
        │   ├── ApiGatewayApplication.java
        │   ├── config/SecurityConfig.java
        │   └── routes/Routes.java
        ├── main/resources/application.properties
        └── test/java/com/fudn/gateway/ApiGatewaySecurityTests.java
```

---

# 6. Lỗi thường gặp & cách xử lý

| Lỗi | Nguyên nhân | Cách xử lý |
|---|---|---|
| `NoSuchBeanDefinitionException: InventoryClient` | Thiếu `@EnableFeignClients` | Thêm vào `OrderServiceApplication` |
| `Spring Boot [4.1.0] is not compatible with this Spring Cloud release train` | Sai `spring-cloud.version` (VD còn `2023.0.x`/`2025.0.x`) | Dùng `2025.1.3` (xem 0.3) |
| App chạy nhưng không có bảng `t_orders` / log không thấy Flyway | Boot 4 thiếu `spring-boot-starter-flyway` | Thêm starter (giữ `flyway-mysql`) |
| `package org.springframework.cloud.contract.wiremock does not exist` | Code/pom kiểu Boot 3 | Dùng `wiremock-spring-boot` + `@EnableWireMock` |
| `cannot find symbol: class MockBean` | Boot 4 đã xóa `@MockBean` | Dùng `@MockitoBean` |
| `package org.springframework.boot.test.autoconfigure.web.servlet does not exist` | Package cũ của `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure` + dependency `spring-boot-starter-webmvc-test` |
| `Could not find artifact org.springframework.cloud:spring-cloud-starter-gateway-mvc` | Tên artifact cũ | `spring-cloud-starter-gateway-server-webmvc` |
| REST Assured lỗi `NoClassDefFoundError: groovy/...` hoặc không map được object | Dùng rest-assured 5.x với Boot 4 | Nâng `rest-assured` lên `6.0.1` |
| `RequestParam.value() was empty on parameter 0` | Không ghi tên `@RequestParam` và không compile `-parameters` | Ghi `@RequestParam("skuCode")` |
| `feign.RetryableException: Connection refused` | Inventory Service chưa chạy / sai `inventory.url` | Start inventory; kiểm tra URL |
| Test Order: `Connection refused` | Chưa trỏ `inventory.url` sang WireMock | `@EnableWireMock(@ConfigureWireMock(baseUrlProperties = "inventory.url"))` |
| Test Order: WireMock trả **404** | URL stub khác URL Feign gửi (thứ tự query, giá trị qty) | So khớp đúng `?skuCode=..&quantity=..`; hoặc dùng `urlPathEqualTo` + `withQueryParam` |
| Test Order: `Could not find a valid Docker environment` | Docker Desktop chưa chạy | Bật Docker Desktop |
| Gateway `GET /api/products` → **404** | Route `/api/product` (thiếu `s`) | Dùng `path("/api/products/**")` |
| Gateway → **500** / `I/O error ... Connection refused` | Service đích chưa chạy | Start service đích trước |
| Keycloak không lưu gì sau khi restart container | Dùng biến `DB_VENDOR/DB_ADDR` cũ → H2 trong container | Dùng `KC_DB*` như Bước 4.1 |
| Keycloak exit ngay khi start | MySQL chưa sẵn sàng | `depends_on: condition: service_healthy` |
| Gateway 401 dù có token | Token hết hạn; hoặc `iss` ≠ `issuer-uri` | Lấy token mới; kiểm tra host/port/realm khớp tuyệt đối |
| Request đầu tiên 401/500, log `Unable to resolve the Configuration with the provided Issuer` | Keycloak chưa chạy / realm sai | Start Keycloak, kiểm tra URL `.well-known` mở được |
| Postman: `unauthorized_client` / `Client not enabled to retrieve service account` | Chưa bật **Service accounts roles** | Client → Settings → bật → Save |
| Không có tab **Credentials** | Client authentication OFF | Bật **Client authentication: ON** → Save |
| `invalid_client` | Sai client secret | Copy lại secret ở tab Credentials |

---

# 7. Câu hỏi ôn tập

1. Khác nhau giữa giao tiếp đồng bộ và bất đồng bộ? Vì sao Order → Inventory chọn đồng bộ? Rủi ro là gì (gợi ý: Phần 5 – Circuit Breaker)?
2. Nếu quên `@EnableFeignClients` thì chuyện gì xảy ra? Vì sao?
3. Tại sao phải externalize `inventory.url`? Trong test ta thay giá trị của nó bằng gì?
4. `urlEqualTo` khác `urlPathEqualTo` thế nào?
5. Kể 4 cross-cutting concern mà API Gateway đảm nhận.
6. Route – Predicate – Filter là gì? Vì sao dùng `/api/products/**` thay vì `/api/products`?
7. OAuth2 khác OIDC ở điểm nào? Project này dùng grant type nào và vì sao?
8. Keycloak, API Gateway, Postman lần lượt đóng vai trò gì trong OAuth2?
9. Resource Server kiểm tra những gì trong một JWT? Public key lấy từ đâu?
10. Vì sao token lấy qua `127.0.0.1:8181` lại bị Gateway (cấu hình `localhost:8181`) từ chối?
