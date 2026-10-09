package com.locallife.backend.push.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.push.api.PushTokenController.PushTokenRequest;
import com.locallife.backend.push.infrastructure.PushTokenRepository;
import com.locallife.backend.user.domain.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PushTokenControllerTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private PushTokenController controller;

    private final JwtAuthentication authentication = new JwtAuthentication(7L, "a@example.com", Role.USER);

    @Test
    void register_ShouldSaveValidToken_ForCurrentUser() {
        assertEquals(HttpStatus.NO_CONTENT, controller.register(authentication,
                new PushTokenRequest("ExponentPushToken[abc]", "android"), httpRequest).getStatusCode());
        verify(pushTokenRepository).save("ExponentPushToken[abc]", 7L, "android");
    }

    @Test
    void register_ShouldRejectInvalidToken_AndIgnoreUnknownPlatform() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.register(authentication,
                new PushTokenRequest("pas-un-jeton", "ios"), httpRequest).getStatusCode());
        verify(pushTokenRepository, never()).save(any(), any(), any());

        controller.register(authentication, new PushTokenRequest("ExponentPushToken[abc]", "windows"), httpRequest);
        verify(pushTokenRepository).save("ExponentPushToken[abc]", 7L, null);
    }

    @Test
    void unregister_ShouldOnlyDeleteCurrentUsersToken() {
        controller.unregister(authentication, new PushTokenRequest("ExponentPushToken[abc]", null));
        verify(pushTokenRepository).deleteByTokenAndUserId("ExponentPushToken[abc]", 7L);
    }

}
