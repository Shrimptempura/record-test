package com.mytest.oplib;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class OplibApplication {

	public static void main(String[] args) {
		SpringApplication.run(OplibApplication.class, args);
	}

}
