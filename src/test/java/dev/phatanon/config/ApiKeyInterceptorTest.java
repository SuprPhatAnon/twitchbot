package dev.phatanon.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyInterceptorTest {

    private ApiKeyInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private String apiKey = "test-key";

    @BeforeEach
    void setUp() {
        interceptor = new ApiKeyInterceptor();
        ReflectionTestUtils.setField(interceptor, "apiKey", apiKey);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    void shouldAllowGetRequest() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result);
    }

    @Test
    void shouldAllowPostRequestWithValidKey() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-API-Key")).thenReturn(apiKey);
        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result);
    }

    @Test
    void shouldDenyPostRequestWithInvalidKey() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-API-Key")).thenReturn("wrong-key");
        
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("Unauthorized: Invalid API Key", stringWriter.toString());
    }

    @Test
    void shouldDenyPostRequestWithMissingKey() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-API-Key")).thenReturn(null);
        
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
