package com.cakequake.cakequakeback.payment.sercurity;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class JasyptTestRunner implements CommandLineRunner {
    private final EncryptionService encryptionService;

    @Override
    public void run(String... args) {
        String original = "test-key";
        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);

        log.debug("원본:     {}", original);
        log.debug("암호화된: {}", encrypted);
        log.debug("복호화된: {}", decrypted);
    }
}
