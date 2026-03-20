package com.localcloud.photos.config;

import com.localcloud.photos.security.DeviceAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private DeviceAuthInterceptor deviceAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Legacy DeviceAuthInterceptor removed. 
        // Device identity is now managed via JWT subject in JwtAuthFilter.
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*") // In a real production environment running on local network, this is usually
                                     // sufficient for testing
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("X-Device-Id", "Content-Type", "Authorization")
                .maxAge(3600);
    }
}
