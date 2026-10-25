package com.hazard.music.api;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** NetEase weapi / eapi request encryption. */
public final class Crypto {

    private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String IV = "0102030405060708";
    private static final String EAPI_KEY = "e82ckenh8dichen8";
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static PublicKey publicKey;

    private Crypto() {
    }

    public static Map<String, String> weapi(String text) throws Exception {
        String secretKey = randomKey();
        String first = aesCbcBase64(text, PRESET_KEY);
        String params = aesCbcBase64(first, secretKey);

        String reversed = new StringBuilder(secretKey).reverse().toString();
        byte[] reversedBytes = reversed.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[128];
        System.arraycopy(reversedBytes, 0, input, input.length - reversedBytes.length, reversedBytes.length);

        Cipher rsa = Cipher.getInstance("RSA/ECB/NoPadding");
        rsa.init(Cipher.ENCRYPT_MODE, getPublicKey());

        Map<String, String> form = new LinkedHashMap<>();
        form.put("params", params);
        form.put("encSecKey", hex(rsa.doFinal(input)));
        return form;
    }

    public static String eapi(String uri, String text) throws Exception {
        String message = "nobody" + uri + "use" + text + "md5forencrypt";
        String digest = hex(MessageDigest.getInstance("MD5")
                .digest(message.getBytes(StandardCharsets.UTF_8)));
        String data = uri + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest;
        return hex(aesEcb(data.getBytes(StandardCharsets.UTF_8), EAPI_KEY));
    }

    public static String md5Hex(String text) throws Exception {
        return hex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String randomKey() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    private static String aesCbcBase64(String text, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
        return Base64.encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    private static byte[] aesEcb(byte[] data, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"));
        return cipher.doFinal(data);
    }

    private static synchronized PublicKey getPublicKey() throws Exception {
        if (publicKey == null) {
            byte[] decoded = Base64.decode(PUBLIC_KEY, Base64.DEFAULT);
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        }
        return publicKey;
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        final char[] digits = "0123456789ABCDEF".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = digits[v >>> 4];
            out[i * 2 + 1] = digits[v & 0x0F];
        }
        return new String(out);
    }
}
