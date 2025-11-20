package com.evidence.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final DataSource dataSource;

    public SecurityConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable();

        http.authorizeRequests()
                // 放行所有页面访问（开发环境友好）
                .antMatchers("/", "/login", "/upload", "/query", "/index").permitAll()
                // 放行API登录接口
                .antMatchers("/api/auth/login").permitAll()
                // 放行静态资源
                .antMatchers("/static/**", "/css/**", "/js/**", "/images/**", 
                           "/favicon.ico", "/templates/**").permitAll()
                // API接口需要认证（但通过JWT拦截器处理）
                .antMatchers("/api/**").permitAll()  // 暂时放行，由JWT拦截器处理认证
                // 其他请求允许访问
                .anyRequest().permitAll()
                .and()
                // 使用自定义登录页
                .formLogin()
                .loginPage("/login")           // 你的 PageController 映射的 login
                .defaultSuccessUrl("/index")   // 登录成功跳转
                .permitAll()
                .and()
                .logout().permitAll();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        // 从你建的 users 表里读用户名和密码（BCrypt）
        auth.jdbcAuthentication()
                .dataSource(dataSource)
                .usersByUsernameQuery(
                        "SELECT username, password, status = 1 FROM users WHERE username = ?")
                .authoritiesByUsernameQuery(
                        "SELECT username, 'ROLE_USER' FROM users WHERE username = ?")
                .passwordEncoder(new BCryptPasswordEncoder());
    }
}
