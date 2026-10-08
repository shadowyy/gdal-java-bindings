package ch.so.agi.gdal.ffm.internal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeLoaderTest {
    private static final String CLASSIFIER = "linux-x86_64";

    @Test
    void failsIfMultipleNativeManifestsExistForSameClassifier() throws Exception {
        Path firstRoot = createManifestRoot("native-loader-first");
        Path secondRoot = createManifestRoot("native-loader-second");

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{firstRoot.toUri().toURL(), secondRoot.toUri().toURL()},
                null
        )) {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> invokeFindManifest(classLoader));
            assertTrue(error.getMessage().contains("Multiple bundled GDAL native resources found"));
            assertTrue(error.getMessage().contains("gdal-ffm-natives"));
            assertTrue(error.getMessage().contains("gdal-ffm-natives-swiss"));
            assertTrue(error.getMessage().contains("gdal-ffm-natives-cn"));
        }
    }

    @Test
    void resolvesSingleNativeManifest() throws Exception {
        Path onlyRoot = createManifestRoot("native-loader-only");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{onlyRoot.toUri().toURL()}, null)) {
            URL manifest = invokeFindManifest(classLoader);
            assertEquals("file", manifest.getProtocol());
            assertTrue(manifest.toString().endsWith("/META-INF/gdal-native/" + CLASSIFIER + "/manifest.json"));
        }
    }

    @Test
    void recognizesLibraryAlreadyLoadedInAnotherClassLoaderMessage() throws Exception {
        Path libPath = Path.of("/tmp/gdal-ffm/3.12.2/osx-aarch64/lib/libproj.25.dylib");

        boolean recognized = invokeAlreadyLoadedByOtherClassLoader(
                new UnsatisfiedLinkError("Native Library " + libPath + " already loaded in another classloader"),
                libPath
        );
        assertTrue(recognized);

        boolean unrelated = invokeAlreadyLoadedByOtherClassLoader(
                new UnsatisfiedLinkError("Unable to load library"),
                libPath
        );
        assertFalse(unrelated);
    }

    @Test
    void usesFileTreeBundleRootDirectly() throws Exception {
        Path manifestRoot = createManifestRoot("native-loader-refresh");
        URL manifestUrl = manifestRoot.resolve("META-INF/gdal-native/" + CLASSIFIER + "/manifest.json").toUri().toURL();

        Path bundleRoot = invokeResolveBundleRoot(manifestUrl);
        assertEquals(manifestRoot.resolve("META-INF/gdal-native/" + CLASSIFIER), bundleRoot);
    }

    @Test
    void resolvesFileTreeRootFromManifestUrl() throws Exception {
        Path manifestRoot = createManifestRoot("native-loader-file-root");
        URL manifestUrl = manifestRoot.resolve("META-INF/gdal-native/" + CLASSIFIER + "/manifest.json").toUri().toURL();

        Path bundleRoot = invokeFileTreeRoot(manifestUrl);
        assertEquals(manifestRoot.resolve("META-INF/gdal-native/" + CLASSIFIER), bundleRoot);
    }

    @Test
    void resolvesCaBundleFromFileTreeBundle() throws Exception {
        Path manifestRoot = createManifestRoot("native-loader-ca-file");
        Path bundleRoot = manifestRoot.resolve("META-INF/gdal-native/" + CLASSIFIER);
        Files.createDirectories(bundleRoot.resolve("share/gdal"));
        Files.createDirectories(bundleRoot.resolve("share/proj"));
        Files.createDirectories(bundleRoot.resolve("ssl"));
        Files.writeString(bundleRoot.resolve("ssl/cacert.pem"), "bundle-ca", StandardCharsets.UTF_8);

        String json = manifestJson("ca-file-tree-1", "ssl/cacert.pem", null);
        Files.writeString(bundleRoot.resolve("manifest.json"), json, StandardCharsets.UTF_8);

        NativeBundleInfo bundleInfo = NativeLoader.resolveBundleInfo(
                bundleRoot.resolve("manifest.json").toUri().toURL(),
                NativeManifest.parse(json),
                CLASSIFIER
        );

        assertEquals(bundleRoot, bundleInfo.extractionRoot());
        assertNotNull(bundleInfo.caBundle());
        assertEquals(bundleRoot.resolve("ssl/cacert.pem"), bundleInfo.caBundle());
    }

    @Test
    void resolvesCaBundleFromJarBundle() throws Exception {
        Path tmpDir = Files.createTempDirectory("native-loader-jar-ca");
        NativeBundleInfo bundleInfo = withJavaTmpDir(
                tmpDir,
                () -> resolveBundleInfoFromJar("jar-ca-1", "ssl/cacert.pem", "cache-key-jar-ca-1")
        );

        assertTrue(Files.isRegularFile(bundleInfo.caBundle()));
        assertEquals("bundle-ca", Files.readString(bundleInfo.caBundle(), StandardCharsets.UTF_8));
        assertTrue(bundleInfo.caBundle().endsWith("ssl/cacert.pem"));
        assertEquals(tmpDir.resolve("gdal-ffm").resolve("cache-key-jar-ca-1").resolve(CLASSIFIER), bundleInfo.extractionRoot());
        assertTrue(bundleInfo.caBundle().startsWith(tmpDir.toAbsolutePath()));
    }

    @Test
    void fallsBackToBundleVersionExtractionRootWhenCacheKeyMissing() throws Exception {
        Path tmpDir = Files.createTempDirectory("native-loader-legacy-root");

        NativeBundleInfo bundleInfo = withJavaTmpDir(
                tmpDir,
                () -> resolveBundleInfoFromJar("legacy-bundle-version-1", "ssl/cacert.pem", null)
        );

        assertEquals(
                tmpDir.resolve("gdal-ffm").resolve("legacy-bundle-version-1").resolve(CLASSIFIER),
                bundleInfo.extractionRoot()
        );
        assertTrue(Files.isRegularFile(bundleInfo.caBundle()));
    }

    @Test
    void usesCacheKeyToAvoidLegacyBundleVersionRootReuse() throws Exception {
        Path tmpDir = Files.createTempDirectory("native-loader-cache-key-root");

        NativeBundleInfo bundleInfo = withJavaTmpDir(tmpDir, () -> {
            Path legacyRoot = tmpDir.resolve("gdal-ffm").resolve("3.12.2").resolve(CLASSIFIER);
            Files.createDirectories(legacyRoot);
            Files.writeString(legacyRoot.resolve(".extract-complete"), "3.12.2", StandardCharsets.UTF_8);
            Files.writeString(legacyRoot.resolve("manifest.json"), "stale-manifest", StandardCharsets.UTF_8);

            return resolveBundleInfoFromJar("3.12.2", "ssl/cacert.pem", "cache-key-current");
        });

        assertEquals(
                tmpDir.resolve("gdal-ffm").resolve("cache-key-current").resolve(CLASSIFIER),
                bundleInfo.extractionRoot()
        );
        assertNotEquals(tmpDir.resolve("gdal-ffm").resolve("3.12.2").resolve(CLASSIFIER), bundleInfo.extractionRoot());
        assertTrue(Files.isRegularFile(bundleInfo.caBundle()));
    }

    @Test
    void differentCacheKeysDoNotCollideForSameBundleVersion() throws Exception {
        Path tmpDir = Files.createTempDirectory("native-loader-cache-collision");

        withJavaTmpDir(tmpDir, () -> {
            NativeBundleInfo first = resolveBundleInfoFromJar("3.12.2", "ssl/cacert.pem", "cache-key-first");
            NativeBundleInfo second = resolveBundleInfoFromJar("3.12.2", "ssl/cacert.pem", "cache-key-second");

            assertEquals(
                    tmpDir.resolve("gdal-ffm").resolve("cache-key-first").resolve(CLASSIFIER),
                    first.extractionRoot()
            );
            assertEquals(
                    tmpDir.resolve("gdal-ffm").resolve("cache-key-second").resolve(CLASSIFIER),
                    second.extractionRoot()
            );
            assertNotEquals(first.extractionRoot(), second.extractionRoot());
            assertTrue(Files.isRegularFile(first.caBundle()));
            assertTrue(Files.isRegularFile(second.caBundle()));
            return null;
        });
    }

    private static URL invokeFindManifest(URLClassLoader classLoader) {
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();
        current.setContextClassLoader(classLoader);
        try {
            Method method = NativeLoader.class.getDeclaredMethod("findManifest", String.class, String.class);
            method.setAccessible(true);
            return (URL) method.invoke(null, "META-INF/gdal-native/" + CLASSIFIER, CLASSIFIER);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        } finally {
            current.setContextClassLoader(previous);
        }
    }

    private static boolean invokeAlreadyLoadedByOtherClassLoader(UnsatisfiedLinkError error, Path libPath) {
        try {
            Method method = NativeLoader.class.getDeclaredMethod(
                    "isAlreadyLoadedByAnotherClassLoader",
                    UnsatisfiedLinkError.class,
                    Path.class
            );
            method.setAccessible(true);
            return (boolean) method.invoke(null, error, libPath);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path invokeResolveBundleRoot(URL manifestUrl) {
        try {
            Method method = NativeLoader.class.getDeclaredMethod(
                    "resolveBundleRoot",
                    URL.class,
                    String.class,
                    String.class
            );
            method.setAccessible(true);
            return (Path) method.invoke(null, manifestUrl, "3.12.2", CLASSIFIER);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path invokeFileTreeRoot(URL manifestUrl) {
        try {
            Method method = NativeLoader.class.getDeclaredMethod("fileTreeRoot", URL.class);
            method.setAccessible(true);
            return (Path) method.invoke(null, manifestUrl);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path createManifestRoot(String prefix) throws Exception {
        Path root = Files.createTempDirectory(prefix);
        Path manifest = root.resolve("META-INF/gdal-native/" + CLASSIFIER + "/manifest.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{}", StandardCharsets.UTF_8);
        return root;
    }

    private static NativeBundleInfo resolveBundleInfoFromJar(String bundleVersion, String caBundlePath, String cacheKey)
            throws Exception {
        Path jarFile = createJarBundle(bundleVersion, caBundlePath, cacheKey);
        URL manifestUrl = new URL(
                "jar:" + jarFile.toUri().toURL() + "!/META-INF/gdal-native/" + CLASSIFIER + "/manifest.json"
        );
        return NativeLoader.resolveBundleInfo(
                manifestUrl,
                NativeManifest.parse(manifestJson(bundleVersion, caBundlePath, cacheKey)),
                CLASSIFIER
        );
    }

    private static Path createJarBundle(String bundleVersion, String caBundlePath, String cacheKey) throws Exception {
        Path jarFile = Files.createTempFile("native-loader-bundle", ".jar");
        String prefix = "META-INF/gdal-native/" + CLASSIFIER + "/";
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarFile))) {
            writeJarEntry(output, prefix + "manifest.json", manifestJson(bundleVersion, caBundlePath, cacheKey));
            writeJarEntry(output, prefix + "share/gdal/README.txt", "gdal-data");
            writeJarEntry(output, prefix + "share/proj/proj.db", "proj-data");
            writeJarEntry(output, prefix + "ssl/cacert.pem", "bundle-ca");
        }
        return jarFile;
    }

    private static void writeJarEntry(JarOutputStream output, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        output.putNextEntry(entry);
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static String manifestJson(String bundleVersion, String caBundlePath, String cacheKey) {
        String cacheKeyJson = cacheKey == null ? "" : ",\n  \"cacheKey\": \"%s\"".formatted(cacheKey);
        return """
                {
                  "bundleVersion": "%s",
                  "entryLibrary": "lib/libgdal.so.38",
                  "preloadLibraries": [],
                  "gdalDataPath": "share/gdal",
                  "projDataPath": "share/proj",
                  "driverPath": "lib/gdalplugins",
                  "caBundlePath": "%s"%s
                }
                """.formatted(bundleVersion, caBundlePath, cacheKeyJson);
    }

    private static <T> T withJavaTmpDir(Path tmpDir, ThrowingSupplier<T> supplier) throws Exception {
        String previous = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tmpDir.toAbsolutePath().toString());
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                System.clearProperty("java.io.tmpdir");
            } else {
                System.setProperty("java.io.tmpdir", previous);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
