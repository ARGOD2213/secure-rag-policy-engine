package io.github.argod2213.policyrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PolicyRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyRagApplication.class, args);
    }
}
