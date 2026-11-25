package com.seatsense.live;

import com.seatsense.auth.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.web.csrf.CsrfChannelInterceptor;

/**
 * Who may subscribe to what (docs/api.md, "Who can subscribe"). Seat and availability topics carry no names,
 * so anyone may listen. The gate topic carries student names, so only GATE may. The STOMP CONNECT frame must carry
 * the X-XSRF-TOKEN header, like every HTTP POST, so another site cannot open a socket with the user's cookie.
 */
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
        MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages
            .simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.DISCONNECT, SimpMessageType.HEARTBEAT,
                SimpMessageType.UNSUBSCRIBE).permitAll()
            .simpSubscribeDestMatchers("/topic/gate/**").hasRole(Role.GATE.name())
            .simpSubscribeDestMatchers("/topic/seats/*", "/topic/availability").permitAll()
            .simpSubscribeDestMatchers("/user/queue/**").authenticated()
            .anyMessage().denyAll()
            .build();
    }

    // The frontend sends the raw XSRF-TOKEN cookie value (as for HTTP), not the masked form Spring expects by default.
    @Bean
    ChannelInterceptor csrfChannelInterceptor() {
        return new CsrfChannelInterceptor();
    }
}
