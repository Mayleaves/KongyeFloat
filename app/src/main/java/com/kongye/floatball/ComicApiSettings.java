package com.kongye.floatball;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** API credentials remain encrypted on this device, outside portable settings backups. */
final class ComicApiSettings {
    private static final String ALIAS="KongyeFloat.ComicApi.v1";
    record Config(String endpoint,String model,String key) {
        static Config empty(){return new Config("","","");}
        void validate() throws IOException {
            okhttp3.HttpUrl url=okhttp3.HttpUrl.parse(endpoint);
            if(url==null||!url.isHttps()||!url.username().isEmpty()||!url.password().isEmpty()||url.query()!=null||url.fragment()!=null)
                throw new IOException("请输入 HTTPS 接口地址，例如 https://服务地址/v1");
            if(model.isBlank()||key.isBlank())throw new IOException("请填写模型和 API Key");
            if(key.contains("\n")||key.contains("\r"))throw new IOException("API Key 格式不正确");
        }
    }
    static Config load(Context c) throws IOException {
        String value=c.getSharedPreferences("comic_api",Context.MODE_PRIVATE).getString("encrypted","");
        if(value.isEmpty())return Config.empty();
        try{
            String[] parts=value.split(":",-1);if(parts.length!=2)throw new IOException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
            JSONObject json=new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));
            return new Config(json.getString("endpoint"),json.getString("model"),json.getString("key"));
        }catch(Exception error){throw new IOException("API 配置无法读取，请重新填写");}
    }
    static void save(Context c,Config config)throws IOException {
        config.validate();
        try{
            JSONObject json=new JSONObject().put("endpoint",config.endpoint()).put("model",config.model()).put("key",config.key());
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
            String encrypted=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
            if(!c.getSharedPreferences("comic_api",Context.MODE_PRIVATE).edit().putString("encrypted",encrypted).commit())throw new IOException();
        }catch(Exception error){throw new IOException("无法保存 API 配置");}
    }
    private static javax.crypto.SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
