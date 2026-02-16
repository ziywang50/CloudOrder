package com.highvia.common;

import com.highvia.common.enums.UserRole;
import com.highvia.common.security.JwtUtils;
import com.highvia.common.entity.UserInfo;
import com.highvia.common.utils.RsaUtils;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilsTest {
    @Test
    void testGenerateAndValidateToken() throws Exception {
        // 1. Load RSA keys
        String publicKeyPath = "./config/rsa.pub";
        String privateKeyPath = "./config/rsa.pri";

        PrivateKey privateKey = RsaUtils.getPrivateKey(privateKeyPath);
        PublicKey publicKey = RsaUtils.getPublicKey(publicKeyPath);

        System.out.println("Keys loaded successfully!");

        // 2. Create UserInfo
        UserInfo userInfo = new UserInfo(123L, "test@example.com", "TestUser", UserRole.USER);

        // 3. Generate token
        String token = JwtUtils.generateToken(userInfo, privateKey, 1440);
        assertNotNull(token);
        System.out.println("Generated Token: " + token);

        // 4. Extract and validate
        UserInfo extracted = JwtUtils.getInfoFromToken(token, publicKey);

        // 5. Verify extracted data
        assertEquals(123L, extracted.id());
        assertEquals("test@example.com", extracted.email());
        assertEquals("TestUser", extracted.username());

        System.out.println("Extracted ID: " + extracted.id());
        System.out.println("Extracted Email: " + extracted.email());
        System.out.println("Extracted Username: " + extracted.username());

        System.out.println("\nAll tests passed!");
    }

    @Test
    void testInvalidToken() throws Exception {
        String publicKeyPath = "./config/rsa.pub";
        PublicKey publicKey = RsaUtils.getPublicKey(publicKeyPath);

        String invalidToken = "invalid.token.here";

        // Should throw exception
        assertThrows(Exception.class, () -> {
            JwtUtils.getInfoFromToken(invalidToken, publicKey);
        });

        System.out.println("Invalid token correctly rejected!");
    }

    @Test
    void testByteArrayVersion() throws Exception {
        // Test the byte[] overload methods
        String publicKeyPath = "./config/rsa.pub";
        String privateKeyPath = "./config/rsa.pri";

        byte[] privateKeyBytes = java.nio.file.Files.readAllBytes(
                new java.io.File(privateKeyPath).toPath());
        byte[] publicKeyBytes = java.nio.file.Files.readAllBytes(
                new java.io.File(publicKeyPath).toPath());

        UserInfo userInfo = new UserInfo(456L, "user@example.com", "ByteUser", UserRole.USER);

        // Generate with byte[]
        String token = JwtUtils.generateToken(userInfo, privateKeyBytes, 1440);
        assertNotNull(token);

        // Validate with byte[]
        UserInfo extracted = JwtUtils.getInfoFromToken(token, publicKeyBytes);
        assertEquals(456L, extracted.id());

        System.out.println("Byte array version works!");
    }
}
