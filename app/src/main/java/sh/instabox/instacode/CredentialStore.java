package sh.instabox.instacode;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.SecureRandom;

final class CredentialStore {
    private static final String FILE = "instacode.secure";
    private final SharedPreferences preferences;

    CredentialStore(Context context) throws Exception {
        MasterKey key = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        preferences = EncryptedSharedPreferences.create(
                context,
                FILE,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    String apiKey() { return preferences.getString("api_key", ""); }
    String machineId() { return preferences.getString("machine_id", ""); }
    String machineName() { return preferences.getString("machine_name", ""); }
    String webPassword() { return preferences.getString("web_password", ""); }
    String webUrl() { return preferences.getString("web_url", ""); }

    void saveConnection(String apiKey, String machineId, String machineName) {
        preferences.edit()
                .putString("api_key", apiKey)
                .putString("machine_id", machineId)
                .putString("machine_name", machineName)
                .apply();
    }

    void saveWebUrl(String url) { preferences.edit().putString("web_url", url).apply(); }

    String getOrCreateWebPassword() {
        String current = webPassword();
        if (!current.isEmpty()) return current;
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        StringBuilder value = new StringBuilder();
        for (byte b : bytes) value.append(String.format("%02x", b));
        preferences.edit().putString("web_password", value.toString()).apply();
        return value.toString();
    }

    void clear() { preferences.edit().clear().apply(); }
}
