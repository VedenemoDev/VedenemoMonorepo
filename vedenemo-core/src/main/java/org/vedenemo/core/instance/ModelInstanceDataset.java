package org.vedenemo.core.instance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModelInstanceDataset {

    private final String modelAzName;
    private final String modelVersion;
    private final Map<String, Map<InstanceId, EntityInstance>> instancesByEntity = new LinkedHashMap<>();
    private final Map<String, List<AssociationInstanceLink>> linksByAssociation = new LinkedHashMap<>();

    public ModelInstanceDataset(String modelAzName, String modelVersion) {
        this.modelAzName = Objects.requireNonNull(modelAzName, "modelAzName must not be null");
        this.modelVersion = Objects.requireNonNull(modelVersion, "modelVersion must not be null");
    }

    public String modelAzName() {
        return modelAzName;
    }

    public String modelVersion() {
        return modelVersion;
    }

    synchronized EntityInstance addEntityInstance(EntityInstance instance) {
        instancesByEntity
                .computeIfAbsent(instance.entityAzName(), ignored -> new LinkedHashMap<>())
                .put(instance.id(), instance);
        return instance;
    }

    synchronized Optional<EntityInstance> findEntityInstance(String entityAzName, InstanceId id) {
        Map<InstanceId, EntityInstance> instances = instancesByEntity.get(entityAzName);
        if (instances == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(instances.get(id));
    }

    synchronized List<EntityInstance> listEntityInstances(String entityAzName) {
        Map<InstanceId, EntityInstance> instances = instancesByEntity.get(entityAzName);
        if (instances == null) {
            return List.of();
        }
        return List.copyOf(instances.values());
    }

    synchronized AssociationInstanceLink addAssociationLink(AssociationInstanceLink link) {
        linksByAssociation
                .computeIfAbsent(link.associationAzName(), ignored -> new ArrayList<>())
                .add(link);
        return link;
    }

    synchronized List<AssociationInstanceLink> listAssociationLinks(String associationAzName) {
        return List.copyOf(linksByAssociation.getOrDefault(associationAzName, List.of()));
    }
}
