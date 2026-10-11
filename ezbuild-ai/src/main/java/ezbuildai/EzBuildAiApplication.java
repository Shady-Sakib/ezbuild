package ezbuildai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** AI microservice: the store calls it over HTTP when ADVISOR_MODE=remote. */
@SpringBootApplication
public class EzBuildAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EzBuildAiApplication.class, args);
    }
}
