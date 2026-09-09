package com.omar.vendora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VendoraApplication {

    public static void main(String[] args) {
        SpringApplication.run(VendoraApplication.class, args);
    }

}
