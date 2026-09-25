# Part 2 – Giao tiếp đồng bộ với OpenFeign (Order Service → Inventory Service)

> > **Tiền đề:** đã hoàn thành Part 1 — `order-service` (8081, MySQL) và `inventory-service` (8082, endpoint `GET /api/inventory?skuCode=...&quantity=...` trả `boolean`).

---

## Mục tiêu

Order Service cần gọi sang Inventory Service để kiểm tra tồn kho **trước khi** tạo đơn hàng. Đây là giao tiếp **đồng bộ (synchronous)** — Order Service phải đợi response của Inventory Service rồi mới quyết định lưu đơn hay từ chối.

## Vì sao dùng OpenFeign?

| Cách gọi REST | Mức trừu tượng | Mã viết |
|---|---|---|
| HttpURLConnection (JDK) | Thấp | Nhiều, lặp lại |
| RestTemplate / WebClient | Trung bình | Vẫn phải tự build URL, params, parse JSON |
| **OpenFeign** | Cao — khai báo (declarative) | Chỉ khai báo interface, Spring tự sinh implementation |

Chỉ cần khai báo interface kiểu:
```java
@FeignClient("inventory")
interface InventoryClient {
    @GetMapping("/api/inventory")
    boolean isInStock(@RequestParam String skuCode, @RequestParam Integer quantity);
}
```
rồi inject và gọi như method bình thường — Spring tự sinh class thực thi HTTP request bên dưới.

**Lợi ích:** ít boilerplate, type-safe (sai kiểu tham số → lỗi compile), dễ test (swap implementation), tích hợp sẵn với Eureka/Resilience4j sau này.
**Nhược điểm:** vì là interface "ảo" nên debug khi lỗi khó hơn RestTemplate truyền thống.

---

## ✅ Checklist tổng quan

- [ ] Bước 1 – Thêm dependency + BOM OpenFeign vào `order-service/pom.xml`
- [ ] Bước 2 – Tạo `InventoryClient` (FeignClient interface)
- [ ] Bước 3 – Thêm property `inventory.url`
- [ ] Bước 4 – Gọi `InventoryClient` trong `OrderService.placeOrder()`
- [ ] Bước 5 – Bật `@EnableFeignClients` ở class main
- [ ] Build project thành công, không lỗi Maven
- [ ] (Testing) Xem file `Part2_OpenFegin_Guide.md` để test thủ công + viết lại Integration Test

---

## BƯỚC 1 – Thêm dependency OpenFeign vào Order Service

Mở `order-service/pom.xml`, thêm 2 phần:

### 1.1. Dependency

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

### 1.2. BOM (Bill of Materials) trong `<dependencyManagement>`

```xml
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
```

Và trong `<properties>`:
```xml
<spring-cloud.version>2023.0.0</spring-cloud.version>
```

> ⚠️ Vì project đã tạo trong Part 1 dùng **Spring Boot 3.3.4**, theo bảng tương thích bên dưới nên dùng `spring-cloud.version = 2023.0.3` (hoặc bản mới nhất trong dòng 2023.0.x) thay vì 2023.0.0 để tránh lỗi tương thích nhỏ. Kiểm tra version mới nhất tại https://spring.io/projects/spring-cloud trước khi build.

### Giải thích: BOM là gì và tại sao cần?

BOM là 1 file POM đặc biệt chỉ chứa danh sách version của nhiều dependency liên quan.

- Spring Cloud gồm hàng chục module (OpenFeign, Eureka, Gateway, Config...) — mỗi module có version riêng nhưng phải tương thích với nhau và với Spring Boot.
- Lấy nhầm version → conflict, lỗi runtime khó debug.
- BOM giúp chỉ định version ở **1 chỗ duy nhất** (`spring-cloud.version`), mọi module Spring Cloud tự lấy version tương thích.

**Bảng tương thích (tham khảo):**

| Spring Boot | Spring Cloud |
|---|---|
| 3.5.x | 2024.0.x |
| 3.4.x | 2024.0.x |
| 3.3.x | 2023.0.x |
| 3.2.x | 2023.0.x |

### TODO
- [ ] Thêm dependency `spring-cloud-starter-openfeign`
- [ ] Thêm `dependencyManagement` với BOM `spring-cloud-dependencies`
- [ ] Thêm property `spring-cloud.version` khớp với Spring Boot 3.3.x
- [ ] Reload Maven (IDE tự refresh, hoặc chạy `mvn clean install`)

---

## BƯỚC 2 – Tạo FeignClient interface cho Inventory Service

Tạo package mới `com.fudn.orderservice.client`, trong đó tạo interface `InventoryClient`:

`src/main/java/com/fudn/orderservice/client/InventoryClient.java`:

```java
package com.fudn.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "inventory", url = "${inventory.url}")
public interface InventoryClient {

    @RequestMapping(method = RequestMethod.GET, value = "/api/inventory")
    boolean isInStock(@RequestParam String skuCode, @RequestParam Integer quantity);
}
```

### Giải thích: Tại sao là interface, không phải class?

OpenFeign hoạt động theo cơ chế **dynamic proxy**. Lúc Spring khởi động, nó scan các interface có `@FeignClient`, dùng Java Reflection sinh ra 1 class implement interface đó. Class này biết cách:
- Build HTTP URL từ `@RequestMapping` + tham số `@RequestParam`
- Gửi HTTP request
- Parse response JSON về kiểu return (`boolean`)

Vì proxy được tạo lúc runtime nên ta chỉ cần "khai báo cách gọi" qua interface, không cần tự viết code thực hiện.

### Ý nghĩa attribute của `@FeignClient`

| Attribute | Vai trò |
|---|---|
| `value = "inventory"` | Tên logic của service. Khi có Eureka (Part 3) thì tên này phải khớp `spring.application.name` của service đích. Hiện tại chưa có Eureka nên chỉ là label. |
| `url = "${inventory.url}"` | URL cứng. Khi chưa có service discovery, hard-code URL nhưng externalize vào properties. Khi có Eureka, bỏ attribute `url` để Feign lấy URL từ Eureka. |

**Tại sao externalize `url` vào property thay vì hard-code trong code?**
- Dev: `http://localhost:8082`
- Staging: `http://inventory-staging.internal:8080`
- Production: `http://inventory.prod.svc.cluster.local`
- Test: `http://localhost:${wiremock.port}` (port ngẫu nhiên)

Hard-code thì mỗi lần đổi môi trường phải build lại code. Externalize ra property/env var thì chỉ cần sửa config lúc deploy — đây là pattern **12-factor app**.

### Giải thích method signature

- `@RequestMapping(method = GET, value = "/api/inventory")` — có thể thay bằng `@GetMapping("/api/inventory")` cho ngắn gọn.
- `@RequestParam String skuCode` — Feign build URL thành `?skuCode=xxx`.
- `@RequestParam Integer quantity` — tương tự, `&quantity=N`.
- Return `boolean` — Feign parse body response (JSON `true`/`false`) về `boolean`.

→ Endpoint cuối cùng được gọi: `GET http://localhost:8082/api/inventory?skuCode=iphone_15&quantity=100`

### TODO
- [ ] Tạo package `com.fudn.orderservice.client`
- [ ] Tạo interface `InventoryClient` với annotation `@FeignClient`
- [ ] Method `isInStock` khớp đúng signature endpoint bên Inventory Service

---

## BƯỚC 3 – Thêm property `inventory.url`

Mở `order-service/src/main/resources/application.properties`, thêm:

```properties
inventory.url=http://localhost:8082
```

### Giải thích

Spring sẽ thay `${inventory.url}` trong annotation `@FeignClient` bằng giá trị này. Có thể override bằng:
- Environment variable: `INVENTORY_URL=http://other-host:8082`
- Command-line arg: `--inventory.url=http://other:8082`
- Profile-specific file: `application-prod.properties` ghi đè khi chạy với `-Dspring.profiles.active=prod`

Đây tiếp tục là pattern 12-factor app — config nằm trong environment, không trong code.

### TODO
- [ ] Thêm dòng `inventory.url=http://localhost:8082` vào `application.properties`

---

## BƯỚC 4 – Gọi FeignClient từ OrderService

Mở `OrderService.java`, inject `InventoryClient` và dùng trong `placeOrder()`:

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
    private final InventoryClient inventoryClient; // <-- thêm

    public void placeOrder(OrderRequest orderRequest) {
        boolean inStock = inventoryClient.isInStock(
                orderRequest.skuCode(),
                orderRequest.quantity());

        if (inStock) {
            var order = mapToOrder(orderRequest);
            orderRepository.save(order);
        } else {
            throw new RuntimeException(
                    "Product with Skucode " + orderRequest.skuCode() + " is not in stock");
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

### Giải thích

**`@RequiredArgsConstructor`** (Lombok) sinh constructor nhận tất cả field `final`. Chỉ cần khai báo `private final InventoryClient inventoryClient;` là Spring tự inject qua constructor — không cần `@Autowired`.

**Vì sao có `@Transactional`?** Khi `orderRepository.save(order)` chạy trong transaction:
- Save thành công → commit transaction → đơn được lưu.
- Xảy ra `RuntimeException` (vd: DB lỗi) → rollback → đơn **không** bị lưu nửa vời.

### ⚠️ Vấn đề lớn của giao tiếp đồng bộ

Order Service giờ **phụ thuộc availability** của Inventory Service. Nếu Inventory down → toàn bộ flow đặt hàng down theo (cascading failure). Đây là lý do thực tế thường ưu tiên giao tiếp **bất đồng bộ** qua message queue (Kafka, RabbitMQ). Đồng bộ chỉ nên dùng khi cần kết quả ngay và chấp nhận Inventory phải luôn sống.

### Cải tiến có thể làm sau này (không bắt buộc ở Part 2)
- Thêm `@Retry` (Resilience4j) — tự retry khi lỗi mạng.
- Thêm `@CircuitBreaker` — nếu Inventory liên tục lỗi thì dừng gọi 30s rồi mới gọi lại.
- Thay `RuntimeException` bằng custom `OutOfStockException` + `@ResponseStatus(BAD_REQUEST)`.

### TODO
- [ ] Inject `InventoryClient` vào `OrderService` qua constructor (final field)
- [ ] Gọi `isInStock()` trước khi save order
- [ ] Throw `RuntimeException` khi hết hàng (`else` branch)

---

## BƯỚC 5 – Bật Feign Client với `@EnableFeignClients`

Mở class main `OrderServiceApplication.java`, thêm annotation:

```java
package com.fudn.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients // <-- thêm
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

### Giải thích

Tại sao cần annotation này dù đã thêm dependency? Spring Boot auto-config phần lớn mọi thứ, nhưng OpenFeign cần biết **scan package nào** để tìm interface `@FeignClient`. Mặc định `@EnableFeignClients` scan package chứa class main và các package con.

Custom scan path nếu interface ở package khác:
```java
@EnableFeignClients(basePackages = "com.fudn.orderservice.client")
```

**Lỗi thường gặp khi quên annotation này:**
```
NoSuchBeanDefinitionException: No qualifying bean of type 'InventoryClient' available
```
→ Spring không sinh proxy nên không có bean để inject vào `OrderService`.

### TODO
- [ ] Thêm `@EnableFeignClients` vào `OrderServiceApplication`
- [ ] Build & chạy thử `mvn spring-boot:run`, không thấy lỗi `NoSuchBeanDefinitionException`

---

## ✅ Checklist hoàn thành Part 2 (phần cài đặt OpenFeign)

- [ ] `pom.xml` có dependency `spring-cloud-starter-openfeign` + BOM `spring-cloud-dependencies` đúng version tương thích Spring Boot 3.3.x
- [ ] Package `com.fudn.orderservice.client` chứa interface `InventoryClient` với `@FeignClient(value = "inventory", url = "${inventory.url}")`
- [ ] `application.properties` có `inventory.url=http://localhost:8082`
- [ ] `OrderService` inject `InventoryClient`, gọi `isInStock()` trước khi save, throw exception khi hết hàng
- [ ] `OrderServiceApplication` có `@EnableFeignClients`
- [ ] `mvn clean verify` (hoặc build trong IDE) không lỗi
- [ ] Cả `inventory-service` và `order-service` khởi động được cùng lúc không xung đột port


## Tổng kết Part 2 và chuẩn bị Part 3

**Đã làm được:**
- Order Service gọi Inventory Service đồng bộ qua OpenFeign
- Hiểu rõ vì sao cần BOM, vì sao dùng interface, vì sao externalize URL

**Vấn đề còn lại (giải quyết ở Part 3+):**

| Vấn đề | Giải pháp |
|---|---|
| Hard-code URL — nếu có nhiều instance Inventory thì sao? | Service Discovery với Eureka |
| Không load balancing — luôn gọi 1 URL cố định | Eureka + Spring Cloud LoadBalancer |
| Không retry — lỡ lỗi mạng → đơn fail luôn | Resilience4j Retry |
| Coupling đồng bộ chặt — Inventory down kéo Order down | Async + Kafka (chương sau) |

