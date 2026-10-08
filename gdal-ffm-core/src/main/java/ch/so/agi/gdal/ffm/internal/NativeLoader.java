package ch.so.agi.gdal.ffm.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads the bundled GDAL native libraries exactly once per JVM.
 * <p>
 * 每个 JVM 仅加载一次随包发布的 GDAL 本地库，负责按当前平台查找 manifest、解压本地 bundle 并按顺序加载本地库。此为内部 API（internal, not public），请勿在业务代码中直接使用。
 */
final class NativeLoader {
    /**
     * Classpath root under which per-platform native bundles are published.
     * <p>
     * 各平台本地 bundle 在 classpath 下的发布根目录。
     */
    private static final String RESOURCE_ROOT = "META-INF/gdal-native";
    /**
     * Cached bundle info once loading has completed.
     * <p>
     * 加载完成后缓存的 bundle 信息。
     */
    private static final AtomicReference<NativeBundleInfo> LOADED = new AtomicReference<>();
    /**
     * Guards one-time native loading.
     * <p>
     * 保护本地库一次性加载的锁。
     */
    private static final Object LOCK = new Object();

    /**
     * Prevents instantiation; all members are static.
     * <p>
     * 禁止实例化，所有成员均为静态。
     */
    private NativeLoader() {
    }

    /**
     * Returns the loaded native bundle, loading it on first call.
     * <p>
     * 返回已加载的本地 bundle，首次调用时执行加载；线程安全且仅加载一次。
     *
     * @return the loaded bundle info, never {@code null} / 已加载的 bundle 信息，永不为 {@code null}
     * @throws IllegalStateException if no matching bundle is found or loading fails / 找不到匹配 bundle 或加载失败时抛出
     */
    static NativeBundleInfo load() {
        NativeBundleInfo existing = LOADED.get();
        if (existing != null) {
            return existing;
        }

        synchronized (LOCK) {
            existing = LOADED.get();
            if (existing != null) {
                return existing;
            }
            NativeBundleInfo loaded = loadOnce();
            LOADED.set(loaded);
            return loaded;
        }
    }

    /**
     * Detects the platform, extracts the bundle and loads libraries in manifest order.
     * <p>
     * 检测当前平台、解压 bundle，并按 manifest 顺序先加载预加载库再加载入口库。
     *
     * @return the loaded bundle info, never {@code null} / 已加载的 bundle 信息，永不为 {@code null}
     * @throws IllegalStateException if the manifest is missing or any library fails to load / manifest 缺失或任一本地库加载失败时抛出
     */
    private static NativeBundleInfo loadOnce() {
        NativePlatform platform = NativePlatform.current();
        String classifier = platform.classifier();
        String prefix = RESOURCE_ROOT + "/" + classifier;

        URL manifestUrl = findManifest(prefix, classifier);
        NativeManifest manifest = NativeManifest.parse(readUtf8(manifestUrl));
        NativeBundleInfo bundleInfo = resolveBundleInfo(manifestUrl, manifest, classifier);
        WindowsNativeLibraryPathSupport.configureIfNeeded(platform, bundleInfo.extractionRoot());

        for (String preload : manifest.preloadLibraries()) {
            loadLibrary(bundleInfo.extractionRoot(), preload, "preloadLibraries");
        }
        loadLibrary(bundleInfo.extractionRoot(), manifest.entryLibrary(), "entryLibrary");
        return bundleInfo;
    }

    /**
     * Builds bundle info for the given manifest without loading any library.
     * <p>
     * 根据给定 manifest 构建 bundle 信息，仅解析路径，不加载任何本地库。
     *
     * @param manifestUrl URL of {@code manifest.json}, must not be {@code null} / {@code manifest.json} 的 URL，不能为 {@code null}
     * @param manifest    parsed manifest, must not be {@code null} / 已解析的 manifest，不能为 {@code null}
     * @param classifier  platform classifier such as {@code linux-x86_64}, must not be {@code null} / 平台分类串，例如 {@code linux-x86_64}，不能为 {@code null}
     * @return resolved bundle info, never {@code null} / 解析后的 bundle 信息，永不为 {@code null}
     */
    static NativeBundleInfo resolveBundleInfo(URL manifestUrl, NativeManifest manifest, String classifier) {
        Path extractionRoot = resolveBundleRoot(manifestUrl, extractionIdentity(manifest), classifier);
        return new NativeBundleInfo(
                classifier,
                manifest.bundleVersion(),
                extractionRoot,
                resolveOptional(extractionRoot, manifest.gdalDataPath()),
                resolveOptional(extractionRoot, manifest.projDataPath()),
                resolveOptional(extractionRoot, manifest.driverPath()),
                resolveOptional(extractionRoot, manifest.caBundlePath())
        );
    }

    /**
     * Returns the directory identity used for bundle extraction.
     * <p>
     * 返回 bundle 解压目录所用的标识，优先使用 manifest 的 cacheKey，回退为 bundle 版本。
     *
     * @param manifest parsed manifest, must not be {@code null} / 已解析的 manifest，不能为 {@code null}
     * @return extraction identity, never {@code null} / 解压标识，永不为 {@code null}
     */
    private static String extractionIdentity(NativeManifest manifest) {
        String cacheKey = manifest.cacheKey();
        if (cacheKey != null && !cacheKey.isBlank()) {
            return cacheKey;
        }
        return manifest.bundleVersion();
    }

    /**
     * Finds the single {@code manifest.json} for the given classifier on the classpath.
     * <p>
     * 在 classpath 上查找指定平台分类串唯一的 {@code manifest.json}。
     *
     * @param prefix     classpath prefix of the platform bundle, must not be {@code null} / 平台 bundle 的 classpath 前缀，不能为 {@code null}
     * @param classifier platform classifier such as {@code linux-x86_64}, must not be {@code null} / 平台分类串，不能为 {@code null}
     * @return manifest URL, never {@code null} / manifest 的 URL，永不为 {@code null}
     * @throws IllegalStateException if zero or multiple manifests are found, or scanning fails / 找不到、找到多个 manifest 或扫描失败时抛出
     */
    private static URL findManifest(String prefix, String classifier) {
        String manifestResource = prefix + "/manifest.json";
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = NativeLoader.class.getClassLoader();
            }
            Enumeration<URL> urls = classLoader.getResources(manifestResource);
            if (!urls.hasMoreElements()) {
                throw new IllegalStateException(
                        "No bundled GDAL native resources found for classifier '" + classifier + "'. "
                                + "Add runtime dependency ch.so.agi:gdal-ffm-natives:<VERSION>:natives-"
                                + classifier
                );
            }

            URL first = urls.nextElement();
            if (urls.hasMoreElements()) {
                StringBuilder matches = new StringBuilder(first.toString());
                while (urls.hasMoreElements()) {
                    matches.append(", ").append(urls.nextElement());
                }
                throw new IllegalStateException(
                        "Multiple bundled GDAL native resources found for classifier '" + classifier + "': "
                                + matches
                                + ". Add exactly one runtime dependency, either "
                                + "ch.so.agi:gdal-ffm-natives:<VERSION>:natives-" + classifier
                                + ", ch.so.agi:gdal-ffm-natives-swiss:<VERSION>:natives-" + classifier
                                + " or ch.so.agi:gdal-ffm-natives-cn:<VERSION>:natives-" + classifier
                );
            }
            return first;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan classpath for " + manifestResource, e);
        }
    }

    /**
     * Reads the full text of the given URL as UTF-8.
     * <p>
     * 以 UTF-8 读取给定 URL 的全部文本内容。
     *
     * @param url source URL, must not be {@code null} / 源 URL，不能为 {@code null}
     * @return file content, never {@code null} / 文件内容，永不为 {@code null}
     * @throws IllegalStateException if reading fails / 读取失败时抛出
     */
    private static String readUtf8(URL url) {
        try (InputStream inputStream = url.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read native manifest: " + url, e);
        }
    }

    /**
     * Returns the extraction directory for the given identity and classifier.
     * <p>
     * 返回指定标识与平台分类串对应的解压目录（位于系统临时目录下）。
     *
     * @param extractionIdentity extraction identity, must not be {@code null} / 解压标识，不能为 {@code null}
     * @param classifier         platform classifier, must not be {@code null} / 平台分类串，不能为 {@code null}
     * @return extraction directory path, never {@code null} / 解压目录路径，永不为 {@code null}
     */
    private static Path extractionRoot(String extractionIdentity, String classifier) {
        String javaTmp = System.getProperty("java.io.tmpdir");
        return Path.of(javaTmp, "gdal-ffm", extractionIdentity, classifier);
    }

    /**
     * Resolves the usable bundle root, extracting from the classpath when needed.
     * <p>
     * 解析可用的 bundle 根目录；file 协议直接使用源码树，否则按需从 classpath 解压（带完成标记避免重复解压）。
     *
     * @param manifestUrl        URL of {@code manifest.json}, must not be {@code null} / {@code manifest.json} 的 URL，不能为 {@code null}
     * @param extractionIdentity extraction identity, must not be {@code null} / 解压标识，不能为 {@code null}
     * @param classifier         platform classifier, must not be {@code null} / 平台分类串，不能为 {@code null}
     * @return bundle root directory, never {@code null} / bundle 根目录，永不为 {@code null}
     * @throws IllegalStateException if extraction fails / 解压失败时抛出
     */
    private static Path resolveBundleRoot(URL manifestUrl, String extractionIdentity, String classifier) {
        if ("file".equals(manifestUrl.getProtocol())) {
            return fileTreeRoot(manifestUrl);
        }

        Path extractionRoot = extractionRoot(extractionIdentity, classifier);
        Path marker = extractionRoot.resolve(".extract-complete");
        if (!Files.exists(marker)) {
            extractBundle(manifestUrl, extractionRoot);
            try {
                Files.createDirectories(extractionRoot);
                Files.writeString(marker, extractionIdentity, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create extraction marker: " + marker, e);
            }
        }
        return extractionRoot;
    }

    /**
     * Resolves the source directory containing the given file-based manifest.
     * <p>
     * 解析 file 协议 manifest 所在的源码目录。
     *
     * @param manifestUrl file-based manifest URL, must not be {@code null} / file 协议的 manifest URL，不能为 {@code null}
     * @return source root directory, never {@code null} / 源码根目录，永不为 {@code null}
     * @throws IllegalStateException if the URL is invalid or the path does not exist / URL 非法或路径不存在时抛出
     */
    private static Path fileTreeRoot(URL manifestUrl) {
        Path manifestPath;
        try {
            manifestPath = Path.of(manifestUrl.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid manifest URL: " + manifestUrl, e);
        }

        Path sourceRoot = manifestPath.getParent();
        if (sourceRoot == null || !Files.exists(sourceRoot)) {
            throw new IllegalStateException("Native resource source path does not exist: " + manifestPath);
        }
        return sourceRoot;
    }

    /**
     * Extracts the native bundle addressed by the manifest URL into the target directory.
     * <p>
     * 将 manifest URL 指向的本地 bundle 解压到目标目录，仅支持 jar 与 file 协议。
     *
     * @param manifestUrl    URL of {@code manifest.json}, must not be {@code null} / {@code manifest.json} 的 URL，不能为 {@code null}
     * @param extractionRoot target directory, must not be {@code null} / 目标解压目录，不能为 {@code null}
     * @throws IllegalStateException if the protocol is unsupported or extraction fails / 协议不支持或解压失败时抛出
     */
    private static void extractBundle(URL manifestUrl, Path extractionRoot) {
        try {
            Files.createDirectories(extractionRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create extraction directory: " + extractionRoot, e);
        }

        String protocol = manifestUrl.getProtocol();
        if ("jar".equals(protocol)) {
            extractFromJar(manifestUrl, extractionRoot);
            return;
        }
        if ("file".equals(protocol)) {
            extractFromFileTree(manifestUrl, extractionRoot);
            return;
        }
        throw new IllegalStateException("Unsupported manifest URL protocol for native extraction: " + protocol);
    }

    /**
     * Extracts the platform bundle entries from the enclosing JAR file.
     * <p>
     * 从 manifest 所在 JAR 包中解压当前平台 bundle 的全部条目。
     *
     * @param manifestUrl    manifest URL with jar protocol, must not be {@code null} / jar 协议的 manifest URL，不能为 {@code null}
     * @param extractionRoot target directory, must not be {@code null} / 目标解压目录，不能为 {@code null}
     * @throws IllegalStateException if extraction fails / 解压失败时抛出
     */
    private static void extractFromJar(URL manifestUrl, Path extractionRoot) {
        try {
            JarURLConnection connection = (JarURLConnection) manifestUrl.openConnection();
            connection.setUseCaches(false);

            String entryName = connection.getEntryName();
            String prefix = entryName.substring(0, entryName.length() - "manifest.json".length());

            try (JarFile jarFile = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                        continue;
                    }

                    String relativeName = entry.getName().substring(prefix.length());
                    if (relativeName.isBlank()) {
                        continue;
                    }

                    Path target = safeResolve(extractionRoot, relativeName);
                    Files.createDirectories(target.getParent());
                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract native bundle from JAR: " + manifestUrl, e);
        }
    }

    /**
     * Copies the native bundle from a file-system source tree.
     * <p>
     * 从文件系统源码树复制本地 bundle（主要用于本地开发与测试场景）。
     *
     * @param manifestUrl    manifest URL with file protocol, must not be {@code null} / file 协议的 manifest URL，不能为 {@code null}
     * @param extractionRoot target directory, must not be {@code null} / 目标解压目录，不能为 {@code null}
     * @throws IllegalStateException if traversal or copying fails / 遍历或复制失败时抛出
     */
    private static void extractFromFileTree(URL manifestUrl, Path extractionRoot) {
        Path sourceRoot = fileTreeRoot(manifestUrl);

        try (var stream = Files.walk(sourceRoot)) {
            stream.filter(Files::isRegularFile).forEach(source -> {
                Path relative = sourceRoot.relativize(source);
                Path target = safeResolve(extractionRoot, relative.toString());
                try {
                    Files.createDirectories(Objects.requireNonNull(target.getParent()));
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to copy native resource " + source + " to " + target, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to traverse native resource tree: " + sourceRoot, e);
        }
    }

    /**
     * Loads one native library from the extraction root via {@code System.load}.
     * <p>
     * 通过 {@code System.load} 从解压根目录加载单个本地库；若已被其他类加载器加载则忽略。
     *
     * @param extractionRoot bundle root directory, must not be {@code null} / bundle 根目录，不能为 {@code null}
     * @param relativePath   library path relative to the root, must not be {@code null} / 相对根目录的库路径，不能为 {@code null}
     * @param sourceField    manifest field name used in error messages, must not be {@code null} / 出错信息中引用的 manifest 字段名，不能为 {@code null}
     * @throws IllegalStateException if the library file is missing / 库文件缺失时抛出
     * @throws UnsatisfiedLinkError  if the native load fails / 本地加载失败时抛出
     */
    private static void loadLibrary(Path extractionRoot, String relativePath, String sourceField) {
        Path libPath = safeResolve(extractionRoot, relativePath);
        if (!Files.exists(libPath)) {
            throw new IllegalStateException(
                    "Native library listed in " + sourceField + " is missing: " + relativePath
            );
        }
        try {
            System.load(libPath.toString());
        } catch (UnsatisfiedLinkError e) {
            if (isAlreadyLoadedByAnotherClassLoader(e, libPath)) {
                return;
            }
            throw e;
        }
    }

    /**
     * Checks whether the link error only reports a duplicate load from another class loader.
     * <p>
     * 判断链接错误是否仅表示该库已被另一个类加载器加载（此种情况可安全忽略）。
     *
     * @param error   the link error, must not be {@code null} / 链接错误，不能为 {@code null}
     * @param libPath the library path that was loaded, must not be {@code null} / 尝试加载的库路径，不能为 {@code null}
     * @return {@code true} if the library is already loaded elsewhere / 若库已在别处加载则返回 {@code true}
     */
    private static boolean isAlreadyLoadedByAnotherClassLoader(UnsatisfiedLinkError error, Path libPath) {
        String message = error.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("already loaded in another classloader")
                && (message.contains(libPath.toString()) || message.contains(libPath.getFileName().toString()));
    }

    /**
     * Resolves an optional manifest path that may be absent.
     * <p>
     * 解析 manifest 中的可选路径；为空或文件不存在时返回 {@code null}。
     *
     * @param extractionRoot bundle root directory, must not be {@code null} / bundle 根目录，不能为 {@code null}
     * @param relativePath   relative path, may be {@code null} / 相对路径，可为 {@code null}
     * @return resolved path, or {@code null} if absent / 解析后的路径，缺失时为 {@code null}
     */
    private static Path resolveOptional(Path extractionRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path path = safeResolve(extractionRoot, relativePath);
        return Files.exists(path) ? path : null;
    }

    /**
     * Resolves a manifest-provided relative path while blocking path traversal.
     * <p>
     * 解析 manifest 给出的相对路径并拦截目录穿越，保证结果仍在根目录之内。
     *
     * @param base     base directory, must not be {@code null} / 基准目录，不能为 {@code null}
     * @param relative relative path from the manifest, must not be {@code null} / manifest 中的相对路径，不能为 {@code null}
     * @return normalized resolved path, never {@code null} / 规范化后的解析路径，永不为 {@code null}
     * @throws IllegalStateException if the path escapes the base directory / 路径逃逸出基准目录时抛出
     */
    private static Path safeResolve(Path base, String relative) {
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalStateException("Invalid path in native manifest: " + relative);
        }
        return resolved;
    }
}
