
package com.example.backend.domain.user.service;

import com.example.backend.domain.site.entity.Site;
import com.example.backend.domain.site.repository.SiteRepository;
import com.example.backend.domain.user.dto.AuthResponse;
import com.example.backend.domain.user.dto.LoginRequest;
import com.example.backend.domain.user.dto.SignupRequest;
import com.example.backend.domain.user.entity.User;
import com.example.backend.domain.user.repository.UserRepository;
import com.example.backend.global.config.JwtTokenProvider;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final String SITE_MANAGER = "SITE_MANAGER";

    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            SiteRepository siteRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.siteRepository = siteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public void signup(SignupRequest request) {

        String loginId = request.loginId().trim();

        if (userRepository.existsByLoginId(loginId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 사용 중인 로그인 아이디입니다."
            );
        }

        Long siteId = resolveSiteId(request);

        User user = new User(
                loginId,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                request.hp(),
                request.email() == null
                        ? null
                        : request.email().trim(),
                request.role(),
                siteId
        );

        userRepository.save(user);
    }

    private Long resolveSiteId(SignupRequest request) {
        if (!SITE_MANAGER.equals(request.role())) {
            return null;
        }

        if (request.siteName() == null || request.siteName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "SITE_MANAGER는 현장 이름을 입력해야 합니다."
            );
        }

        String siteName = request.siteName().trim();

        Site site = siteRepository.findByName(siteName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 현장입니다."
                ));

        return site.getId();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByLoginId(
                request.loginId().trim()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "로그인 아이디 또는 비밀번호가 올바르지 않습니다."
        ));

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 아이디 또는 비밀번호가 올바르지 않습니다."
            );
        }

        String token = jwtTokenProvider.generateToken(
                user.getLoginId()
        );

        return new AuthResponse(token, "Bearer", user.getRole());
    }

    @Transactional(readOnly = true)
    public boolean isLoginIdAvailable(String loginId) {
        if (loginId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "로그인 아이디를 입력해주세요."
            );
        }

        String normalizedLoginId = loginId.trim();

        if (normalizedLoginId.isEmpty()
                || normalizedLoginId.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "로그인 아이디는 공백이 아닌 1~255자여야 합니다."
            );
        }
        return !userRepository.existsByLoginId(normalizedLoginId);
    }
}
