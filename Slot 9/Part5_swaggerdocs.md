# MSS301 — Microservices Project (Part 1 → Part 5)

Project thực hành tích hợp cho môn **MSS301 — Microservices with Spring Boot**.  
Sinh viên xây dựng hệ thống thương mại điện tử từ đầu, trải qua 5 giai đoạn từ REST API cơ bản đến API Documentation.

---

## Kiến trúc tổng thể

```
Client (Postman / Swagger UI)
    │
    │  Authorization: Bearer <JWT>
    ▼
API Gateway (:9000)          ← Part 3 + 4: Routing + Bảo mật OAuth2/JWT
    │  Xác minh JWT với Keycloak     Part 5: Swagger UI tổng hợp
    ├──→  Product Service  (:8080)  ← Part 1: MongoDB, CRUD sản phẩm
    ├──→  Order Service    (:8081)  ← Part 1 + 2: MySQL, đặt hàng + OpenFeign
    └──→  Inventory Service(:8082)  ← Part 1: MySQL, kiểm tra tồn kho

Keycloak (:8181)             ← Authorization Server (cấp JWT)
```

---

## Khởi động hệ thống

```bash
# 1. Khởi động tất cả infrastructure (MongoDB, MySQL, Keycloak)
docker compose up -d

# 2. Khởi động từng service theo thứ tự (mỗi terminal riêng)
cd inventory-service && ./mvnw spring-boot:run   # port 8082
cd product-service   && ./mvnw spring-boot:run   # port 8080
cd order-service     && ./mvnw spring-boot:run   # port 8081
cd api-gateway       && ./mvnw spring-boot:run   # port 9000
```

---

## Danh sách TODO (17 + 16 = 33 TODO)

> **Part 1 → 4** (17 TODO): đã hoàn thành trong phiên trước.  
> **Part 5** (16 TODO): tập trung vào API Documentation với Springdoc OpenAPI & Swagger.

---

## PHẦN 1–4 (đã hoàn thành — không cần làm lại)

<details>
<summary>Xem danh sách TODO Part 1–4</summary>

### Product Service: PS-1 → PS-8 | Inventory Service: IS-1 → IS-4
### Order Service: OS-1 → OS-9 | API Gateway: GW-1 → GW-4

</details>

---

## PHẦN 5 — API Documentation với Springdoc OpenAPI & Swagger

### Mục tiêu

Sau khi hoàn thành Part 5, mỗi microservice sẽ expose tự động:
- **Swagger UI** tại `/swagger-ui.html` — giao diện web để thử API
- **JSON Docs** tại `/api-docs` — machine-readable format

API Gateway sẽ tổng hợp docs của cả 3 services tại `http://localhost:9000/swagger-ui.html`.

---

### NHÓM A – Product Service (`product-service/`)

#### TODO DOC-1 · `pom.xml` — Thêm Springdoc dependencies

Mở `product-service/pom.xml`, tìm comment `TODO DOC-1` và thêm 2 dependencies:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
    <version>2.5.0</version>
</dependency>
```

#### TODO DOC-2 · `application.properties` — Cấu hình đường dẫn Swagger

Mở `product-service/src/main/resources/application.properties`, bỏ comment 2 dòng:

```properties
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/api-docs
```

#### TODO DOC-3 · **TẠO MỚI** `config/OpenAPIConfig.java`

Tạo file mới tại `product-service/src/main/java/com/fudn/productservice/config/OpenAPIConfig.java`:

```java
package com.fudn.productservice.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI productServiceAPI() {
        return new OpenAPI()
                .info(new Info().title("Product Service API")
                        .description("This is the REST API for Product Service")
                        .version("v0.0.1")
                        .license(new License().name("Apache 2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("Product Service Wiki Documentation")
                        .url("https://fudn-product-service-dummy-url.com/docs"));
    }
}
```

#### TODO DOC-4 · **TẠO MỚI** `config/CorsConfig.java`

Tạo file mới tại `product-service/src/main/java/com/fudn/productservice/config/CorsConfig.java`:

```java
package com.fudn.productservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowedOriginPatterns("*")
                .allowCredentials(false);
    }
}
```

**Kiểm thử:**
```bash
cd product-service && ./mvnw test -Dtest=SwaggerIntegrationTest
```
✅ Pass khi: `swaggerUiShouldBeAccessible`, `apiDocsShouldReturnJson`, `apiDocsShouldContainProductEndpoints`

---

### NHÓM B – Inventory Service (`inventory-service/`)

#### TODO DOC-5 · `pom.xml` — Thêm Springdoc dependencies

Tương tự DOC-1, thêm 2 dependency springdoc vào `inventory-service/pom.xml`.

#### TODO DOC-6 · `application.properties` — Cấu hình đường dẫn Swagger

Bỏ comment 2 dòng springdoc trong `inventory-service/src/main/resources/application.properties`.

#### TODO DOC-7 · **TẠO MỚI** `config/OpenAPIConfig.java`

Tạo `inventory-service/src/main/java/com/fudn/inventoryservice/config/OpenAPIConfig.java`:
- Package: `com.fudn.inventoryservice.config`
- Bean method: `inventoryServiceAPI()`
- Title: `"Inventory Service API"`
- Description: `"This is the REST API for Inventory Service"`

#### TODO DOC-8 · **TẠO MỚI** `config/CorsConfig.java`

Tạo `inventory-service/src/main/java/com/fudn/inventoryservice/config/CorsConfig.java`:
- Package: `com.fudn.inventoryservice.config`
- Nội dung tương tự DOC-4

**Kiểm thử:**
```bash
cd inventory-service && ./mvnw test -Dtest=SwaggerIntegrationTest
```

---

### NHÓM C – Order Service (`order-service/`)

#### TODO DOC-9 · `pom.xml` — Thêm Springdoc dependencies

Tương tự DOC-1, thêm 2 dependency springdoc vào `order-service/pom.xml`.

#### TODO DOC-10 · `application.properties` — Cấu hình đường dẫn Swagger

Bỏ comment 2 dòng springdoc trong `order-service/src/main/resources/application.properties`.

#### TODO DOC-11 · **TẠO MỚI** `config/OpenAPIConfig.java`

Tạo `order-service/src/main/java/com/fudn/orderservice/config/OpenAPIConfig.java`:
- Package: `com.fudn.orderservice.config`
- Bean method: `orderServiceAPI()`
- Title: `"Order Service API"`
- Description: `"This is the REST API for Order Service"`

#### TODO DOC-12 · **TẠO MỚI** `config/CorsConfig.java`

Tạo `order-service/src/main/java/com/fudn/orderservice/config/CorsConfig.java`:
- Package: `com.fudn.orderservice.config`
- Nội dung tương tự DOC-4

**Kiểm thử:**
```bash
cd order-service && ./mvnw test -Dtest=SwaggerIntegrationTest
```

---

### NHÓM D – API Gateway (`api-gateway/`)

#### TODO DOC-13 · `pom.xml` — Thêm Springdoc dependencies

Tương tự DOC-1, thêm 2 dependency springdoc vào `api-gateway/pom.xml`.

#### TODO DOC-14 · `application.properties` — Cấu hình aggregate Swagger URLs

Mở `api-gateway/src/main/resources/application.properties`, bỏ comment 9 dòng:

```properties
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.enabled=true
springdoc.api-docs.enabled=true
springdoc.swagger-ui.urls[0].name=Product Service
springdoc.swagger-ui.urls[0].url=/aggregate/product-service/v3/api-docs
springdoc.swagger-ui.urls[1].name=Order Service
springdoc.swagger-ui.urls[1].url=/aggregate/order-service/v3/api-docs
springdoc.swagger-ui.urls[2].name=Inventory Service
springdoc.swagger-ui.urls[2].url=/aggregate/inventory-service/v3/api-docs
```

#### TODO DOC-15 · `routes/Routes.java` — Thêm 3 Swagger routing rules

Mở `api-gateway/src/main/java/com/fudn/gateway/routes/Routes.java`, tìm các comment `TODO DOC-15a/b/c` và viết 3 `@Bean`:

```java
// DOC-15a
@Bean
public RouterFunction<ServerResponse> productServiceSwaggerRoute() {
    return route("product_service_swagger")
            .route(path("/aggregate/product-service/v3/api-docs"),
                   http("http://localhost:8080"))
            .filter(setPath("/api-docs"))
            .build();
}

// DOC-15b
@Bean
public RouterFunction<ServerResponse> orderServiceSwaggerRoute() {
    return route("order_service_swagger")
            .route(path("/aggregate/order-service/v3/api-docs"),
                   http("http://localhost:8081"))
            .filter(setPath("/api-docs"))
            .build();
}

// DOC-15c
@Bean
public RouterFunction<ServerResponse> inventoryServiceSwaggerRoute() {
    return route("inventory_service_swagger")
            .route(path("/aggregate/inventory-service/v3/api-docs"),
                   http("http://localhost:8082"))
            .filter(setPath("/api-docs"))
            .build();
}
```

#### TODO DOC-16 · `config/SecurityConfig.java` — Cập nhật Security + CORS

Mở `api-gateway/src/main/java/com/fudn/gateway/config/SecurityConfig.java`, hoàn thành 3 TODO:

**DOC-16a** — Khai báo `freeResourceUrls`:
```java
private final String[] freeResourceUrls = {
    "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
    "/swagger-resources/**", "/aggregate/**"
};
```

**DOC-16b** — Cập nhật `securityFilterChain`:
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(freeResourceUrls).permitAll()
                    .anyRequest().authenticated())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
}
```

**DOC-16c** — Thêm `corsConfigurationSource`:
```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("*"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST"));
    configuration.setAllowedHeaders(List.of("*"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

**Kiểm thử:**
```bash
cd api-gateway && ./mvnw test
```
✅ Pass khi: `contextLoads`, `swaggerUiShouldBeAccessibleWithoutToken`, `aggregateProductDocsShouldBePermittedWithoutToken`, `protectedApiShouldRequireToken`

---

## Tổng quan thứ tự làm bài Part 5

```
1. Hoàn thành NHÓM A – Product Service   (DOC-1 → DOC-4)  → chạy SwaggerIntegrationTest
2. Hoàn thành NHÓM B – Inventory Service (DOC-5 → DOC-8)  → chạy SwaggerIntegrationTest
3. Hoàn thành NHÓM C – Order Service     (DOC-9 → DOC-12) → chạy SwaggerIntegrationTest
4. Hoàn thành NHÓM D – API Gateway       (DOC-13 → DOC-16) → chạy tất cả tests
5. Khởi động toàn bộ hệ thống và truy cập http://localhost:9000/swagger-ui.html
```

---

## Kiểm tra kết quả cuối

| URL | Kết quả kỳ vọng |
|-----|----------------|
| `http://localhost:8080/swagger-ui.html` | Swagger UI của Product Service |
| `http://localhost:8081/swagger-ui.html` | Swagger UI của Order Service |
| `http://localhost:8082/swagger-ui.html` | Swagger UI của Inventory Service |
| `http://localhost:9000/swagger-ui.html` | Swagger UI tổng hợp (dropdown 3 services) |
| `http://localhost:9000/swagger-ui.html` không cần JWT | ✅ Truy cập tự do |
| `http://localhost:9000/api/product` không có JWT | ❌ `401 Unauthorized` |

---

## Tài liệu tham khảo

| Phần | Tài liệu |
|------|----------|
| Part 1–4 | Xem README gốc |
| Part 5 | `part5.md` |
| Springdoc | https://springdoc.org |
