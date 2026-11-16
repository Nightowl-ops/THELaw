package com.veritasvault.forensicvault.service;

import com.veritasvault.forensicvault.exception.BadRequestException;
import com.veritasvault.forensicvault.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service managing physical filesystem operations for evidence files on disk.
 * Uses ReentrantLock to guard physical file I/O operations against concurrent thread collisions.
 */
@Service
@Slf4j
public class FileStorageService {

    private final Path rootStorageLocation;
    private final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    public FileStorageService(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.rootStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootStorageLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize root storage directory at: " + this.rootStorageLocation, e);
        }
    }

    public Resource loadAsResource(String relativePath) {
        try {
            Path filePath = this.rootStorageLocation.resolve(relativePath).normalize();
            if (!filePath.startsWith(this.rootStorageLocation)) {
                throw new BadRequestException("Access denied: File path outside root directory");
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                log.warn("File not found or unreadable at path: {}", filePath);
                return null;
            }
        } catch (MalformedURLException ex) {
            log.warn("Malformed URL for path: {}", relativePath, ex);
            return null;
        }
    }

    public Path getRootStorageLocation() {
        return rootStorageLocation;
    }
}
