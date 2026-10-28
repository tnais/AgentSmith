package org.gianoziaorientale.rag;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties({RagProperties.class, RagSecurityProperties.class})
public class RagSecurityConfiguration {

    @Bean
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http, RagSecurityProperties securityProperties)
            throws Exception {
        securityProperties.validate();

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    if (securityProperties.isEnabled()) {
                        authorize.requestMatchers("/api/rag/config", "/api/rag/config/**")
                                .hasRole("RAG_OPERATOR");
                        authorize.requestMatchers("/actuator/metrics", "/actuator/metrics/**")
                                .hasRole("RAG_OPERATOR");
                    } else {
                        authorize.requestMatchers("/api/rag/config", "/api/rag/config/**").denyAll();
                        authorize.requestMatchers("/actuator/metrics", "/actuator/metrics/**").denyAll();
                    }
                    authorize.anyRequest().permitAll();
                })
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    UserDetailsService ragUserDetailsService(RagSecurityProperties properties) {
        properties.validate();
        if (!properties.isEnabled()) {
            return username -> {
                throw new UsernameNotFoundException("RAG API credentials are not configured.");
            };
        }
        return new InMemoryUserDetailsManager(User.withUsername(properties.getOperatorUsername())
                .password(properties.getOperatorPasswordHash())
                .roles("RAG_OPERATOR")
                .build());
    }
}
