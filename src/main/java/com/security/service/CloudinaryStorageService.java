package com.security.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.security.dto.StorageUploadResult;
import com.security.util.ImageValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;
    private final ImageValidator imageValidator;

    public CloudinaryStorageService(
            ImageValidator imageValidator,
            String cloudName,
            String apiKey,
            String apiSecret) {
        
        this.imageValidator = imageValidator;
        
        if (!StringUtils.hasText(cloudName) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)) {
            throw new IllegalStateException("Cloudinary credentials are not properly configured.");
        }
        
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    @Override
    public StorageUploadResult uploadProductImage(MultipartFile file, Long productId, String folderPath) throws IOException {
        return uploadToCloudinary(file, folderPath);
    }

    @Override
    public StorageUploadResult uploadBrandImage(MultipartFile file, Long brandId, String folderPath) throws IOException {
        return uploadToCloudinary(file, folderPath);
    }

    @Override
    public void delete(String publicIdOrPath) throws IOException {
        if (publicIdOrPath == null || publicIdOrPath.trim().isEmpty()) {
            return;
        }
        cloudinary.uploader().destroy(publicIdOrPath, ObjectUtils.emptyMap());
    }

    @Override
    public String getProviderName() {
        return "CLOUDINARY";
    }

    @Override
    public void validateImageFile(MultipartFile file) {
        imageValidator.validateImage(file);
    }

    private StorageUploadResult uploadToCloudinary(MultipartFile file, String folder) throws IOException {
        validateImageFile(file);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "image");
        String extension = getExtension(originalFilename);
        
        // Remove extension from original filename to avoid duplicate extensions in public_id
        String baseName = originalFilename.contains(".") 
                ? originalFilename.substring(0, originalFilename.lastIndexOf('.')) 
                : originalFilename;

        Map<String, Object> params = ObjectUtils.asMap(
                "folder", folder,
                "public_id", UUID.randomUUID().toString(),
                "resource_type", "image"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), params);

        return StorageUploadResult.builder()
                .url(uploadResult.get("url").toString())
                .secureUrl(uploadResult.get("secure_url").toString())
                .publicId(uploadResult.get("public_id").toString())
                .originalFilename(originalFilename)
                .storedFilename(uploadResult.get("public_id").toString() + "." + extension)
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
