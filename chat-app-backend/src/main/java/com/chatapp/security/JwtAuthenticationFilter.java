
package com.chatapp.security;

import com.chatapp.entity.User;
import com.chatapp.repository.UserRepository;
import com.chatapp.service.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String authHeader = request.getHeader("Authorization");

        // Chỉ ghi log thông tin request, tuyệt đối không log JWT.
        System.out.println("[JWT] Request: " + requestUri);

        // Không có Bearer token thì để Spring Security quyết định
        // request này có được phép truy cập hay không.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();

        // Header có Bearer nhưng token rỗng.
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtService.isTokenValid(token)) {
                Claims claims = jwtService.extractClaims(token);
                String username = claims.getSubject();

                // Chỉ xác thực nếu SecurityContext chưa có người dùng.
                if (username != null
                        && SecurityContextHolder.getContext()
                        .getAuthentication() == null) {

                    Optional<User> optionalUser =
                            userRepository.findByUsername(username);

                    if (optionalUser.isPresent()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        username,
                                        null,
                                        Collections.emptyList()
                                );

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource()
                                        .buildDetails(request)
                        );

                        SecurityContextHolder.getContext()
                                .setAuthentication(authentication);

                        System.out.println(
                                "[JWT] Đã xác thực user: " + username
                        );
                    } else {
                        System.out.println(
                                "[JWT] Không tìm thấy user trong DB"
                        );
                    }
                }
            } else {
                System.out.println("[JWT] Token không hợp lệ");
            }
        } catch (JwtException | IllegalArgumentException e) {
            // Không in token hoặc stack trace chứa thông tin không cần thiết.
            System.out.println(
                    "[JWT] Xác thực token thất bại: "
                            + e.getClass().getSimpleName()
            );

            SecurityContextHolder.clearContext();
        }

        // Tiếp tục chuỗi filter để Spring Security xử lý quyền truy cập.
        filterChain.doFilter(request, response);
    }
}
