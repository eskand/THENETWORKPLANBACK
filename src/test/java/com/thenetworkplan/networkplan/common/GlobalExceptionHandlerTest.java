package com.thenetworkplan.networkplan.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.thenetworkplan.networkplan.common.web.ApiError;
import com.thenetworkplan.networkplan.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Audit du 23/09 : une route inconnue (GET /v1/does-not-exist) repondait
 * 500 « Unexpected error, see server logs » et ecrivait une pile d'erreur dans le
 * journal — le gestionnaire generique attrapait NoResourceFoundException. Une
 * route inconnue est un 404, pas une panne du serveur.
 */
class GlobalExceptionHandlerTest {

    @Test
    void anUnknownRouteIsA404NotAServerError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/does-not-exist");

        ResponseEntity<ApiError> response = new GlobalExceptionHandler()
                .handleNoRoute(new NoResourceFoundException(HttpMethod.GET, "/v1/does-not-exist", "v1/does-not-exist"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().path()).isEqualTo("/v1/does-not-exist");
    }
}
