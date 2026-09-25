# OnlineShopping — Product Service 

Project mẫu cho môn **MSS301 — Microservices with Spring Boot**. Sinh viên hoàn thiện 2 chức năng còn thiếu: **UPDATE** và **DELETE** sản phẩm. 
---

## 1. Tổng quan project

### Đã được implement sẵn
| Chức năng | Endpoint | File code |
|-----------|----------|-----------|
| Tạo sản phẩm | `POST /api/products` | `service/ProductService.java#createProduct`, `controller/ProductController.java#createProduct` |
| Liệt kê sản phẩm | `GET /api/products` | `service/ProductService.java#getAllProducts`, `controller/ProductController.java#getAllProducts` |

### Sinh viên cần làm
| Chức năng | Endpoint | TODO |
|-----------|----------|------|
| Update sản phẩm | `PUT /api/products/{id}` | TODO 1, TODO 3 |
| Delete sản phẩm | `DELETE /api/products/{id}` | TODO 2, TODO 4 |
| Trả về 404 khi không tìm thấy | (cross-cutting) | TODO 5 |

---

## 2. Danh sách TODO chi tiết

Tất cả TODO đều có comment hướng dẫn cụ thể ngay trong code. Mở file để xem.

### TODO 1 — `ProductService.updateProduct(...)`
**File:** `product-service/src/main/java/com/fudn/product_service/service/ProductService.java`

**Yêu cầu:**
1. Tìm `Product` theo `id` bằng `productRepository.findById(id)`
2. Nếu KHÔNG tìm thấy → `throw new ProductNotFoundException(id)`
3. Nếu có → cập nhật 3 field (`name`, `description`, `price`) từ request → `productRepository.save(product)`
4. Trả về `ProductResponse` chứa dữ liệu MỚI

**Lưu ý:** Đừng `new Product()` rồi save — phải update đúng record cũ, nếu không sẽ tạo id mới khác.

---

### TODO 2 — `ProductService.deleteProduct(...)`
**File:** `product-service/src/main/java/com/fudn/product_service/service/ProductService.java`

**Yêu cầu:**
1. Kiểm tra `productRepository.existsById(id)`
2. Nếu KHÔNG → `throw new ProductNotFoundException(id)`
3. Nếu có → `productRepository.deleteById(id)`
4. Trả về `void`

**Lưu ý:** PHẢI check `existsById` trước. Spring Data `deleteById` không throw nếu id không tồn tại — sẽ làm test 404 fail.

---

### TODO 3 — Endpoint `PUT /api/products/{id}`
**File:** `product-service/src/main/java/com/fudn/product_service/controller/ProductController.java`

**Yêu cầu spec:**
- Method: `PUT`
- Path: `/api/products/{id}`
- Path variable: `id` (String)
- Body: `ProductRequest`
- Return: `ProductResponse`
- Success status: `200 OK` (mặc định Spring)
- Khi service throw `ProductNotFoundException` → trả về `404` (do TODO 5 xử lý)

**Mẫu annotation:**
```java
@PutMapping("/{id}")
public ProductResponse updateProduct(@PathVariable String id,
                                      @RequestBody ProductRequest productRequest) {
    return productService.updateProduct(id, productRequest);
}
```

---

### TODO 4 — Endpoint `DELETE /api/products/{id}`
**File:** `product-service/src/main/java/com/fudn/product_service/controller/ProductController.java`

**Yêu cầu spec:**
- Method: `DELETE`
- Path: `/api/products/{id}`
- Return: `void`
- Success status: `204 No Content` (dùng `@ResponseStatus(HttpStatus.NO_CONTENT)`)
- Khi service throw `ProductNotFoundException` → `404`

**Mẫu annotation:**
```java
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteProduct(@PathVariable String id) {
    productService.deleteProduct(id);
}
```

---

### TODO 5 — Tạo `GlobalExceptionHandler` để map exception sang HTTP 404
**File mới:** `product-service/src/main/java/com/fudn/product_service/exception/GlobalExceptionHandler.java`

Mặc định khi `ProductNotFoundException` bay ra, Spring trả về **HTTP 500**. Để chuyển thành **404**, tạo file `GlobalExceptionHandler.java` với nội dung:

```java
package com.fudn.product_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ProductNotFoundException ex) {
        return ex.getMessage();
    }
}
```

> Class `ProductNotFoundException` đã được tạo sẵn cho bạn ở `exception/ProductNotFoundException.java`. Chỉ cần tạo thêm `GlobalExceptionHandler`.

---

## 4. Cách chạy ứng dụng (thử manual với Postman/curl)

### Bước 1: Bật MongoDB
```bash
cd product-service
docker compose up -d
```

### Bước 2: Chạy Spring Boot
```bash
./mvnw spring-boot:run     # macOS/Linux
mvn spring-boot:run   # Windows
```
Ứng dụng chạy tại http://localhost:8080. Mongo Express xem DB tại http://localhost:8081.

### Bước 3: Thử endpoint
```bash
# 1. Tạo product (đã có sẵn)
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone 15","description":"Apple phone","price":999.99}'
# → ghi nhớ id trong response

# 2. Liệt kê (đã có sẵn)
curl http://localhost:8080/api/products

# 3. Update (sinh viên cần làm)
curl -X PUT http://localhost:8080/api/products/<ID> \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone 15 Pro","description":"Updated","price":1199.99}'

# 4. Delete (sinh viên cần làm)
curl -X DELETE http://localhost:8080/api/products/<ID> -i
# → header phải có HTTP/1.1 204
```

---

## 5. Tiêu chí chấm điểm (tổng 10 điểm)

| # | Tiêu chí | Điểm | Liên quan TODO |
|---|----------|------|----------------|
| 1 | UPDATE — Trả về 200 và body chứa dữ liệu mới | 2.0 | TODO 1, 3 |
| 2 | UPDATE — Dữ liệu thật sự lưu vào DB, không tạo bản ghi mới | 2.0 | TODO 1 |
| 3 | UPDATE — Trả về 404 khi id không tồn tại | 1.0 | TODO 1, 5 |
| 4 | DELETE — Trả về 204 (hoặc 200) khi xoá thành công | 2.0 | TODO 2, 4 |
| 5 | DELETE — Sản phẩm thật sự bị xoá khỏi DB | 2.0 | TODO 2, 4 |
| 6 | DELETE — Trả về 404 khi id không tồn tại | 1.0 | TODO 2, 5 |

---

## 6. Cấu trúc file sau khi hoàn thiện

```
product-service/src/main/java/com/fudn/product_service/
├── ProductServiceApplication.java
├── controller/
│   └── ProductController.java        ← thêm PUT + DELETE endpoint (TODO 3, 4)
├── service/
│   └── ProductService.java           ← thêm updateProduct + deleteProduct (TODO 1, 2)
├── repository/
│   └── IProductRepository.java
├── model/
│   └── Product.java
├── dto/
│   ├── ProductRequest.java
│   └── ProductResponse.java
└── exception/
    ├── ProductNotFoundException.java   ← đã tạo sẵn
    └── GlobalExceptionHandler.java     ← TODO 5: sinh viên tạo file này
```


