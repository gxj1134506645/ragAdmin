package com.ragadmin.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.ragadmin.server")
@EnableScheduling
public class RagAdminServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAdminServerApplication.class, args);
    }
}
