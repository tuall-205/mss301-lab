package com.fudn.product_service.controller;

import com.fudn.product_service.dto.ProductRequest;
import com.fudn.product_service.dto.ProductResponse;
import com.fudn.product_service.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ==========================================================
    // ĐÃ ĐƯỢC IMPLEMENT — đọc kỹ làm mẫu
    // ==========================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@RequestBody ProductRequest productRequest) {
        return productService.createProduct(productRequest);
    }

    @GetMapping
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    // ==========================================================
    // TODO 3 — Endpoint UPDATE sản phẩm
    // ----------------------------------------------------------
    // YÊU CẦU:
    //   - HTTP method : PUT
    //   - URL path   : /api/products/{id}
    //   - Path var   : id  (String)
    //   - Body       : ProductRequest
    //   - Return     : ProductResponse (dữ liệu sau khi update)
    //   - Status     : 200 OK (mặc định của Spring khi return không-void)
    //   - Khi service throw ProductNotFoundException -> 404
    //     (xử lý bằng GlobalExceptionHandler ở TODO 5)
    //
    // GỢI Ý ANNOTATION:
    //   @PutMapping("/{id}")
    //   public ProductResponse updateProduct(
    //          @PathVariable String id,
    //          @RequestBody ProductRequest productRequest) { ... }
    // ==========================================================
    // TODO: viết endpoint update tại đây


    // ==========================================================
    // TODO 4 — Endpoint DELETE sản phẩm
    // ----------------------------------------------------------
    // YÊU CẦU:
    //   - HTTP method : DELETE
    //   - URL path   : /api/products/{id}
    //   - Path var   : id  (String)
    //   - Return     : void
    //   - Status     : 204 NO CONTENT  (dùng @ResponseStatus)
    //   - Khi service throw ProductNotFoundException -> 404
    //
    // GỢI Ý ANNOTATION:
    //   @DeleteMapping("/{id}")
    //   @ResponseStatus(HttpStatus.NO_CONTENT)
    //   public void deleteProduct(@PathVariable String id) { ... }
    // ==========================================================
    // TODO: viết endpoint delete tại đây
}
