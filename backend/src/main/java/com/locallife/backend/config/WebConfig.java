package com.locallife.backend.config;

import com.locallife.backend.admin.api.AdminActionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Intercepteurs MVC : journal des actions d'administration ({@link AdminActionInterceptor}). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminActionInterceptor adminActionInterceptor;

    public WebConfig(AdminActionInterceptor adminActionInterceptor) {
        this.adminActionInterceptor = adminActionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminActionInterceptor).addPathPatterns("/api/v1/admin/**", "/api/v1/sources/**");
    }
}
