package com.fudn.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

//Class product request đại diện cho dữ liệu yêu cầu từ client khi tạo hoặc cập nhật sản phẩm.
// Nó có thể chứa các trường dữ liệu như id, name, description, price,...
// hoặc bất kỳ thông tin nào khác mà bạn muốn nhận từ client khi họ gửi yêu cầu tạo hoặc cập nhật sản phẩm.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
}


