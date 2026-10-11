package ezbuild;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EzBuildApplication {

    public static void main(String[] args) {
        DotEnv.load(); // load .env first so ${...} placeholders resolve
        SpringApplication.run(EzBuildApplication.class, args);
    }
}
