package com.locallife.backend.auth.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private AuthService authService;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void run_ShouldDoNothing_WhenEmailIsBlank() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(authService, "admin", "", "motDePasseAdmin1");

        runner.run(applicationArguments);

        verify(authService, never()).bootstrapFirstAdmin(anyString(), anyString(), anyString());
    }

    @Test
    void run_ShouldDoNothing_WhenPasswordIsBlank() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(authService, "admin", "admin@example.com", "");

        runner.run(applicationArguments);

        verify(authService, never()).bootstrapFirstAdmin(anyString(), anyString(), anyString());
    }

    @Test
    void run_ShouldCallBootstrap_WhenEmailAndPasswordAreProvided() {
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(authService, "admin", "admin@example.com", "motDePasseAdmin1");
        User admin = new User(1L, "admin", "admin@example.com", "hash", Role.ADMIN, LocalDateTime.now());
        when(authService.bootstrapFirstAdmin("admin", "admin@example.com", "motDePasseAdmin1"))
                .thenReturn(Optional.of(admin));

        runner.run(applicationArguments);

        verify(authService, times(1)).bootstrapFirstAdmin("admin", "admin@example.com", "motDePasseAdmin1");
    }

    @Test
    void run_ShouldNotThrow_WhenAnAdminAlreadyExists() {
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(authService, "admin", "admin@example.com", "motDePasseAdmin1");
        when(authService.bootstrapFirstAdmin(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        runner.run(applicationArguments);

        verify(authService, times(1)).bootstrapFirstAdmin(any(), any(), any());
    }

    @Test
    void run_ShouldNotThrow_WhenBootstrapCredentialsAreInvalid() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(authService, "admin", "admin@example.com", "court");
        when(authService.bootstrapFirstAdmin("admin", "admin@example.com", "court"))
                .thenThrow(new IllegalArgumentException("Le mot de passe doit contenir au moins 8 caractères."));

        runner.run(applicationArguments);

        verify(authService, times(1)).bootstrapFirstAdmin("admin", "admin@example.com", "court");
    }
}
