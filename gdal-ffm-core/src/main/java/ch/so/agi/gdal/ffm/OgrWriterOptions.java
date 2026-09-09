package ch.so.agi.gdal.ffm;

/**
 * Key constants for legacy {@link OgrDataSource#openWriter(String, java.util.Map)} options.
 * <p>
 * 遗留 {@link OgrDataSource#openWriter} 写入选项的键常量，新代码请用 {@link OgrLayerWriteSpec}。
 */
public final class OgrWriterOptions {
    /**
     * Write mode value ({@link OgrWriteMode} name, case-insensitive).
     * <p>
     * 写模式取值（{@link OgrWriteMode} 名称，大小写不敏感）。
     */
    public static final String WRITE_MODE = "writeMode";

    private OgrWriterOptions() {
    }
}
