package cd.moziki.reconciliation;

import cd.moziki.common.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores the bytes behind a cash-count photo or receipt on local disk and
 * returns only opaque keys to the database. Files are content-validated, hashed
 * with SHA-256 and cannot be overwritten (the key includes a fresh UUID).
 *
 * <p>For a multi-instance deployment, point this directory at a shared volume or
 * replace the store with object storage; the database only needs the key.</p>
 */
@Service
public class EvidenceStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    private final Path root;
    private final long maxBytes;

    public EvidenceStorageService(
            @Value("${moziki.reconciliation.evidence-dir:./data/evidence}") String directory,
            @Value("${moziki.reconciliation.evidence-max-bytes:5242880}") long maxBytes) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    @PostConstruct
    void createDirectory() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create evidence directory " + root, ex);
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EVIDENCE_REQUIRED", "error.evidence.required");
        }
        if (file.getSize() > maxBytes) {
            throw ApiException.unprocessable("EVIDENCE_TOO_LARGE",
                    "error.evidence.too_large", maxBytes / (1024 * 1024));
        }

        String contentType = normalizeType(file.getContentType());
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.unprocessable("EVIDENCE_TYPE_NOT_SUPPORTED",
                    "error.evidence.type_not_supported");
        }

        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = input.readAllBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("EVIDENCE_UNREADABLE", "error.evidence.unreadable");
        }
        if (bytes.length == 0) {
            throw ApiException.badRequest("EVIDENCE_REQUIRED", "error.evidence.required");
        }
        if (!matchesSignature(contentType, bytes)) {
            throw ApiException.unprocessable("EVIDENCE_CONTENT_MISMATCH",
                    "error.evidence.content_mismatch");
        }

        String key = UUID.randomUUID() + ".bin";
        Path target = resolve(key);
        try {
            Files.createDirectories(root);
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot store evidence file", ex);
        }

        return new StoredFile(
                key,
                sanitizeName(file.getOriginalFilename()),
                contentType,
                bytes.length,
                sha256(bytes));
    }

    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw ApiException.notFound("EVIDENCE_NOT_FOUND", "error.evidence.not_found");
        }
        return new FileSystemResource(path);
    }

    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ignored) {
            // A failed cleanup must not mask the original database error.
        }
    }

    private Path resolve(String storageKey) {
        String normalized = StringUtils.cleanPath(storageKey == null ? "" : storageKey);
        if (normalized.isBlank() || normalized.contains("..") || normalized.contains("/")) {
            throw ApiException.notFound("EVIDENCE_NOT_FOUND", "error.evidence.not_found");
        }
        return root.resolve(normalized).normalize();
    }

    private static String sanitizeName(String original) {
        String cleaned = StringUtils.cleanPath(original == null ? "evidence" : original);
        String base = Path.of(cleaned).getFileName().toString().replaceAll("[\\r\\n]", "");
        if (base.length() > 255) {
            base = base.substring(base.length() - 255);
        }
        return base.isBlank() ? "evidence" : base;
    }

    private static String normalizeType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String lowered = contentType.toLowerCase(Locale.ROOT).trim();
        return "image/jpg".equals(lowered) ? "image/jpeg" : lowered;
    }

    private static boolean matchesSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                    && bytes.length > 11
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            case "application/pdf" -> startsWith(bytes, 0x25, 0x50, 0x44, 0x46);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record StoredFile(String storageKey, String fileName, String contentType,
                             long sizeBytes, String sha256) {
    }
}
