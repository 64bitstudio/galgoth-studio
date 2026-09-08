package com.galgothstudio.backend;

import org.springframework.boot.SpringApplication;

public class TestGalgothStudioApplication {

	public static void main(String[] args) {
		SpringApplication.from(GalgothStudioApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
