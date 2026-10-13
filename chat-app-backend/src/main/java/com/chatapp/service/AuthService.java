package com.chatapp.service;
import com.chatapp.dto.AuthResponse;
import com.chatapp.dto.LoginRequest;
import com.chatapp.dto.RegisterRequest;
import com.chatapp.entity.User;
import com.chatapp.exception.AuthenticationException;
import com.chatapp.exception.DuplicateResourceException;
import com.chatapp.repository.UserRepository;
import com.chatapp.service.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            BCryptPasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse login(LoginRequest request) {

        Optional<User> optionalUser =
                userRepository.findByUsername(request.getUsername());

        if (optionalUser.isEmpty()) {
            throw new AuthenticationException(
                    "Tên đăng nhập hoặc mật khẩu bị sai !"
            );
        }

        User user = optionalUser.get();

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new AuthenticationException(
                    "Tên đăng nhập hoặc mật khẩu bị sai !"
            );
        }

        // Đăng nhập thành công → tạo JWT
        String token = jwtService.generateToken(
                user.getUsername()
        );

        return new AuthResponse(
                true,
                "Đăng nhập thành công",
                user.getUsername(),
                token
        );
    }

    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException(
                    "Tên đăng nhập đã tồn tại !"
            );
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Email đã tồn tại !"
            );
        }

        User user = new User();

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        String hashedPassword =
                passwordEncoder.encode(request.getPassword());

        user.setPassword(hashedPassword);

        userRepository.save(user);

        return new AuthResponse(
                true,
                "Đăng ký thành công",
                user.getUsername(),
                null
        );
    }
}
