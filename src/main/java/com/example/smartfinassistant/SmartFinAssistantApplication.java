package com.example.smartfinassistant;

import com.example.smartfinassistant.rag.RagProperties;
import com.example.smartfinassistant.transaction.sql.SqlProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RagProperties.class, SqlProperties.class})
public class SmartFinAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartFinAssistantApplication.class, args);
    }

}
