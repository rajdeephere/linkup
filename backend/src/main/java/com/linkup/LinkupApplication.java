package com.linkup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * LinkUp backend entry point.
 *
 * @ConfigurationPropertiesScan lets us bind typed @ConfigurationProperties
 * classes (e.g. JwtProperties) without listing each on @EnableConfigurationProperties.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LinkupApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkupApplication.class, args);
    }
}
