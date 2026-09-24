package io.github.tsndre.minijava.common.upload;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class Uploader {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");

    private final Path baseDir;
    private final long maxSize;

    public Uploader(String baseDir, long maxSize) {
        this.baseDir = Path.of(baseDir);
        this.maxSize = maxSize;
    }

    /**
     * Saves the file under {@code subDir} and returns its path relative to the base directory,
     * e.g. {@code products/0b9f....png}.
     */
    public String upload(MultipartFile file, String subDir) throws UploadException {
        if (file.getSize() > maxSize) {
            throw new UploadException("file size exceeds maximum allowed size of " + maxSize + " bytes");
        }

        String ext = extension(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new UploadException("file extension " + ext + " is not allowed");
        }

        Path dir = baseDir.resolve(subDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UploadException("failed to create upload directory: " + e.getMessage());
        }

        String filename = UUID.randomUUID() + ext;
        Path target = dir.resolve(filename);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        } catch (IOException e) {
            deleteQuietly(target);
            throw new UploadException("failed to write file: " + e.getMessage());
        }

        return subDir + "/" + filename;
    }

    /** Deletes a previously uploaded file; a file that is already gone is not an error. */
    public void delete(String relativePath) throws IOException {
        Files.deleteIfExists(baseDir.resolve(relativePath));
    }

    /** The extension of the last path element, including the dot, or "" when there is none. */
    static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        String name = filename.substring(filename.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort: the write already failed and is being reported.
        }
    }
}
