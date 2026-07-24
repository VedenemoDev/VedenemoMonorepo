package org.vedenemo.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModelRoot {

    private final String azName;
    private final String visName;
    private final ModelVersion version;
    private final Map<String, VEntity> entitiesByAzName = new LinkedHashMap<>();
    private final Map<String, Association> associationsByAzName = new LinkedHashMap<>();

    public ModelRoot(String azName, String visName, ModelVersion version) {
        this.azName = ModelTextRules.requireAzName(azName);
        this.visName = ModelTextRules.requireVisName(visName);
        this.version = Objects.requireNonNull(version, "version must not be null");
    }

    public static ModelRoot create(String azName, String visName, String version) {
        return new ModelRoot(azName, visName, ModelVersion.parse(version));
    }

    public static String uniquenessKey(String azName) {
        return ModelTextRules.uniquenessKey(azName);
    }

    public String azName() {
        return azName;
    }

    public String visName() {
        return visName;
    }

    public ModelVersion version() {
        return version;
    }

    public synchronized VEntity addEntity(VEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        String key = VEntity.uniquenessKey(entity.azName());
        if (entitiesByAzName.containsKey(key)) {
            throw new IllegalArgumentException("entity azName must be unique within ModelRoot");
        }
        entitiesByAzName.put(key, entity);
        return entity;
    }

    public synchronized Optional<VEntity> removeEntity(String azName) {
        return Optional.ofNullable(entitiesByAzName.remove(ModelTextRules.uniquenessKey(azName)));
    }

    public synchronized boolean removeEntity(VEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        String key = VEntity.uniquenessKey(entity.azName());
        return entitiesByAzName.remove(key, entity);
    }

    public synchronized List<VEntity> entities() {
        return List.copyOf(entitiesByAzName.values());
    }

    public synchronized Association addAssociation(Association association) {
        Objects.requireNonNull(association, "association must not be null");
        String key = Association.uniquenessKey(association.azName());
        if (associationsByAzName.containsKey(key)) {
            throw new IllegalArgumentException("association azName must be unique within ModelRoot");
        }
        requireEntityExists(association.sourceEntityAzName(), "source entity");
        requireEntityExists(association.targetEntityAzName(), "target entity");
        associationsByAzName.put(key, association);
        return association;
    }

    public synchronized Optional<Association> removeAssociation(String azName) {
        return Optional.ofNullable(associationsByAzName.remove(ModelTextRules.uniquenessKey(azName)));
    }

    public synchronized boolean removeAssociation(Association association) {
        Objects.requireNonNull(association, "association must not be null");
        String key = Association.uniquenessKey(association.azName());
        return associationsByAzName.remove(key, association);
    }

    public synchronized List<Association> associations() {
        return List.copyOf(associationsByAzName.values());
    }

    private void requireEntityExists(String entityAzName, String role) {
        if (!entitiesByAzName.containsKey(VEntity.uniquenessKey(entityAzName))) {
            throw new IllegalArgumentException(role + " must exist in ModelRoot");
        }
    }
}
