package com.andrelair.globalcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Simulated legacy claims core — a SOAP facade over MySQL, wrapped by the ktayl-claims ACL. */
@SpringBootApplication
public class GlobalCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(GlobalCoreApplication.class, args);
    }
}
