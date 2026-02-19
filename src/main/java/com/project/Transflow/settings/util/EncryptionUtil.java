package com.project.Transflow.settings.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    @Value("${encryption.secret.key}")
    private String encryptionSecretKey;

    /**
     * 문자열을 AES로 암호화
     */
    public String encrypt(String plainText) throws Exception {
        SecretKey secretKey = generateKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * AES로 암호화된 문자열을 복호화
     */
    public String decrypt(String encryptedText) throws Exception {
        SecretKey secretKey = generateKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * SecretKey 생성 (encryptionSecretKey를 32바이트로 맞춤)
     */
    private SecretKey generateKey() {
        byte[] keyBytes = encryptionSecretKey.getBytes(StandardCharsets.UTF_8);
        byte[] fixedKeyBytes = new byte[32]; // AES-256은 32바이트 키 필요

        // 키가 32바이트보다 짧으면 0으로 패딩, 길면 자름
        System.arraycopy(keyBytes, 0, fixedKeyBytes, 0, Math.min(keyBytes.length, 32));

        return new SecretKeySpec(fixedKeyBytes, ALGORITHM);
    }
}

