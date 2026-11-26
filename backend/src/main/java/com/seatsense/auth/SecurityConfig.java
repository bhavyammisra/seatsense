package com.seatsense.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsense.common.ApiError;
import com.seatsense.common.SpaRoutes;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Who may call what (docs/api.md). Login is a session cookie. CSRF protection is on: the frontend reads the
 * XSRF-TOKEN cookie and sends it back in the X-XSRF-TOKEN header on every POST, PUT and DELETE.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // /ws: the socket handshake; who may subscribe to what is checked per message (WebSocketSecurityConfig)
                .requestMatchers("/api/public/**", "/api/auth/login", "/actuator/health", "/error", "/ws").permitAll()
                // The built React app on the hosted demo (SpaRoutes). Each page asks for a login itself where needed.
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/*.svg", "/*.jpg", "/*.png").permitAll()
                .requestMatchers(HttpMethod.GET, SpaRoutes.PAGES).permitAll()
                .requestMatchers("/api/gate/**", "/api/sessions/**").hasRole(Role.GATE.name())
                .requestMatchers("/api/admin/**", "/api/sim/**").hasRole(Role.LIBRARIAN.name())
                .requestMatchers("/api/waitlist/**", "/api/seats/*/report").hasRole(Role.STUDENT.name())
                .anyRequest().authenticated())
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            // Tokens are lazy; reading it on every request makes Spring write the XSRF-TOKEN cookie the SPA needs.
            .addFilterAfter((req, res, chain) -> {
                CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
                if (token != null) {
                    token.getToken();
                }
                chain.doFilter(req, res);
            }, CsrfFilter.class)
            .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository()))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    write(res, json, HttpStatus.UNAUTHORIZED, ApiError.of("NOT_LOGGED_IN", "Please log in.")))
                .accessDeniedHandler((req, res, e) ->
                    write(res, json, HttpStatus.FORBIDDEN, ApiError.of("FORBIDDEN", "You are not allowed to do this."))))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_LIBRARIAN");
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> users.findByUsername(username)
            .map(u -> User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .roles(u.getRole().name())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    private static void write(HttpServletResponse res, ObjectMapper json, HttpStatus status, ApiError body)
        throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getOutputStream(), body);
    }
}
