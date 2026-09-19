package com.fudn.product_service.grading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fudn.product_service.dto.ProductRequest;
import com.fudn.product_service.model.Product;
import com.fudn.product_service.repository.IProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUTOGRADING TESTS — Chấm điểm tự động cho 2 chức năng:
 *   1. UPDATE  (PUT    /api/products/{id})
 *   2. DELETE  (DELETE /api/products/{id})
 *
 * Cách hoạt động:
 *  - Mỗi @Test gắn @Points để khai báo điểm tối đa.
 *  - GradingExtension sẽ tự cộng điểm khi test PASS, 0 điểm khi test FAIL.
 *  - Khi tất cả test chạy xong, báo cáo được ghi vào:
 *      target/grading-report.txt
 *      target/grading-report.json
 *
 * Cách chạy:
 *   mvn -Dtest=ProductUpdateDeleteGradingTests test
 *
 * Tổng điểm: 10.0
 *   - UPDATE: 5.0 điểm  (3 test)
 *   - DELETE: 5.0 điểm  (3 test)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ExtendWith(GradingExtension.class)
class ProductUpdateDeleteGradingTests {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.5");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    IProductRepository productRepository;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void cleanup() {
        productRepository.deleteAll();
    }

    /** Helper: tạo sẵn 1 product trong DB để test update/delete. */
    private Product seedProduct() {
        Product p = Product.builder()
                .name("iPhone 15")
                .description("Apple smartphone")
                .price(new BigDecimal("999.99"))
                .build();
        return productRepository.save(p);
    }

    // =====================================================================
    //  UPDATE  -  PUT /api/products/{id}     (5.0 điểm)
    // =====================================================================

    @Test
    @DisplayName("UPDATE-1: PUT /api/products/{id} trả về 200 và body có dữ liệu mới")
    @Points(value = 2.0, description = "UPDATE - Trả về 200 và body đúng")
    void updateShouldReturn200WithUpdatedBody() throws Exception {
        Product existing = seedProduct();

        ProductRequest update = ProductRequest.builder()
                .name("iPhone 15 Pro")
                .description("Updated description")
                .price(new BigDecimal("1199.99"))
                .build();

        mockMvc.perform(put("/api/products/" + existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.getId()))
                .andExpect(jsonPath("$.name").value("iPhone 15 Pro"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.price").value(1199.99));
    }

    @Test
    @DisplayName("UPDATE-2: Dữ liệu thực sự được lưu vào database")
    @Points(value = 2.0, description = "UPDATE - Dữ liệu thay đổi trong DB")
    void updateShouldPersistChangesToDatabase() throws Exception {
        Product existing = seedProduct();

        ProductRequest update = ProductRequest.builder()
                .name("Samsung Galaxy S24")
                .description("Android flagship")
                .price(new BigDecimal("899.00"))
                .build();

        mockMvc.perform(put("/api/products/" + existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        // kiểm tra trực tiếp trong DB
        Optional<Product> fromDb = productRepository.findById(existing.getId());
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getName()).isEqualTo("Samsung Galaxy S24");
        assertThat(fromDb.get().getDescription()).isEqualTo("Android flagship");
        assertThat(fromDb.get().getPrice()).isEqualByComparingTo("899.00");

        // tổng số bản ghi vẫn là 1, không tạo thêm bản ghi mới
        assertThat(productRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("UPDATE-3: Update id không tồn tại trả về 404")
    @Points(value = 1.0, description = "UPDATE - 404 khi id không tồn tại")
    void updateShouldReturn404WhenProductNotFound() throws Exception {
        ProductRequest update = ProductRequest.builder()
                .name("Ghost")
                .description("does not exist")
                .price(new BigDecimal("1.00"))
                .build();

        mockMvc.perform(put("/api/products/non-existing-id-xyz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    //  DELETE  -  DELETE /api/products/{id}    (5.0 điểm)
    // =====================================================================

    @Test
    @DisplayName("DELETE-1: DELETE /api/products/{id} trả về 204 (hoặc 200)")
    @Points(value = 2.0, description = "DELETE - Trả về 204/200")
    void deleteShouldReturnNoContent() throws Exception {
        Product existing = seedProduct();

        mockMvc.perform(delete("/api/products/" + existing.getId()))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 204 && s != 200) {
                        throw new AssertionError(
                                "Mong đợi status 204 hoặc 200, thực tế: " + s);
                    }
                });
    }

    @Test
    @DisplayName("DELETE-2: Sản phẩm bị xoá khỏi database")
    @Points(value = 2.0, description = "DELETE - Xoá khỏi DB")
    void deleteShouldRemoveProductFromDatabase() throws Exception {
        Product existing = seedProduct();
        // thêm 1 product khác để chắc chắn chỉ đúng 1 cái bị xoá
        Product other = productRepository.save(Product.builder()
                .name("Other")
                .description("other product")
                .price(new BigDecimal("10.00"))
                .build());

        mockMvc.perform(delete("/api/products/" + existing.getId()));

        assertThat(productRepository.findById(existing.getId())).isEmpty();
        assertThat(productRepository.findById(other.getId())).isPresent();
        assertThat(productRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("DELETE-3: Xoá id không tồn tại trả về 404")
    @Points(value = 1.0, description = "DELETE - 404 khi id không tồn tại")
    void deleteShouldReturn404WhenProductNotFound() throws Exception {
        mockMvc.perform(delete("/api/products/non-existing-id-xyz"))
                .andExpect(status().isNotFound());
    }
}
