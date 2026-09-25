package com.fudn.product_service.exception;

/**
 * Ném ra khi không tìm thấy sản phẩm theo id.
 *
 * Mặc định khi RuntimeException này bay ra mà KHÔNG được handle,
 * Spring sẽ trả về HTTP 500. Để chuyển sang HTTP 404, cần có 1
 * @RestControllerAdvice với @ExceptionHandler bắt class này.
 *
 * Xem TODO 5 trong README để biết cách tạo handler đó.
 */
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String id) {
        super("Khong tim thay san pham voi id: " + id);
    }
}
