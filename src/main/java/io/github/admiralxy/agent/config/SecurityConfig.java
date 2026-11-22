package io.github.admiralxy.agent.config;

import io.github.admiralxy.agent.config.properties.AdminProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private static final String ALL_PATHS = "/**";
    private static final String SETTINGS_PATHS = "/settings/models/**";
    private static final String PLAIN_PASSWORD = "{noop}";
    private static final String ADMIN_ROLE = "SETTINGS_ADMIN";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, ALL_PATHS).permitAll()
                        .requestMatchers(SETTINGS_PATHS).authenticated()
                        .anyRequest().permitAll()
                )
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(AdminProperties adminProperties) {
        UserDetails user = User.withUsername(AdminProperties.USERNAME)
                .password(PLAIN_PASSWORD + adminProperties.getPassword())
                .roles(ADMIN_ROLE)
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
