/*
 * MCDLSSG - Runtime library downloader
 * Third-party binaries (NVIDIA RTX SDK, Streamline, XeSS) are not bundled
 * for license compliance and are downloaded from official sources on demand.
 */

package com.dgtdi.mcdlssg.common.download;

import com.dgtdi.mcdlssg.common.MCDLSSG;
import com.dgtdi.mcdlssg.core.MCDLSSGConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class RuntimeLibraryDownloader {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static final String[] STREAMLINE_ZIP_ENTRIES = {
            "bin/x64/NvLowLatencyVk.dll",
            "bin/x64/sl.common.dll",
            "bin/x64/sl.dlss_g.dll",
            "bin/x64/sl.interposer.dll",
            "bin/x64/sl.pcl.dll",
            "bin/x64/sl.reflex.dll"
    };

    public enum Feature {
        DLSS_SR("NVIDIA DLSS 超分辨率运行库", List.of(
                new FileSpec("nvngx_dlss.dll",
                        "https://raw.githubusercontent.com/NVIDIA/DLSS/main/lib/Windows_x86_64/rel/nvngx_dlss.dll",
                        58_977_904L, false)
        )),
        XESS("Intel XeSS 运行库", List.of(
                new FileSpec("libxess.dll",
                        "https://raw.githubusercontent.com/intel/xess/main/bin/libxess.dll",
                        77_795_704L, false)
        )),
        DLSS_G("NVIDIA DLSS 帧生成运行库", List.of(
                new FileSpec("nvngx_dlssg.dll",
                        "https://raw.githubusercontent.com/NVIDIA/DLSS/main/lib/Windows_x86_64/rel/nvngx_dlssg.dll",
                        7_519_856L, false),
                new FileSpec("streamline-sdk-v2.12.0.zip",
                        "https://github.com/NVIDIA-RTX/Streamline/releases/download/v2.12.0/streamline-sdk-v2.12.0.zip",
                        231_958_617L, true)
        ));

        public final String displayName;
        public final List<FileSpec> files;

        Feature(String displayName, List<FileSpec> files) {
            this.displayName = displayName;
            this.files = files;
        }

        public long totalBytes() {
            return files.stream().mapToLong(f -> f.expectedSize).sum();
        }
    }

    public record FileSpec(String fileName, String url, long expectedSize, boolean zip) {
    }

    public interface ProgressListener {
        void onProgress(String fileName, long downloaded, long total);

        void onExtract(String entryName);
    }

    public static Path librariesDir() {
        return MCDLSSGConstants.NATIVE_LIBRARIES_DIR.getPath();
    }

    public static boolean isFeatureReady(Feature feature) {
        Path dir = librariesDir();
        for (FileSpec file : feature.files) {
            if (file.zip) {
                for (String entry : STREAMLINE_ZIP_ENTRIES) {
                    String name = entry.substring(entry.lastIndexOf('/') + 1);
                    if (!Files.isRegularFile(dir.resolve(name))) {
                        return false;
                    }
                }
            } else if (!Files.isRegularFile(dir.resolve(file.fileName))) {
                return false;
            }
        }
        return true;
    }

    public static void download(Feature feature, ProgressListener listener, Runnable onDone, java.util.function.Consumer<Throwable> onError) {
        Thread thread = new Thread(() -> {
            try {
                Path dir = librariesDir();
                Files.createDirectories(dir);
                for (FileSpec file : feature.files) {
                    Path temp = Files.createTempFile("mcdlssg-dl-", ".part");
                    try {
                        downloadFile(file, temp, listener);
                        if (file.zip) {
                            extractZip(temp, dir, listener);
                        } else {
                            Files.move(temp, dir.resolve(file.fileName), StandardCopyOption.REPLACE_EXISTING);
                        }
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                }
                MCDLSSG.LOGGER.info("Downloaded {} runtime libraries to {}", feature.displayName, dir);
                onDone.run();
            } catch (Throwable throwable) {
                MCDLSSG.LOGGER.error("Failed to download {} runtime libraries", feature.displayName, throwable);
                onError.accept(throwable);
            }
        }, "MCDLSSG-Downloader");
        thread.setDaemon(true);
        thread.start();
    }

    private static void downloadFile(FileSpec file, Path target, ProgressListener listener) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(file.url))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + file.url);
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(file.expectedSize);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[256 * 1024];
            long downloaded = 0;
            int read;
            long lastNotify = 0;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (downloaded - lastNotify >= 512 * 1024) {
                    listener.onProgress(file.fileName, downloaded, total);
                    lastNotify = downloaded;
                }
            }
            listener.onProgress(file.fileName, downloaded, total);
        }
    }

    private static void extractZip(Path zipPath, Path targetDir, ProgressListener listener) throws IOException {
        List<String> wanted = List.of(STREAMLINE_ZIP_ENTRIES);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (!wanted.contains(name)) {
                    continue;
                }
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                listener.onExtract(fileName);
                Files.copy(zip, targetDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                zip.closeEntry();
            }
        }
    }
}
