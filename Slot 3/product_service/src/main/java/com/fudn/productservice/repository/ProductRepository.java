package com.fudn.productservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fudn.productservice.model.Product;

public interface ProductRepository extends MongoRepository<Product, String> {
}
