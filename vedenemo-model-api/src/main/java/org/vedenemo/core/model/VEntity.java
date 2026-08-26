package org.vedenemo.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class VEntity extends Versionable {

    private final String azName;
    private final String visName;
    private final Map<String, VAttribute> attributesByAzName = new LinkedHashMap<>();

    public VEntity(String azName, String visName, ModelVersion activeSince) {
        this(azName, visName, activeSince, null);
    }

    public VEntity(String azName, String visName, ModelVersion activeSince, ModelVersion deprecatedSince) {
        this(azName, visName, activeSince, deprecatedSince, null);
    }

    public VEntity(
            String azName,
            String visName,
            ModelVersion activeSince,
            ModelVersion deprecatedSince,
            ModelVersion retiredSince
    ) {
        super(activeSince, deprecatedSince, retiredSince);
        this.azName = ModelTextRules.requireAzName(azName);
        this.visName = ModelTextRules.requireVisName(visName);
    }

    public String azName() {
        return azName;
    }

    public String visName() {
        return visName;
    }

    public VAttribute addAttribute(VAttribute attribute) {
        Objects.requireNonNull(attribute, "attribute must not be null");
        String uniquenessKey = VAttribute.uniquenessKey(attribute.azName());
        if (attributesByAzName.containsKey(uniquenessKey)) {
            throw new IllegalArgumentException("attribute azName must be unique within VEntity");
        }
        attributesByAzName.put(uniquenessKey, attribute);
        return attribute;
    }

    public Optional<VAttribute> removeAttribute(String azName) {
        return Optional.ofNullable(attributesByAzName.remove(ModelTextRules.uniquenessKey(azName)));
    }

    public VAttribute replaceAttribute(VAttribute attribute) {
        Objects.requireNonNull(attribute, "attribute must not be null");
        String uniquenessKey = VAttribute.uniquenessKey(attribute.azName());
        if (!attributesByAzName.containsKey(uniquenessKey)) {
            throw new IllegalArgumentException("attribute not found");
        }
        attributesByAzName.put(uniquenessKey, attribute);
        return attribute;
    }

    public boolean removeAttribute(VAttribute attribute) {
        Objects.requireNonNull(attribute, "attribute must not be null");
        String uniquenessKey = VAttribute.uniquenessKey(attribute.azName());
        return attributesByAzName.remove(uniquenessKey, attribute);
    }

    public List<VAttribute> attributes() {
        return List.copyOf(attributesByAzName.values());
    }

    public static String uniquenessKey(String azName) {
        return ModelTextRules.uniquenessKey(azName);
    }
}
