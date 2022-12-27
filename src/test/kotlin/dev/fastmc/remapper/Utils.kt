package dev.fastmc.remapper

import com.google.gson.JsonParser
import dev.fastmc.remapper.util.GlobalMavenCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

fun getMcVersions() : List<String> {
    val url = URL("https://launchermeta.mojang.com/mc/game/version_manifest.json")
    val json = JsonParser.parseString(url.readText())
    return json.asJsonObject.getAsJsonArray("versions").asSequence()
        .map { it.asJsonObject }
        .filter { it.get("type").asString == "release" }
        .map { it.get("id").asString }
        .toList()
}

suspend fun getMavenVersion(url: String): List<String> {
    val file = GlobalMavenCache.getMaven(url).await()!!
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val doc = withContext(Dispatchers.IO) {
        builder.parse(file.readBytes().inputStream())
    }
    return doc.getElementsByTagName("version").toList().map { it.textContent }
}

fun NodeList.toList(): List<Node> {
    val list = mutableListOf<Node>()
    for (i in 0 until length) {
        list.add(item(i))
    }
    return list
}