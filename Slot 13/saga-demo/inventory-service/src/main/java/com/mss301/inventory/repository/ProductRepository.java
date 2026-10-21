package com.mss301.inventory.repository;

import com.mss301.inventory.entity.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {
}
