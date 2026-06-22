package com.security.service;

import com.security.dto.StorageUploadResult;
import com.security.util.ImageValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class LocalStorageService implements StorageService {

    private final ImageValidator imageValidator;
    private final Path productsLocation;
    private final Path brandsLocation;

    public LocalStorageService(
            ImageValidator imageValidator,
            @Value("${storage.local.products-path:uploads/products}") String productsPath,
            @Value("${storage.local.brands-path:uploads/brands}") String brandsPath) {
        this.imageValidator = imageValidator;
        this.productsLocation = Paths.get(productsPath).toAbsolutePath().normalize();
        this.brandsLocation = Paths.get(brandsPath).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.productsLocation);
            Files.createDirectories(this.brandsLocation);
        } catch (Exception ex) {
            throw new RuntimeException("No se pudieron crear los directorios de almacenamiento local.", ex);
        }
    }

    @Override
    public StorageUploadResult uploadProductImage(MultipartFile file, Long productId, String folderPath) throws IOException {
        return storeFile(file, productsLocation, "products");
    }

    @Override
    public StorageUploadResult uploadBrandImage(MultipartFile file, Long brandId, String folderPath) throws IOException {
        return storeFile(file, brandsLocation, "brands");
    }

    @Override
    public void delete(String publicIdOrPath) throws IOException {
        // En LOCAL, el publicIdOrPath es el nombre del archivo o ruta relativa.
        if (publicIdOrPath == null || publicIdOrPath.contains("..")) {
            return;
        }
        
        Path targetFile = null;
        if (publicIdOrPath.startsWith("products/")) {
            targetFile = productsLocation.resolve(publicIdOrPath.substring(9)).normalize();
            if (!targetFile.startsWith(productsLocation)) targetFile = null;
        } else if (publicIdOrPath.startsWith("brands/")) {
            targetFile = brandsLocation.resolve(publicIdOrPath.substring(7)).normalize();
            if (!targetFile.startsWith(brandsLocation)) targetFile = null;
        }

        if (targetFile != null && Files.exists(targetFile)) {
            Files.delete(targetFile);
        }
    }

    @Override
    public String getProviderName() {
        return "LOCAL";
    }

    @Override
    public void validateImageFile(MultipartFile file) {
        imageValidator.validateImage(file);
    }

    private StorageUploadResult storeFile(MultipartFile file, Path location, String folderName) throws IOException {
        validateImageFile(file);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg");
        String extension = getExtension(originalFilename);
        String newFilename = UUID.randomUUID().toString() + "." + extension;

        Path targetLocation = location.resolve(newFilename).normalize();
        if (!targetLocation.startsWith(location)) {
            throw new SecurityException("No se puede almacenar el archivo fuera del directorio destino.");
        }

        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/")
                .path(folderName)
                .path("/")
                .path(newFilename)
                .toUriString();

        return StorageUploadResult.builder()
                .url(fileDownloadUri)
                .secureUrl(fileDownloadUri)
                .publicId(folderName + "/" + newFilename)
                .originalFilename(originalFilename)
                .storedFilename(newFilename)
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .provider(getProviderName())
                .build();
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "jpg"; // fallback
    }
}
