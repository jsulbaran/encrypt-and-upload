package com.jsulbaran.file.encryptor;

import com.jsulbaran.file.encryptor.model.ExtensionFileVisitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@SpringBootApplication
@Configuration
public class Application implements InitializingBean {

    @Value("${input.path}")
    private String inputPath;
    @Autowired
    private ExtensionFileVisitor extensionFileVisitor;


    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        Files.walkFileTree(Path.of(inputPath), extensionFileVisitor);
    }
}
