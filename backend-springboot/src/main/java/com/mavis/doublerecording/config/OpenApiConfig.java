package com.mavis.doublerecording.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 (Swagger) 配置
 *
 * UI 访问: http://localhost:8080/swagger-ui.html
 * JSON:    http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("线上线下双录融合系统 API")
                .description("""
                    AIRecord - 开箱即用的双录解决方案
                    
                    ## 核心模块
                    - **录制工作台**: WebRTC 开画录制 + 时间轴
                    - **AI 风控**: 15 种风险类型 + 4 级风险等级
                    - **区块链存证**: 全链路证据上链
                    - **录制合规**: 8 项合规检查
                    - **Saga 分布式事务**: 注解驱动 + AOP 切面
                    - **集成能力**: SM4/WebRTC/AI 质检/灰度/智能风评
                    
                    ## 技术栈
                    - Spring Boot 3.2.5 + JDK 17
                    - H2 内存数据库
                    - 雪花算法 ID
                    - 区块链 (SHA-256 + PoW + Merkle)
                    """)
                .version("1.5.0")
                .contact(new Contact()
                    .name("AIRecord Team")
                    .email("ai@double-recording.com")
                    .url("https://github.com/liugl951127/AIRecord"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("本地开发")
            ));
    }
}
