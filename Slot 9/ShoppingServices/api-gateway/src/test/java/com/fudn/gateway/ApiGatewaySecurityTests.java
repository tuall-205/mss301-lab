package com.fudn.gateway;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest
@AutoConfigureMockMvc
@EnableWireMock(@ConfigureWireMock(baseUrlProperties = {
        "services.product.url", "services.order.url", "services.inventory.url"}))
class ApiGatewaySecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void requestWithValidJwtShouldBeRoutedToProductService() throws Exception {
        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/api/products"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"1\",\"name\":\"iPhone 15\",\"price\":1000}]")));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/products").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("iPhone 15")));

        WireMock.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/api/products")));
    }

    @Test
    void postOrderWithValidJwtShouldBeRoutedToOrderService() throws Exception {
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/api/order"))
                .willReturn(WireMock.aResponse()
                        .withStatus(201)
                        .withBody("Order Placed Successfully")));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/order")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"iphone_15\",\"price\":1000,\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(content().string("Order Placed Successfully"));
    }

    @Test
    void inventoryRouteShouldForwardQueryParams() throws Exception {
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/inventory"))
                .withQueryParam("skuCode", WireMock.equalTo("iphone_15"))
                .withQueryParam("quantity", WireMock.equalTo("1"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/inventory")
                        .param("skuCode", "iphone_15")
                        .param("quantity", "1")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }
}
