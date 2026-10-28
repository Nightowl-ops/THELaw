package com.veritasvault.service;

import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service managing physical filesystem operations for evidence files on disk.
 * Uses ReentrantLock to guard physical file I/O operations (such as tampering simulation
 * and file deletions) against concurrent thread collisions on the local filesystem,
 * where database SQL locks cannot operate.
 */
@Service
public class FileStorageService {

    private final Path rootStorageLocation;

    // Per-file ReentrantLock map ensuring thread-safe disk modifications on physical files
    private final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    public FileStorageService(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.rootStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootStorageLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize root storage directory at: " + this.rootStorageLocation, e);
        }
    }

    private ReentrantLock getLockForPath(String relativePath) {
        return fileLocks.computeIfAbsent(relativePath, path -> new ReentrantLock());
    }

    private void releaseLockForPath(String relativePath, ReentrantLock lock) {
        lock.unlock();
        if (!lock.hasQueuedThreads()) {
            fileLocks.remove(relativePath, lock);
        }
    }

    public String storeFile(MultipartFile file, String subfolder) {
        if (file.isEmpty()) {
            throw new BadRequestException("Cannot upload an empty file");
        }

        String rawOriginalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

        if (rawOriginalFilename.contains("..")) {
            throw new BadRequestException("Filename contains invalid path sequence: " + rawOriginalFilename);
        }

        String fileExtension = "";
        int extensionIndex = rawOriginalFilename.lastIndexOf('.');
        if (extensionIndex > 0) {
            fileExtension = rawOriginalFilename.substring(extensionIndex);
        }

        String uniqueFilename = UUID.randomUUID() + fileExtension;

        try {
            Path targetDirectory = this.rootStorageLocation.resolve(subfolder).normalize();
            Files.createDirectories(targetDirectory);

            Path targetPath = targetDirectory.resolve(uniqueFilename).normalize();

            if (!targetPath.startsWith(this.rootStorageLocation)) {
                throw new BadRequestException("Unauthorized storage path traversal attempt");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return Paths.get(subfolder).resolve(uniqueFilename).toString().replace("\\", "/");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file " + rawOriginalFilename, e);
        }
    }

    public Resource loadAsResource(String relativePath) {
        try {
            Path filePath = this.rootStorageLocation.resolve(relativePath).normalize();

            if (!filePath.startsWith(this.rootStorageLocation)) {
                throw new BadRequestException("Access denied: invalid file path");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("File not found or not readable: " + relativePath);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File path malformed: " + relativePath);
        }
    }

    public void deleteFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return;
        }

        ReentrantLock lock = getLockForPath(relativePath);
        lock.lock();
        try {
            Path filePath = this.rootStorageLocation.resolve(relativePath).normalize();
            if (filePath.startsWith(this.rootStorageLocation)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException ignored) {
            // Ignore deletion failures during cleanup
        } finally {
            releaseLockForPath(relativePath, lock);
        }
    }

    public void appendTamperByte(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return;
        }

        ReentrantLock lock = getLockForPath(relativePath);
        lock.lock();
        try {
            Path filePath = this.rootStorageLocation.resolve(relativePath).normalize();
            if (Files.exists(filePath) && filePath.startsWith(this.rootStorageLocation)) {
                Files.write(filePath, "\n[TAMPERED_BYTE_IN_LAB]".getBytes(), java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to simulate tampering on: " + relativePath, e);
        } finally {
            releaseLockForPath(relativePath, lock);
        }
    }
}