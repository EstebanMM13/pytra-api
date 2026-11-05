package com.estebanmm13.pytra_api.config;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Railway's healthcheck: public, checks the database, reveals nothing; no other actuator endpoint is reachable. */
class HealthEndpointIntegrationTests extends AbstractIntegrationTest {

    @Test
    void healthIsPublicAndUpWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        String auth = bearer(createVerifiedUser("actuator"));
        for (String endpoint : new String[] {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/info"}) {
            performAs(auth, get(endpoint)).andExpect(status().is(not(200)));
        }
    }
}
