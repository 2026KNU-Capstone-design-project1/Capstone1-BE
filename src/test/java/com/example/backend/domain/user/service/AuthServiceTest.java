package com.example.backend.domain.user.service;

import com.example.backend.domain.site.entity.Site;
import com.example.backend.domain.site.repository.SiteRepository;
import com.example.backend.domain.user.dto.SignupRequest;
import com.example.backend.domain.user.entity.User;
import com.example.backend.domain.user.repository.UserRepository;
import com.example.backend.global.config.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private SiteRepository siteRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        siteRepository = mock(SiteRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);

        authService = new AuthService(
                userRepository,
                siteRepository,
                passwordEncoder,
                jwtTokenProvider
        );
    }

    @Test
    void siteManagerSignupStoresSiteId() {
        SignupRequest request = signupRequest("SITE_MANAGER", " 서울 현장 ");
        Site site = mock(Site.class);

        when(siteRepository.findByName("서울 현장"))
                .thenReturn(Optional.of(site));
        when(site.getId()).thenReturn(10L);
        when(passwordEncoder.encode("password123"))
                .thenReturn("encoded-password");

        authService.signup(request);

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(10L, userCaptor.getValue().getSiteId());
    }

    @Test
    void siteManagerSignupWithoutSiteNameReturnsBadRequest() {
        SignupRequest request = signupRequest("SITE_MANAGER", null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.signup(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void siteManagerSignupWithUnknownSiteReturnsNotFound() {
        SignupRequest request = signupRequest("SITE_MANAGER", "없는 현장");
        when(siteRepository.findByName("없는 현장"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.signup(request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void adminSignupDoesNotStoreSiteId() {
        SignupRequest request = signupRequest("ADMIN", null);
        when(passwordEncoder.encode("password123"))
                .thenReturn("encoded-password");

        authService.signup(request);

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertNull(userCaptor.getValue().getSiteId());
        verify(siteRepository, never()).findByName(any());
    }

    private SignupRequest signupRequest(String role, String siteName) {
        return new SignupRequest(
                "login-id",
                "password123",
                "홍길동",
                1012345678L,
                "user@example.com",
                role,
                siteName
        );
    }
}
