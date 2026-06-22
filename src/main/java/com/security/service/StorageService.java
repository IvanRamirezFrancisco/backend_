package com.security.service;

import com.security.dto.StorageUploadResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageService {
    
    /**
     * Sube una imagen de producto.
     */
    StorageUploadResult uploadProductImage(MultipartFile file, Long productId, String folderPath) throws IOException;

    /**
     * Sube un logo de marca.
     */
    StorageUploadResult uploadBrandImage(MultipartFile file, Long brandId, String folderPath) throws IOException;

    /**
     * Elimina una imagen mediante su identificador público o ruta.
     */
    void delete(String publicIdOrPath) throws IOException;

    /**
     * Devuelve el identificador del proveedor (ej. "LOCAL", "CLOUDINARY").
     */
    String getProviderName();

    /**
     * Valida el archivo (tamaño, tipo, formato) antes de subir.
     */
    void validateImageFile(MultipartFile file);
}
