package com.retap.fcmmock;

import com.retap.fcmmock.config.FcmMockProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FcmMockProperties.class)
public class FcmMockServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FcmMockServerApplication.class, args);
    }
}
