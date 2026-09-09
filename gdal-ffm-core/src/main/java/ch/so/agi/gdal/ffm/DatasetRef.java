package ch.so.agi.gdal.ffm;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Addressing of a GDAL dataset: local path, HTTP(S) URL, explicit GDAL/VSI path or
 * verbatim pass-through identifier.
 * <p>
 * GDAL 数据集寻址：本地路径、HTTP(S) 地址、显式 GDAL/VSI 路径（如 {@code /vsizip/...}）
 * 或原样透传的标识（如 {@code PG:"..."} 驱动连接串）。
 *
 * @param type dataset reference type, must not be {@code null} / 数据集引用类型，不能为 {@code null}
 * @param identifier normalized identifier (absolute local path, URL or VSI string) /
 *                   归一化标识（本地绝对路径、URL 或 VSI 字符串）
 */
public record DatasetRef(DatasetRefType type, String identifier) {
    private static final String VSICURL_PREFIX = "/vsicurl/";

    /**
     * Canonical constructor with normalization and validation.
     * <p>
     * 规范构造器，做归一化与校验（本地路径转绝对路径、HTTP 校验 scheme、VSI 校验前缀）。
     *
     * @param type dataset reference type / 数据集引用类型
     * @param identifier raw identifier, blank values are rejected / 原始标识，空白不合法
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the identifier is blank or malformed / 标识为空或格式非法时抛出
     */
    public DatasetRef {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(identifier, "identifier must not be null");

        identifier = identifier.trim();
        if (identifier.isEmpty()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }

        switch (type) {
            case LOCAL_PATH -> identifier = Path.of(identifier).toAbsolutePath().normalize().toString();
            case HTTP_URL -> validateHttpUrl(identifier);
            case GDAL_VSI -> validateVsi(identifier);
            case RAW -> {
                // pass-through: identifier is used verbatim by GDAL / 直通：标识原样交给 GDAL
            }
            default -> throw new IllegalStateException("Unhandled dataset ref type: " + type);
        }
    }

    /**
     * Creates a reference to a local file.
     * <p>
     * 创建本地文件引用（自动转绝对路径并归一化）。
     *
     * @param path local path, must not be {@code null} / 本地路径，不能为 {@code null}
     * @return dataset reference, never {@code null} / 数据集引用，不会为 {@code null}
     * @throws NullPointerException if {@code path} is {@code null} / 参数为 {@code null} 时抛出
     */
    public static DatasetRef local(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        return new DatasetRef(DatasetRefType.LOCAL_PATH, path.toAbsolutePath().normalize().toString());
    }

    /**
     * Creates a reference to a local file given as string.
     * <p>
     * 由字符串路径创建本地文件引用。
     *
     * @param path local path string, must not be {@code null} / 本地路径字符串，不能为 {@code null}
     * @return dataset reference, never {@code null} / 数据集引用，不会为 {@code null}
     * @throws NullPointerException if {@code path} is {@code null} / 参数为 {@code null} 时抛出
     */
    public static DatasetRef local(String path) {
        Objects.requireNonNull(path, "path must not be null");
        return local(Path.of(path));
    }

    /**
     * Creates a reference to an HTTP(S) dataset (mapped to {@code /vsicurl/}).
     * <p>
     * 创建 HTTP(S) 数据集引用（底层映射为 {@code /vsicurl/}）。
     *
     * @param url http/https URL, must not be {@code null} / http/https 地址，不能为 {@code null}
     * @return dataset reference, never {@code null} / 数据集引用，不会为 {@code null}
     * @throws NullPointerException if {@code url} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the scheme is not http/https / 非 http/https 时抛出
     */
    public static DatasetRef httpUrl(String url) {
        return new DatasetRef(DatasetRefType.HTTP_URL, url);
    }

    /**
     * Creates a reference to an explicit GDAL/VSI path.
     * <p>
     * 创建显式 GDAL/VSI 路径引用，必须以 {@code /vsi} 开头。
     *
     * @param identifier VSI identifier, e.g. {@code "/vsizip/a.zip/a.tif"}, must not be {@code null} /
     *                   VSI 标识，例如 {@code "/vsizip/a.zip/a.tif"}，不能为 {@code null}
     * @return dataset reference, never {@code null} / 数据集引用，不会为 {@code null}
     * @throws NullPointerException if {@code identifier} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if it does not start with {@code /vsi} / 非 {@code /vsi} 开头时抛出
     */
    public static DatasetRef gdalVsi(String identifier) {
        return new DatasetRef(DatasetRefType.GDAL_VSI, identifier);
    }

    /**
     * Creates a pass-through reference for a verbatim GDAL dataset identifier, e.g. a driver
     * connection string such as {@code PG:"host=... dbname=..."}. No format validation is
     * performed; only {@code null} and blank values are rejected.
     * <p>
     * 创建直通引用：标识原样传给 GDAL（如驱动连接串 {@code PG:"host=... dbname=..."}）。
     * 不做格式校验，仅拒绝 {@code null} 与空白值。
     *
     * @param identifier verbatim GDAL identifier, must not be {@code null} or blank /
     *                   原样 GDAL 标识，不能为 {@code null} 或空白
     * @return dataset reference, never {@code null} / 数据集引用，永不为 {@code null}
     * @throws NullPointerException if {@code identifier} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code identifier} is blank / 参数为空白时抛出
     */
    public static DatasetRef raw(String identifier) {
        return new DatasetRef(DatasetRefType.RAW, identifier);
    }

    /**
     * Checks whether this reference is a local path.
     * <p>
     * 是否为本地路径引用。
     *
     * @return {@code true} for {@link DatasetRefType#LOCAL_PATH} / 本地路径时返回 {@code true}
     */
    public boolean isLocalPath() {
        return type == DatasetRefType.LOCAL_PATH;
    }

    /**
     * Returns the local path of this reference.
     * <p>
     * 返回本地路径；非本地引用调用会抛异常。
     *
     * @return local path, never {@code null} / 本地路径，不会为 {@code null}
     * @throws IllegalStateException if this reference is not a local path / 非本地引用时抛出
     */
    public Path localPath() {
        if (!isLocalPath()) {
            throw new IllegalStateException("DatasetRef is not a local path: " + type);
        }
        return Path.of(identifier);
    }

    /**
     * Returns the identifier string consumable by GDAL natives.
     * <p>
     * 返回可直接传给 GDAL 本地的标识字符串（HTTP 会加 {@code /vsicurl/} 前缀）。
     *
     * @return GDAL identifier, never {@code null} / GDAL 标识，不会为 {@code null}
     */
    public String toGdalIdentifier() {
        return switch (type) {
            case LOCAL_PATH -> identifier;
            case HTTP_URL -> VSICURL_PREFIX + identifier;
            case GDAL_VSI -> identifier;
            case RAW -> identifier;
        };
    }

    private static void validateHttpUrl(String identifier) {
        try {
            URI uri = new URI(identifier);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("HTTP dataset references must use http or https: " + identifier);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid HTTP dataset reference: " + identifier, e);
        }
    }

    private static void validateVsi(String identifier) {
        if (!identifier.startsWith("/vsi")) {
            throw new IllegalArgumentException("Explicit GDAL/VSI paths must start with /vsi: " + identifier);
        }
    }
}
