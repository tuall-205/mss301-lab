package com.fudn.orderservice;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import io.restassured.RestAssured;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock(@ConfigureWireMock(baseUrlProperties = "inventory.url"))
class SwaggerIntegrationTest {

    @ServiceConnection
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.3.0");

    static {
        mySQLContainer.start();
    }

    @LocalServerPort
    private Integer port;

    @BeforeEach
    void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Test
    void swaggerUiShouldBeAccessible() {
        RestAssured.given()
                .when().get("/swagger-ui/index.html")
                .then().statusCode(200);
    }

    @Test
    void apiDocsShouldReturnJson() {
        RestAssured.given()
                .when().get("/api-docs")
                .then().statusCode(200)
                .body("info.title", equalTo("Order Service API"))
                .body("info.version", equalTo("v0.0.1"));
    }

    @Test
    void apiDocsShouldContainOrderEndpoints() {
        RestAssured.given()
                .when().get("/api-docs")
                .then().statusCode(200)
                .body("paths.'/api/order'", notNullValue());
    }
}
