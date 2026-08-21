package com.attt.incident.config;

import com.attt.incident.security.JwtService;
import com.attt.incident.security.AppUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Cho phép frontend kết nối qua endpoint /ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // Cung cấp fallback nếu trình duyệt không hỗ trợ WebSocket thuần
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Topic chung (vd: /topic/incidents để dashboard lắng nghe)
        // Hàng đợi riêng tư (vd: /user/queue/notifications)
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        String username = jwtService.extractUsername(token);
                        
                        if (username != null) {
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            if (jwtService.isTokenValid(token, userDetails)) {
                                UsernamePasswordAuthenticationToken auth = 
                                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                                SecurityContextHolder.getContext().setAuthentication(auth);
                                accessor.setUser(auth);
                            }
                        }
                    }

                    if (accessor.getUser() == null) {
                        throw new AccessDeniedException("WebSocket yêu cầu JWT hợp lệ");
                    }
                }

                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && "/topic/incidents".equals(accessor.getDestination())) {
                    if (!(accessor.getUser() instanceof Authentication authentication)
                            || authentication.getAuthorities().stream().noneMatch(authority ->
                            authority.getAuthority().equals("ROLE_ADMIN")
                                    || authority.getAuthority().equals("ROLE_MANAGER")
                                    || authority.getAuthority().equals("ROLE_HELPDESK"))) {
                        throw new AccessDeniedException("Bạn không có quyền theo dõi luồng sự cố toàn hệ thống");
                    }
                }
                return message;
            }
        });
    }
}
