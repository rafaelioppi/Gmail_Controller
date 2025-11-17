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
            // Em produção, avalie habilitar CSRF para formulários
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Recursos estáticos e página inicial liberados
                .requestMatchers("/", "/index.html", "/app.js", "/css/**", "/images/**").permitAll()
                // Fluxo OAuth2 liberado
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                // Endpoints da API Gmail exigem login
                .requestMatchers("/gmail/**").authenticated()
                // Qualquer outra rota exige autenticação
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                // Página de login padrão redireciona para Google
                .loginPage("/oauth2/authorization/google")
                // Após sucesso, força sempre voltar para index.html
                .defaultSuccessUrl("/index.html", true)
                // Se quiser tratar falhas de login
                .failureUrl("/index.html?error=true")
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/index.html")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }
}
