import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

plugins {
    `java-base`
    `maven-publish`
}

val nativeClassifiers = listOf(
    "linux-x86_64",
    "linux-aarch64",
    "osx-x86_64",
    "osx-aarch64",
    "windows-x86_64"
)

data class SwissProjRequirement(
    val label: String,
    val candidates: List<String>
)

val gdalSwissNativesEnabled = providers.gradleProperty("gdalSwissNativesEnabled")
    .map { raw ->
        when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw GradleException(
                "Invalid value for gdalSwissNativesEnabled: '$raw' (expected true or false)"
            )
        }
    }
    .orElse(true)
    .get()

val swissProjRequirements = listOf(
    SwissProjRequirement("proj.db", listOf("proj.db")),
    SwissProjRequirement("CHENyx06a", listOf("CHENyx06a.gsb", "ch_swisstopo_CHENyx06a.tif")),
    SwissProjRequirement("CHENyx06_ETRS", listOf("CHENyx06_ETRS.gsb", "ch_swisstopo_CHENyx06_ETRS.tif")),
    SwissProjRequirement("egm96_15", listOf("egm96_15.gtx", "us_nga_egm96_15.tif"))
)

val swissProjAllowlistCandidates = swissProjRequirements
    .flatMap { it.candidates }
    .distinct()

val gdalCnNativesEnabled = providers.gradleProperty("gdalCnNativesEnabled")
    .map { raw ->
        when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw GradleException(
                "Invalid value for gdalCnNativesEnabled: '$raw' (expected true or false)"
            )
        }
    }
    .orElse(true)
    .get()

val cnProjRequirements = listOf(
    SwissProjRequirement("proj.db", listOf("proj.db")),
    SwissProjRequirement("egm96_15", listOf("egm96_15.gtx", "us_nga_egm96_15.tif")),
    SwissProjRequirement("egm08_25", listOf("egm08_25.gtx", "us_nga_egm08_25.tif"))
)

val cnProjAllowlistCandidates = cnProjRequirements
    .flatMap { it.candidates }
    .distinct()

fun String.toTaskSuffix(): String {
    return split('-', '_').joinToString("") { part ->
        part.replaceFirstChar { c -> c.uppercase() }
    }
}

fun relativeClassifierPath(classifierRoot: File, file: File): String {
    return classifierRoot.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
}

fun collectVariantFiles(
    classifierRoot: File,
    includeSwissProjSubset: Boolean,
    swissProjAllowlistCandidates: Set<String>
): List<Pair<String, File>> {
    return classifierRoot.walkTopDown()
        .filter(File::isFile)
        .mapNotNull { file ->
            val relativePath = relativeClassifierPath(classifierRoot, file)
            if (relativePath == "manifest.json") {
                return@mapNotNull null
            }
            if (includeSwissProjSubset && relativePath.startsWith("share/proj/")) {
                val projFile = relativePath.removePrefix("share/proj/")
                if (projFile !in swissProjAllowlistCandidates) {
                    return@mapNotNull null
                }
            }
            relativePath to file
        }
        .sortedBy { it.first }
        .toList()
}

fun updateDigestWithFile(digest: MessageDigest, file: File) {
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
}

fun computeCacheKey(
    classifier: String,
    artifactId: String,
    classifierRoot: File,
    manifestData: Map<String, Any?>,
    includeSwissProjSubset: Boolean,
    swissProjAllowlistCandidates: Set<String>
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("artifactId=$artifactId\n".toByteArray(StandardCharsets.UTF_8))
    digest.update("classifier=$classifier\n".toByteArray(StandardCharsets.UTF_8))
    digest.update(JsonOutput.toJson(manifestData).toByteArray(StandardCharsets.UTF_8))
    for ((relativePath, file) in collectVariantFiles(classifierRoot, includeSwissProjSubset, swissProjAllowlistCandidates)) {
        digest.update("\nfile=$relativePath\n".toByteArray(StandardCharsets.UTF_8))
        updateDigestWithFile(digest, file)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun validateSwissProjSubset(
    classifier: String,
    projDir: File,
    swissProjRequirements: List<SwissProjRequirement>
) {
    val stagedFiles = projDir.listFiles { file -> file.isFile }
        ?.map { it.name }
        ?.filterNot { it.startsWith(".") }
        ?.toSet()
        .orEmpty()

    if (stagedFiles.isEmpty()) {
        return
    }

    val missingGroups = swissProjRequirements.filter { requirement ->
        requirement.candidates.none(stagedFiles::contains)
    }

    if (missingGroups.isNotEmpty()) {
        val missingLabels = missingGroups.joinToString(", ") { requirement ->
            "${requirement.label} [${requirement.candidates.joinToString(" | ")}]"
        }

        throw GradleException(
            "PROJ subset for classifier '$classifier' is incomplete. " +
                "Missing groups: $missingLabels. Available files in share/proj: " +
                stagedFiles.sorted().joinToString(", ")
        )
    }
}

fun registerPackagedManifestTask(
    classifier: String,
    artifactId: String,
    includeSwissProjSubset: Boolean,
    swissProjAllowlistCandidates: Set<String>,
    swissProjRequirements: List<SwissProjRequirement>
): Pair<TaskProvider<Task>, Provider<RegularFile>> {
    val classifierRoot = layout.projectDirectory.dir("src/main/resources/META-INF/gdal-native/$classifier")
    val projRoot = classifierRoot.dir("share/proj")
    val taskName = buildString {
        append("generate")
        append(
            when {
                artifactId.endsWith("-swiss") -> "NativesSwiss"
                artifactId.endsWith("-cn") -> "NativesCn"
                else -> "Natives"
            }
        )
        append("Manifest")
        append(classifier.toTaskSuffix())
    }
    val outputFile = layout.buildDirectory.file("generated/packaged-manifests/$artifactId/$classifier/manifest.json")
    val task = tasks.register(taskName) {
        inputs.dir(classifierRoot)
        inputs.property("artifactId", artifactId)
        inputs.property("includeSwissProjSubset", includeSwissProjSubset)
        inputs.property("swissProjAllowlistCandidates", swissProjAllowlistCandidates.toList())
        outputs.file(outputFile)

        doLast {
            if (includeSwissProjSubset) {
                validateSwissProjSubset(classifier, projRoot.asFile, swissProjRequirements)
            }

            val sourceManifestFile = classifierRoot.file("manifest.json").asFile
            @Suppress("UNCHECKED_CAST")
            val sourceManifest = JsonSlurper().parseText(sourceManifestFile.readText()) as Map<String, Any?>
            val manifestData = LinkedHashMap(sourceManifest)
            manifestData.remove("cacheKey")
            manifestData["cacheKey"] = computeCacheKey(
                classifier,
                artifactId,
                classifierRoot.asFile,
                manifestData,
                includeSwissProjSubset,
                swissProjAllowlistCandidates
            )

            val targetFile = outputFile.get().asFile
            targetFile.parentFile.mkdirs()
            targetFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifestData)) + "\n", StandardCharsets.UTF_8)
        }
    }
    return task to outputFile
}

val classifierJarTasks = nativeClassifiers.associateWith { classifier ->
    val classifierRoot = layout.projectDirectory.dir("src/main/resources/META-INF/gdal-native/$classifier")
    val (packagedManifestTask, packagedManifestFile) = registerPackagedManifestTask(
        classifier,
        "gdal-ffm-natives",
        false,
        swissProjAllowlistCandidates.toSet(),
        swissProjRequirements
    )
    tasks.register<Jar>("nativesJar${classifier.toTaskSuffix()}") {
        group = LifecycleBasePlugin.BUILD_GROUP
        description = "Builds native bundle JAR for $classifier"
        archiveBaseName.set("gdal-ffm-natives")
        archiveClassifier.set("natives-$classifier")

        from(classifierRoot) {
            into("META-INF/gdal-native/$classifier")
            exclude("manifest.json")
        }
        from(packagedManifestFile) {
            into("META-INF/gdal-native/$classifier")
        }
        dependsOn(packagedManifestTask)
    }
}

val swissClassifierJarTasks: Map<String, TaskProvider<Jar>> = if (gdalSwissNativesEnabled) {
    nativeClassifiers.associateWith { classifier ->
        val classifierRoot = layout.projectDirectory.dir("src/main/resources/META-INF/gdal-native/$classifier")
        val projRoot = classifierRoot.dir("share/proj")
        val (packagedManifestTask, packagedManifestFile) = registerPackagedManifestTask(
            classifier,
            "gdal-ffm-natives-swiss",
            true,
            swissProjAllowlistCandidates.toSet(),
            swissProjRequirements
        )
        tasks.register<Jar>("nativesSwissJar${classifier.toTaskSuffix()}") {
            group = LifecycleBasePlugin.BUILD_GROUP
            description = "Builds Swiss native bundle JAR for $classifier"
            archiveBaseName.set("gdal-ffm-natives-swiss")
            archiveClassifier.set("natives-$classifier")

            from(classifierRoot) {
                into("META-INF/gdal-native/$classifier")
                exclude("manifest.json")
                exclude("share/proj/**")
            }

            from(projRoot) {
                into("META-INF/gdal-native/$classifier/share/proj")
                include(*swissProjAllowlistCandidates.toTypedArray())
            }

            from(packagedManifestFile) {
                into("META-INF/gdal-native/$classifier")
            }

            doFirst {
                validateSwissProjSubset(classifier, projRoot.asFile, swissProjRequirements)
            }

            dependsOn(packagedManifestTask)
        }
    }
} else {
    emptyMap()
}

val cnClassifierJarTasks: Map<String, TaskProvider<Jar>> = if (gdalCnNativesEnabled) {
    nativeClassifiers.associateWith { classifier ->
        val classifierRoot = layout.projectDirectory.dir("src/main/resources/META-INF/gdal-native/$classifier")
        val projRoot = classifierRoot.dir("share/proj")
        val (packagedManifestTask, packagedManifestFile) = registerPackagedManifestTask(
            classifier,
            "gdal-ffm-natives-cn",
            true,
            cnProjAllowlistCandidates.toSet(),
            cnProjRequirements
        )
        tasks.register<Jar>("nativesCnJar${classifier.toTaskSuffix()}") {
            group = LifecycleBasePlugin.BUILD_GROUP
            description = "Builds China-focused native bundle JAR for $classifier"
            archiveBaseName.set("gdal-ffm-natives-cn")
            archiveClassifier.set("natives-$classifier")

            from(classifierRoot) {
                into("META-INF/gdal-native/$classifier")
                exclude("manifest.json")
                exclude("share/proj/**")
            }

            from(projRoot) {
                into("META-INF/gdal-native/$classifier/share/proj")
                include(*cnProjAllowlistCandidates.toTypedArray())
            }

            from(packagedManifestFile) {
                into("META-INF/gdal-native/$classifier")
            }

            doFirst {
                validateSwissProjSubset(classifier, projRoot.asFile, cnProjRequirements)
            }

            dependsOn(packagedManifestTask)
        }
    }
} else {
    emptyMap()
}

tasks.assemble {
    dependsOn(classifierJarTasks.values)
    if (gdalSwissNativesEnabled) {
        dependsOn(swissClassifierJarTasks.values)
    }
    if (gdalCnNativesEnabled) {
        dependsOn(cnClassifierJarTasks.values)
    }
}

tasks.register("verifyNativeBundleLayout") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks that each classifier bundle has a manifest.json file and required Unix CA metadata"
    doLast {
        nativeClassifiers.forEach { classifier ->
            val classifierRoot = layout.projectDirectory
                .dir("src/main/resources/META-INF/gdal-native/$classifier")
                .asFile
            val manifest = layout.projectDirectory
                .file("src/main/resources/META-INF/gdal-native/$classifier/manifest.json")
                .asFile
            if (!manifest.isFile) {
                throw GradleException("Missing manifest.json for classifier: $classifier")
            }

            @Suppress("UNCHECKED_CAST")
            val manifestData = JsonSlurper().parseText(manifest.readText()) as Map<String, Any?>
            val caBundlePath = (manifestData["caBundlePath"] as String?)?.takeIf { it.isNotBlank() }
            if (caBundlePath != null) {
                val caBundle = classifierRoot.resolve(caBundlePath)
                if (!caBundle.isFile) {
                    throw GradleException(
                        "Manifest caBundlePath for classifier '$classifier' points to a missing file: $caBundlePath"
                    )
                }
            }

            val isUnixClassifier = classifier.startsWith("linux-") || classifier.startsWith("osx-")
            val hasLibcurl = classifierRoot.resolve("lib")
                .walkTopDown()
                .any { it.isFile && it.name.startsWith("libcurl.") }
            if (isUnixClassifier && hasLibcurl && caBundlePath == null) {
                throw GradleException(
                    "Unix classifier '$classifier' bundles libcurl but manifest.json does not declare caBundlePath"
                )
            }
        }
    }
}

tasks.check {
    dependsOn("verifyNativeBundleLayout")
}

publishing {
    publications {
        create<MavenPublication>("natives") {
            artifactId = "gdal-ffm-natives"

            classifierJarTasks.values.forEach { jarTaskProvider ->
                artifact(jarTaskProvider)
            }

            pom {
                name.set("gdal-ffm-natives")
                description.set("Bundled GDAL native libraries and runtime data per platform classifier")
            }
        }

        if (gdalSwissNativesEnabled) {
            create<MavenPublication>("nativesSwiss") {
                artifactId = "gdal-ffm-natives-swiss"

                swissClassifierJarTasks.values.forEach { jarTaskProvider ->
                    artifact(jarTaskProvider)
                }

                pom {
                    name.set("gdal-ffm-natives-swiss")
                    description.set("Bundled GDAL native libraries with Swiss-focused PROJ data subset")
                }
            }
        }

        if (gdalCnNativesEnabled) {
            create<MavenPublication>("nativesCn") {
                artifactId = "gdal-ffm-natives-cn"

                cnClassifierJarTasks.values.forEach { jarTaskProvider ->
                    artifact(jarTaskProvider)
                }

                pom {
                    name.set("gdal-ffm-natives-cn")
                    description.set("Bundled GDAL native libraries with China-focused PROJ data subset")
                }
            }
        }
    }
    repositories {
        maven {
            name = "releaseTarget"
            val publishUrl = providers.gradleProperty("publishRepositoryUrl")
                .orElse(providers.environmentVariable("MAVEN_REPOSITORY_URL"))
                .orElse(layout.buildDirectory.dir("repo").map { it.asFile.absolutePath })
            url = uri(publishUrl.get())

            val publishUser = providers.gradleProperty("publishUsername")
                .orElse(providers.environmentVariable("MAVEN_USERNAME"))
                .orNull
            val publishPassword = providers.gradleProperty("publishPassword")
                .orElse(providers.environmentVariable("MAVEN_PASSWORD"))
                .orNull

            if (publishUser != null && publishPassword != null) {
                credentials {
                    username = publishUser
                    password = publishPassword
                }
            }
        }
    }
}
