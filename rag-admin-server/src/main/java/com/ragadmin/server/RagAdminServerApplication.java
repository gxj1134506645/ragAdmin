package com.ragadmin.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.ragadmin.server")
public class RagAdminServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAdminServerApplication.class, args);
    }
}
