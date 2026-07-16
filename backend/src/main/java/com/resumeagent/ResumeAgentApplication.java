package com.resumeagent;

import com.resumeagent.config.ResumeAgentProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = ResumeAgentProperties.class)
public class ResumeAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeAgentApplication.class, args);
    }
}
