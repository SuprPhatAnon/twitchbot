package dev.phatanon.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that validates the "X-API-Key" header in incoming requests.
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    @Value("${twitch.api-key}")
    private String apiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestApiKey = request.getHeader("X-API-Key");

        if (apiKey.equals(requestApiKey)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Unauthorized: Invalid API Key");
        return false;
    }
}
