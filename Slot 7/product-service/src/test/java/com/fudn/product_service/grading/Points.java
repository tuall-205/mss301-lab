package com.fudn.product_service.grading;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation đánh dấu số điểm cho một test case dùng để chấm tự động.
 *
 * Ví dụ:
 *   @Test
 *   @Points(value = 2.0, description = "Update sản phẩm thành công")
 *   void shouldUpdateProduct() { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Points {
    /** Số điểm cho test case này. */
    double value();

    /** Mô tả ngắn gọn của tiêu chí chấm. */
    String description() default "";
}
