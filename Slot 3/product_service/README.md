# Product Service

Spring Boot 3.2.4 REST API using Java 21 and MongoDB.

- Group: `com.fudn`
- Artifact: `product_service`
- Java package: `com.fudn.productservice`

Java package names conventionally use lowercase letters; the package remains
`com.fudn.productservice` because it is the valid Java form of the project name.

## Endpoints

- `POST /api/product/` creates a product.
- `GET /api/product/` returns all products.

Example request body:

```json
{
  "name": "Mechanical Keyboard",
  "description": "Compact wireless keyboard",
  "price": 79.99
}
```

Run with `mvnw.cmd spring-boot:run` on Windows or `./mvnw spring-boot:run` on macOS/Linux.
