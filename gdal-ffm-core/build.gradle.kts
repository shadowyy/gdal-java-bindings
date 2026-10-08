plugins {
    `java-library`
    `maven-publish`
}

fun currentNativeClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()

    return when {
        os.contains("mac") || os.contains("darwin") -> when (arch) {
            "aarch64", "arm64" -> "osx-aarch64"
            "x86_64", "amd64" -> "osx-x86_64"
            else -> error("Unsupported macOS architecture for native test resources: $arch")
        }

        os.contains("linux") -> when (arch) {
            "aarch64", "arm64" -> "linux-aarch64"
            "x86_64", "amd64" -> "linux-x86_64"
            else -> error("Unsupported Linux architecture for native test resources: $arch")
        }

        os.contains("win") -> when (arch) {
            "x86_64", "amd64" -> "windows-x86_64"
            else -> error("Unsupported Windows architecture for native test resources: $arch")
        }

        else -> error("Unsupported operating system for native test resources: $os")
    }
}

val hostNativeClassifier = currentNativeClassifier()
val hostNativeResources = tasks.register<Sync>("hostNativeResources") {
    from(project(":gdal-ffm-natives").layout.projectDirectory.dir("src/main/resources")) {
        include("META-INF/gdal-native/$hostNativeClassifier/**")
    }
    into(layout.buildDirectory.dir("host-native-resources"))
}

val javaToolchainVersion = providers.gradleProperty("gdalFfmJavaToolchainVersion")
    .map(String::toInt)
    .orElse(25)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion.get()))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaToolchainVersion.get())
}

sourceSets {
    named("main") {
        java.srcDir("src/generated/java")
    }

    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }
}

configurations {
    named("integrationTestImplementation") {
        extendsFrom(configurations["testImplementation"])
    }
    named("integrationTestRuntimeOnly") {
        extendsFrom(configurations["testRuntimeOnly"])
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "integrationTestImplementation"(platform("org.junit:junit-bom:5.11.4"))
    "integrationTestImplementation"("org.junit.jupiter:junit-jupiter")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests that require bundled GDAL libraries."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(hostNativeResources)
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath + files(hostNativeResources)
    shouldRunAfter(tasks.test)
    onlyIf {
        System.getenv("GDAL_FFM_RUN_INTEGRATION") == "true"
    }
    maxHeapSize = "2g"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("smokeTest") {
    description = "Runs a GDAL translate smoke test using repository test data."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn("integrationTestClasses", hostNativeResources)

    val inputFile = layout.projectDirectory.file("src/integrationTest/resources/smoke/reclass.tif").asFile
    val outputFile = layout.buildDirectory.file("smoke-test-output/reclass-smoke.tif").get().asFile

    classpath = sourceSets["integrationTest"].runtimeClasspath + files(hostNativeResources)
    mainClass.set("ch.so.agi.gdal.ffm.GdalSmoke")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    inputs.file(inputFile)
    outputs.file(outputFile)
    outputs.upToDateWhen { false }

    doFirst {
        if (!inputFile.isFile) {
            throw GradleException(
                "Smoke test input is missing: ${inputFile.absolutePath}. " +
                        "Expected gdal-ffm-core/src/integrationTest/resources/smoke/reclass.tif."
            )
        }
        outputFile.parentFile.mkdirs()
        setArgs(listOf(outputFile.absolutePath, inputFile.absolutePath))
    }
}

tasks.register<JavaExec>("smokeTestPackagedNative") {
    description = "Runs raster and OGR smoke tests against a packaged native classifier JAR."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn("integrationTestClasses")

    val inputFile = layout.projectDirectory.file("src/integrationTest/resources/smoke/reclass.tif").asFile
    val smokeNativeJar = providers.gradleProperty("gdalFfmSmokeNativeJar")
    val smokeLabel = providers.gradleProperty("gdalFfmSmokeLabel")
        .map { label -> label.trim().ifEmpty { "packaged" } }
        .orElse("packaged")
    val smokeTmpDirOverride = providers.gradleProperty("gdalFfmSmokeTmpDir")
    val outputFile = smokeLabel.flatMap { label ->
        layout.buildDirectory.file("smoke-test-output/${label}-reclass-smoke.tif")
    }
    val tmpDir = smokeLabel.flatMap { label ->
        layout.buildDirectory.dir("tmp/smoke/$label")
    }

    classpath = sourceSets["integrationTest"].runtimeClasspath
    mainClass.set("ch.so.agi.gdal.ffm.internal.GdalPackagedNativeSmoke")
    inputs.file(inputFile)
    inputs.property("gdalFfmSmokeNativeJar", smokeNativeJar.orNull ?: "")
    inputs.property("gdalFfmSmokeLabel", smokeLabel)
    inputs.property("gdalFfmSmokeTmpDir", smokeTmpDirOverride.orNull ?: "")
    outputs.file(outputFile)
    outputs.upToDateWhen { false }

    doFirst {
        val nativeJarPath = smokeNativeJar.orNull?.trim()
        if (nativeJarPath.isNullOrEmpty()) {
            throw GradleException(
                "Missing required property -PgdalFfmSmokeNativeJar=<path-to-native-jar> for smokeTestPackagedNative."
            )
        }
        val nativeJarPathFile = File(nativeJarPath)
        val nativeJar = if (nativeJarPathFile.isAbsolute) {
            nativeJarPathFile
        } else {
            rootProject.file(nativeJarPath)
        }
        if (!nativeJar.isFile) {
            throw GradleException("Packaged smoke native JAR does not exist: ${nativeJar.absolutePath}")
        }
        if (!inputFile.isFile) {
            throw GradleException(
                "Smoke test input is missing: ${inputFile.absolutePath}. " +
                        "Expected gdal-ffm-core/src/integrationTest/resources/smoke/reclass.tif."
            )
        }

        val smokeOutputFile = outputFile.get().asFile
        val smokeTmpDir = smokeTmpDirOverride.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            val pathFile = File(path)
            if (pathFile.isAbsolute) {
                pathFile
            } else {
                rootProject.file(path)
            }
        } ?: tmpDir.get().asFile
        smokeOutputFile.parentFile.mkdirs()
        smokeTmpDir.mkdirs()

        classpath = sourceSets["integrationTest"].runtimeClasspath + files(nativeJar)
        val smokeJvmArgs = mutableListOf(
            "--enable-native-access=ALL-UNNAMED",
            "-Djava.io.tmpdir=${smokeTmpDir.absolutePath}"
        )
        if (!hostNativeClassifier.startsWith("windows-")) {
            smokeJvmArgs += "-Dgdal.ffm.smoke.expectBundledCaBundle=true"
        }
        jvmArgs(smokeJvmArgs)
        setArgs(listOf(smokeOutputFile.absolutePath, inputFile.absolutePath))
    }
}

tasks.check {
    dependsOn(integrationTest)
}

tasks.register<Exec>("generateFfmBindings") {
    group = "code generation"
    description = "Regenerates low-level FFM bindings using tools/jextract/regenerate.sh"
    workingDir = rootDir
    commandLine("bash", "tools/jextract/regenerate.sh")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "gdal-ffm-core"
            from(components["java"])
            pom {
                name.set("gdal-ffm-core")
                description.set("Java FFM core bindings and high-level API for GDAL utilities")
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
