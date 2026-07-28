package org.vedenemo.storage.gcs;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.api.gax.paging.Page;
import org.vedenemo.core.spi.snapshot.SnapshotContent;
import org.vedenemo.core.spi.snapshot.SnapshotDescriptor;
import org.vedenemo.core.spi.snapshot.SnapshotStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public final class GcsSnapshotStore implements SnapshotStore {

    private static final Pattern SNAPSHOT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final String CONTENT_TYPE = "text/plain; charset=utf-8";
    private static final String FORMAT_VERSION = "vedenemo-script 1";

    private final Storage storage;
    private final String bucketName;
    private final String objectPrefix;

    public GcsSnapshotStore(String projectId, String bucketName, String objectPrefix) {
        this(
                StorageOptions.newBuilder()
                        .setProjectId(requireText(projectId, "projectId"))
                        .build()
                        .getService(),
                bucketName,
                objectPrefix
        );
    }

    GcsSnapshotStore(Storage storage, String bucketName, String objectPrefix) {
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.bucketName = requireText(bucketName, "bucketName");
        this.objectPrefix = normalizePrefix(objectPrefix);
    }

    @Override
    public List<SnapshotDescriptor> listSnapshots(String scope) throws IOException {
        String prefix = objectPrefix + "/";
        try {
            Page<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefix));
            return StreamSupport.stream(blobs.iterateAll().spliterator(), false)
                    .filter(blob -> !blob.isDirectory())
                    .filter(blob -> blob.getName().endsWith(".vdos"))
                    .map(blob -> descriptorFrom(blob, keyFromObjectName(blob.getName())))
                    .sorted(Comparator.comparing(SnapshotDescriptor::savedAt).reversed()
                            .thenComparing(SnapshotDescriptor::key))
                    .toList();
        } catch (RuntimeException exception) {
            throw new IOException("snapshot list failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public Optional<SnapshotContent> readSnapshot(String scope, String snapshotKey) throws IOException {
        String objectName = objectNameForKey(snapshotKey);
        try {
            Blob blob = storage.get(BlobId.of(bucketName, objectName));
            if (blob == null || !blob.exists()) {
                return Optional.empty();
            }
            String content = new String(blob.getContent(), StandardCharsets.UTF_8);
            return Optional.of(new SnapshotContent(descriptorFrom(blob, snapshotKey), content));
        } catch (RuntimeException exception) {
            throw new IOException("snapshot read failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public SnapshotDescriptor writeSnapshot(
            String scope,
            String modelAzName,
            String snapshotName,
            String content,
            SnapshotDescriptor descriptor
    ) throws IOException {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        String key = snapshotKey(modelAzName, snapshotName);
        String objectName = objectNameForKey(key);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("scope", requireText(scope, "scope"));
        metadata.put("model-az-name", descriptor.modelAzName());
        metadata.put("model-vis-name", descriptor.modelVisName());
        metadata.put("model-version", descriptor.modelVersion());
        metadata.put("command-count", Integer.toString(descriptor.commandCount()));
        metadata.put("saved-at", descriptor.savedAt().toString());
        metadata.put("format-version", FORMAT_VERSION);

        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectName))
                    .setContentType(CONTENT_TYPE)
                    .setMetadata(metadata)
                    .build();
            Blob blob = storage.create(blobInfo, content.getBytes(StandardCharsets.UTF_8));
            return descriptorFrom(blob, key);
        } catch (RuntimeException exception) {
            throw new IOException("snapshot write failed: " + exception.getMessage(), exception);
        }
    }

    private SnapshotDescriptor descriptorFrom(Blob blob, String key) {
        Map<String, String> metadata = blob.getMetadata() == null ? Map.of() : blob.getMetadata();
        return new SnapshotDescriptor(
                key,
                metadata.getOrDefault("model-az-name", modelAzNameFromKey(key)),
                metadata.getOrDefault("model-vis-name", metadata.getOrDefault("model-az-name", modelAzNameFromKey(key))),
                metadata.getOrDefault("model-version", "unknown"),
                parseCommandCount(metadata.get("command-count")),
                parseInstant(metadata.get("saved-at"))
        );
    }

    private String objectNameForKey(String key) {
        String normalizedKey = normalizeKey(key);
        return objectPrefix + "/" + normalizedKey;
    }

    private String keyFromObjectName(String objectName) {
        String prefix = objectPrefix + "/";
        if (!objectName.startsWith(prefix)) {
            throw new IllegalArgumentException("snapshot object is outside configured prefix");
        }
        return objectName.substring(prefix.length());
    }

    private static String snapshotKey(String modelAzName, String snapshotName) {
        String safeModelAzName = requireText(modelAzName, "modelAzName");
        String safeSnapshotName = normalizeSnapshotName(snapshotName);
        return safeModelAzName + "/" + safeSnapshotName + ".vdos";
    }

    private static String normalizeSnapshotName(String snapshotName) {
        String value = requireText(snapshotName, "snapshotName");
        if (value.endsWith(".vdos")) {
            value = value.substring(0, value.length() - ".vdos".length());
        }
        if (!SNAPSHOT_NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("snapshot name must use letters, digits, dot, underscore, or hyphen");
        }
        return value;
    }

    private static String normalizeKey(String key) {
        String value = requireText(key, "snapshotKey");
        if (value.startsWith("/") || value.contains("..") || !value.endsWith(".vdos")) {
            throw new IllegalArgumentException("snapshot key is invalid");
        }
        return value;
    }

    private static String normalizePrefix(String prefix) {
        String value = requireText(prefix, "objectPrefix");
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("objectPrefix must not be blank");
        }
        return value;
    }

    private static String modelAzNameFromKey(String key) {
        int slash = key.indexOf('/');
        return slash < 0 ? key : key.substring(0, slash);
    }

    private static int parseCommandCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.EPOCH;
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
