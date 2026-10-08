package com.example.kufim.config;

import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.admin.repository.AdminBoothRepository;
import com.example.kufim.waiting.repository.WaitingRepository;
import com.example.kufim.waiting.service.ClientToken;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WaitingWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WaitingRepository waitings;
    private final AdminAccountRepository accounts;
    private final AdminBoothRepository assignments;
    @Value("${app.cors.allowed-origins}") private String origins;

    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toArray(String[]::new));
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var stomp = StompHeaderAccessor.wrap(message);
                if (stomp.getCommand() == StompCommand.CONNECT) {
                    String raw = stomp.getFirstNativeHeader("X-Client-Token");
                    if (raw != null && stomp.getSessionAttributes() != null)
                        stomp.getSessionAttributes().put("clientToken", ClientToken.require(raw));
                }
                if (stomp.getCommand() != StompCommand.SUBSCRIBE) return message;
                String destination = stomp.getDestination();
                if (destination == null) throw new AccessDeniedException("구독 경로가 필요합니다.");
                if (destination.equals("/topic/notice") || destination.matches("/topic/booth/[0-9]+/waiting")) return message;
                if (destination.matches("/topic/waiting/[0-9a-f-]{36}")) {
                    String id = destination.substring("/topic/waiting/".length());
                    var attrs = stomp.getSessionAttributes();
                    String token = attrs == null ? null : (String) attrs.get("clientToken");
                    if (token == null || waitings.findByPublicId(id).filter(w -> w.getClientToken().equals(token)).isEmpty())
                        throw new AccessDeniedException("대기표 구독 권한이 없습니다.");
                    return message;
                }
                if (destination.matches("/topic/admin/booth/[0-9]+/waiting")) {
                    var principal = stomp.getUser();
                    if (principal == null) throw new AccessDeniedException("관리자 세션이 필요합니다.");
                    var actor = accounts.findByLoginId(principal.getName()).filter(a -> a.isActive())
                            .orElseThrow(() -> new AccessDeniedException("관리자 세션이 필요합니다."));
                    var attributes = stomp.getSessionAttributes();
                    if (attributes == null || !Integer.valueOf(actor.getSessionVersion()).equals(attributes.get("adminSessionVersion")))
                        throw new AccessDeniedException("관리자 세션이 만료되었습니다.");
                    long boothId = Long.parseLong(destination.split("/")[4]);
                    if (actor.getRole() != AdminRole.SUPER_ADMIN &&
                            !assignments.existsByAdminAccountIdAndBoothId(actor.getId(), boothId))
                        throw new AccessDeniedException("담당 부스 권한이 없습니다.");
                    return message;
                }
                throw new AccessDeniedException("허용되지 않은 구독 경로입니다.");
            }
        });
    }
}
