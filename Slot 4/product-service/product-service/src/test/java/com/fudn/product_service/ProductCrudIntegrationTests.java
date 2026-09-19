package com.fudn.product_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fudn.product_service.dto.ProductRequest;
import com.fudn.product_service.model.Product;
import com.fudn.product_service.repository.IProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests DÀNH CHO SINH VIÊN tự kiểm tra phần làm bài.
 *
 * Khác với bộ test trong package "grading/" (dùng để chấm điểm),
 * file này chỉ cover các case PHỔ BIẾN nhất — đủ để sinh viên biết
 * code của mình đã chạy đúng hướng chưa.
 *
 * Cách chạy:
 *   ./mvnw -Dtest=ProductCrudIntegrationTests test
 *
 * Khi cả 4 test ở đây PASS, có khả năng cao bộ test grading cũng pass.
 * Nhưng có thể có edge case mà file này không cover — đừng coi đây là
 * "đáp án đầy đủ", hãy đọc kỹ README để hiểu full yêu cầu.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
class ProductCrudIntegrationTests {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.5");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired MockMvc mockMvc;
    @Autowired IProductRepository productRepository;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void cleanup() {
        productRepository.deleteAll();
    }

    /** Tạo sẵn 1 product để dùng cho test update/delete. */
    private Product givenAnExistingProduct() {
        return productRepository.save(Product.builder()
                .name("Macbook Pro")
                .description("Apple laptop")
                .price(new BigDecimal("1999.00"))
                .build());
    }

    // ============================================================
    //  UPDATE — PUT /api/products/{id}
    // ============================================================

    @Test
    @DisplayName("[UPDATE happy path] PUT thành công trả về 200 và body có dữ liệu mới")
    void updateExistingProduct_shouldReturnOkAndNewBody() throws Exception {
        Product existing = givenAnExistingProduct();

        ProductRequest body = ProductRequest.builder()
                .name("Macbook Pro M3")
                .description("Apple laptop 2024")
                .price(new BigDecimal("2499.00"))
                .build();

        mockMvc.perform(put("/api/products/" + existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.getId()))
                .andExpect(jsonPath("$.name").value("Macbook Pro M3"))
                .andExpect(jsonPath("$.price").value(2499.00));

        // Bonus check: DB phải thật sự thay đổi (không tạo bản ghi mới)
        Optional<Product> after = productRepository.findById(existing.getId());
        assertThat(after).isPresent();
        assertThat(after.get().getName()).isEqualTo("Macbook Pro M3");
        assertThat(productRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("[UPDATE error] PUT id không tồn tại trả về 404")
    void updateMissingProduct_shouldReturn404() throws Exception {
        ProductRequest body = ProductRequest.builder()
                .name("X").description("X").price(BigDecimal.ONE).build();

        mockMvc.perform(put("/api/products/this-id-does-not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    //  DELETE — DELETE /api/products/{id}
    // ============================================================

    @Test
    @DisplayName("[DELETE happy path] DELETE thành công trả về 204 và xoá khỏi DB")
    void deleteExistingProduct_shouldReturnNoContentAndRemove() throws Exception {
        Product existing = givenAnExistingProduct();

        mockMvc.perform(delete("/api/products/" + existing.getId()))
                .andExpect(status().isNoContent());

        assertThat(productRepository.findById(existing.getId())).isEmpty();
        assertThat(productRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("[DELETE error] DELETE id không tồn tại trả về 404")
    void deleteMissingProduct_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/products/this-id-does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
