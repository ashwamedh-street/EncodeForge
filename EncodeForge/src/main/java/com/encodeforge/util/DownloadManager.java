package com.encodeforge.util;

import com.encodeforge.model.ProgressUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reusable download utility for dependencies and future app updates
 */
public class DownloadManager {
    private static final Logger logger = LoggerFactory.getLogger(DownloadManager.class);
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT = 30000; // 30 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds

    /**
     * Download a file from URL to destination with progress callback
     */
    public CompletableFuture<Path> downloadFile(String urlString, Path destination, Consumer<ProgressUpdate> callback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Downloading from: {}", urlString);
                logger.info("Destination: {}", destination);

                // Create parent directories if needed
                Files.createDirectories(destination.getParent());

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("User-Agent", "EncodeForge/0.4.0");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + responseCode);
                }

                long fileSize = connection.getContentLengthLong();
                logger.info("File size: {} bytes", fileSize);

                try (InputStream in = connection.getInputStream();
                     OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {

                    byte[] buffer = new byte[BUFFER_SIZE];
                    long downloaded = 0;
                    int bytesRead;
                    long lastUpdate = System.currentTimeMillis();

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloaded += bytesRead;

                        // Update progress every 500ms to avoid UI flooding
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 500 || downloaded == fileSize) {
                            if (callback != null && fileSize > 0) {
                                int progress = (int) ((downloaded * 100) / fileSize);
                                String message = String.format("Downloaded %s / %s",
                                        formatBytes(downloaded),
                                        formatBytes(fileSize));
                                callback.accept(new ProgressUpdate("downloading", progress, message, ""));
                            }
                            lastUpdate = now;
                        }
                    }
                }

                logger.info("Download completed: {}", destination);
                return destination;

            } catch (IOException e) {
                logger.error("Download failed", e);
                throw new RuntimeException("Failed to download file: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Verify file checksum (SHA-256)
     */
    public boolean verifyChecksum(Path file, String expectedChecksum) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (InputStream fis = Files.newInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            String actualChecksum = hexString.toString();
            boolean matches = actualChecksum.equalsIgnoreCase(expectedChecksum);
            
            if (!matches) {
                logger.warn("Checksum mismatch! Expected: {}, Actual: {}", expectedChecksum, actualChecksum);
            }
            
            return matches;

        } catch (Exception e) {
            logger.error("Failed to verify checksum", e);
            return false;
        }
    }

    /**
     * Extract archive (supports .zip, .tar.gz, .tar.xz)
     */
    public void extractArchive(Path archivePath, Path destination) throws IOException {
        String fileName = archivePath.getFileName().toString().toLowerCase();
        
        Files.createDirectories(destination);
        
        if (fileName.endsWith(".zip")) {
            extractZip(archivePath, destination);
        } else if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            extractTarGz(archivePath, destination);
        } else if (fileName.endsWith(".tar.xz")) {
            extractTarXz(archivePath, destination);
        } else {
            throw new IOException("Unsupported archive format: " + fileName);
        }
        
        logger.info("Extracted archive to: {}", destination);
    }

    /**
     * Extract ZIP archive
     */
    private void extractZip(Path zipPath, Path destination) throws IOException {
        logger.info("Extracting ZIP: {}", zipPath);
        
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = destination.resolve(entry.getName());
                
                // Security: Prevent zip slip vulnerability
                if (!targetPath.normalize().startsWith(destination.normalize())) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    
                    try (OutputStream fos = Files.newOutputStream(targetPath)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    // Set executable permissions for files that should be executable (Unix)
                    // This is a heuristic - files in bin/ directories or with no extension are likely executables
                    String fileName = targetPath.getFileName().toString();
                    Path parent = targetPath.getParent();
                    if (parent != null && (parent.endsWith("bin") || !fileName.contains("."))) {
                        targetPath.toFile().setExecutable(true);
                    }
                }
                
                zis.closeEntry();
            }
        }
    }

    /**
     * Extract TAR.GZ archive
     */
    private void extractTarGz(Path tarGzPath, Path destination) throws IOException {
        logger.info("Extracting TAR.GZ: {}", tarGzPath);
        
        // Use Apache Commons Compress or external tar command
        // For now, throw unsupported exception - can implement later if needed
        throw new UnsupportedOperationException("TAR.GZ extraction not yet implemented. Please use ZIP archives.");
    }

    /**
     * Extract TAR.XZ archive
     */
    private void extractTarXz(Path tarXzPath, Path destination) throws IOException {
        logger.info("Extracting TAR.XZ: {}", tarXzPath);
        
        // Use Apache Commons Compress or external tar command
        // For now, throw unsupported exception - can implement later if needed
        throw new UnsupportedOperationException("TAR.XZ extraction not yet implemented. Please use ZIP archives.");
    }

    /**
     * Format bytes to human-readable string
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

