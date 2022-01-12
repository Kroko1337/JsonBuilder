package me.krokoyt.json

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.krokoyt.json.api.ArgumentParser
import me.krokoyt.json.api.Dependency
import me.tongfei.progressbar.ProgressBar
import org.junit.Assert
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
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

    val fastMode = ArgumentParser.getArgumentUnsafe("fastmode") != null
    val nativesFix = ArgumentParser.getArgumentUnsafe("nativesfix") != null

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
            if (!artifactId.item(0).textContent.contains("minecraft"))
                libs.add(
                    Dependency(
                        groupId.item(0).textContent,
                        artifactId.item(0).textContent,
                        versionElement.item(0).textContent
                    )
                )
        }
    }

    repositories.add("https://libraries.minecraft.net/")
    val repository = document.getElementsByTagName("repository")
    for (i in 0 until repository.length) {
        val node = repository.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            val url = element.getElementsByTagName("url")
            repositories.add(url.item(0).textContent)
        }
    }

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

    val progressbar = ProgressBar(
        "Test repositories",
        (libs.size - 1).toLong()
    )

    var hasLWJGL = false
    var lwjgl: Dependency

    for (lib in libs) {
        val urlArray = getURL(lib, progressbar, fastMode)
        val url = urlArray[0]
        val rawUrl = urlArray[1]
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
            /*if (lib.artifactId.contains("lwjgl-platform") && !nativesFix) {
                val classifiers = JsonObject()
                val nativesLinux = JsonObject()
                val nativesOSX = JsonObject()
                val nativesWindows = JsonObject()

                val nativePath = "${lib.groupId.replace(".", "/")}/${lib.artifactId}/${lib.version}/${lib.artifactId}-${lib.version}"

                val linuxURL = getURL("$nativePath-natives-linux.jar")[0]
                val osxURL = getURL("$nativePath-natives-osx.jar")[0]
                val windowsURL = getURL("$nativePath-natives-windows.jar")[0]

                val linuxFile = downloadFile(linuxURL, "natives-linux.jar")
                val osxFile = downloadFile(osxURL, "natives-osx.jar")
                val windowsFile = downloadFile(windowsURL, "natives-windows.jar")

                nativesLinux.addProperty("path", "$nativePath-natives-linux.jar")
                nativesLinux.addProperty("sha1", getSha1Code(linuxFile))
                nativesLinux.addProperty("size", getFileSize(linuxFile.absolutePath))
                nativesLinux.addProperty("url", linuxURL)

                nativesOSX.addProperty("path", "$nativePath-natives-osx.jar")
                nativesOSX.addProperty(
                    "sha1",
                    getSha1Code(osxFile))
                nativesOSX.addProperty("size", getFileSize(osxFile.absolutePath))
                nativesOSX.addProperty("url", osxURL)

                nativesWindows.addProperty("path", "$nativePath-natives-windows.jar")
                nativesWindows.addProperty(
                    "sha1",
                    getSha1Code(windowsFile))
                nativesWindows.addProperty("size", getFileSize(windowsFile.absolutePath))
                nativesWindows.addProperty("url", windowsURL)

                classifiers.add("natives-linux", nativesLinux)
                classifiers.add("natives-osx", nativesOSX)
                classifiers.add("natives-windows", nativesWindows)
                downloads.add("classifiers", classifiers)
            }*/
            libObj.add("downloads", downloads)
            /*if(lib.artifactId.contains("lwjgl-platform") && !nativesFix) {
                val extract = JsonObject()
                val exclude = JsonArray()
                exclude.add("META-INF/")
                extract.add("exclude", exclude)

                libObj.add("extract", extract)
            }*/
        }
        libObj.addProperty("name", "${lib.groupId}:${lib.artifactId}:${lib.version}")
        /*if(lib.artifactId.contains("lwjgl-platform") && !nativesFix) {
            val natives = JsonObject()
            natives.addProperty("linux", "natives-linux")
            natives.addProperty("osx", "natives-osx")
            natives.addProperty("windows", "natives-windows")
            libObj.add("natives", natives)

            val rules = JsonArray()
            val action = JsonObject()
            action.addProperty("action", "allow")
            rules.add(action)
            val disallowAction = JsonObject()
            disallowAction.addProperty("action", "disallow")
            val os = JsonObject()
            os.addProperty("name", "osx")
            disallowAction.add("os", os)
            rules.add(disallowAction)
            libObj.add("rules", rules)
        }*/
        if (!lib.noRepository || fastMode)
            libraries.add(libObj)
    }
    progressbar.pause()

    json.add("libraries", libraries)
    json.addProperty("mainClass", mainClass)
    json.addProperty("minimumLauncherVersion", 14)

    val writer = BufferedWriter(FileWriter(output))
    writer.write(gson.toJson(json))
    writer.close()

    for (error in errors) {
        System.err.println(error)
    }

    println("Finished writing json")

    Toolkit.getDefaultToolkit().systemClipboard.setContents(
        StringSelection("-Djava.library.path=versions/$name/natives/"),
        null
    )
    println("Copied native path")
}

fun getURL(dependency: Dependency, progressbar: ProgressBar, fastMode: Boolean): Array<String> {
    var foundRepository = false
    var repository = ""
    var rawRepository = ""
    val toTest = "${
        dependency.groupId.replace(
            ".",
            "/"
        )
    }/${dependency.artifactId}/${dependency.version}/${dependency.artifactId}-${dependency.version}.jar"
    try {
        progressbar.step()
        var index = 0
        if (!fastMode)
            for (repo in repositories) {
                if (!repo.startsWith("file://")) {
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
                        rawRepository = repo
                    } catch (_: AssertionError) {
                    }
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
    return arrayOf(repository, rawRepository)
}

fun getURL(nativeUrl: String): Array<String> {
    var repository = ""
    var rawRepository = ""
    try {
        var index = 0
        for (repo in repositories) {
            if (!repo.startsWith("file://")) {
                var repo = repo
                if (!repo.endsWith("/"))
                    repo += "/"
                val url = URL(
                    "$repo$nativeUrl"
                )
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.connect()
                println(url)
                index++
                val response = urlConnection.responseCode
                try {
                    Assert.assertEquals(HttpURLConnection.HTTP_OK, response)
                    repository = "$repo$nativeUrl"
                    rawRepository = repo
                } catch (_: AssertionError) {
                }
                urlConnection.disconnect()
            }
        }
    } catch (_: UnknownHostException) {
    }
    return arrayOf(repository, rawRepository)
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

fun getSha1Code(file: File): String {
    val input = FileInputStream(file)
    val digest = MessageDigest.getInstance("SHA-1")
    val digestInputStream = DigestInputStream(input, digest)
    val bytes = ByteArray(1024)
    while (digestInputStream.read(bytes) > 0) {
    }
    val result = digest.digest()
    return bytesToHexString(result)
}

fun getFileSize(path: String): Int {
    return Files.size(Path(path)).toInt()
}

fun downloadFile(url: String, name: String): File {
    println(url)
    val inputStream = BufferedInputStream(URL(url).openStream())
    val fileOutputStream = FileOutputStream(name)
    val dataBuffer = ByteArray(1024)
    var bytesRead: Int
    while (inputStream.read(dataBuffer, 0, 1024).also { bytesRead = it } != -1) {
        fileOutputStream.write(dataBuffer, 0, bytesRead)
    }
    fileOutputStream.close()
    return File(name)
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