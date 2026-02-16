package com.highvia.common;

import com.highvia.common.utils.RsaUtils;
import org.junit.jupiter.api.Test;

import java.io.File;

import java.io.File;

public class GenerateRsaKeysTest {
    @Test
    void generateKeys() throws Exception {
        String projectRoot = System.getProperty("user.dir");
        String configPath = projectRoot + "/config";

        new File(configPath).mkdirs();

        String publicKeyPath = configPath + "/rsa.pub";
        String privateKeyPath = configPath + "/rsa.pri";
        String secret = "CloudShoppingRsaSecret";

        RsaUtils.generateKey(publicKeyPath, privateKeyPath, secret);

        System.out.println("RSA keys generated!");
        System.out.println("Config folder: " + configPath);
        System.out.println("Public key: " + publicKeyPath);
        System.out.println("Private key: " + privateKeyPath);
    }
}
