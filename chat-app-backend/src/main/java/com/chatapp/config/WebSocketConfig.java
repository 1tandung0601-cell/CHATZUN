package com.chatapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    // Spring tự động cung cấp interceptor để xác thực JWT.
    public WebSocketConfig(
            WebSocketAuthInterceptor webSocketAuthInterceptor
    ) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    // Đăng ký địa chỉ để client bắt đầu kết nối WebSocket.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    // Cấu hình cách Spring định tuyến tin nhắn.
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Tin nhắn gửi đến /app được chuyển tới controller.
        registry.setApplicationDestinationPrefixes("/app");

        // Tin nhắn gửi đến /topic được phát cho các client đăng ký nhận.
        registry.enableSimpleBroker("/topic", "/queue");
    }

    // Đăng ký interceptor để kiểm tra JWT trước khi xử lý STOMP.
    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}