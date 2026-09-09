package ch.so.agi.gdal.ffm;

/**
 * Key constants for {@link Ogr#open(java.nio.file.Path, java.util.Map)} open options.
 * <p>
 * {@link Ogr#open} 打开选项的键常量。
 */
public final class OgrOpenOptions {
    /**
     * Comma/semicolon-separated list of driver names that may open the dataset.
     * <p>
     * 允许打开该数据集的驱动名列表（逗号/分号分隔），用于限定驱动。
     */
    public static final String ALLOWED_DRIVERS = "allowedDrivers";

    private OgrOpenOptions() {
    }
}
