package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class TibyanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TibyanAutoConfiguration.class);

    @Bean
    public ToolCallbackProvider tibyanToolProvider(ApplicationContext ctx, ObjectMapper objectMapper) {
        Object productService = ctx.getBean("productService");
        log.info("Tibyaan: discovered ProductService bean, registering getProduct tool");
        ProductToolHandler handler = new ProductToolHandler(productService, objectMapper);
        return MethodToolCallbackProvider.builder()
                .toolObjects(handler)
                .build();
    }
}
