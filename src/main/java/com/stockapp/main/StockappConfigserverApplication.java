package com.stockapp.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class StockappConfigserverApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockappConfigserverApplication.class, args);
	}

	
}
