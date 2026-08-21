package org.vedenemo.storage.gcs;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.vedenemo.core.spi.dump.ModelInstanceDumpContent;
import org.vedenemo.core.spi.dump.ModelInstanceDumpDescriptor;
import org.vedenemo.core.spi.dump.ModelInstanceDumpStore;

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

public final class GcsModelInstanceDumpStore implements ModelInstanceDumpStore {

    private static final Pattern DUMP_NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String FORMAT_VERSION = "vedenemo-instance-dump 1";

    private final Storage storage;
    private final String bucketName;
    private final String objectPrefix;

    public GcsModelInstanceDumpStore(String projectId, String bucketName, String objectPrefix) {
        this(
                StorageOptions.newBuilder()
                        .setProjectId(requireText(projectId, "projectId"))
                        .build()
                        .getService(),
                bucketName,
                objectPrefix
        );
    }

    GcsModelInstanceDumpStore(Storage storage, String bucketName, String objectPrefix) {
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.bucketName = requireText(bucketName, "bucketName");
        this.objectPrefix = normalizePrefix(objectPrefix);
    }

    @Override
    public List<ModelInstanceDumpDescriptor> listDumps(String scope, String modelAzName) throws IOException {
        String prefix = objectPrefix + "/" + requireText(modelAzName, "modelAzName") + "/";
        try {
            Page<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefix));
            return StreamSupport.stream(blobs.iterateAll().spliterator(), false)
                    .filter(blob -> !blob.isDirectory())
                    .filter(blob -> blob.getName().endsWith(".vdmp"))
                    .map(blob -> descriptorFrom(blob, keyFromObjectName(blob.getName())))
                    .sorted(Comparator.comparing(ModelInstanceDumpDescriptor::savedAt).reversed()
                            .thenComparing(ModelInstanceDumpDescriptor::key))
                    .toList();
        } catch (RuntimeException exception) {
            throw new IOException("dump list failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public Optional<ModelInstanceDumpContent> readDump(String scope, String dumpKey) throws IOException {
        String objectName = objectNameForKey(dumpKey);
        try {
            Blob blob = storage.get(BlobId.of(bucketName, objectName));
            if (blob == null || !blob.exists()) {
                return Optional.empty();
            }
            String content = new String(blob.getContent(), StandardCharsets.UTF_8);
            return Optional.of(new ModelInstanceDumpContent(descriptorFrom(blob, dumpKey), content));
        } catch (RuntimeException exception) {
            throw new IOException("dump read failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public ModelInstanceDumpDescriptor writeDump(
            String scope,
            String modelAzName,
            String dumpName,
            String content,
            ModelInstanceDumpDescriptor descriptor
    ) throws IOException {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        String key = dumpKey(modelAzName, dumpName);
        String objectName = objectNameForKey(key);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("scope", requireText(scope, "scope"));
        metadata.put("model-az-name", descriptor.modelAzName());
        metadata.put("model-vis-name", descriptor.modelVisName());
        metadata.put("model-version", descriptor.modelVersion());
        metadata.put("root-vis-name", descriptor.rootVisName() == null ? "" : descriptor.rootVisName());
        metadata.put("entity-record-count", Integer.toString(descriptor.entityRecordCount()));
        metadata.put("association-link-count", Integer.toString(descriptor.associationLinkCount()));
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
            throw new IOException("dump write failed: " + exception.getMessage(), exception);
        }
    }

    private ModelInstanceDumpDescriptor descriptorFrom(Blob blob, String key) {
        Map<String, String> metadata = blob.getMetadata() == null ? Map.of() : blob.getMetadata();
        return new ModelInstanceDumpDescriptor(
                key,
                metadata.getOrDefault("model-az-name", modelAzNameFromKey(key)),
                metadata.getOrDefault("model-vis-name", metadata.getOrDefault("model-az-name", modelAzNameFromKey(key))),
                metadata.getOrDefault("model-version", "unknown"),
                blankToNull(metadata.get("root-vis-name")),
                parseInt(metadata.get("entity-record-count")),
                parseInt(metadata.get("association-link-count")),
                parseInstant(metadata.get("saved-at"))
        );
    }

    private String objectNameForKey(String key) {
        return objectPrefix + "/" + normalizeKey(key);
    }

    private String keyFromObjectName(String objectName) {
        String prefix = objectPrefix + "/";
        if (!objectName.startsWith(prefix)) {
            throw new IllegalArgumentException("dump object is outside configured prefix");
        }
        return objectName.substring(prefix.length());
    }

    private static String dumpKey(String modelAzName, String dumpName) {
        String safeModelAzName = requireText(modelAzName, "modelAzName");
        String safeDumpName = normalizeDumpName(dumpName);
        return safeModelAzName + "/" + safeDumpName + ".vdmp";
    }

    private static String normalizeDumpName(String dumpName) {
        String value = requireText(dumpName, "dumpName");
        if (value.endsWith(".vdmp")) {
            value = value.substring(0, value.length() - ".vdmp".length());
        }
        if (!DUMP_NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("dump name must use letters, digits, dot, underscore, or hyphen");
        }
        return value;
    }

    private static String normalizeKey(String key) {
        String value = requireText(key, "dumpKey");
        if (value.startsWith("/") || value.contains("..") || !value.endsWith(".vdmp")) {
            throw new IllegalArgumentException("dump key is invalid");
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

    private static int parseInt(String value) {
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
