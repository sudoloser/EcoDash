# EcoDash Plugin API Specification

EcoDash features a dynamic, sandbox-safe, and data-driven extension system. Users can import plugins packaged in `.zip` archives to render custom, modular dashboard cards compiled dynamically using Kotlin Scripting (`JSR-223`).

---

## 1. ZIP File Structure

A compliant EcoDash plugin must contain a `manifest.json` in its root directory and its source scripts inside a `src/` folder:

```text
my-plugin.zip
├── manifest.json
└── src
    ├── settings.kts
    ├── script.kts
    └── main.kts
```

---

## 2. Configuration Files

### `manifest.json`
Defines the metadata and script routes of the plugin.

```json
{
  "id": "sunset-media",
  "name": "SunSet Media",
  "author": "dev.sudoloser",
  "description": "Monitors the SunSet Media Server",
  "mainScript": "src/main.kts",
  "logicScript": "src/script.kts",
  "settingsScript": "src/settings.kts"
}
```

* **`id`**: Unique alphanumeric string identifying the plugin.
* **`name`**: Title shown on the configuration cards.
* **`mainScript`**: Path to the Compose rendering script.
* **`logicScript`**: Path to the background evaluation script.
* **`settingsScript`**: Path to the form settings schema script.

---

## 3. Scripts Architecture

### A. Settings Form (`src/settings.kts`)
Defines the input parameters required to run the plugin. It must return a `Map<String, Any>` specifying the form fields.

#### Bound Variables
* `bindings: Map<String, Any>` (empty map)

#### Return Value
Must return a map with a `"fields"` key containing a list of field descriptors:

```kotlin
mapOf(
    "fields" to listOf(
        mapOf(
            "key" to "server_ip",
            "type" to "text",
            "label" to "Server IP Address",
            "defaultValue" to "127.0.0.1"
        ),
        mapOf(
            "key" to "server_port",
            "type" to "text",
            "label" to "Port Number",
            "defaultValue" to "80"
        ),
        mapOf(
            "key" to "auth_token",
            "type" to "password",
            "label" to "Access Token",
            "defaultValue" to ""
        )
    )
)
```

---

### B. Background Evaluation (`src/script.kts`)
Executes asynchronous IO operations (such as HTTP requests) to obtain state metrics. It must return a data map to be processed by the rendering script.

#### Bound Variables
* `httpClient: okhttp3.OkHttpClient`: Thread-safe preconfigured HTTP client.
* `context: android.content.Context`: Safe application context.
* `bindings: Map<String, Any>`: Contains the user-configured inputs matching the keys defined in `settings.kts`.

#### Return Value
Must return a `Map<String, Any>` representing the parsed evaluation results.

```kotlin
import okhttp3.Request
import okhttp3.OkHttpClient
import org.json.JSONObject

val client = bindings["httpClient"] as OkHttpClient
val ip = bindings["server_ip"] as String
val port = bindings["server_port"] as String

val request = Request.Builder()
    .url("http://$ip:$port/health")
    .build()

val result = mutableMapOf<String, Any>()

try {
    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
            val json = JSONObject(response.body?.string() ?: "{}")
            result["status"] = json.optString("status", "healthy")
            result["success"] = true
        } else {
            result["success"] = false
            result["error"] = "HTTP error: ${response.code}"
        }
    }
} catch (e: Exception) {
    result["success"] = false
    result["error"] = e.message ?: "Network error"
}

result
```

---

### C. Layout Renderer (`src/main.kts`)
Converts the evaluation results map into dynamic UI components. It must return a structured layout map representing Compose widgets.

#### Bound Variables
* `context: android.content.Context`: Safe application context.
* `bindings: Map<String, Any>`: Contains a `"data"` key which maps to the evaluation result returned by `script.kts`.

#### Return Value
Must return a map defining nested container and content widgets.

```kotlin
val data = bindings["data"] as Map<String, Any>
val success = data["success"] as? Boolean ?: false

if (!success) {
    mapOf(
        "type" to "Card",
        "backgroundColor" to "#FFCDD2",
        "children" to listOf(
            mapOf("type" to "Text", "text" to "Error", "style" to "Title"),
            mapOf("type" to "Text", "text" to (data["error"] as? String ?: "Connection failed"), "style" to "Body")
        )
    )
} else {
    val status = data["status"] as? String ?: "unknown"
    mapOf(
        "type" to "Card",
        "backgroundColor" to "#E8F5E9",
        "children" to listOf(
            mapOf("type" to "Text", "text" to "Server Health Status", "style" to "Title"),
            mapOf("type" to "Text", "text" to "Uptime Node Status: $status", "style" to "Body")
        )
    )
}
```

---

## 4. UI Elements Schema

The layout engine supports the following element mappings:

| Element Type | Parameters | Description |
|---|---|---|
| **`Card`** | `backgroundColor` (HEX String), `children` (List) | Standard rounded card containing items. |
| **`Text`** | `text` (String), `style` (`"Title"`, `"Subtitle"`, `"Body"`), `color` (HEX) | Styled Material 3 labels. |
| **`Row`** | `children` (List) | Horizontal flex row distribution. |
| **`Column`** | `children` (List) | Vertical stack layout. |
| **`Spacer`** | *None* | Generates safe padding blocks. |
| **`StorageBar`** | `used` (Double), `total` (Double), `percent` (Double) | Pre-styled storage bar indicating percentage bar meters. |

---

## 5. Security Guardrails

To prevent dashboard freezing or crashes, EcoDash implements three isolation rules:
1. **Background Isolation:** All script executions are run on background worker threads (`Dispatchers.IO`).
2. **Crash Containment:** Any syntax or execution exceptions in scripts are caught. An error badge is displayed inside the individual card, leaving the main dashboard intact.
3. **No File Mutation:** Plugins have read-only access to their unpacked directory and cannot write or modify system storage files.
