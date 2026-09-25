# Part 2 – Hướng dẫn Testing OpenFeign (Order Service ↔ Inventory Service)

> Gồm 2 phần: **(A)** Test thủ công bằng Postman, **(B)** Viết lại Integration Test với WireMock.

---

## ✅ Checklist tổng quan

- [ ] Phần A – Test thủ công bằng Postman (đủ hàng / hết hàng)
- [ ] Phần B – Hiểu vì sao Integration Test cũ (Part 1) bị fail
- [ ] Phần B – Thêm dependency WireMock (`spring-cloud-starter-contract-stub-runner`)
- [ ] Phần B – Tạo `InventoryStubs` class
- [ ] Phần B – Trỏ `inventory.url` sang WireMock trong test properties
- [ ] Phần B – Viết lại `OrderServiceApplicationTests`
- [ ] Chạy `mvn test` PASS

---

# PHẦN A – Test thủ công bằng Postman

## Bước 6 – Khởi động cả 2 service

```bash
# Terminal 1 - Inventory Service
cd inventory-service
mvn spring-boot:run

# Terminal 2 - Order Service
cd order-service
mvn spring-boot:run
```

> ⚠️ Đảm bảo Docker container `mysql` đã chạy trước (`docker compose up -d mysql` trong thư mục `order-service`), vì cả 2 service đều cần kết nối MySQL.

### TODO
- [ ] Docker container `mysql` đang chạy
- [ ] `inventory-service` chạy thành công trên port 8082
- [ ] `order-service` chạy thành công trên port 8081, không có lỗi `NoSuchBeanDefinitionException`

---

## Test Case 1 – Đơn hợp lệ (đủ hàng)

**Request:**
```
POST http://localhost:8081/api/order
Content-Type: application/json

{
  "skuCode": "iphone_15",
  "price": 1000,
  "quantity": 100
}
```

**Kết quả kỳ vọng:** `201 Created`, body `"Order Placed Successfully"`. Bảng `t_orders` có thêm 1 record mới.

> Vì Part 1 đã seed sẵn `iphone_15` = 100 trong `t_inventory`, quantity=100 vừa đúng bằng tồn kho (điều kiện `>=` trong Inventory Service).

### TODO
- [ ] Gửi request, xác nhận `201 Created`
- [ ] Kiểm tra `t_orders` (qua DBeaver/Adminer) có record mới với `sku_code = iphone_15`
- [ ] Quan sát log Order Service: có dòng gọi HTTP request sang Inventory Service (nếu bật DEBUG log, xem phần cuối bài)

---

## Test Case 2 – Đơn vượt tồn kho

Đổi `quantity` thành `101` (Part 1 chỉ seed mỗi sản phẩm 100):

```
POST http://localhost:8081/api/order
Content-Type: application/json

{
  "skuCode": "iphone_15",
  "price": 1000,
  "quantity": 101
}
```

**Kết quả kỳ vọng:** `500 Internal Server Error`. Log Order Service ghi:
```
java.lang.RuntimeException: Product with Skucode iphone_15 is not in stock
```

### Tại sao 500 chứ không phải 400?

Vì `RuntimeException` không được handle (`@RestControllerAdvice`), Spring mặc định trả về `500`. Trong dự án thực tế nên có handler map exception sang `400 Bad Request`:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handle(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}


### TODO
- [ ] Gửi request với quantity vượt tồn kho, xác nhận `500`
- [ ] Đọc log Order Service, xác nhận đúng message `RuntimeException`
- [ ] Xác nhận **không** có record mới nào được thêm vào `t_orders` (vì `@Transactional` rollback)
- [ ] *(Tùy chọn)* Thêm `GlobalExceptionHandler`, test lại → xác nhận trả về `400`

---

## Cách debug khi response sai

- Kiểm tra Inventory Service có chạy và đúng port 8082 không:
  ```bash
  curl "http://localhost:8082/api/inventory?skuCode=iphone_15&quantity=1"
  ```
- Bật log Feign request (thêm vào `application.properties` của order-service):
  ```properties
  logging.level.com.fudn.orderservice.client.InventoryClient=DEBUG
  ```
- Bật full Feign logging (thêm Bean config):
  ```java
  @Bean
  Logger.Level feignLoggerLevel() {
      return Logger.Level.FULL;
  }
  ```

### TODO
- [ ] Test `curl` gọi trực tiếp Inventory Service, xác nhận trả `true`/`false` đúng
- [ ] *(Tùy chọn)* Bật DEBUG log Feign, quan sát request/response thực tế Feign gửi đi

---

# PHẦN B – Viết lại Integration Test với WireMock

## Bước 7 – Vì sao Integration Test cũ (Part 1) bị fail?

Khi chạy lại `OrderServiceApplicationTests` (từ Part 1), test sẽ **fail**. Vì:
- Test cũ chỉ kiểm tra Order Service chạy độc lập với MySQL Testcontainer.
- Nhưng giờ `placeOrder()` gọi sang `inventoryClient.isInStock(...)` — và trong môi trường test **không có Inventory Service nào chạy** trên `localhost:8082`.
- Kết quả: Feign client throw `ConnectException` → test fail.

### 3 lựa chọn giải quyết

| Lựa chọn | Mô tả | Ưu/Nhược |
|---|---|---|
| 1. Chạy thật Inventory Service | Khởi động service thật trong test | Nặng, chậm, phức tạp |
| 2. Mock bằng Mockito | Mock `InventoryClient` interface | Đơn giản nhưng không test được URL/params đã đúng chưa |
| **3. WireMock (được chọn)** | HTTP server giả lập trong test, stub response | Test ở mức HTTP thật, verify params, stub lỗi 4xx/5xx |

### TODO
- [ ] Hiểu lý do test cũ fail (đọc log lỗi `ConnectException` nếu chạy thử `mvn test` lúc này)

---

## Bước 8 – Thêm WireMock dependency

Trong `order-service/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-contract-stub-runner</artifactId>
    <scope>test</scope>
</dependency>
```

### Giải thích: tên dependency lạ — "contract-stub-runner"?

Đây là 1 module của **Spring Cloud Contract** — nhóm tính năng test contract giữa các service. Nó đóng gói WireMock kèm tích hợp Spring (auto-configure 1 WireMock server lúc test).

Có thể dùng WireMock bare-bone (`com.github.tomakehurst:wiremock`) nhưng phải tự setup server. Dùng module Spring Cloud thì chỉ cần annotation `@AutoConfigureWireMock` là Spring tự khởi động/dừng WireMock cùng test.

### TODO
- [ ] Thêm dependency `spring-cloud-starter-contract-stub-runner` (scope `test`)
- [ ] Reload Maven

---

## Bước 9 – Tạo class chứa các stub WireMock

Tạo file `order-service/src/test/java/com/fudn/orderservice/stub/InventoryStubs.java`:

```java
package com.fudn.orderservice.stub;

import lombok.experimental.UtilityClass;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

@UtilityClass
public class InventoryStubs {

    public void stubInventoryCall(String skuCode, Integer quantity) {
        stubFor(get(urlEqualTo(
                "/api/inventory?skuCode=" + skuCode + "&quantity=" + quantity))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));
    }
}
```

### Giải thích DSL của WireMock

- `stubFor(...)` — đăng ký 1 rule
- `get(urlEqualTo("..."))` — khi có HTTP GET tới URL chính xác này...
- `.willReturn(aResponse()...)` — ...thì trả về response như sau
- `.withStatus(200)` — HTTP 200
- `.withBody("true")` — body là chuỗi `true`

### `urlEqualTo` vs `urlPathEqualTo`

- `urlPathEqualTo("/api/inventory")` — match path, **bỏ qua** query params
- `urlEqualTo("/api/inventory?skuCode=X&quantity=1")` — match path **và** query params chính xác

Dùng `urlEqualTo` nghiêm ngặt hơn — nếu Feign client gửi sai params, stub không match → trả về `404` → test fail. Đây là cách gián tiếp verify Feign config đúng.

**⚠️ Cảnh báo về thứ tự query param:** WireMock so sánh URL như chuỗi — nếu thứ tự `?skuCode=X&quantity=1` bị đảo ngược sẽ **không match**. Cách an toàn hơn là dùng `withQueryParam()` để match từng param độc lập:

```java
stubFor(get(urlPathEqualTo("/api/inventory"))
        .withQueryParam("skuCode", equalTo(skuCode))
        .withQueryParam("quantity", equalTo(quantity.toString()))
        .willReturn(...));
```

### TODO
- [ ] Tạo package `com.fudn.orderservice.stub` trong `src/test/java`
- [ ] Tạo class `InventoryStubs` với method `stubInventoryCall`
- [ ] *(Tùy chọn nâng cao)* Đổi sang dùng `withQueryParam()` để tránh lỗi thứ tự query param

---

## Bước 10 – Trỏ `inventory.url` về WireMock trong test

Tạo (hoặc cập nhật) file `order-service/src/test/resources/application.properties`:

```properties
inventory.url=http://localhost:${wiremock.server.port}
```

### Giải thích

`${wiremock.server.port}` là property đặc biệt do `@AutoConfigureWireMock(port = 0)` tự set tại runtime. Khi WireMock khởi động với `port = 0`, OS cấp port ngẫu nhiên — Spring expose port này ra dưới tên `wiremock.server.port` để property khác reference.

**Tại sao phải random port?** Để chạy nhiều test class song song không bị xung đột port. Hard-code port `9999` thì khi 2 test class chạy cùng lúc, 1 trong 2 sẽ fail vì port đã bị chiếm.

### TODO
- [ ] Tạo thư mục `src/test/resources/` nếu chưa có
- [ ] Tạo file `application.properties` trong đó với dòng `inventory.url=http://localhost:${wiremock.server.port}`

---

## Bước 11 – Viết lại Integration Test

Cập nhật `order-service/src/test/java/com/fudn/orderservice/OrderServiceApplicationTests.java`:

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
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.testcontainers.containers.MySQLContainer;

import static org.hamcrest.MatcherAssert.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
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

        InventoryStubs.stubInventoryCall("iphone_15", 1);

        var responseBodyString = RestAssured.given()
                .contentType("application/json")
                .body(submitOrderJson)
                .when()
                .post("/api/order")
                .then()
                .log().all()
                .statusCode(201)
                .extract().body().asString();

        assertThat(responseBodyString, Matchers.is("Order Placed Successfully"));
    }
}
```

### Giải thích từng annotation

| Annotation | Ý nghĩa |
|---|---|
| `@SpringBootTest(RANDOM_PORT)` | Khởi động full Spring context như chạy app thật. Web server chạy ở port ngẫu nhiên. |
| `@AutoConfigureWireMock(port = 0)` | Tự khởi động WireMock với port ngẫu nhiên. Expose ra property `wiremock.server.port`. |
| `@ServiceConnection` | Tự cấu hình `spring.datasource.*` từ Testcontainer. Có từ Spring Boot 3.1+. |
| `@LocalServerPort` | Inject port ngẫu nhiên của Tomcat test vào biến. |

### Flow trong test (từng bước)

1. Test khởi động → Testcontainer pull MySQL → start MySQL container
2. Spring Boot start, kết nối MySQL Testcontainer
3. WireMock start ở port random, set `wiremock.server.port`
4. Feign client của Order Service lúc inject sẽ lấy URL `http://localhost:<random>`
5. `stubInventoryCall(...)` đăng ký rule: khi có `GET /api/inventory?skuCode=iphone_15&quantity=1` → trả `200` + body `true`
6. RestAssured gọi `POST /api/order` đến Order Service
7. Order Service nội bộ gọi Feign → request đi tới WireMock → WireMock trả `true`
8. Order Service save vào MySQL Testcontainer → trả về `201`
9. Verify response = `"Order Placed Successfully"`

### TODO
- [ ] Cập nhật lại `OrderServiceApplicationTests.java` theo code trên
- [ ] Xác nhận import đúng package `com.fudn.orderservice.stub.InventoryStubs`
- [ ] Gọi `InventoryStubs.stubInventoryCall(...)` **trước** khi gửi request POST (thứ tự quan trọng)

---

## Bước 12 – Chạy test và xác nhận

```bash
cd order-service
mvn test
```

> Lần đầu sẽ chậm (pull image MySQL, Maven download WireMock). Lần sau cache nhanh hơn.

### Troubleshooting khi test fail

| Triệu chứng | Nguyên nhân & Cách xử lý |
|---|---|
| `ConnectException: Connection refused` | WireMock chưa start hoặc URL sai → kiểm tra `src/test/resources/application.properties` có `${wiremock.server.port}` chưa |
| `404 Not Found` từ Feign | URL stub không khớp request thực — đối chiếu params, path (dùng `withQueryParam()` nếu nghi ngờ thứ tự param) |
| Test pass nhưng vẫn lo ngại | Bật log Feign DEBUG để xem request thực Feign gửi, đối chiếu với stub |
| Lỗi MySQL / container fail | Docker chưa chạy. Kiểm tra: `docker info` |

### TODO
- [ ] `mvn test` chạy PASS
- [ ] Nếu fail, đối chiếu bảng Troubleshooting ở trên
- [ ] *(Tùy chọn)* Viết thêm test case "hết hàng" (`stubInventoryCall` trả `false`) để verify nhánh `throw RuntimeException`

---

## ✅ Checklist hoàn thành Testing Part 2

- [ ] Test thủ công Postman: đơn đủ hàng → `201`
- [ ] Test thủ công Postman: đơn vượt tồn kho → `500` + đúng message exception
- [ ] Xác nhận đơn hết hàng **không** được lưu vào DB (rollback nhờ `@Transactional`)
- [ ] Dependency `spring-cloud-starter-contract-stub-runner` đã thêm
- [ ] `InventoryStubs` đã tạo, dùng `urlEqualTo` (hoặc `withQueryParam` nâng cao)
- [ ] `src/test/resources/application.properties` trỏ `inventory.url` sang WireMock
- [ ] `OrderServiceApplicationTests` viết lại, có gọi `stubInventoryCall` trước khi test
- [ ] `mvn test` chạy PASS toàn bộ
- [ ] Hiểu được flow test end-to-end (9 bước ở Bước 11)

---

## Gợi ý mở rộng (không bắt buộc)

- Viết thêm test case gọi `placeOrder()` khi Inventory trả `false` → verify exception được throw đúng (`assertThrows` hoặc kiểm tra response `500`).
- Viết thêm stub cho trường hợp Inventory Service trả lỗi `503 Service Unavailable` để mô phỏng service down — chuẩn bị tư duy cho Part 3 (Resilience4j Circuit Breaker).
