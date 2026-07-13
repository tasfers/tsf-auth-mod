package com.tasfers.tsfauth.security;

public class StringDecrypter {
    public static String decrypt(byte[] data) {
        byte[] decrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            decrypted[i] = (byte) (data[i] ^ 0x42);
        }
        return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
    }
}
