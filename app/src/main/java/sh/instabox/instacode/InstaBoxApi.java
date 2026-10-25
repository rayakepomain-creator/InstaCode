package sh.instabox.instacode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class InstaBoxApi {
    static final String BASE_URL = "https://api.instabox.sh";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final String apiKey;

    InstaBoxApi(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    OkHttpClient httpClient() {
        return client;
    }

    List<Machine> listMachines() throws IOException, JSONException {
        JSONObject body = request("GET", "/v1/machines?limit=100", null, false);
        JSONArray items = body.getJSONArray("machines");
        List<Machine> machines = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            machines.add(new Machine(items.getJSONObject(i)));
        }
        return machines;
    }

    Machine getMachine(String id) throws IOException, JSONException {
        return new Machine(request("GET", "/v1/machines/" + id, null, false));
    }

    Machine createMachine() throws IOException, JSONException {
        JSONObject create = new JSONObject()
                .put("name", "instacode-" + UUID.randomUUID().toString().substring(0, 8))
                .put("templateId", "universal");
        JSONObject operation = request("POST", "/v1/machines", create, true);
        return waitUntilRunning(operation.getString("machineId"));
    }

    Machine ensureRunning(String id) throws IOException, JSONException, InterruptedException {
        Machine machine = getMachine(id);
        if (machine.state.equals("suspended")) {
            request("POST", "/v1/machines/" + id + "/resume", new JSONObject(), true);
        } else if (machine.state.equals("stopped") || machine.state.equals("error")) {
            request("POST", "/v1/machines/" + id + "/start", new JSONObject(), true);
        }
        return waitUntilRunning(id);
    }

    private Machine waitUntilRunning(String id) throws IOException, JSONException {
        long deadline = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < deadline) {
            Machine machine = getMachine(id);
            if (machine.state.equals("running") && machine.ready) return machine;
            if (machine.state.equals("suspended")) {
                try {
                    request("POST", "/v1/machines/" + id + "/resume", new JSONObject(), true);
                } catch (IOException ignored) {
                    // A concurrent resume is harmless; polling remains authoritative.
                }
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waking the machine", e);
            }
        }
        throw new IOException("The machine took too long to become ready. Try again.");
    }

    JSONObject exec(String machineId, String command, int timeoutSeconds) throws IOException, JSONException {
        JSONObject payload = new JSONObject()
                .put("cmd", command)
                .put("timeoutSeconds", timeoutSeconds);
        JSONObject result = request("POST", "/v1/machines/" + machineId + "/exec", payload, false);
        if (result.optInt("exitCode", 1) != 0) {
            String details = result.optString("stderr", "").trim();
            if (details.isEmpty()) details = result.optString("stdout", "Remote command failed");
            throw new IOException(details);
        }
        return result;
    }

    String publishOpenCodePort(String machineId) throws IOException, JSONException {
        JSONObject payload = new JSONObject().put("port", 4096).put("name", "OpenCode");
        JSONObject result = request("POST", "/v1/machines/" + machineId + "/ports", payload, false);
        return result.getString("url");
    }

    private JSONObject request(String method, String path, JSONObject body, boolean idempotent)
            throws IOException, JSONException {
        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + path)
                .header("X-API-Key", apiKey)
                .header("Accept", "application/json");
        if (idempotent) builder.header("Idempotency-Key", UUID.randomUUID().toString());
        if (body != null) {
            builder.method(method, RequestBody.create(body.toString(), JSON));
        } else {
            builder.method(method, method.equals("GET") ? null : RequestBody.create(new byte[0], null));
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                String message = "InstaBox returned HTTP " + response.code();
                try {
                    JSONObject error = new JSONObject(text).optJSONObject("error");
                    if (error != null && error.has("message")) message = error.getString("message");
                } catch (JSONException ignored) {
                }
                throw new IOException(message);
            }
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        }
    }

    static final class Machine {
        final String id;
        final String name;
        final String state;
        final boolean ready;

        Machine(JSONObject json) {
            id = json.optString("id");
            name = json.optString("name", id);
            state = json.optString("state", "unknown");
            ready = json.optJSONObject("runtime") != null && json.optJSONObject("runtime").optBoolean("ready");
        }

        @Override
        public String toString() {
            return name + "  ·  " + state;
        }
    }
}
