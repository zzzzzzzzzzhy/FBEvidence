package com.evidence.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.config.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("/", "/login", "/api/auth/login",
                        "/static/**", "/css/**", "/js/**", "/images/**", "/favicon.ico")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源配置
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
        
        // 模板资源配置
        registry.addResourceHandler("/templates/**")
                .addResourceLocations("classpath:/templates/");
        
        // 上传文件访问配置（已废弃，文件现在存储在MinIO中）
        // registry.addResourceHandler("/uploads/**")
        //         .addResourceLocations("file:uploads/");
        
        // CSS、JS、图片等资源的直接访问
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")  // 只拦截API路径
                .excludePathPatterns("/api/auth/login");  // 排除登录接口
        
        // 注意：页面路径（/, /login, /upload, /query）不添加拦截器，允许直接访问
    }


    /**
     * 配置HTTP消息转换器的字符编码
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setWriteAcceptCharset(false);
        converters.add(stringConverter);
    }

    /**
     * 配置内容协商
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(org.springframework.http.MediaType.APPLICATION_JSON)  // 修改默认为JSON，适配REST API
                .mediaType("html", org.springframework.http.MediaType.TEXT_HTML)
                .mediaType("json", org.springframework.http.MediaType.APPLICATION_JSON)
                .favorParameter(false)  // 不使用参数协商
                .ignoreAcceptHeader(false);  // 不忽略Accept头
    }
}