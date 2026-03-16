package com.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de conexión a Supabase Storage.
 *
 * <pre>
 * supabase:
 *   url:         https://&lt;project-ref&gt;.supabase.co
 *   service-key: &lt;service_role JWT&gt;
 *   bucket-name: casamusica-backups
 * </pre>
 */
@ConfigurationProperties(prefix = "supabase")
public class SupabaseProperties {

    /** URL base del proyecto Supabase (sin barra final). */
    private String url;

    /**
     * JWT de tipo {@code service_role}.
     * Otorga acceso completo a Storage; <strong>nunca</strong> exponerlo al cliente.
     */
    private String serviceKey;

    /** Nombre del bucket privado donde se almacenan los volcados. */
    private String bucketName = "db-backups";

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getUrl()                  { return url; }
    public void setUrl(String v)            { this.url = v; }

    public String getServiceKey()           { return serviceKey; }
    public void setServiceKey(String v)     { this.serviceKey = v; }

    public String getBucketName()           { return bucketName; }
    public void setBucketName(String v)     { this.bucketName = v; }
}
