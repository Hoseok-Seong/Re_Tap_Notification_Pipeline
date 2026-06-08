package com.retap.notificationproducer;

import com.retap.notificationproducer.config.NotificationProducerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(NotificationProducerProperties.class)
public class NotificationProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationProducerApplication.class, args);
    }
}
