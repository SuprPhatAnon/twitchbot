package dev.phatanon.config;

import dev.phatanon.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration class for Spring Security.
 * Defines the security filter chain, RBAC rules, and password encoding.
 */
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    private final UserService userService;

    public SecurityConfig(@Lazy UserService userService) {
        this.userService = userService;
    }

    /**
     * Defines the security filter chain for the application.
     * @param http The HttpSecurity object to configure.
     * @return The configured SecurityFilterChain.
     * @throws Exception If an error occurs during configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .addFilterBefore(new ApiKeyAuthenticationFilter(userService), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/player.html", "/statistics.html", "/css/**", "/js/**", "/ws/**").permitAll()
                .requestMatchers("/login.html", "/api/login").permitAll()
                .requestMatchers("/api/songs/upload/**", "/upload.html").hasAnyRole("UPLOAD", "STREAMER", "ADMIN")
                .requestMatchers("/streamer.html", "/api/songs/play/**", "/api/songs/queue/**").hasAnyRole("STREAMER", "ADMIN")
                .requestMatchers("/api/users/me/**", "/account.html").authenticated()
                .requestMatchers("/admin.html", "/api/users/**", "/api/twitch-config/**", "/api/redeems/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/api/login")
                .defaultSuccessUrl("/admin.html", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/api/logout")
                .logoutSuccessUrl("/login.html")
                .permitAll()
            );

        return http.build();
    }

    /**
     * Defines the password encoder to be used for user passwords.
     * @return A BCryptPasswordEncoder instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Defines the authentication manager for user authentication.
     * @param passwordEncoder The password encoder to use.
     * @return The configured AuthenticationManager.
     */
    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authProvider);
    }
}
