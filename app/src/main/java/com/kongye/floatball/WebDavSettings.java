package com.kongye.floatball;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Connection details are device-local and excluded from portable backups. */
final class WebDavSettings {
    private static final String ALIAS = "KongyeFloat.WebDAV.v1";
    static final String FOLDER = "KongyeFloat";
    record Config(String server, String username, String password, String folder) {
        static Config empty() { return new Config("", "", "", FOLDER); }
    }

    static Config load(Context context) throws IOException {
        var preferences = context.getSharedPreferences("backup_webdav", Context.MODE_PRIVATE);
        String value = preferences.getString("encrypted", "");
        if (value.isEmpty()) return Config.empty();
        try {
            String[] parts = value.split(":", -1);
            if (parts.length != 2) throw new IOException("WebDAV 配置无法读取，请重新填写");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            JSONObject json = new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
            return new Config(json.getString("server"), json.getString("username"), json.getString("password"), FOLDER);
        } catch (Exception failure) { throw new IOException("WebDAV 配置无法读取，请重新填写", failure); }
    }

    static void save(Context context, Config config) throws IOException {
        try {
            JSONObject json = new JSONObject().put("server", config.server()).put("username", config.username())
                    .put("password", config.password());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
            String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            if (!context.getSharedPreferences("backup_webdav", Context.MODE_PRIVATE).edit().putString("encrypted", encrypted).commit())
                throw new IOException("无法保存 WebDAV 配置");
        } catch (Exception failure) { throw new IOException("无法保存 WebDAV 配置，请检查手机存储空间", failure); }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
