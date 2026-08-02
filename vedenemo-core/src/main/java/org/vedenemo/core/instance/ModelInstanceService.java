package org.vedenemo.core.instance;

import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModelInstanceService {

    private static final int MAX_ROOT_VIS_NAME_LENGTH = 120;

    private final ModelRegistry modelRegistry;
    private final ModelInstanceRegistry instanceRegistry;

    public ModelInstanceService(ModelRegistry modelRegistry, ModelInstanceRegistry instanceRegistry) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
        this.instanceRegistry = Objects.requireNonNull(instanceRegistry, "instanceRegistry must not be null");
    }

    public ModelRoot describeApi(String modelAzName) {
        return requireModel(modelAzName);
    }

    public ModelInstanceRoot createRoot(String modelAzName, String visName) {
        ModelRoot modelRoot = requireModel(modelAzName);
        return instanceRegistry.createDataset(modelRoot, normalizeOptionalRootVisName(visName)).root();
    }

    public List<ModelInstanceRoot> listRoots(String modelAzName) {
        ModelRoot modelRoot = requireModel(modelAzName);
        return instanceRegistry.listDatasets(modelRoot)
                .stream()
                .map(ModelInstanceDataset::root)
                .toList();
    }

    public ModelInstanceRoot readRoot(String modelAzName, String instanceRootId) {
        ModelRoot modelRoot = requireModel(modelAzName);
        return requireDataset(modelRoot, instanceRootId).root();
    }

    public ModelInstanceRoot renameRoot(String modelAzName, String instanceRootId, String visName) {
        ModelRoot modelRoot = requireModel(modelAzName);
        return requireDataset(modelRoot, instanceRootId).renameRoot(normalizeRootVisName(visName));
    }

    public EntityInstance createEntityInstance(String modelAzName, String instanceRootId, String entityAzName, Map<String, Object> submittedValues) {
        ModelRoot modelRoot = requireModel(modelAzName);
        VEntity entity = requireEntity(modelRoot, entityAzName);
        Map<String, InstanceValue> values = normalizeValues(entity, submittedValues);
        EntityInstance instance = new EntityInstance(
                InstanceId.random(),
                modelRoot.azName(),
                modelRoot.version().toString(),
                entity.azName(),
                values
        );
        return requireDataset(modelRoot, instanceRootId).addEntityInstance(instance);
    }

    public List<EntityInstance> listEntityInstances(String modelAzName, String instanceRootId, String entityAzName, Map<String, Object> filters) {
        ModelRoot modelRoot = requireModel(modelAzName);
        VEntity entity = requireEntity(modelRoot, entityAzName);
        Map<String, InstanceValue> normalizedFilters = normalizeValues(entity, filters);
        return requireDataset(modelRoot, instanceRootId)
                .listEntityInstances(entity.azName())
                .stream()
                .filter(instance -> matchesAll(instance, normalizedFilters))
                .toList();
    }

    public int countEntityInstances(String modelAzName, String instanceRootId, String entityAzName) {
        ModelRoot modelRoot = requireModel(modelAzName);
        VEntity entity = requireEntity(modelRoot, entityAzName);
        return requireDataset(modelRoot, instanceRootId).countEntityInstances(entity.azName());
    }

    public EntityInstance readEntityInstance(String modelAzName, String instanceRootId, String entityAzName, String instanceId) {
        ModelRoot modelRoot = requireModel(modelAzName);
        VEntity entity = requireEntity(modelRoot, entityAzName);
        InstanceId id = new InstanceId(instanceId);
        return requireDataset(modelRoot, instanceRootId)
                .findEntityInstance(entity.azName(), id)
                .orElseThrow(() -> new IllegalArgumentException("instance not found"));
    }

    public List<EntityInstance> queryEntityInstances(String modelAzName, String instanceRootId, String entityAzName, EntityInstanceQuery query) {
        ModelRoot modelRoot = requireModel(modelAzName);
        VEntity entity = requireEntity(modelRoot, entityAzName);
        List<NormalizedScalarComparison> comparisons = normalizeScalarComparisons(entity, query.equals(), query.comparisons());
        List<NormalizedRelationshipPredicate> relationships = query.relationships().stream()
                .map(predicate -> normalizeRelationshipPredicate(modelRoot, entity, predicate))
                .toList();
        ModelInstanceDataset dataset = requireDataset(modelRoot, instanceRootId);
        return dataset.listEntityInstances(entity.azName())
                .stream()
                .filter(instance -> matchesAll(instance, comparisons))
                .filter(instance -> relationships.stream().allMatch(predicate -> matchesRelationship(dataset, instance, predicate)))
                .toList();
    }

    public AssociationInstanceLink createAssociationLink(
            String modelAzName,
            String instanceRootId,
            String associationAzName,
            String sourceInstanceId,
            String targetInstanceId
    ) {
        ModelRoot modelRoot = requireModel(modelAzName);
        Association association = requireAssociation(modelRoot, associationAzName);
        ModelInstanceDataset dataset = requireDataset(modelRoot, instanceRootId);
        InstanceId sourceId = new InstanceId(sourceInstanceId);
        InstanceId targetId = new InstanceId(targetInstanceId);
        requireInstanceForAssociationEndpoint(dataset, association.sourceEntityAzName(), sourceId, "source");
        requireInstanceForAssociationEndpoint(dataset, association.targetEntityAzName(), targetId, "target");
        return dataset.addAssociationLink(AssociationInstanceLink.create(modelRoot.azName(), association.azName(), sourceId, targetId));
    }

    public List<AssociationInstanceLink> listAssociationLinks(String modelAzName, String instanceRootId, String associationAzName) {
        ModelRoot modelRoot = requireModel(modelAzName);
        Association association = requireAssociation(modelRoot, associationAzName);
        return requireDataset(modelRoot, instanceRootId).listAssociationLinks(association.azName());
    }

    private ModelRoot requireModel(String modelAzName) {
        return modelRegistry.find(modelAzName)
                .orElseThrow(() -> new IllegalArgumentException("model not found"));
    }

    private ModelInstanceDataset requireDataset(ModelRoot modelRoot, String instanceRootId) {
        if (instanceRootId == null || instanceRootId.isBlank()) {
            throw new IllegalArgumentException("model instance root not found");
        }
        return instanceRegistry.findDataset(modelRoot, instanceRootId)
                .orElseThrow(() -> new IllegalArgumentException("model instance root not found"));
    }

    private static VEntity requireEntity(ModelRoot modelRoot, String entityAzName) {
        String entityKey = VEntity.uniquenessKey(entityAzName);
        return modelRoot.entities().stream()
                .filter(entity -> VEntity.uniquenessKey(entity.azName()).equals(entityKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("entity not found"));
    }

    private static Association requireAssociation(ModelRoot modelRoot, String associationAzName) {
        String associationKey = Association.uniquenessKey(associationAzName);
        return modelRoot.associations().stream()
                .filter(association -> Association.uniquenessKey(association.azName()).equals(associationKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("association not found"));
    }

    private static Map<String, InstanceValue> normalizeValues(VEntity entity, Map<String, Object> submittedValues) {
        Objects.requireNonNull(submittedValues, "submittedValues must not be null");
        Map<String, VAttribute> attributesByKey = new LinkedHashMap<>();
        for (VAttribute attribute : entity.attributes()) {
            attributesByKey.put(VAttribute.uniquenessKey(attribute.azName()), attribute);
        }
        for (Map.Entry<String, Object> entry : submittedValues.entrySet()) {
            String attributeKey = VAttribute.uniquenessKey(entry.getKey());
            if (!attributesByKey.containsKey(attributeKey)) {
                throw new IllegalArgumentException("unknown attribute: " + entry.getKey());
            }
        }
        Map<String, InstanceValue> normalizedValues = new LinkedHashMap<>();
        for (VAttribute attribute : entity.attributes()) {
            String attributeKey = VAttribute.uniquenessKey(attribute.azName());
            for (Map.Entry<String, Object> entry : submittedValues.entrySet()) {
                if (VAttribute.uniquenessKey(entry.getKey()).equals(attributeKey)) {
                    normalizedValues.put(attribute.azName(), normalizeValue(attribute, entry.getValue()));
                    break;
                }
            }
        }
        return normalizedValues;
    }

    private static InstanceValue normalizeValue(VAttribute attribute, Object submittedValue) {
        if (submittedValue == null) {
            throw new IllegalArgumentException("attribute value must not be null: " + attribute.azName());
        }
        return switch (attribute.type()) {
            case TEXT -> stringValue(attribute, submittedValue);
            case DATA -> stringValue(attribute, submittedValue);
            case URL -> urlValue(attribute, submittedValue);
            case NUMERIC -> numericValue(attribute, submittedValue);
        };
    }

    private static InstanceValue stringValue(VAttribute attribute, Object submittedValue) {
        if (!(submittedValue instanceof String value)) {
            throw new IllegalArgumentException(attribute.azName() + " must be a string");
        }
        return new InstanceValue(attribute.type(), value);
    }

    private static InstanceValue urlValue(VAttribute attribute, Object submittedValue) {
        if (!(submittedValue instanceof String value)) {
            throw new IllegalArgumentException(attribute.azName() + " must be a string URL");
        }
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException(attribute.azName() + " must be an absolute URL");
            }
            uri.toURL();
        } catch (URISyntaxException | java.net.MalformedURLException exception) {
            throw new IllegalArgumentException(attribute.azName() + " must be an absolute URL", exception);
        }
        return new InstanceValue(DataType.URL, value);
    }

    private static InstanceValue numericValue(VAttribute attribute, Object submittedValue) {
        try {
            if (submittedValue instanceof BigDecimal value) {
                return new InstanceValue(DataType.NUMERIC, value);
            }
            if (submittedValue instanceof Number number) {
                return new InstanceValue(DataType.NUMERIC, new BigDecimal(number.toString()));
            }
            if (submittedValue instanceof String value) {
                return new InstanceValue(DataType.NUMERIC, new BigDecimal(value));
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(attribute.azName() + " must be numeric", exception);
        }
        throw new IllegalArgumentException(attribute.azName() + " must be numeric");
    }

    private static boolean matchesAll(EntityInstance instance, Map<String, InstanceValue> expectedValues) {
        for (Map.Entry<String, InstanceValue> entry : expectedValues.entrySet()) {
            InstanceValue actual = instance.values().get(entry.getKey());
            if (actual == null || !actual.matches(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static List<NormalizedScalarComparison> normalizeScalarComparisons(
            VEntity entity,
            Map<String, Object> equals,
            List<ScalarComparison> comparisons
    ) {
        Objects.requireNonNull(equals, "equals must not be null");
        Objects.requireNonNull(comparisons, "comparisons must not be null");
        Map<String, VAttribute> attributesByKey = attributesByKey(entity);
        List<ScalarComparison> allComparisons = new ArrayList<>();
        equals.forEach((attributeAzName, value) -> allComparisons.add(ScalarComparison.equals(attributeAzName, value)));
        allComparisons.addAll(comparisons);

        ArrayList<NormalizedScalarComparison> normalized = new ArrayList<>();
        for (ScalarComparison comparison : allComparisons) {
            VAttribute attribute = requireAttribute(attributesByKey, comparison.attributeAzName());
            requireOperatorAllowed(attribute, comparison.operator());
            InstanceValue value = normalizeComparisonValue(attribute, comparison.operator(), comparison.value());
            normalized.add(new NormalizedScalarComparison(attribute.azName(), comparison.operator(), value));
        }
        return List.copyOf(normalized);
    }

    private static InstanceValue normalizeComparisonValue(VAttribute attribute, ScalarComparisonOperator operator, Object submittedValue) {
        if (operator == ScalarComparisonOperator.CONTAINS) {
            return stringValue(attribute, submittedValue);
        }
        return normalizeValue(attribute, submittedValue);
    }

    private static Map<String, VAttribute> attributesByKey(VEntity entity) {
        Map<String, VAttribute> attributesByKey = new LinkedHashMap<>();
        for (VAttribute attribute : entity.attributes()) {
            attributesByKey.put(VAttribute.uniquenessKey(attribute.azName()), attribute);
        }
        return attributesByKey;
    }

    private static VAttribute requireAttribute(Map<String, VAttribute> attributesByKey, String attributeAzName) {
        String attributeKey = VAttribute.uniquenessKey(attributeAzName);
        VAttribute attribute = attributesByKey.get(attributeKey);
        if (attribute == null) {
            throw new IllegalArgumentException("unknown attribute: " + attributeAzName);
        }
        return attribute;
    }

    private static void requireOperatorAllowed(VAttribute attribute, ScalarComparisonOperator operator) {
        switch (operator) {
            case EQUALS -> {
            }
            case LESS_THAN, GREATER_THAN -> {
                if (attribute.type() != DataType.NUMERIC) {
                    throw new IllegalArgumentException(operatorMessage(operator) + " requires NUMERIC attribute: " + attribute.azName());
                }
            }
            case CONTAINS -> {
                if (!isStringLike(attribute.type())) {
                    throw new IllegalArgumentException("contains requires string-like attribute: " + attribute.azName());
                }
            }
        }
    }

    private static String operatorMessage(ScalarComparisonOperator operator) {
        return switch (operator) {
            case LESS_THAN -> "less-than comparison";
            case GREATER_THAN -> "greater-than comparison";
            case EQUALS -> "equals comparison";
            case CONTAINS -> "contains comparison";
        };
    }

    private static boolean isStringLike(DataType type) {
        return type == DataType.TEXT || type == DataType.URL || type == DataType.DATA;
    }

    private static boolean matchesAll(EntityInstance instance, List<NormalizedScalarComparison> comparisons) {
        for (NormalizedScalarComparison comparison : comparisons) {
            InstanceValue actual = instance.values().get(comparison.attributeAzName());
            if (actual == null || !matchesComparison(actual, comparison)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesComparison(InstanceValue actual, NormalizedScalarComparison comparison) {
        return switch (comparison.operator()) {
            case EQUALS -> actual.matches(comparison.value());
            case LESS_THAN -> ((BigDecimal) actual.value()).compareTo((BigDecimal) comparison.value().value()) < 0;
            case GREATER_THAN -> ((BigDecimal) actual.value()).compareTo((BigDecimal) comparison.value().value()) > 0;
            case CONTAINS -> ((String) actual.value()).contains((String) comparison.value().value());
        };
    }

    private static void requireInstanceForAssociationEndpoint(
            ModelInstanceDataset dataset,
            String entityAzName,
            InstanceId instanceId,
            String endpoint
    ) {
        if (dataset.findEntityInstance(entityAzName, instanceId).isEmpty()) {
            throw new IllegalArgumentException(endpoint + " instance not found for association endpoint entity " + entityAzName);
        }
    }

    private static NormalizedRelationshipPredicate normalizeRelationshipPredicate(
            ModelRoot modelRoot,
            VEntity queryEntity,
            RelationshipPredicate predicate
    ) {
        Association association = requireAssociation(modelRoot, predicate.associationAzName());
        VEntity relatedEntity = requireEntity(modelRoot, predicate.entityAzName());
        if (!canTraverse(association, queryEntity.azName(), relatedEntity.azName(), predicate.direction())) {
            throw new IllegalArgumentException("relationship predicate does not match association endpoints");
        }
        return new NormalizedRelationshipPredicate(
                association,
                predicate.direction(),
                relatedEntity,
                normalizeScalarComparisons(relatedEntity, predicate.equals(), predicate.comparisons())
        );
    }

    private static boolean canTraverse(
            Association association,
            String queryEntityAzName,
            String relatedEntityAzName,
            RelationshipDirection direction
    ) {
        return switch (direction) {
            case OUTGOING -> sameAzName(association.sourceEntityAzName(), queryEntityAzName)
                    && sameAzName(association.targetEntityAzName(), relatedEntityAzName);
            case INCOMING -> sameAzName(association.targetEntityAzName(), queryEntityAzName)
                    && sameAzName(association.sourceEntityAzName(), relatedEntityAzName);
            case EITHER -> (sameAzName(association.sourceEntityAzName(), queryEntityAzName)
                    && sameAzName(association.targetEntityAzName(), relatedEntityAzName))
                    || (sameAzName(association.targetEntityAzName(), queryEntityAzName)
                    && sameAzName(association.sourceEntityAzName(), relatedEntityAzName));
        };
    }

    private static boolean matchesRelationship(
            ModelInstanceDataset dataset,
            EntityInstance queryInstance,
            NormalizedRelationshipPredicate predicate
    ) {
        return dataset.listAssociationLinks(predicate.association().azName()).stream()
                .map(link -> relatedInstanceId(queryInstance, link, predicate.direction()))
                .flatMap(Optional::stream)
                .map(relatedId -> dataset.findEntityInstance(predicate.relatedEntity().azName(), relatedId))
                .flatMap(Optional::stream)
                .anyMatch(relatedInstance -> matchesAll(relatedInstance, predicate.comparisons()));
    }

    private static Optional<InstanceId> relatedInstanceId(
            EntityInstance queryInstance,
            AssociationInstanceLink link,
            RelationshipDirection direction
    ) {
        boolean sourceMatches = link.sourceInstanceId().equals(queryInstance.id());
        boolean targetMatches = link.targetInstanceId().equals(queryInstance.id());
        return switch (direction) {
            case OUTGOING -> sourceMatches ? Optional.of(link.targetInstanceId()) : Optional.empty();
            case INCOMING -> targetMatches ? Optional.of(link.sourceInstanceId()) : Optional.empty();
            case EITHER -> {
                if (sourceMatches) {
                    yield Optional.of(link.targetInstanceId());
                }
                if (targetMatches) {
                    yield Optional.of(link.sourceInstanceId());
                }
                yield Optional.empty();
            }
        };
    }

    private static boolean sameAzName(String left, String right) {
        return VEntity.uniquenessKey(left).equals(VEntity.uniquenessKey(right));
    }

    private static String normalizeRootVisName(String visName) {
        if (visName == null) {
            throw new IllegalArgumentException("model instance root name is required");
        }
        String normalized = visName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("model instance root name is required");
        }
        if (normalized.length() > MAX_ROOT_VIS_NAME_LENGTH) {
            throw new IllegalArgumentException("model instance root name must be at most "
                    + MAX_ROOT_VIS_NAME_LENGTH + " characters");
        }
        return normalized;
    }

    private static String normalizeOptionalRootVisName(String visName) {
        if (visName == null) {
            return null;
        }
        String normalized = visName.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_ROOT_VIS_NAME_LENGTH) {
            throw new IllegalArgumentException("model instance root name must be at most "
                    + MAX_ROOT_VIS_NAME_LENGTH + " characters");
        }
        return normalized;
    }

    private record NormalizedRelationshipPredicate(
            Association association,
            RelationshipDirection direction,
            VEntity relatedEntity,
            List<NormalizedScalarComparison> comparisons
    ) {
    }

    private record NormalizedScalarComparison(
            String attributeAzName,
            ScalarComparisonOperator operator,
            InstanceValue value
    ) {
    }
}
