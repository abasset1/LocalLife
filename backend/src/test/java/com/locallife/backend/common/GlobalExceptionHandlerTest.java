package com.locallife.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private WebRequest webRequest;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleException_ShouldReturnInternalServerErrorWithExpectedBody() {
        // Given
        when(webRequest.getDescription(false)).thenReturn("uri=/api/activities");
        RuntimeException exception = new RuntimeException("boom");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleException(exception, webRequest);

        // Then : le contrat HTTP existant reste inchangé pour les clients.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(response.getBody().error()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        assertThat(response.getBody().message()).isEqualTo("boom");
        assertThat(response.getBody().path()).isEqualTo("/api/activities");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

}
