# Hướng dẫn Part 5

> 

---

## Tổng quan Part 5

Part 5 thêm **API Documentation** vào hệ thống microservices đã có từ Part 1–4, sử dụng thư viện **Springdoc OpenAPI** + **Swagger UI**.

| TODO | File | Loại |
|------|------|------|
| DOC-1 | product-service/pom.xml | Sửa |
| DOC-2 | product-service/application.properties | Sửa |
| DOC-3 | product-service/config/OpenAPIConfig.java | **Tạo mới** |
| DOC-4 | product-service/config/CorsConfig.java | **Tạo mới** |
| DOC-5 | inventory-service/pom.xml | Sửa |
| DOC-6 | inventory-service/application.properties | Sửa |
| DOC-7 | inventory-service/config/OpenAPIConfig.java | **Tạo mới** |
| DOC-8 | inventory-service/config/CorsConfig.java | **Tạo mới** |
| DOC-9 | order-service/pom.xml | Sửa |
| DOC-10 | order-service/application.properties | Sửa |
| DOC-11 | order-service/config/OpenAPIConfig.java | **Tạo mới** |
| DOC-12 | order-service/config/CorsConfig.java | **Tạo mới** |
| DOC-13 | api-gateway/pom.xml | Sửa |
| DOC-14 | api-gateway/application.properties | Sửa |
| DOC-15 | api-gateway/routes/Routes.java | Sửa (thêm 3 beans) |
| DOC-16 | api-gateway/config/SecurityConfig.java | Sửa |

---

## Lời giải — NHÓM A: Product Service

### TODO DOC-1 · `product-service/pom.xml`

Thêm vào trong `<dependencies>`:

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

### TODO DOC-2 · `product-service/src/main/resources/application.properties`

Bỏ comment 2 dòng cuối file:

```properties
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/api-docs
```

### TODO DOC-3 · TẠO `product-service/src/main/java/com/fudn/productservice/config/OpenAPIConfig.java`

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

**Điểm cốt yếu:** `title("Product Service API")` và `version("v0.0.1")` — test kiểm tra chính xác 2 giá trị này.

### TODO DOC-4 · TẠO `product-service/src/main/java/com/fudn/productservice/config/CorsConfig.java`

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

**Tại sao cần:** Khi Swagger UI (chạy trên browser từ port 9000) gọi trực tiếp tới service (port 8080) để thử API, browser chặn CORS nếu không có config này.

---

## Lời giải — NHÓM B: Inventory Service

### TODO DOC-5 · `inventory-service/pom.xml`

Giống DOC-1 — thêm 2 dependency springdoc.

### TODO DOC-6 · `inventory-service/src/main/resources/application.properties`

Bỏ comment 2 dòng springdoc (giống DOC-2).

### TODO DOC-7 · TẠO `inventory-service/.../config/OpenAPIConfig.java`

```java
package com.fudn.inventoryservice.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI inventoryServiceAPI() {
        return new OpenAPI()
                .info(new Info().title("Inventory Service API")
                        .description("This is the REST API for Inventory Service")
                        .version("v0.0.1")
                        .license(new License().name("Apache 2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("Inventory Service Wiki Documentation")
                        .url("https://fudn-inventory-service-dummy-url.com/docs"));
    }
}
```

### TODO DOC-8 · TẠO `inventory-service/.../config/CorsConfig.java`

Giống DOC-4 — chỉ đổi package thành `com.fudn.inventoryservice.config`.

---

## Lời giải — NHÓM C: Order Service

### TODO DOC-9 · `order-service/pom.xml`

Giống DOC-1 — thêm 2 dependency springdoc.

### TODO DOC-10 · `order-service/src/main/resources/application.properties`

Bỏ comment 2 dòng springdoc (giống DOC-2).

### TODO DOC-11 · TẠO `order-service/.../config/OpenAPIConfig.java`

```java
package com.fudn.orderservice.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI orderServiceAPI() {
        return new OpenAPI()
                .info(new Info().title("Order Service API")
                        .description("This is the REST API for Order Service")
                        .version("v0.0.1")
                        .license(new License().name("Apache 2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("Order Service Wiki Documentation")
                        .url("https://fudn-order-service-dummy-url.com/docs"));
    }
}
```

### TODO DOC-12 · TẠO `order-service/.../config/CorsConfig.java`

Giống DOC-4 — chỉ đổi package thành `com.fudn.orderservice.config`.

---

## Lời giải — NHÓM D: API Gateway

### TODO DOC-13 · `api-gateway/pom.xml`

Giống DOC-1 — thêm 2 dependency springdoc.

### TODO DOC-14 · `api-gateway/src/main/resources/application.properties`

Bỏ comment 9 dòng:

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

**Cơ chế:** `springdoc.swagger-ui.urls` định nghĩa danh sách services trong dropdown của Swagger UI. Khi user chọn "Product Service", Swagger UI gọi `/aggregate/product-service/v3/api-docs` → Gateway route forward tới `http://localhost:8080/api-docs`.

### TODO DOC-15 · `api-gateway/src/main/java/com/fudn/gateway/routes/Routes.java`

Thêm 3 `@Bean` vào class `Routes` (sau phần 3 routes hiện có):

```java
@Bean
public RouterFunction<ServerResponse> productServiceSwaggerRoute() {
    return route("product_service_swagger")
            .route(path("/aggregate/product-service/v3/api-docs"),
                   http("http://localhost:8080"))
            .filter(setPath("/api-docs"))
            .build();
}

@Bean
public RouterFunction<ServerResponse> orderServiceSwaggerRoute() {
    return route("order_service_swagger")
            .route(path("/aggregate/order-service/v3/api-docs"),
                   http("http://localhost:8081"))
            .filter(setPath("/api-docs"))
            .build();
}

@Bean
public RouterFunction<ServerResponse> inventoryServiceSwaggerRoute() {
    return route("inventory_service_swagger")
            .route(path("/aggregate/inventory-service/v3/api-docs"),
                   http("http://localhost:8082"))
            .filter(setPath("/api-docs"))
            .build();
}
```

**Import cần thêm:**
```java
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;
```

**Cơ chế `.filter(setPath("/api-docs"))`:** Khi Gateway nhận `/aggregate/product-service/v3/api-docs`, nó rewrite path thành `/api-docs` trước khi forward tới service, vì các microservices expose docs tại `/api-docs`.

### TODO DOC-16 · `api-gateway/src/main/java/com/fudn/gateway/config/SecurityConfig.java`

File hoàn chỉnh sau khi implement DOC-16a + 16b + 16c:

```java
package com.fudn.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    // DOC-16a
    private final String[] freeResourceUrls = {
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
        "/swagger-resources/**", "/aggregate/**"
    };

    // DOC-16b
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

    // DOC-16c
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
}
```

**Tại sao `/aggregate/**` trong freeResourceUrls:** Khi Swagger UI gọi `/aggregate/product-service/v3/api-docs` để lấy API spec, request này không mang JWT. Nếu thiếu path này trong permitAll → 401 → Swagger UI hiển thị lỗi "Failed to fetch".

---

## Cơ chế Tests

### Tests mới (Part 5)

| Test class | Service | Kiểm tra |
|-----------|---------|----------|
| `SwaggerIntegrationTest` | product, inventory, order | `/swagger-ui.html` accessible, `/api-docs` trả JSON đúng title/version, path endpoint tồn tại |
| `SwaggerSecurityTest` | api-gateway | Swagger URLs bypass auth, `/api/product` vẫn cần JWT |

### Chạy tests

```bash
# Chạy chỉ Swagger tests
cd product-service   && ./mvnw test -Dtest=SwaggerIntegrationTest
cd inventory-service && ./mvnw test -Dtest=SwaggerIntegrationTest
cd order-service     && ./mvnw test -Dtest=SwaggerIntegrationTest
cd api-gateway       && ./mvnw test  # chạy cả contextLoads + SwaggerSecurityTest

# Chạy tất cả tests (Part 1-4 + Part 5)
cd product-service   && ./mvnw test
cd inventory-service && ./mvnw test
cd order-service     && ./mvnw test
cd api-gateway       && ./mvnw test
```

### Lý do test kiểm tra `info.title` và `info.version`

Test `apiDocsShouldReturnJson()` kiểm tra:
```
.body("info.title", equalTo("Product Service API"))
.body("info.version", equalTo("v0.0.1"))
```

Điều này đảm bảo sinh viên đã **tạo đúng** `OpenAPIConfig` với metadata chính xác, không chỉ add dependency mà không config.

---

## Lỗi thường gặp

### `No bean named 'productServiceAPI'` / Context fail

**Nguyên nhân:** Sinh viên chưa tạo `OpenAPIConfig.java` (DOC-3/7/11), hoặc tạo sai package.  
**Kiểm tra:** Package phải là `com.fudn.productservice.config` (match với component scan của `@SpringBootApplication`).

---

### Test `apiDocsShouldReturnJson` fail với `info.title` không khớp

**Nguyên nhân:** Sinh viên đặt title khác (vd: `"product-service"` thay vì `"Product Service API"`).  
**Cách sửa:** Title phải là `"Product Service API"` / `"Inventory Service API"` / `"Order Service API"` — đúng như test expect.

---

### `swaggerUiShouldBeAccessibleWithoutToken` fail với 401

**Nguyên nhân:** DOC-16a hoặc DOC-16b chưa hoàn thành — `freeResourceUrls` chưa được permit.  
**Kiểm tra:** `SecurityConfig.java` phải có `.requestMatchers(freeResourceUrls).permitAll()` trước `.anyRequest().authenticated()`.

---

### `aggregateProductDocsShouldBePermittedWithoutToken` fail với 401

**Nguyên nhân:** `/aggregate/**` thiếu trong `freeResourceUrls`.  
**Cách sửa:** Đảm bảo mảng `freeResourceUrls` có `"/aggregate/**"`.

---

### Swagger UI tại Gateway hiển thị "Failed to fetch"

**Nguyên nhân:** DOC-15 chưa implement routes, hoặc microservices chưa khởi động.  
**Kiểm tra:** `Routes.java` phải có đủ 3 swagger `@Bean`. Các service phải đang chạy.

---

### `UnsatisfiedDependencyException` khi chạy SwaggerIntegrationTest của order-service

**Nguyên nhân:** `@AutoConfigureWireMock` trong `SwaggerIntegrationTest` cần `spring-cloud-starter-contract-stub-runner` — đã có trong pom.xml.  
Nếu vẫn lỗi: kiểm tra `order-service/src/test/resources/application.properties` có dòng `inventory.url=http://localhost:${wiremock.server.port}`.

---

## Kiểm tra end-to-end

Sau khi tất cả tests pass và các services đang chạy:

1. Truy cập `http://localhost:9000/swagger-ui.html` **không cần JWT** → Swagger UI tải được
2. Dropdown góc trên phải có 3 options: **Product Service**, **Order Service**, **Inventory Service**
3. Chọn "Product Service" → thấy 2 endpoints: `POST /api/product`, `GET /api/product`
4. Click "Try it out" → "Execute" → nhập JWT → API trả về kết quả đúng
5. Truy cập `http://localhost:9000/api/product` không có JWT → `401 Unauthorized`

---


