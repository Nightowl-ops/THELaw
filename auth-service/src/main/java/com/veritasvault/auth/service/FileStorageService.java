package com.veritasvault.auth.service;

import com.veritasvault.auth.exception.BadRequestException;
import com.veritasvault.auth.exception.ResourceNotFoundException;
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

    public String storeFile(MultipartFile file, String subfolder) {
        if (file.isEmpty()) {
            throw new BadRequestException("Cannot upload an empty file");
        }

        String originalFilename = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        if (originalFilename.contains("..")) {
            throw new BadRequestException("Filename contains invalid path sequence: " + originalFilename);
        }

        String fileExtension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            fileExtension = originalFilename.substring(dotIndex);
        }

        String storedFilename = UUID.randomUUID() + fileExtension;

        try {
            Path targetDirectory = this.rootStorageLocation.resolve(subfolder).normalize();
            Files.createDirectories(targetDirectory);

            Path targetPath = targetDirectory.resolve(storedFilename);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return subfolder + "/" + storedFilename;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file: " + originalFilename, e);
        }
    }

    public Resource loadAsResource(String relativePath) {
        try {
            Path filePath = this.rootStorageLocation.resolve(relativePath).normalize();
            if (!filePath.startsWith(this.rootStorageLocation)) {
                throw new BadRequestException("Cannot access file outside designated storage directory");
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
}
