package com.evidence.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CharacterEncodingFilter;

/**
 * 字符编码配置
 * 解决中文乱码问题
 */
@Configuration
public class EncodingConfig {

    /**
     * 字符编码过滤器
     * 优先级设置为最高，确保所有请求都经过UTF-8编码处理
     */
    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> encodingFilter() {
        FilterRegistrationBean<CharacterEncodingFilter> registration = new FilterRegistrationBean<>();
        
        CharacterEncodingFilter encodingFilter = new CharacterEncodingFilter();
        encodingFilter.setEncoding("UTF-8");
        encodingFilter.setForceEncoding(true);
        encodingFilter.setForceRequestEncoding(true);
        encodingFilter.setForceResponseEncoding(true);
        
        registration.setFilter(encodingFilter);
        registration.addUrlPatterns("/*");
        registration.setName("encodingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        
        return registration;
    }
}