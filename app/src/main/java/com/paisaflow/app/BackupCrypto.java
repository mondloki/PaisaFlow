package com.paisaflow.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class BackupCrypto {
    private static final byte[] MAGIC = {'P', 'F', 'B', 'K'};
    private static final int VERSION = 1;
    private static final int KDF_SHA256 = 1;
    private static final int KDF_SHA1 = 2;
    private static final int ITERATIONS = 300_000;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int KEY_BITS = 256;
    private static final int MAX_PLAINTEXT_BYTES = 50_000_000;
    private static final int HEADER_BYTES = 4 + 1 + 1 + 4 + SALT_BYTES + IV_BYTES;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BackupCrypto() {}

    static byte[] encrypt(String json, char[] password) throws Exception {
        requirePassword(password);
        byte[] plaintext = json.getBytes(StandardCharsets.UTF_8);
        if (plaintext.length > MAX_PLAINTEXT_BYTES) throw new IllegalArgumentException("Backup is larger than 50 MB");
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(iv);

        int kdf = supportsSha256() ? KDF_SHA256 : KDF_SHA1;
        byte[] header = header(kdf, ITERATIONS, salt, iv);
        SecretKeySpec key = derive(password, salt, ITERATIONS, kdf);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(header);
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] result = Arrays.copyOf(header, header.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, result, header.length, ciphertext.length);
            return result;
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            Arrays.fill(key.getEncoded(), (byte) 0);
        }
    }

    static String decrypt(byte[] encrypted, char[] password) throws Exception {
        requirePassword(password);
        if (encrypted == null || encrypted.length <= HEADER_BYTES + 16
                || encrypted.length > MAX_PLAINTEXT_BYTES + HEADER_BYTES + 16) {
            throw new IllegalArgumentException("Backup is empty or too large");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encrypted))) {
            byte[] magic = new byte[MAGIC.length];
            input.readFully(magic);
            if (!Arrays.equals(magic, MAGIC) || input.readUnsignedByte() != VERSION) {
                throw new IllegalArgumentException("This is not a supported PaisaFlow backup");
            }
            int kdf = input.readUnsignedByte();
            int iterations = input.readInt();
            if ((kdf != KDF_SHA256 && kdf != KDF_SHA1)
                    || iterations < 100_000 || iterations > 2_000_000) {
                throw new IllegalArgumentException("This is not a supported PaisaFlow backup");
            }
            byte[] salt = new byte[SALT_BYTES];
            byte[] iv = new byte[IV_BYTES];
            input.readFully(salt);
            input.readFully(iv);
            byte[] ciphertext = new byte[encrypted.length - HEADER_BYTES];
            input.readFully(ciphertext);

            SecretKeySpec key = derive(password, salt, iterations, kdf);
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
                cipher.updateAAD(Arrays.copyOf(encrypted, HEADER_BYTES));
                byte[] plaintext = cipher.doFinal(ciphertext);
                try {
                    return new String(plaintext, StandardCharsets.UTF_8);
                } finally {
                    Arrays.fill(plaintext, (byte) 0);
                }
            } finally {
                Arrays.fill(key.getEncoded(), (byte) 0);
            }
        } catch (GeneralSecurityException error) {
            throw new IllegalArgumentException("Password is incorrect or the backup is damaged", error);
        }
    }

    static boolean isEncrypted(byte[] value) {
        if (value == null || value.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) if (value[i] != MAGIC[i]) return false;
        return true;
    }

    private static byte[] header(int kdf, int iterations, byte[] salt, byte[] iv) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(MAGIC);
            output.writeByte(VERSION);
            output.writeByte(kdf);
            output.writeInt(iterations);
            output.write(salt);
            output.write(iv);
        }
        return bytes.toByteArray();
    }

    private static SecretKeySpec derive(char[] password, byte[] salt, int iterations, int kdf) throws Exception {
        String algorithm = kdf == KDF_SHA256 ? "PBKDF2WithHmacSHA256" : "PBKDF2WithHmacSHA1";
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            byte[] encoded = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(encoded, "AES");
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean supportsSha256() {
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return true;
        } catch (GeneralSecurityException ignored) {
            return false;
        }
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Use a password with at least 8 characters");
        }
    }
}
