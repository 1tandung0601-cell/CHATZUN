
package com.chatapp.config;

import com.chatapp.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    // Chỉ chạy JWT Filter trong Spring Security, tránh đăng ký hai lần.
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                // API dùng JWT, không sử dụng CSRF token.
                .csrf(csrf -> csrf.disable())

                // Không lưu trạng thái đăng nhập trong HTTP Session.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Trả JSON nếu truy cập API cần đăng nhập mà chưa xác thực.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(
                                    HttpServletResponse.SC_UNAUTHORIZED
                            );
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"Vui lòng đăng nhập hoặc cung cấp JWT hợp lệ\"}"
                            );
                        })
                )

                // Các đường dẫn công khai cần thiết để thử WebSocket.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/websocket-test.html",
                                "/ws",
                                "/ws/**"
                        ).permitAll()

                        // Các API còn lại vẫn yêu cầu xác thực.
                        .anyRequest().authenticated()
                )

                // Không dùng form login hoặc HTTP Basic.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                // Xác thực JWT trước bước kiểm tra quyền.
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
