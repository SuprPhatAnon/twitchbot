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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
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
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; " +
                                     "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                     "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
                                     "font-src 'self' https://fonts.gstatic.com; " +
                                     "img-src 'self' data: https://static-cdn.jtvnw.net; " +
                                     "connect-src 'self' ws: wss: https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                     "form-action 'self' https://id.twitch.tv; " +
                                     "frame-ancestors 'self';")
                )
                .frameOptions(org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig::deny)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**") // Disable CSRF for API endpoints (using API Keys)
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .addFilterBefore(new ApiKeyAuthenticationFilter(userService), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/overlay.html", "/player.html", "/statistics.html", "/css/**", "/js/**", "/ws/**").permitAll()
                .requestMatchers("/login.html", "/api/login", "/api/twitch/callback").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/**").permitAll() // Permit all GET requests to API
                .requestMatchers("/*.mp3", "/playlist.m3u").permitAll() // Permit all access to songs and playlist
                .requestMatchers("/api/songs/upload/**", "/upload.html").hasAnyRole("UPLOAD", "STREAMER", "ADMIN")
                .requestMatchers("/streamer.html", "/api/songs/play/**", "/api/songs/queue/**").hasAnyRole("STREAMER", "ADMIN")
                .requestMatchers("/api/users/me/**", "/api/users/me/api-key", "/account.html").authenticated()
                .requestMatchers("/admin.html", "/status.html", "/song-management.html", "/api/users/**", "/api/twitch-config/**", "/api/redeems/**").hasRole("ADMIN")
                .requestMatchers("/api/songs/files/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/api/login")
                .successHandler(new CustomAuthenticationSuccessHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/api/logout"))
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
