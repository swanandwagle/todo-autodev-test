package com.example.todo.config;

import com.example.todo.error.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterOrderConfig {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(RequestIdFilter filter) {
        FilterRegistrationBean<RequestIdFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(1);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilterRegistration(
            RequestSizeLimitFilter filter) {
        FilterRegistrationBean<RequestSizeLimitFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(2);
        return bean;
    }
}
