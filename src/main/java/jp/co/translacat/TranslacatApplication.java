package jp.co.translacat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class TranslacatApplication {

    public static void main(String[] args) {
        SpringApplication.run(TranslacatApplication.class, args);
    }

}
