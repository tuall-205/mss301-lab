package com.fudn.product_service.dto;

import java.math.BigDecimal;

//Record đại diện cho dữ liệu phản hồi từ API, có thể chứa các trường dữ liệu mà bạn
// muốn trả về cho client sau khi xử lý yêu cầu.
// Trong trường hợp này, ProductResponse có thể chứa các trường như id, name, description, price,...
// hoặc bất kỳ thông tin nào khác mà bạn muốn gửi lại cho client sau khi tạo hoặc lấy thông tin sản phẩm.
public record ProductResponse(String id, String name, String description, BigDecimal price) {
}

