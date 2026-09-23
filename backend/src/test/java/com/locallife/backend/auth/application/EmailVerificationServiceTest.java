package com.locallife.backend.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.auth.domain.EmailVerificationToken;
import com.locallife.backend.auth.infrastructure.EmailVerificationTokenRepository;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String BASE_URL = "http://localhost:5173";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userRepository, tokenRepository, emailService, BASE_URL);
    }

    private static User user(boolean verified) {
        return new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now(), verified);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static EmailVerificationToken token(boolean used, LocalDateTime expiresAt, LocalDateTime createdAt) {
        return new EmailVerificationToken(5L, 1L, "hash", expiresAt, used, createdAt);
    }

    // --- sendVerification ---

    @Test
    void sendVerification_ShouldStoreOnlyTheHashAndSendTheLink() throws Exception {
        service.sendVerification(user(false));

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailVerificationEmail(org.mockito.ArgumentMatchers.eq("alice@example.com"),
                link.capture());

        String prefix = BASE_URL + "/verify-email?token=";
        assertTrue(link.getValue().startsWith(prefix));
        String rawToken = link.getValue().substring(prefix.length());

        assertNotEquals(rawToken, saved.getValue().tokenHash());
        assertEquals(sha256(rawToken), saved.getValue().tokenHash());
        assertFalse(saved.getValue().used());
        assertEquals(1L, saved.getValue().userId());
        assertTrue(saved.getValue().expiresAt().isAfter(LocalDateTime.now().plusHours(23)));
    }

    @Test
    void sendVerification_ShouldInvalidatePreviousActiveTokens() {
        EmailVerificationToken previous = token(false, LocalDateTime.now().plusHours(1), LocalDateTime.now().minusHours(1));
        when(tokenRepository.findAllByUserIdAndUsedFalse(1L)).thenReturn(List.of(previous));

        service.sendVerification(user(false));

        verify(tokenRepository).save(new EmailVerificationToken(
                5L, 1L, "hash", previous.expiresAt(), true, previous.createdAt()));
    }

    @Test
    void sendVerification_ShouldNotThrow_WhenMailSendingFails() {
        doThrow(new MailSendException("SMTP indisponible"))
                .when(emailService).sendEmailVerificationEmail(anyString(), anyString());

        service.sendVerification(user(false));

        verify(tokenRepository).save(any(EmailVerificationToken.class));
    }

    // --- verify ---

    @Test
    void verify_ShouldMarkUserVerifiedAndConsumeToken() throws Exception {
        LocalDateTime created = LocalDateTime.now().minusMinutes(5);
        EmailVerificationToken stored = token(false, LocalDateTime.now().plusHours(1), created);
        when(tokenRepository.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false)));

        service.verify("raw-token");

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertTrue(savedUser.getValue().emailVerified());
        assertEquals("hash", savedUser.getValue().passwordHash());
        verify(tokenRepository).save(new EmailVerificationToken(
                5L, 1L, "hash", stored.expiresAt(), true, created));
    }

    @Test
    void verify_ShouldThrow_WhenTokenIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> service.verify("  "));
        assertThrows(IllegalArgumentException.class, () -> service.verify(null));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_ShouldThrow_WhenTokenIsUnknown() throws Exception {
        when(tokenRepository.findByTokenHash(sha256("inconnu"))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.verify("inconnu"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_ShouldThrow_WhenTokenIsExpired() throws Exception {
        EmailVerificationToken expired = token(false, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusDays(2));
        when(tokenRepository.findByTokenHash(sha256("expire"))).thenReturn(Optional.of(expired));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false)));

        assertThrows(IllegalArgumentException.class, () -> service.verify("expire"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_ShouldThrow_WhenTokenAlreadyUsedAndAccountNotVerified() throws Exception {
        EmailVerificationToken used = token(true, LocalDateTime.now().plusHours(1), LocalDateTime.now().minusMinutes(5));
        when(tokenRepository.findByTokenHash(sha256("utilise"))).thenReturn(Optional.of(used));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false)));

        assertThrows(IllegalArgumentException.class, () -> service.verify("utilise"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_ShouldDoNothing_WhenAccountIsAlreadyVerified() throws Exception {
        EmailVerificationToken used = token(true, LocalDateTime.now().plusHours(1), LocalDateTime.now().minusMinutes(5));
        when(tokenRepository.findByTokenHash(sha256("deja-clique"))).thenReturn(Optional.of(used));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(true)));

        service.verify("deja-clique");

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    // --- resend ---

    @Test
    void resend_ShouldThrow_WhenEmailIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> service.resend(" "));
        assertThrows(IllegalArgumentException.class, () -> service.resend(null));
    }

    @Test
    void resend_ShouldDoNothing_WhenNoAccountMatches() {
        when(userRepository.findByEmail("inconnu@example.com")).thenReturn(Optional.empty());

        service.resend("inconnu@example.com");

        verify(emailService, never()).sendEmailVerificationEmail(anyString(), anyString());
    }

    @Test
    void resend_ShouldDoNothing_WhenAccountAlreadyVerified() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user(true)));

        service.resend("alice@example.com");

        verify(emailService, never()).sendEmailVerificationEmail(anyString(), anyString());
    }

    @Test
    void resend_ShouldDoNothing_WhenALinkWasIssuedLessThanAMinuteAgo() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user(false)));
        when(tokenRepository.findAllByUserIdAndUsedFalse(1L)).thenReturn(List.of(
                token(false, LocalDateTime.now().plusHours(24), LocalDateTime.now().minusSeconds(10))));

        service.resend("alice@example.com");

        verify(emailService, never()).sendEmailVerificationEmail(anyString(), anyString());
    }

    @Test
    void resend_ShouldSendNewLink_WhenAccountUnverifiedAndCooldownElapsed() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user(false)));
        when(tokenRepository.findAllByUserIdAndUsedFalse(1L)).thenReturn(List.of(
                token(false, LocalDateTime.now().plusHours(20), LocalDateTime.now().minusMinutes(10))));

        service.resend("  alice@example.com ");

        verify(emailService).sendEmailVerificationEmail(anyString(), anyString());
    }

}
