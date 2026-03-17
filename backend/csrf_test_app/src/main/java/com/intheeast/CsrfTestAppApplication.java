package com.intheeast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CsrfTestAppApplication {

    // Double Submit Cookie
    // 프론트엔드에서 Http request Header에
    // Header cookie : XSRF-TOKEN
    // Header X-XSRF-TOKEN : XSRF-TOKEN
    public static void main(String[] args) {
        SpringApplication.run(CsrfTestAppApplication.class, args);
    }

}
