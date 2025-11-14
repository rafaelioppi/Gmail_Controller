package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // desabilita CSRF para chamadas REST
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/hello", "/gmail/inbox", "/gmail/send").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(); // habilita login via Google OAuth2

        return http.build();
    }
}
