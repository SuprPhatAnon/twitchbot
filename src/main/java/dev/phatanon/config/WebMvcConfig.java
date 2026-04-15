package dev.phatanon.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the application.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${twitch.song-upload-path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String path = uploadPath;
        if (!path.endsWith("/")) {
            path += "/";
        }
        if (!path.startsWith("file:")) {
            path = "file:" + path;
        }

        registry.addResourceHandler("/*.mp3", "/playlist.m3u")
                .addResourceLocations(path);
    }
}
