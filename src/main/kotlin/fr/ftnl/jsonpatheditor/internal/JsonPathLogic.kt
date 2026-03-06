package fr.ftnl.jsonpatheditor.internal

import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonElement
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import java.io.StringReader

object JsonPathLogic {
    private val gsonPretty = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val gsonStrict = GsonBuilder().disableHtmlEscaping().create()
    
    fun getPaths(jsonText: String, query: String): List<String> {
        return try {
            val strictJson = lenientToStrictJson(jsonText)
            val conf = Configuration.builder().options(Option.AS_PATH_LIST).build()
            JsonPath.using(conf).parse(strictJson).read(query)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun findPsiElement(psiFile: JsonFile, path: String): JsonElement? {
        var curr: JsonElement = psiFile.topLevelValue ?: return null
        val regex = """\['([^']+)'\]|\[(\d+)\]""".toRegex()
        for (m in regex.findAll(path)) {
            val k = m.groups[1]?.value
            val i = m.groups[2]?.value?.toIntOrNull()
            if (k != null) {
                if (curr !is JsonObject) return null
                curr = (curr as JsonObject).propertyList.find { it.name == k }?.value ?: return null
            } else if (i != null) {
                if (curr !is JsonArray) return null
                curr = (curr as JsonArray).valueList.getOrNull(i) ?: return null
            }
        }
        return curr
    }
    
    fun processValueForDoc(input: String, currentIndent: String): String {
        val processed = try {
            JsonParser.parseString(input)
            input
        } catch (e: Exception) {
            gsonStrict.toJson(input)
        }
        return processed.replace("\n", "\n$currentIndent")
    }
    
    fun formatPretty(raw: String): String = try {
        val reader = JsonReader(StringReader(raw)).apply { isLenient = true }
        gsonPretty.toJson(JsonParser.parseReader(reader))
    } catch (e: Exception) { raw }
    
    private fun lenientToStrictJson(raw: String): String = try {
        val reader = JsonReader(StringReader(raw)).apply { isLenient = true }
        gsonStrict.toJson(JsonParser.parseReader(reader))
    } catch (e: Exception) { raw }
}