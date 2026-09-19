package com.fudn.product_service.service;

import com.fudn.product_service.dto.ProductRequest;
import com.fudn.product_service.dto.ProductResponse;
import com.fudn.product_service.exception.ProductNotFoundException;
import com.fudn.product_service.model.Product;
import com.fudn.product_service.repository.IProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final IProductRepository productRepository;

    // ==========================================================
    // ĐÃ ĐƯỢC IMPLEMENT — đọc kỹ để hiểu pattern, rồi làm tương tự
    // ==========================================================

    public ProductResponse createProduct(ProductRequest productRequest) {
        // Ánh xạ từ ProductRequest sang Product entity, sau đó lưu vào DB.
        Product product = Product.builder()
                .id(productRequest.getId())
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .build();
        productRepository.save(product);
        log.info("Product {} saved..", product.getId());
        // Ánh xạ từ Product entity sang ProductResponse để trả về cho client.
        return new ProductResponse(product.getId(), product.getName(),
                product.getDescription(), product.getPrice());
    }

    public List<ProductResponse> getAllProducts() {
        // Lấy tất cả sản phẩm từ DB, ánh xạ sang ProductResponse.
        List<Product> products = productRepository.findAll();
        return products.stream()
                .map(product -> new ProductResponse(product.getId(),
                        product.getName(), product.getDescription(),
                        product.getPrice())).toList();
    }

    // ==========================================================
    // TODO 1 — Hoàn thiện method updateProduct
    // ----------------------------------------------------------
    // YÊU CẦU:
    //   1. Tìm Product theo id bằng productRepository.findById(id)
    //   2. Nếu KHÔNG tìm thấy -> throw new ProductNotFoundException(id)
    //   3. Nếu tìm thấy -> cập nhật 3 field (name, description, price)
    //      từ request, sau đó productRepository.save(product)
    //   4. Trả về ProductResponse chứa dữ liệu MỚI vừa cập nhật
    //
    // GỢI Ý:
    //   - Optional<Product> opt = productRepository.findById(id);
    //   - Hoặc dùng .orElseThrow(() -> new ProductNotFoundException(id))
    //   - Dùng product.setName(...), product.setDescription(...), product.setPrice(...)
    //   - Đừng tạo Product mới — phải update đúng record cũ để không bị tạo id khác
    // ==========================================================
    public ProductResponse updateProduct(String id, ProductRequest productRequest) {
        // TODO: viết logic update tại đây

        throw new UnsupportedOperationException(
                "TODO: Sinh vien chua implement updateProduct()");
    }

    // ==========================================================
    // TODO 2 — Hoàn thiện method deleteProduct
    // ----------------------------------------------------------
    // YÊU CẦU:
    //   1. Kiểm tra id có tồn tại trong DB không
    //      (dùng productRepository.existsById(id))
    //   2. Nếu KHÔNG tồn tại -> throw new ProductNotFoundException(id)
    //   3. Nếu có -> productRepository.deleteById(id)
    //   4. Method trả về kiểu void
    //
    // LƯU Ý:
    //   - PHẢI kiểm tra existsById TRƯỚC khi delete, nếu không Spring Data
    //     sẽ "âm thầm thành công" khi xoá id không tồn tại -> không trả 404.
    // ==========================================================
    public void deleteProduct(String id) {
        // TODO: viết logic delete tại đây
        throw new UnsupportedOperationException(
                "TODO: Sinh vien chua implement deleteProduct()");
    }
}
