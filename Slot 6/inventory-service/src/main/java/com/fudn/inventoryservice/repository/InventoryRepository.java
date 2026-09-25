package com.fudn.inventoryservice.repository;

import com.fudn.inventoryservice.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // Spring Data JPA tự sinh query theo tên method:
    // SELECT COUNT(*) > 0 FROM t_inventory WHERE sku_code = ? AND quantity >= ?
    boolean existsBySkuCodeAndQuantityIsGreaterThanEqual(String skuCode, int quantity);
}
