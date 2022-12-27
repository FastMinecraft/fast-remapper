package dev.fastmc.remapper

import com.google.gson.JsonParser
import java.net.URL

fun getMcVersions() : List<String> {
    val url = URL("https://launchermeta.mojang.com/mc/game/version_manifest.json")
    val json = JsonParser.parseString(url.readText())
    return json.asJsonObject.getAsJsonArray("versions").asSequence()
        .map { it.asJsonObject }
        .filter { it.get("type").asString == "release" }
        .map { it.get("id").asString }
        .toList()
}