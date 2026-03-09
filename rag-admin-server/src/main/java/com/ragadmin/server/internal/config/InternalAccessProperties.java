package com.ragadmin.server.internal.config;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Accessors(chain = true)
@ConfigurationProperties(prefix = "rag.internal")
public class InternalAccessProperties {

    private String callbackToken;
}
