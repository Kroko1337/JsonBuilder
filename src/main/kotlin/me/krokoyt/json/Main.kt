package me.krokoyt.json

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.krokoyt.json.api.ArgumentParser
import me.krokoyt.json.api.Dependency
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.junit.Assert
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.lang.AssertionError
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*
import javax.swing.plaf.ProgressBarUI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.collections.ArrayList
import kotlin.experimental.and
import kotlin.io.path.Path

val gson: Gson = GsonBuilder().setPrettyPrinting().create()

val repositories = ArrayList<String>()
val errors = ArrayList<String>()

fun main(args: Array<String>) {
    val libs = ArrayList<Dependency>()

    ArgumentParser.parse(args)
    val name: String = ArgumentParser.getArgument("name")[0]
    val version: String = ArgumentParser.getArgument("version")[0]
    val main = ArgumentParser.getArgumentUnsafe("main")
    val mainClass = if (main != null) main[0] else "net.minecraft.client.main.Main"
    val inputArg = ArgumentParser.getArgumentUnsafe("input")
    val input = File((if (inputArg != null) inputArg[0] else "pom") + ".xml")

    val documentBuilder = DocumentBuilderFactory.newInstance()
    documentBuilder.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    val builder = documentBuilder.newDocumentBuilder()
    val document = builder.parse(input)

    document.documentElement.normalize()

    println("Reading pom.xml")

    val dependencies = document.getElementsByTagName("dependency")
    for (i in 0 until dependencies.length) {
        val node = dependencies.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            val groupId = element.getElementsByTagName("groupId")
            val artifactId = element.getElementsByTagName("artifactId")
            val versionElement = element.getElementsByTagName("version")
            libs.add(
                Dependency(
                    groupId.item(0).textContent,
                    artifactId.item(0).textContent,
                    versionElement.item(0).textContent
                )
            )
        }
    }

    val repository = document.getElementsByTagName("repository")
    for (i in 0 until repository.length) {
        val node = repository.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            val url = element.getElementsByTagName("url")
            repositories.add(url.item(0).textContent)
        }
    }

    repositories.add("https://libraries.minecraft.net/")

    println("Found repositories: $repositories")

    println("Finished reading pom.xml")

    val output = File("$name.json")

    if (!output.exists())
        output.createNewFile()

    println("Writing json")

    val json = JsonObject()
    json.addProperty("id", name)
    json.addProperty("assets", version)
    val calendar = Calendar.getInstance()
    json.addProperty(
        "time",
        "${calendar.get(Calendar.YEAR)}-${format(calendar.get(Calendar.DAY_OF_MONTH))}-${(format(calendar.get(Calendar.MONTH) + 1))}T${
            format(calendar.get(Calendar.HOUR_OF_DAY))
        }:${format(calendar.get(Calendar.MINUTE))}:${format(calendar.get(Calendar.SECOND))}+${
            format(
                (calendar.get(
                    Calendar.MILLISECOND
                ))
            )
        }:00"
    )
    json.addProperty(
        "releaseTime",
        "${calendar.get(Calendar.YEAR)}-${format(calendar.get(Calendar.DAY_OF_MONTH))}-${(format(calendar.get(Calendar.MONTH) + 1))}T${
            format(calendar.get(Calendar.HOUR_OF_DAY))
        }:${format(calendar.get(Calendar.MINUTE))}:${format(calendar.get(Calendar.SECOND))}+${
            format(
                (calendar.get(
                    Calendar.MILLISECOND
                ))
            )
        }:00"
    )
    json.addProperty("type", "release")
    json.addProperty(
        "minecraftArguments",
        "--username \${auth_player_name} --version \${version_name} --gameDir \${game_directory} --assetsDir \${assets_root} --assetIndex \${assets_index_name} --uuid \${auth_uuid} --accessToken \${auth_access_token} --userProperties \${user_properties} --userType \${user_type}"
    )
    val libraries = JsonArray()

    val progressbar = ProgressBar("Test repositories",
        (libs.size - 1).toLong()
    )
    for (lib in libs) {
        val url = getURL(lib, progressbar)
        val libObj = JsonObject()
        if (!lib.noRepository) {
            val downloads = JsonObject()
            val artifact = JsonObject()
            val path = "${System.getProperty("user.home")}/.m2/repository/${
                lib.groupId.replace(
                    ".",
                    "/"
                )
            }/${lib.artifactId}/${lib.version}/${lib.artifactId}-${lib.version}.jar"
            artifact.addProperty(
                "path",
                "${lib.groupId.replace(".", "/")}/${lib.artifactId}/${lib.version}/${lib.artifactId}-${lib.version}.jar"
            )
            artifact.addProperty("sha1", getSha1Code(path))
            artifact.addProperty("size", getFileSize(path))
            artifact.addProperty("url", url)
            downloads.add("artifact", artifact)
            libObj.add("downloads", downloads)
        }
        libObj.addProperty("name", "${lib.groupId}:${lib.artifactId}:${lib.version}")
        libraries.add(libObj)
    }
    progressbar.pause()

    json.add("libraries", libraries)
    json.addProperty("mainClass", mainClass)
    json.addProperty("minimumLauncherVersion", 14)

    val writer = BufferedWriter(FileWriter(output))
    writer.write(gson.toJson(json))
    writer.close()

    for(error in errors) {
        System.err.println(error)
    }

    println("Finished writing json")
}

fun getURL(dependency: Dependency, progressbar: ProgressBar): String {
    var foundRepository = false
    var repository = ""
    val toTest = "${
        dependency.groupId.replace(
            ".",
            "/"
        )
    }/${dependency.artifactId}/${dependency.version}/${dependency.artifactId}-${dependency.version}.jar"
    try {
        progressbar.step()
        var index = 0
        for (repo in repositories) {
            if(!repo.startsWith("file://")) {
                var repo = repo
                if (!repo.endsWith("/"))
                    repo += "/"
                val url = URL(
                    "$repo$toTest"
                )
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.connect()

                index++
                val response = urlConnection.responseCode
                try {
                    Assert.assertEquals(HttpURLConnection.HTTP_OK, response)
                    foundRepository = true
                    repository = "$repo$toTest"
                } catch (_: AssertionError) { }
                urlConnection.disconnect()
            }
        }
    } catch (exception: UnknownHostException) {
        errors.add("host not found for ${dependency.artifactId} ${dependency.version} -> $repository")
    }
    if (!foundRepository) {
        errors.add("\n${dependency.groupId}:${dependency.artifactId}:${dependency.version} has no working repository (maybe local repository?)")
        dependency.noRepository = true
    }
    return repository
}

fun getSha1Code(path: String): String {
    val input = FileInputStream(path)
    val digest = MessageDigest.getInstance("SHA-1")
    val digestInputStream = DigestInputStream(input, digest)
    val bytes = ByteArray(1024)
    while (digestInputStream.read(bytes) > 0) {
    }
    val result = digest.digest()
    return bytesToHexString(result)
}

fun getFileSize(path: String): String {
    return Files.size(Path(path)).toString()
}

private fun bytesToHexString(bytes: ByteArray): String {
    val stringBuilder = StringBuilder()
    for (byte in bytes) {
        val value: Int = byte.toInt().and(0xFF)
        if (value < 16) {
            stringBuilder.append("0")
        }
        stringBuilder.append(Integer.toHexString(value).lowercase())
    }
    return stringBuilder.toString()
}

fun format(input: Any): String {
    var input = "$input"
    if (input.length == 1) {
        input = "0$input"
    }
    return input
}