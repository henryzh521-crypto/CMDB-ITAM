package com.dcim.security;

import com.dcim.collector.AgentKeyFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizedUrl;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${dcim.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();

        for (String origin : allowedOrigins.split(",")) {
            if (!origin.isBlank()) {
                config.addAllowedOrigin(origin.trim());
            }
        }

        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http, JwtAuthFilter jwtAuthFilter, AgentKeyFilter agentKeyFilter, CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(
                auth -> ((AuthorizedUrl)((AuthorizedUrl)((AuthorizedUrl)((AuthorizedUrl)((AuthorizedUrl)((AuthorizedUrl)((AuthorizedUrl)((AuthorizedUrl)((AuthorizedUrl)((AuthorizedUrl)auth.requestMatchers(
                                                                HttpMethod.POST,
                                                                new String[]{
                                                                    "/api/auth/login", "/api/auth/refresh", "/api/v1/auth/login", "/api/v1/auth/refresh"
                                                                }
                                                            ))
                                                            .permitAll()
                                                            .requestMatchers(
                                                                HttpMethod.GET, new String[]{"/api/auth/wecom/config", "/api/v1/auth/wecom/config"}
                                                            ))
                                                        .permitAll()
                                                        .requestMatchers(
                                                            HttpMethod.POST,
                                                            new String[]{
                                                                "/api/auth/wecom/login",
                                                                "/api/auth/wecom/dev-login",
                                                                "/api/v1/auth/wecom/login",
                                                                "/api/v1/auth/wecom/dev-login"
                                                            }
                                                        ))
                                                    .permitAll()
                                                    .requestMatchers(new String[]{"/api/collector/**"}))
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, new String[]{"/api/healthz", "/api/v1/healthz"}))
                                            .permitAll()
                                            .requestMatchers(new String[]{"/api/monitoring/sd", "/api/monitoring/sd/**", "/api/monitoring/ingest/**"}))
                                        .permitAll()
                                        .requestMatchers(new String[]{"/api-docs/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"}))
                                    .authenticated()
                                    .requestMatchers(new String[]{"/api/**", "/api/v1/**"}))
                                .authenticated()
                                .requestMatchers(new String[]{"/error"}))
                            .permitAll()
                            .anyRequest())
                        .denyAll()
            )
            .exceptionHandling(
                eh -> eh.authenticationEntryPoint((req, res, ex) -> this.writeJson(res, 401, "未登录或会话已过期"))
                        .accessDeniedHandler((req, res, ex) -> this.writeJson(res, 403, "没有执行该操作的权限"))
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(agentKeyFilter, JwtAuthFilter.class);
        return (SecurityFilterChain)http.build();
    }

    private void writeJson(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg + "\"}");
    }
}
