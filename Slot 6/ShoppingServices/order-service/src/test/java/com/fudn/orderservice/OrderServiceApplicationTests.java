package com.fudn.orderservice;

import com.fudn.orderservice.stub.InventoryStubs;
import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import static org.hamcrest.MatcherAssert.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderServiceApplicationTests {

    @ServiceConnection
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.3.0");

    static WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());

    static {
        wireMockServer.start();
        mySQLContainer.start();
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("inventory.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @LocalServerPort
    private Integer port;

    @BeforeEach
    void setup() {
        configureFor("localhost", wireMockServer.port());
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Test
    void shouldSubmitOrder() {
        String submitOrderJson = """
                {
                     "skuCode": "iphone_15",
                     "price": 1000,
                     "quantity": 1
                }
                """;

        InventoryStubs.stubInventoryCall("iphone_15", 1);

        var responseBodyString = RestAssured.given()
                .contentType("application/json")
                .body(submitOrderJson)
                .when()
                .post("/api/order")
                .then()
                .log().all()
                .statusCode(201)
                .extract().body().asString();

        assertThat(responseBodyString, Matchers.is("Order Placed Successfully"));
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }
}
