package ch.so.agi.gdal.ffm.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsNativeLibraryPathSupportTest {
    @Test
    void nonWindowsShouldBeNoOp() {
        RecordingKernel32Access kernel32 = new RecordingKernel32Access(true, true, true);

        WindowsNativeLibraryPathSupport.configureIfNeeded(
                NativePlatform.from("Mac OS X", "aarch64"),
                Path.of("/tmp/nonexistent"),
                new WindowsNativeLibraryPathSupport.RegistrationState(),
                kernel32
        );

        assertEquals(0, kernel32.setDefaultCalls);
        assertEquals(0, kernel32.addCalls);
        assertEquals(0, kernel32.setDllDirectoryCalls);
    }

    @Test
    void windowsShouldRegisterBinDirectoryOncePerBundle() throws Exception {
        Path extractionRoot = Files.createTempDirectory("windows-dll-register");
        Files.createDirectories(extractionRoot.resolve("bin"));
        RecordingKernel32Access kernel32 = new RecordingKernel32Access(true, true, true);
        WindowsNativeLibraryPathSupport.RegistrationState state =
                new WindowsNativeLibraryPathSupport.RegistrationState();

        WindowsNativeLibraryPathSupport.configureIfNeeded(
                NativePlatform.from("Windows 11", "amd64"),
                extractionRoot,
                state,
                kernel32
        );
        WindowsNativeLibraryPathSupport.configureIfNeeded(
                NativePlatform.from("Windows 11", "amd64"),
                extractionRoot,
                state,
                kernel32
        );

        Path expected = extractionRoot.resolve("bin").toAbsolutePath().normalize();
        assertEquals(1, kernel32.setDefaultCalls);
        assertEquals(1, kernel32.addCalls);
        assertEquals(expected, kernel32.lastAddDllDirectoryPath);
        assertEquals(0, kernel32.setDllDirectoryCalls);
        assertTrue(state.registeredPaths().contains(expected));
    }

    @Test
    void windowsShouldFallbackToSetDllDirectoryWhenAdvancedApiUnavailable() throws Exception {
        Path extractionRoot = Files.createTempDirectory("windows-dll-fallback");
        Files.createDirectories(extractionRoot.resolve("bin"));
        RecordingKernel32Access kernel32 = new RecordingKernel32Access(false, true, true);

        WindowsNativeLibraryPathSupport.configureIfNeeded(
                NativePlatform.from("Windows Server 2025", "amd64"),
                extractionRoot,
                new WindowsNativeLibraryPathSupport.RegistrationState(),
                kernel32
        );

        Path expected = extractionRoot.resolve("bin").toAbsolutePath().normalize();
        assertEquals(0, kernel32.setDefaultCalls);
        assertEquals(0, kernel32.addCalls);
        assertEquals(1, kernel32.setDllDirectoryCalls);
        assertEquals(expected, kernel32.lastSetDllDirectoryPath);
    }

    @Test
    void windowsShouldThrowExplicitErrorWhenRegistrationFails() throws Exception {
        Path extractionRoot = Files.createTempDirectory("windows-dll-error");
        Files.createDirectories(extractionRoot.resolve("bin"));
        RecordingKernel32Access kernel32 = new RecordingKernel32Access(true, false, false);
        kernel32.lastError = 126;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> WindowsNativeLibraryPathSupport.configureIfNeeded(
                        NativePlatform.from("Windows 11", "amd64"),
                        extractionRoot,
                        new WindowsNativeLibraryPathSupport.RegistrationState(),
                        kernel32
                )
        );

        assertTrue(error.getMessage().contains("Failed to register Windows DLL search path"));
        assertTrue(error.getMessage().contains("windows-x86_64"));
        assertTrue(error.getMessage().contains("GetLastError=126"));
        assertTrue(error.getMessage().contains(extractionRoot.resolve("bin").toAbsolutePath().normalize().toString()));
    }

    private static final class RecordingKernel32Access implements WindowsNativeLibraryPathSupport.Kernel32Access {
        private final boolean supportsAddDllDirectory;
        private final boolean addDllDirectorySuccess;
        private final boolean setDllDirectorySuccess;
        private int setDefaultCalls;
        private int addCalls;
        private int setDllDirectoryCalls;
        private int lastError = 5;
        private Path lastAddDllDirectoryPath;
        private Path lastSetDllDirectoryPath;

        private RecordingKernel32Access(
                boolean supportsAddDllDirectory,
                boolean addDllDirectorySuccess,
                boolean setDllDirectorySuccess
        ) {
            this.supportsAddDllDirectory = supportsAddDllDirectory;
            this.addDllDirectorySuccess = addDllDirectorySuccess;
            this.setDllDirectorySuccess = setDllDirectorySuccess;
        }

        @Override
        public boolean supportsAddDllDirectory() {
            return supportsAddDllDirectory;
        }

        @Override
        public boolean setDefaultDllDirectories(int flags) {
            setDefaultCalls++;
            return supportsAddDllDirectory;
        }

        @Override
        public boolean addDllDirectory(Path dllDirectory) {
            addCalls++;
            lastAddDllDirectoryPath = dllDirectory;
            return addDllDirectorySuccess;
        }

        @Override
        public boolean setDllDirectory(Path dllDirectory) {
            setDllDirectoryCalls++;
            lastSetDllDirectoryPath = dllDirectory;
            return setDllDirectorySuccess;
        }

        @Override
        public int getLastError() {
            return lastError;
        }
    }
}
