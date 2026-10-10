
package com.example.backend.domain.user.service;

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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
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

        User user = new User(
                loginId,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                request.hp(),
                request.email() == null
                        ? null
                        : request.email().trim()
        );

        userRepository.save(user);
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

        return new AuthResponse(token, "Bearer");
    }
}
