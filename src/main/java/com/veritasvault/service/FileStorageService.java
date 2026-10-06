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


@Service
public class FileStorageService {

    private final Path rootStorageLocation;

    public FileStorageService(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.rootStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootStorageLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize root storage directory at: " + this.rootStorageLocation, e);
        }
    }

    /*
      Stores an evidence file under a designated subfolder (e.g., "evidence/case-101").

      @param file      the multipart payload
     @param subfolder relative folder category or case directory
      @return the relative normalized storage path of the stored file
     */
    public String storeFile(MultipartFile file, String subfolder) {
        if (file.isEmpty()) {
            throw new BadRequestException("Cannot upload an empty file");
        }

        String rawOriginalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

        // Guard against Path Traversal vulnerabilities
        if (rawOriginalFilename.contains("..")) {
            throw new BadRequestException("Filename contains invalid path sequence: " + rawOriginalFilename);
        }

        // Clean extension and generate a conflict-free unique file name
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

            // Double check target path is strictly within the allowed root directory
            if (!targetPath.startsWith(this.rootStorageLocation)) {
                throw new BadRequestException("Unauthorized storage path traversal attempt");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Return relative path so database stores a portable reference
            return Paths.get(subfolder).resolve(uniqueFilename).toString().replace("\\", "/");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file " + rawOriginalFilename, e);
        }
    }

    /**
     * Loads a file from disk as a readable Spring Resource for download or streaming.
     *
     * @param relativePath the relative storage path saved in the entity
     * @return loaded UrlResource
     */
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

    /**
     * Deletes a file from storage if present.
     *
     * @param relativePath the relative storage path
     */
    public void deleteFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return;
        }

        try {
            Path filePath = this.rootStorageLocation.resolve(relativePath).normalize();
            if (filePath.startsWith(this.rootStorageLocation)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException ignored) {
            // Ignore deletion failures during cleanup
        }
    }
}