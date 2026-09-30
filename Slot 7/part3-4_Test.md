# Part 3 & Part 4 – Hướng dẫn Test các chức năng

> Đi kèm `Part3-4.md`. Gồm **test tự động** (Maven: WireMock, MockMvc) và **test thủ công** (Postman / curl) cho từng yêu cầu TODO.
> **Phiên bản:** Spring Boot **4.1.0** · Spring Cloud **2025.1.3** · Testcontainers 2 · `wiremock-spring-boot` 4.2.3 · REST Assured 6.0.1 (chi tiết: `Part3-4.md` mục 0.3–0.4).
> Mỗi test case có: **mục đích → các bước → kết quả mong đợi → cách xác nhận**.

---

## Mục lục

- [0. Chuẩn bị môi trường](#0-chuẩn-bị-môi-trường)
- [1. Ma trận test theo TODO](#1-ma-trận-test-theo-todo)
- [T1. Build & khởi động từng service](#t1-build--khởi-động-từng-service)
- [T2. Inventory Service trả lời đúng (tiền đề cho OpenFeign)](#t2-inventory-service-trả-lời-đúng)
- [T3. OpenFeign: Order → Inventory (Postman)](#t3-openfeign-order--inventory)
- [T4. Integration test Order Service với WireMock (mvn test)](#t4-integration-test-order-service-với-wiremock)
- [T5. API Gateway routing (Phần 3 – chưa bảo mật)](#t5-api-gateway-routing-phần-3)
- [T6. Keycloak: realm, client, token](#t6-keycloak-realm-client-token)
- [T7. Gateway bảo mật – các case 401](#t7-gateway-bảo-mật--các-case-401)
- [T8. Gateway bảo mật – các case thành công (end-to-end)](#t8-gateway-bảo-mật--các-case-thành-công)
- [T9. Test tự động Gateway (MockMvc + WireMock)](#t9-test-tự-động-gateway)
- [T10. Postman Collection gợi ý](#t10-postman-collection-gợi-ý)
- [11. Checklist nghiệm thu](#11-checklist-nghiệm-thu)
- [12. Xử lý khi test fail](#12-xử-lý-khi-test-fail)

---

## 0. Chuẩn bị môi trường

| Công cụ | Kiểm tra | Ghi chú |
|---|---|---|
| JDK 21 (≥ 17) | `java -version` | Spring Boot 4.1 yêu cầu Java 17+ |
| Maven 3.9+ | `mvn -v` (hoặc dùng `mvnw`) | |
| Docker Desktop | `docker ps` | Cần cho MySQL, MongoDB, Keycloak và Testcontainers |
| Postman | — | Hoặc `curl` |
| (tùy chọn) DBeaver | — | Xem bảng `t_orders` |

> **Windows / PowerShell:** `curl` trong PowerShell là alias của `Invoke-WebRequest`. Luôn gõ **`curl.exe`** cho các lệnh bên dưới, và đặt JSON trong nháy đơn: `-d '{\"skuCode\":\"iphone_15\"}'` (escape `"` bằng `\"`). Hoặc chạy trong **Git Bash** để dùng y nguyên lệnh.

**Bảng port:**

| Thành phần | Port | Khởi động bằng |
|---|---|---|
| MySQL (order + inventory) | 3306 | `order-service/docker-compose.yml` |
| MongoDB (product) | 27017 | `product-service/docker-compose.yml` |
| Product Service | 8080 | `mvn spring-boot:run` |
| Order Service | 8081 | `mvn spring-boot:run` |
| Inventory Service | 8082 | `mvn spring-boot:run` |
| Keycloak | 8181 | `api-gateway/docker-compose.yml` |
| API Gateway | 9000 | `mvn spring-boot:run` |

**Dữ liệu seed Inventory** (`V2__add_inventory.sql`): `iphone_15`, `pixel_8`, `galaxy_24`, `oneplus_12` – mỗi mã **100**.

---

## 1. Ma trận test theo TODO

| TODO | Chức năng | Test case | Loại |
|---|---|---|---|
| 3.1–3.5 | OpenFeign cấu hình đúng, app khởi động | T1.2 | Thủ công |
| 3.4 | Còn hàng → lưu đơn | T3.1, T3.2 | Thủ công |
| 3.4 | Hết hàng → lỗi | T3.3, T3.4 | Thủ công |
| 3.4 | Inventory chết → lỗi | T3.5 | Thủ công |
| 3.5 | Thiếu `@EnableFeignClients` → lỗi | T3.6 (thử nghiệm âm) | Thủ công |
| 3.7–3.9 | WireMock – đơn thành công | T4 `shouldSubmitOrder` | Tự động |
| 3.8–3.9 | WireMock – hết hàng | T4 `shouldFailOrderWhenProductIsNotInStock` | Tự động |
| 3.12–3.13 | Routing products / order / inventory | T5.1 – T5.4 | Thủ công |
| 3.13 | Path không có route → 404 | T5.5 | Thủ công |
| 4.1–4.3 | Keycloak chạy, đăng nhập được | T6.1 | Thủ công |
| 4.4–4.7 | Realm, client, discovery | T6.2, T6.3 | Thủ công |
| 4.11 | Lấy token Client Credentials | T6.4, T6.5 | Thủ công |
| 4.11 | Sai secret → không lấy được token | T6.6 | Thủ công |
| 4.10, 4.12 | Không token / token rác / token sửa / hết hạn → 401 | T7.1 – T7.5 | Thủ công |
| 4.10 | Health public | T7.6 | Thủ công |
| 4.12 | Có token → 200 / 201, end-to-end | T8.1 – T8.4 | Thủ công |
| 4.13 | Security + routing Gateway | T9 (5 tests) | Tự động |

---

## T1. Build & khởi động từng service

### T1.1 – Hạ tầng Docker

```bash
cd order-service   && docker compose up -d     # MySQL
cd ../product-service && docker compose up -d  # MongoDB
docker ps
```

**Mong đợi:** container `mysql` và mongo ở trạng thái `Up`.

### T1.2 – Build & chạy service (sau khi làm TODO 3.1–3.5)

```bash
# Terminal 1
cd inventory-service && mvn spring-boot:run
# Terminal 2
cd product-service   && mvn spring-boot:run
# Terminal 3
cd order-service     && mvn clean compile && mvn spring-boot:run
```

**Mong đợi:**
- `mvn clean compile` của order-service: `BUILD SUCCESS` (chứng tỏ Boot 4.1.0 + BOM 2025.1.3 + openfeign resolve được).
- Log có dòng Flyway `Successfully validated ... migration` / `Schema ... is up to date` (chứng tỏ đã có `spring-boot-starter-flyway` – Boot 4 thiếu starter này thì Flyway **im lặng không chạy**).
- Kiểm tra nhanh cây dependency: `mvn dependency:tree -Dincludes=org.springframework.cloud` → các artifact `spring-cloud-*` đều ở `5.0.x`.
- Log order-service có `Tomcat started on port 8081`, **không** có `NoSuchBeanDefinitionException` hay `is not compatible with this Spring Cloud release train`.

- [ ] Inventory up :8082 · [ ] Product up :8080 · [ ] Order up :8081

---

## T2. Inventory Service trả lời đúng

Mục đích: xác nhận "server" mà Feign sẽ gọi hoạt động đúng trước khi test Order.

| # | Request | Mong đợi |
|---|---|---|
| T2.1 | `GET http://localhost:8082/api/inventory?skuCode=iphone_15&quantity=100` | `200`, body `true` |
| T2.2 | `GET http://localhost:8082/api/inventory?skuCode=iphone_15&quantity=101` | `200`, body `false` |
| T2.3 | `GET http://localhost:8082/api/inventory?skuCode=khong_ton_tai&quantity=1` | `200`, body `false` |

```bash
curl.exe -s "http://localhost:8082/api/inventory?skuCode=iphone_15&quantity=100"
curl.exe -s "http://localhost:8082/api/inventory?skuCode=iphone_15&quantity=101"
```

---

## T3. OpenFeign: Order → Inventory

### T3.1 – Đặt hàng thành công (quantity = 100, đúng bằng tồn kho)

```
POST http://localhost:8081/api/order
Content-Type: application/json

{ "skuCode": "iphone_15", "price": 1000, "quantity": 100 }
```

```bash
curl.exe -i -X POST http://localhost:8081/api/order -H "Content-Type: application/json" \
  -d "{\"skuCode\":\"iphone_15\",\"price\":1000,\"quantity\":100}"
```

**Mong đợi:** `HTTP/1.1 201`, body `Order Placed Successfully`.

### T3.2 – Xác nhận dữ liệu đã lưu

```bash
docker exec -it mysql mysql -uroot -pmysql -e "SELECT id, order_number, sku_code, price, quantity FROM order_service.t_orders ORDER BY id DESC LIMIT 3;"
```

**Mong đợi:** có bản ghi mới `sku_code = iphone_15`, `quantity = 100`, `order_number` dạng UUID.

### T3.3 – Hết hàng (quantity = 101)

```bash
curl.exe -i -X POST http://localhost:8081/api/order -H "Content-Type: application/json" \
  -d "{\"skuCode\":\"iphone_15\",\"price\":1000,\"quantity\":101}"
```

**Mong đợi:**
- `HTTP/1.1 500` (hoặc `409` nếu đã làm phần mở rộng `GlobalExceptionHandler`)
- Log order-service: `java.lang.RuntimeException: Product with SkuCode iphone_15 is not in stock`
- **Không** có bản ghi mới trong `t_orders` (chạy lại lệnh T3.2 để kiểm tra).

### T3.4 – SKU không tồn tại

Body `{ "skuCode": "nokia_3310", "price": 50, "quantity": 1 }` → **Mong đợi:** như T3.3 (500, message chứa `nokia_3310`).

### T3.5 – Inventory Service ngừng hoạt động

1. Dừng inventory-service (Ctrl+C ở Terminal 1).
2. Gửi lại request T3.1 (quantity 1).

**Mong đợi:** `500`; log order-service có `feign.RetryableException: Connection refused ... executing GET http://localhost:8082/api/inventory?...`.
→ Minh họa nhược điểm **temporal coupling** của giao tiếp đồng bộ (sẽ xử lý bằng Circuit Breaker ở Phần 5).
3. Khởi động lại inventory-service trước khi test tiếp.

### T3.6 – (Thử nghiệm âm) Thiếu `@EnableFeignClients`

1. Comment dòng `@EnableFeignClients` → chạy order-service.
2. **Mong đợi:** app **không khởi động**, log: `Parameter 1 of constructor in ...OrderService required a bean of type '...InventoryClient' that could not be found.`
3. Bỏ comment, chạy lại → OK.

### (Tùy chọn) Quan sát request Feign

Thêm vào `order-service/application.properties`:
```properties
logging.level.com.fudn.orderservice.client=DEBUG
spring.cloud.openfeign.client.config.inventory.logger-level=full
```
Gửi T3.1 → log hiện `---> GET http://localhost:8082/api/inventory?skuCode=iphone_15&quantity=100 HTTP/1.1` và `<--- HTTP/1.1 200 ... true`.

---

## T4. Integration test Order Service với WireMock

**Yêu cầu:** Docker Desktop đang chạy (Testcontainers tạo MySQL tạm). **Không cần** inventory-service chạy – đó chính là mục đích của WireMock.

```bash
cd order-service
mvn clean test
```

**Mong đợi:**

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| Test | Stub WireMock | Kỳ vọng |
|---|---|---|
| `shouldSubmitOrder` | `GET /api/inventory?skuCode=iphone_15&quantity=1` → `true` | `201`, body `Order Placed Successfully`, `verify` thấy đúng 1 request tới WireMock |
| `shouldFailOrderWhenProductIsNotInStock` | `GET /api/inventory?skuCode=iphone_15&quantity=1000` → `false` | `500` |

Chạy riêng 1 test:
```bash
mvn test -Dtest=OrderServiceApplicationTests#shouldSubmitOrder
```

**Thử nghiệm âm để hiểu WireMock (khuyến khích):**

| Thay đổi thử | Kết quả sẽ thấy | Bài học |
|---|---|---|
| Xóa dòng `InventoryStubs.stubInventoryCall(...)` | Test fail: `500`; log WireMock `Request was not matched` | Không có stub → WireMock trả 404 → Feign ném `FeignException$NotFound` |
| Stub `quantity=2` nhưng body gửi `quantity=1` | Fail tương tự | `urlEqualTo` so khớp chính xác cả query |
| Bỏ `baseUrlProperties = "inventory.url"` (chỉ để `@EnableWireMock`) | Fail: `Connection refused localhost:8082` | Phải trỏ `inventory.url` sang WireMock |

Báo cáo: `order-service/target/surefire-reports/`.

---

## T5. API Gateway routing (Phần 3)

> Test này áp dụng cho **trạng thái cuối Phần 3** (chưa có dependency `oauth2-resource-server`). Nếu bạn đã làm xong Phần 4, mọi request dưới đây sẽ trả **401** – chuyển sang T8 (thêm header Bearer) để kiểm tra routing.

Khởi động theo thứ tự: Inventory → Product → Order → `cd api-gateway && mvn spring-boot:run` (log: `Tomcat started on port 9000`).

| # | Request qua Gateway | So sánh với gọi trực tiếp | Mong đợi |
|---|---|---|---|
| T5.1 | `GET http://localhost:9000/api/products` | `GET :8080/api/products` | `200`, **cùng** JSON danh sách sản phẩm |
| T5.2 | `POST http://localhost:9000/api/products` body `{"name":"iPhone 15","description":"iPhone 15 is a smartphone from Apple","price":1000}` | — | `201`, JSON sản phẩm có `id` |
| T5.3 | `POST http://localhost:9000/api/order` body `{"skuCode":"iphone_15","price":1000,"quantity":1}` | `POST :8081/api/order` | `201`, `Order Placed Successfully` |
| T5.4 | `GET http://localhost:9000/api/inventory?skuCode=iphone_15&quantity=1` | `GET :8082/...` | `200`, `true` (query param được forward nguyên vẹn) |
| T5.5 | `GET http://localhost:9000/api/khong-co-route` | — | `404` (không predicate nào khớp) |
| T5.6 | `PUT http://localhost:9000/api/products/{id}` (nếu Part1 đã làm TODO update) | `PUT :8080/api/products/{id}` | Giống gọi trực tiếp – chứng minh `/**` route được path con |

```bash
curl.exe -s http://localhost:9000/api/products
curl.exe -i -X POST http://localhost:9000/api/order -H "Content-Type: application/json" \
  -d "{\"skuCode\":\"iphone_15\",\"price\":1000,\"quantity\":1}"
curl.exe -s "http://localhost:9000/api/inventory?skuCode=iphone_15&quantity=1"
```

**T5.7 – Gateway khi service đích chết:** dừng product-service → `GET :9000/api/products` → **Mong đợi** lỗi `500`/`502` (Connection refused). Bật lại product-service → `200`.

---

## T6. Keycloak: realm, client, token

### T6.1 – Keycloak chạy

```bash
cd api-gateway
docker compose up -d
docker compose ps
docker compose logs keycloak | findstr /i "started"     # Git Bash/Linux: | grep -i started
```

**Mong đợi:** `keycloak` và `keycloak-mysql` đều `Up` (mysql `healthy`); log có `Keycloak 24.0.1 on JVM ... started`. Mở http://localhost:8181 → đăng nhập `admin/admin` thành công.

**Kiểm tra dữ liệu thực sự nằm trong MySQL** (chứng minh cấu hình `KC_DB*` đúng):
```bash
docker exec -it keycloak-mysql mysql -ukeycloak -ppassword -e "USE keycloak; SELECT NAME FROM REALM;"
```
**Mong đợi:** thấy `master` và (sau T6.2) `spring-microservices-realm`.

### T6.2 – Realm & client

Trong Admin Console:
- [ ] Dropdown realm có `spring-microservices-realm`
- [ ] Clients → `spring-microservices-client`: **Client authentication = On**, Authentication flow chỉ tick **Service accounts roles**
- [ ] Tab **Credentials** hiện Client Secret
- [ ] Tab **Service account roles** tồn tại (chứng tỏ service account đã bật)

### T6.3 – Discovery document

```bash
curl.exe -s http://localhost:8181/realms/spring-microservices-realm/.well-known/openid-configuration
```

**Mong đợi:** JSON có
- `"issuer":"http://localhost:8181/realms/spring-microservices-realm"`
- `"token_endpoint":".../protocol/openid-connect/token"`
- `"jwks_uri":".../protocol/openid-connect/certs"`
- `grant_types_supported` chứa `client_credentials`

### T6.4 – Lấy token bằng curl

```bash
curl.exe -s -X POST "http://localhost:8181/realms/spring-microservices-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=spring-microservices-client" \
  -d "client_secret=<SECRET>"
```

**Mong đợi:** `200`, JSON:
```json
{ "access_token": "eyJ...", "expires_in": 300, "token_type": "Bearer", "scope": "email profile", ... }
```

Lưu token vào biến (Git Bash):
```bash
TOKEN=$(curl -s -X POST "http://localhost:8181/realms/spring-microservices-realm/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=spring-microservices-client -d client_secret=<SECRET> \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
echo $TOKEN
```

PowerShell:
```powershell
$resp = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8181/realms/spring-microservices-realm/protocol/openid-connect/token" `
  -Body @{ grant_type="client_credentials"; client_id="spring-microservices-client"; client_secret="<SECRET>" }
$TOKEN = $resp.access_token
```

**Kiểm tra nội dung token:** dán vào https://jwt.io → payload có `iss = http://localhost:8181/realms/spring-microservices-realm`, `azp = spring-microservices-client`, `exp - iat = 300`.

### T6.5 – Lấy token bằng Postman

Authorization → **OAuth 2.0** → Grant Type **Client Credentials**, Access Token URL `http://localhost:8181/realms/spring-microservices-realm/protocol/openid-connect/token`, Client ID, Client Secret, Client Authentication **Send as Basic Auth header** → **Get New Access Token**.
**Mong đợi:** popup "Authentication complete", hiển thị token → **Use Token**.

### T6.6 – Sai secret (test âm)

Lặp T6.4 với `client_secret=sai-secret`.
**Mong đợi:** `401`, `{"error":"unauthorized_client","error_description":"Invalid client or Invalid client credentials"}`.

---

## T7. Gateway bảo mật – các case 401

Chuẩn bị: Keycloak chạy, Gateway (bản Phần 4) chạy trên 9000, 3 service chạy.

| # | Kịch bản | Lệnh | Mong đợi |
|---|---|---|---|
| T7.1 | Không có header Authorization | `curl.exe -i http://localhost:9000/api/products` | `401`, header `WWW-Authenticate: Bearer` |
| T7.2 | Token rác | `curl.exe -i -H "Authorization: Bearer abc.def.ghi" http://localhost:9000/api/products` | `401`, `WWW-Authenticate: Bearer error="invalid_token"` |
| T7.3 | Token bị sửa payload | Lấy token thật, đổi 1 ký tự ở phần giữa (payload) rồi gọi | `401` `invalid_token` (chữ ký không khớp) |
| T7.4 | Token hết hạn | Lấy token, **chờ > 5 phút**, gọi lại | `401`, `error_description="Jwt expired at ..."` |
| T7.5 | POST order không token | `curl.exe -i -X POST http://localhost:9000/api/order -H "Content-Type: application/json" -d "{\"skuCode\":\"iphone_15\",\"price\":1000,\"quantity\":1}"` | `401`, **không** có bản ghi mới trong `t_orders` |
| T7.6 | Health check (public) | `curl.exe -i http://localhost:9000/actuator/health` | `200`, `{"status":"UP"}` |

> Mẹo test T7.4 nhanh: Keycloak → Realm settings → **Tokens** → *Access Token Lifespan* = **1 minute** → Save. Nhớ trả lại 5 phút sau khi test.

**T7.7 – Issuer không khớp (hiểu vì sao phải khớp `iss`):** lấy token qua `http://127.0.0.1:8181/realms/.../token` (thay `localhost` bằng `127.0.0.1`) rồi gọi Gateway → **Mong đợi** `401` (`The iss claim is not valid`). Lấy lại qua `localhost` → 200.

---

## T8. Gateway bảo mật – các case thành công

Dùng `$TOKEN` từ T6.4 (Git Bash) – hoặc Postman với token đã "Use Token".

| # | Request | Mong đợi |
|---|---|---|
| T8.1 | `curl -i -H "Authorization: Bearer $TOKEN" http://localhost:9000/api/products` | `200`, JSON danh sách sản phẩm |
| T8.2 | `curl -i -H "Authorization: Bearer $TOKEN" "http://localhost:9000/api/inventory?skuCode=iphone_15&quantity=1"` | `200`, `true` |
| T8.3 | `curl -i -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"skuCode":"iphone_15","price":1000,"quantity":1}' http://localhost:9000/api/order` | `201`, `Order Placed Successfully` |
| T8.4 | Như T8.3 với `"quantity": 101` | `500` từ Order Service (đã qua được Gateway – lỗi là do hết hàng, **không phải** 401) |

PowerShell tương đương T8.1:
```powershell
Invoke-RestMethod -Uri http://localhost:9000/api/products -Headers @{ Authorization = "Bearer $TOKEN" }
```

**T8.5 – End-to-end đầy đủ (luồng tổng hợp Phần 3 + 4):**
1. Lấy token (Keycloak) →
2. `POST :9000/api/order` kèm Bearer →
3. Gateway verify JWT → forward tới Order (:8081) →
4. Order gọi Feign tới Inventory (:8082) → `true` →
5. Lưu `t_orders` → `201`.

Xác nhận: response `201` + bản ghi mới trong `t_orders` + (nếu bật DEBUG Feign) log request tới inventory.

---

## T9. Test tự động Gateway

**Không cần** Docker, Keycloak hay các service thật.

```bash
cd api-gateway
mvn clean test
```

**Mong đợi:**
```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| Test | Kiểm tra | Kỳ vọng |
|---|---|---|
| `healthEndpointIsPublic` | `/actuator/health` không cần token | `200` |
| `requestWithoutTokenShouldReturn401` | `/api/products` không token | `401` + header `WWW-Authenticate` |
| `requestWithValidJwtShouldBeRoutedToProductService` | Có JWT (giả lập) → route tới WireMock đóng vai Product | `200`, body chứa `iPhone 15`, WireMock nhận đúng `GET /api/products` |
| `postOrderWithValidJwtShouldBeRoutedToOrderService` | POST có JWT → route Order, body được forward | `201`, `Order Placed Successfully` |
| `inventoryRouteShouldForwardQueryParams` | Query `skuCode`, `quantity` được forward | `200`, `true` |

**Thử nghiệm âm:** comment dòng `.anyRequest().authenticated()` → đổi thành `.anyRequest().permitAll()` → `requestWithoutTokenShouldReturn401` **fail** (nhận 404/200 thay vì 401). Hoàn tác sau khi thử.

> Test `contextLoads` mặc định do start.spring.io sinh ra (nếu còn) có thể giữ nguyên – vì JwtDecoder từ `issuer-uri` được tạo *lazy*, context vẫn load được dù Keycloak không chạy.

---

## T10. Postman Collection gợi ý

Tạo Collection **MSS301 – Part 3-4**, tab **Variables**:

| Variable | Value |
|---|---|
| `gateway` | `http://localhost:9000` |
| `keycloak` | `http://localhost:8181/realms/spring-microservices-realm` |
| `clientId` | `spring-microservices-client` |
| `clientSecret` | *(secret của bạn)* |

Tab **Authorization** của Collection: OAuth 2.0 → Client Credentials, Access Token URL `{{keycloak}}/protocol/openid-connect/token`, Client ID `{{clientId}}`, Secret `{{clientSecret}}`, bật **Auto-refresh token** (Postman mới). Mọi request con để *Inherit auth from parent*.

Requests:

| Folder | Request | Auth |
|---|---|---|
| Part3 – Direct | `POST http://localhost:8081/api/order` (qty 100 / 101) | No Auth |
| Part3 – Direct | `GET http://localhost:8082/api/inventory?skuCode=iphone_15&quantity=100` | No Auth |
| Part4 – Gateway | `GET {{gateway}}/api/products` | Inherit |
| Part4 – Gateway | `POST {{gateway}}/api/order` | Inherit |
| Part4 – Gateway | `GET {{gateway}}/api/inventory?skuCode=iphone_15&quantity=1` | Inherit |
| Part4 – Negative | `GET {{gateway}}/api/products` | **No Auth** → 401 |

Script tab **Tests** mẫu (Postman):
```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.test("Có sản phẩm", () => pm.expect(pm.response.json()).to.be.an("array"));
```
Cho request Negative:
```javascript
pm.test("Bị chặn 401", () => pm.response.to.have.status(401));
pm.test("Có WWW-Authenticate", () => pm.response.to.have.header("WWW-Authenticate"));
```

Chạy cả collection bằng **Runner** để có báo cáo pass/fail.

---

## 11. Checklist nghiệm thu

### Phần 3
- [ ] `order-service` build OK với Spring Boot `4.1.0` + OpenFeign + BOM `2025.1.3`
- [ ] Có `InventoryClient` với `@FeignClient(value="inventory", url="${inventory.url}")`
- [ ] `@EnableFeignClients` trên main class
- [ ] T3.1: qty 100 → `201`, T3.2: có bản ghi trong `t_orders`
- [ ] T3.3: qty 101 → `500`, không có bản ghi mới
- [ ] T4: `mvn test` order-service → **2/2 PASS**
- [ ] `api-gateway` chạy port 9000
- [ ] T5.1–T5.4: 3 service truy cập được qua `localhost:9000`
- [ ] T5.5: path lạ → `404`

### Phần 4
- [ ] T6.1: Keycloak + MySQL chạy, dữ liệu realm nằm trong MySQL
- [ ] T6.2: realm `spring-microservices-realm`, client `spring-microservices-client` (Client auth ON, Service accounts)
- [ ] T6.4/T6.5: lấy được access token (expires_in 300)
- [ ] T6.6: sai secret → `401 unauthorized_client`
- [ ] T7.1: không token → `401`
- [ ] T7.2/T7.3: token rác / bị sửa → `401`
- [ ] T7.4: token hết hạn → `401`
- [ ] T8.1–T8.3: có token → `200` / `201`
- [ ] T8.5: luồng end-to-end Keycloak → Gateway → Order → Inventory thành công
- [ ] T9: `mvn test` api-gateway → **5/5 PASS**

---

## 12. Xử lý khi test fail

| Hiện tượng | Kiểm tra | Cách sửa |
|---|---|---|
| T1.2 `NoSuchBeanDefinitionException` | Main class có `@EnableFeignClients`? | Thêm annotation |
| T1.2 `not compatible with this Spring Cloud release train` | `spring-cloud.version` | Boot 4.1.x → `2025.1.3` (tối thiểu `2025.1.2`) |
| T1.2 app chạy nhưng `Table 'order_service.t_orders' doesn't exist` | Boot 4 thiếu `spring-boot-starter-flyway` | Thêm starter, giữ `flyway-mysql` |
| Build lỗi `package org.springframework.cloud.contract.wiremock does not exist` / `org.testcontainers.containers.MySQLContainer` | Code viết theo Boot 3 | Đổi sang `org.wiremock.spring.EnableWireMock`, `org.testcontainers.mysql.MySQLContainer` (xem `Part3-4.md` mục 0.4) |
| T3.1 trả `500` dù qty ≤ 100 | Inventory chạy chưa? `inventory.url` đúng? Seed data có chưa (T2.1)? | Start inventory, sửa URL, kiểm tra `t_inventory` |
| T3.1 trả `500` với `FeignException$NotFound` | Path trong `@RequestMapping` của Feign | Phải là `/api/inventory` |
| T4 `Could not find a valid Docker environment` | Docker Desktop | Bật Docker |
| T4 `Connection refused` tới 8082 | `@ConfigureWireMock(baseUrlProperties = "inventory.url")` | Thêm vào `@EnableWireMock(...)` |
| T4 test success fail với 500, log `Request was not matched` | URL stub vs URL thực tế | Khớp đúng `skuCode` và `quantity` |
| T5.1 `404` | Route `/api/product` thiếu `s` | `path("/api/products/**")` |
| T5.x `500` `I/O error on GET request` | Service đích chưa chạy | Start service |
| T5.x `401` | Đã thêm oauth2-resource-server | Làm theo T8 (kèm token) |
| T6.1 Keycloak restart liên tục | `docker compose logs keycloak` | Chờ MySQL healthy; kiểm tra `KC_DB_URL` host `keycloak-mysql` |
| T6.1 bảng `REALM` không có trong MySQL | Dùng biến `DB_VENDOR/DB_ADDR` cũ | Chuyển sang `KC_DB*` rồi `docker compose down -v && docker compose up -d` (xóa luôn `volume-data/` nếu cần) |
| T6.4 `unauthorized_client` / `Client not enabled to retrieve service account` | Service accounts roles | Bật trong Settings của client |
| T6.4 `invalid_client` | Secret | Copy lại tab Credentials |
| T8.1 vẫn `401` dù token mới | `iss` trong token (jwt.io) vs `issuer-uri` | Khớp tuyệt đối `http://localhost:8181/realms/spring-microservices-realm` |
| T8.1 request đầu `401`/`500`, log `Unable to resolve the Configuration with the provided Issuer` | Keycloak có chạy? URL `.well-known` mở được? | Start Keycloak rồi gọi lại |
| T9 `requestWithValidJwt...` fail với 404 từ WireMock | Stub URL | `urlEqualTo("/api/products")` đúng path Gateway forward |
| T9 lỗi tạo context vì JwtDecoder | Có `@MockitoBean JwtDecoder`? | Thêm `@MockitoBean` (Boot 4 đã xóa `@MockBean`) |
| T9 `cannot find symbol AutoConfigureMockMvc` | Thiếu `spring-boot-starter-webmvc-test` / import package cũ | Thêm dependency, import `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |

Bật log chi tiết khi cần:
```properties
# api-gateway
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.cloud.gateway=DEBUG
```
