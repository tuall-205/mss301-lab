# Testing Inventory Service bằng Postman

> Dựa trên `inventory-service.md`. Áp dụng cho service `inventory-service` (package `com.fudn.inventoryservice`).
> **Base URL:** `http://localhost:8082` | **Endpoint:** `GET /api/inventory?skuCode=xxx&quantity=yyy`

---

## 0. Checklist chuẩn bị trước khi test

- [ ] Docker container `mysql` đang chạy (dùng chung với order-service)
- [ ] Database `inventory_service` đã tồn tại (đã cập nhật `init.sql` thêm dòng `CREATE DATABASE IF NOT EXISTS inventory_service;`)
- [ ] `inventory-service` đã chạy thành công (`mvn spring-boot:run`), không lỗi
- [ ] Log xác nhận Flyway áp dụng đủ **2 migration**: `Successfully applied 2 migrations to schema inventory_service, now at version v2`
- [ ] Bảng `t_inventory` có sẵn 4 dòng dữ liệu mẫu (từ `V2__add_inventory.sql`):

| sku_code | quantity |
|---|---|
| iphone_15 | 100 |
| pixel_8 | 100 |
| galaxy_24 | 100 |
| oneplus_12 | 100 |

- [ ] Postman đã cài đặt, đã mở ứng dụng

---

## 1. Cấu hình Postman

### 1.1. Tạo Environment

Tạo Environment `inventory-service-local`:

| Variable | Initial Value | Current Value |
|---|---|---|
| `inventory_base_url` | `http://localhost:8082` | `http://localhost:8082` |

### TODO
- [ ] Tạo Environment `inventory-service-local`
- [ ] Thêm biến `inventory_base_url`
- [ ] Chọn Environment trước khi gửi request

### 1.2. Tạo Collection

Tạo Collection `Inventory Service`.

### TODO
- [ ] Tạo Collection `Inventory Service`

---

## 2. Test Case 1 – Đủ hàng (quantity ≤ tồn kho)

**Request:**
```
GET {{inventory_base_url}}/api/inventory?skuCode=iphone_15&quantity=100
```

**Kết quả kỳ vọng:**
| Tiêu chí | Giá trị mong đợi |
|---|---|
| Status Code | `200 OK` |
| Body | `true` |

**Postman Test Script:**
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("In stock returns true", function () {
    pm.expect(pm.response.text()).to.eql("true");
});
```

### TODO
- [ ] Tạo request `GET Check Stock - In Stock` trong Collection
- [ ] Gửi request, xác nhận `200` + body `true`

---

## 3. Test Case 2 – Không đủ hàng (quantity > tồn kho)

**Request:**
```
GET {{inventory_base_url}}/api/inventory?skuCode=iphone_15&quantity=200
```

**Kết quả kỳ vọng:** `200 OK`, body `false`

### TODO
- [ ] Gửi request, xác nhận body `false`

---

## 4. Test Case 3 – Đúng bằng số lượng tồn kho (boundary test)

**Request:**
```
GET {{inventory_base_url}}/api/inventory?skuCode=pixel_8&quantity=100
```

**Kết quả kỳ vọng:** `200 OK`, body `true` — vì query dùng `GreaterThanEqual` (`>=`), 100 vẫn hợp lệ.

### TODO
- [ ] Gửi request với quantity đúng bằng tồn kho (100)
- [ ] Xác nhận trả về `true` (kiểm tra đúng boundary `>=` chứ không phải `>`)

---

## 5. Test Case 4 – Vượt quá tồn kho đúng 1 đơn vị (boundary test)

**Request:**
```
GET {{inventory_base_url}}/api/inventory?skuCode=pixel_8&quantity=101
```

**Kết quả kỳ vọng:** `200 OK`, body `false`

### TODO
- [ ] Gửi request quantity = 101
- [ ] Xác nhận trả về `false`

---

## 6. Test Case 5 – SKU không tồn tại trong hệ thống

**Request:**
```
GET {{inventory_base_url}}/api/inventory?skuCode=not_exist_sku&quantity=1
```

**Kết quả kỳ vọng:** `200 OK`, body `false` (vì `existsBySkuCodeAndQuantityIsGreaterThanEqual` không tìm thấy record nào khớp `sku_code`)

### TODO
- [ ] Gửi request với SKU không có trong DB
- [ ] Xác nhận trả về `false`, không phải lỗi 404/500

---

## 7. Test Case 6 – Thiếu param `quantity`

**Request:**
```
GET {{inventory_base_url}}/api/inventory?skuCode=iphone_15
```

**Kết quả kỳ vọng:** `400 Bad Request` — vì `@RequestParam Integer quantity` là bắt buộc theo mặc định, Spring tự trả lỗi khi thiếu.

**Postman Test Script:**
```javascript
pm.test("Missing quantity returns 400", function () {
    pm.response.to.have.status(400);
});
```

### TODO
- [ ] Gửi request thiếu `quantity`
- [ ] Xác nhận status 400

---

## 8. Test Case 7 – Thiếu param `skuCode`

**Request:**
```
GET {{inventory_base_url}}/api/inventory?quantity=10
```

**Kết quả kỳ vọng:** `400 Bad Request`

### TODO
- [ ] Gửi request thiếu `skuCode`
- [ ] Xác nhận status 400

---

## 9. Test Case 8 – `quantity` sai kiểu dữ liệu (chuỗi thay vì số)

**Request:**
```
GET {{inventory_base_url}}/api/inventory?skuCode=iphone_15&quantity=abc
```

**Kết quả kỳ vọng:** `400 Bad Request` (Spring không convert được `"abc"` sang `Integer`)

### TODO
- [ ] Gửi request với `quantity=abc`
- [ ] Xác nhận status 400

---

## 10. Test Case 9 – `quantity` là số âm

**Request:**
```
GET {{inventory_base_url}}/api/inventory?skuCode=iphone_15&quantity=-5
```

**Kết quả kỳ vọng (theo code hiện tại):** `200 OK`, body `true` — vì chưa có validate `quantity >= 0`, số âm vẫn thỏa điều kiện `>=`.

> ⚠️ **Ghi nhận:** đây là edge case cho thấy thiếu validation đầu vào (tương tự order-service). Có thể ghi nhận làm điểm cải tiến (thêm `@Min(1)` hoặc kiểm tra thủ công trong Service).

### TODO
- [ ] Gửi request với `quantity=-5`
- [ ] Ghi nhận kết quả thực tế so với kỳ vọng hợp lý (nên từ chối số âm)

---

## 11. Test Case 10 – Sai method (POST thay vì GET)

**Request:**
```
POST {{inventory_base_url}}/api/inventory?skuCode=iphone_15&quantity=1
```

**Kết quả kỳ vọng:** `405 Method Not Allowed`

### TODO
- [ ] Đổi method sang POST, gửi request
- [ ] Xác nhận status 405

---

## ✅ Checklist tổng hợp kết quả test Inventory Service

| # | Test case | Params | Status kỳ vọng | Body kỳ vọng | Đạt? |
|---|---|---|---|---|---|
| 1 | Đủ hàng | skuCode=iphone_15, quantity=100 | 200 | true | [ ] |
| 2 | Không đủ hàng | skuCode=iphone_15, quantity=200 | 200 | false | [ ] |
| 3 | Boundary `=` tồn kho | skuCode=pixel_8, quantity=100 | 200 | true | [ ] |
| 4 | Boundary vượt 1 đơn vị | skuCode=pixel_8, quantity=101 | 200 | false | [ ] |
| 5 | SKU không tồn tại | skuCode=not_exist_sku, quantity=1 | 200 | false | [ ] |
| 6 | Thiếu `quantity` | skuCode=iphone_15 | 400 | — | [ ] |
| 7 | Thiếu `skuCode` | quantity=10 | 400 | — | [ ] |
| 8 | Sai kiểu `quantity` | quantity=abc | 400 | — | [ ] |
| 9 | `quantity` âm | quantity=-5 | 200 (hiện tại) / nên 400 | true (hiện tại) | [ ] |
| 10 | Sai method (POST) | — | 405 | — | [ ] |

- [ ] Export Postman Collection ra file `.json` để lưu lại
- [ ] Ghi lại kết quả thực tế từng test case (chụp màn hình/note) để nộp bài
- [ ] Đối chiếu dữ liệu bảng `t_inventory` qua DBeaver/Adminer khớp với dữ liệu mẫu ở Test Case 0

---

## Ghi chú

- Test Case 3 và 4 (boundary testing) đặc biệt quan trọng vì kiểm tra đúng logic `GreaterThanEqual` trong `InventoryRepository` — nếu vô tình code dùng `GreaterThan` thay vì `GreaterThanEqual`, Test Case 3 sẽ **fail** (trả `false` thay vì `true`), giúp phát hiện bug.
- Test Case 9 cho thấy input âm hiện chưa được chặn — đây là lỗ hổng giống order-service, có thể tổng hợp chung vào phần "Điểm cải tiến" khi báo cáo bài tập.
- Có thể dùng **Postman Collection Runner** để chạy tự động toàn bộ 10 test case liên tiếp và xuất báo cáo kết quả.
