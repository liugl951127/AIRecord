package com.mavis.doublerecording;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 双录系统启动类
 *
 * 一套话术 · 一套流程 · 一套质检 · 一份证据
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class DoubleRecordingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoubleRecordingApplication.class, args);
        System.out.println("\n" +
            "================================================================\n" +
            "  双录融合系统启动成功\n" +
            "  访问地址:\n" +
            "    - API:     http://localhost:8080/api\n" +
            "    - 演示页:  http://localhost:8080/index.html\n" +
            "    - H2控制台:http://localhost:8080/h2-console\n" +
            "  默认账号: 无需登录(演示模式)\n" +
            "================================================================\n");
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
