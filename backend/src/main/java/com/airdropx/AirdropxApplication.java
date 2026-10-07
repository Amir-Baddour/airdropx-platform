package com.airdropx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Note: the worker (see com.airdropx.worker.AirdropWorker) runs on a plain daemon Thread started from an
// ApplicationRunner, not @Async/@Scheduled — so those annotations are deliberately not enabled here. Add
// @EnableScheduling if a future task (e.g. sweeping expired idempotency keys) needs @Scheduled.
@SpringBootApplication
public class AirdropxApplication {

    public static void main(String[] args) {
        SpringApplication.run(AirdropxApplication.class, args);
    }
}
