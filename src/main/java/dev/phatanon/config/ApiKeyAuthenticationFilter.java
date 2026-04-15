package dev.phatanon.config;

import dev.phatanon.entity.User;
import dev.phatanon.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Filter for authenticating requests using an API key in the X-API-KEY header.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final UserService userService;

    public ApiKeyAuthenticationFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-KEY");

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            Optional<User> userOptional = userService.findByApiKey(apiKey);

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                        user.getUsername(),
                        user.getPassword(),
                        user.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority(role.name()))
                                .collect(Collectors.toList())
                );

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
