package dev.sudoloser.ecodash.plugin

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class PluginMetadata(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val mainScript: String,
    val logicScript: String,
    val settingsScript: String,
    var isEnabled: Boolean = true,
    val settingsValues: MutableMap<String, String> = mutableMapOf()
)

class PluginManager(private val context: Context) {
    private val gson = Gson()
    private val pluginsDir = File(context.filesDir, "plugins")
    private val sharedPrefs = context.getSharedPreferences("plugins_pref", Context.MODE_PRIVATE)

    init {
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }
    }

    fun getInstalledPlugins(): List<PluginMetadata> {
        val list = mutableListOf<PluginMetadata>()
        val dirs = pluginsDir.listFiles { file -> file.isDirectory } ?: return emptyList()
        
        for (dir in dirs) {
            val manifestFile = File(dir, "manifest.json")
            if (manifestFile.exists()) {
                try {
                    val content = manifestFile.readText()
                    val obj = JSONObject(content)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val author = obj.optString("author", "Unknown")
                    val description = obj.optString("description", "")
                    val mainScript = obj.getString("mainScript")
                    val logicScript = obj.getString("logicScript")
                    val settingsScript = obj.getString("settingsScript")

                    val isEnabled = sharedPrefs.getBoolean("enabled_$id", true)
                    val settingsJson = sharedPrefs.getString("settings_$id", "{}")
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val settingsMap: Map<String, String> = gson.fromJson(settingsJson, type) ?: emptyMap()

                    list.add(
                        PluginMetadata(
                            id = id,
                            name = name,
                            author = author,
                            description = description,
                            mainScript = mainScript,
                            logicScript = logicScript,
                            settingsScript = settingsScript,
                            isEnabled = isEnabled,
                            settingsValues = settingsMap.toMutableMap()
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return list
    }

    fun setPluginEnabled(id: String, enabled: Boolean) {
        sharedPrefs.edit().putBoolean("enabled_$id", enabled).apply()
    }

    fun savePluginSettings(id: String, settings: Map<String, String>) {
        val json = gson.toJson(settings)
        sharedPrefs.edit().putString("settings_$id", json).apply()
    }

    fun importPluginFromZip(zipUri: Uri): Result<PluginMetadata> {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(zipUri) ?: throw IllegalArgumentException("Cannot open zip stream")
            importPluginFromStream(inputStream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importPluginFromStream(inputStream: InputStream): Result<PluginMetadata> {
        val tempDir = File(context.cacheDir, "temp_unpack_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(tempDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                tempDir.deleteRecursively()
                return Result.failure(IllegalArgumentException("manifest.json is missing in the root of the ZIP file"))
            }

            val manifestContent = manifestFile.readText()
            val obj = JSONObject(manifestContent)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val author = obj.optString("author", "Unknown")
            val description = obj.optString("description", "")
            val mainScript = obj.getString("mainScript")
            val logicScript = obj.getString("logicScript")
            val settingsScript = obj.getString("settingsScript")

            val finalDir = File(pluginsDir, id)
            if (finalDir.exists()) {
                finalDir.deleteRecursively()
            }
            finalDir.mkdirs()

            tempDir.copyRecursively(finalDir, overwrite = true)
            tempDir.deleteRecursively()

            val meta = PluginMetadata(
                id = id,
                name = name,
                author = author,
                description = description,
                mainScript = mainScript,
                logicScript = logicScript,
                settingsScript = settingsScript,
                isEnabled = true
            )
            return Result.success(meta)
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            return Result.failure(e)
        }
    }

    fun getPluginDirectory(id: String): File {
        return File(pluginsDir, id)
    }
}
