package com.estebanmm13.pytra_api.metadata.client;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SteamStoreClientTest {

    private static final String SEARCH_URL = "https://store.steampowered.com/api/storesearch/";
    private static final String DETAILS_URL = "https://store.steampowered.com/api/appdetails";

    private MockRestServiceServer server;
    private SteamStoreClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = SteamStoreClient.configure(RestClient.builder());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SteamStoreClient(builder.build());
    }

    @Test
    void searchAsksTheSpanishStorefront() {
        server.expect(requestTo(startsWith(SEARCH_URL)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("term", "portal"))
                .andExpect(queryParam("l", "spanish"))
                .andExpect(queryParam("cc", "ES"))
                .andRespond(withSuccess("""
                        {"total":1,"items":[{"type":"app","name":"Portal 2","id":620,
                          "tiny_image":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/620/capsule_231x87.jpg"}]}""",
                        MediaType.APPLICATION_JSON));

        var results = client.search("portal", 10);

        server.verify();
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().appId()).isEqualTo(620);
    }

    @Test
    void detailsAsksForTheGivenApp() {
        server.expect(requestTo(startsWith(DETAILS_URL)))
                .andExpect(queryParam("appids", "620"))
                .andExpect(queryParam("l", "spanish"))
                .andRespond(withSuccess("""
                        {"620":{"success":true,"data":{"type":"game","name":"Portal 2",
                          "release_date":{"coming_soon":false,"date":"18 ABR 2011"}}}}""", MediaType.APPLICATION_JSON));

        var details = client.getDetails(620);

        server.verify();
        assertThat(details.name()).isEqualTo("Portal 2");
    }

    @ParameterizedTest(name = "HTTP {0} -> STEAM_UNAVAILABLE")
    @ValueSource(ints = {403, 429, 500, 502})
    void httpErrorsBecomeUnavailable(int httpStatus) {
        server.expect(requestTo(startsWith(DETAILS_URL))).andRespond(withStatus(HttpStatus.valueOf(httpStatus)));

        assertUnavailable(() -> client.getDetails(620));
    }

    @Test
    void timeoutBecomesUnavailable() {
        server.expect(requestTo(startsWith(SEARCH_URL))).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertUnavailable(() -> client.search("portal", 10));
    }

    @Test
    void garbageBodyBecomesUnavailable() {
        server.expect(requestTo(startsWith(SEARCH_URL))).andRespond(withSuccess("<html>busy</html>", MediaType.TEXT_HTML));

        assertUnavailable(() -> client.search("portal", 10));
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(SteamIntegrationException.class, e -> {
            assertThat(e.getCode()).isEqualTo(SteamIntegrationException.UNAVAILABLE);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        });
    }
}
