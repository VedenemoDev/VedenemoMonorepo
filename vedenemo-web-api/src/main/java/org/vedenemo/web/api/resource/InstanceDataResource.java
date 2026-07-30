package org.vedenemo.web.api.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.vedenemo.core.instance.AssociationInstanceLink;
import org.vedenemo.core.instance.EntityInstance;
import org.vedenemo.core.instance.EntityInstanceQuery;
import org.vedenemo.core.instance.InstanceValue;
import org.vedenemo.core.instance.ModelInstanceService;
import org.vedenemo.core.instance.RelationshipDirection;
import org.vedenemo.core.instance.RelationshipPredicate;
import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InstanceDataResource {

    private static final TypeReference<LinkedHashMap<String, Object>> ATTRIBUTE_MAP_TYPE = new TypeReference<>() {
    };

    private final ModelInstanceService instanceService;
    private final ObjectMapper objectMapper;

    public InstanceDataResource(ModelInstanceService instanceService) {
        this.instanceService = Objects.requireNonNull(instanceService, "instanceService must not be null");
        this.objectMapper = new ObjectMapper()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    }

    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/data/{modelAzName}/_api", context -> {
            try {
                ModelRoot modelRoot = instanceService.describeApi(context.pathParam("modelAzName"));
                writeJson(context, 200, ApiDescriptionResponse.from(modelRoot));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.post("/data/{modelAzName}/_links/{associationAzName}", context -> {
            try {
                CreateLinkRequest request = objectMapper.readValue(context.body(), CreateLinkRequest.class);
                request.requireComplete();
                AssociationInstanceLink link = instanceService.createAssociationLink(
                        context.pathParam("modelAzName"),
                        context.pathParam("associationAzName"),
                        request.sourceInstanceId(),
                        request.targetInstanceId()
                );
                writeJson(context, 201, LinkResponse.from(link));
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.get("/data/{modelAzName}/_links/{associationAzName}", context -> {
            try {
                List<LinkResponse> links = instanceService
                        .listAssociationLinks(context.pathParam("modelAzName"), context.pathParam("associationAzName"))
                        .stream()
                        .map(LinkResponse::from)
                        .toList();
                writeJson(context, 200, links);
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.post("/data/{modelAzName}/{entityAzName}/_query", context -> {
            try {
                QueryRequest request = objectMapper.readValue(context.body(), QueryRequest.class);
                EntityInstanceQuery query = request.toCoreQuery();
                List<EntityInstanceResponse> instances = instanceService
                        .queryEntityInstances(context.pathParam("modelAzName"), context.pathParam("entityAzName"), query)
                        .stream()
                        .map(EntityInstanceResponse::from)
                        .toList();
                writeJson(context, 200, instances);
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.post("/data/{modelAzName}/{entityAzName}", context -> {
            try {
                LinkedHashMap<String, Object> values = objectMapper.readValue(context.body(), ATTRIBUTE_MAP_TYPE);
                EntityInstance instance = instanceService.createEntityInstance(
                        context.pathParam("modelAzName"),
                        context.pathParam("entityAzName"),
                        values
                );
                writeJson(context, 201, EntityInstanceResponse.from(instance));
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.get("/data/{modelAzName}/{entityAzName}", context -> {
            try {
                List<EntityInstanceResponse> instances = instanceService
                        .listEntityInstances(
                                context.pathParam("modelAzName"),
                                context.pathParam("entityAzName"),
                                queryFilters(context)
                        )
                        .stream()
                        .map(EntityInstanceResponse::from)
                        .toList();
                writeJson(context, 200, instances);
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.get("/data/{modelAzName}/{entityAzName}/{instanceId}", context -> {
            try {
                EntityInstance instance = instanceService.readEntityInstance(
                        context.pathParam("modelAzName"),
                        context.pathParam("entityAzName"),
                        context.pathParam("instanceId")
                );
                writeJson(context, 200, EntityInstanceResponse.from(instance));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
    }

    private Map<String, Object> queryFilters(Context context) {
        Map<String, Object> filters = new LinkedHashMap<>();
        context.queryParamMap().forEach((key, values) -> {
            if (!values.isEmpty()) {
                filters.put(key, values.getFirst());
            }
        });
        return filters;
    }

    private void writeError(Context context, int status, IllegalArgumentException exception) throws JsonProcessingException {
        writeJson(context, status, new ErrorResponse(exception.getMessage()));
    }

    private int statusFor(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if ("model not found".equals(message)
                || "entity not found".equals(message)
                || "association not found".equals(message)
                || "instance not found".equals(message)) {
            return 404;
        }
        return 400;
    }

    private void writeJson(Context context, int status, Object body) throws JsonProcessingException {
        context.status(status)
                .contentType("application/json")
                .result(objectMapper.writeValueAsString(body));
    }

    private record CreateLinkRequest(String sourceInstanceId, String targetInstanceId) {

        private void requireComplete() {
            if (sourceInstanceId == null || sourceInstanceId.isBlank()) {
                throw new IllegalArgumentException("sourceInstanceId is required");
            }
            if (targetInstanceId == null || targetInstanceId.isBlank()) {
                throw new IllegalArgumentException("targetInstanceId is required");
            }
        }
    }

    private record QueryRequest(WhereRequest where, List<RelationshipRequest> relationships) {

        EntityInstanceQuery toCoreQuery() {
            Map<String, Object> equals = where == null || where.equals() == null
                    ? Map.of()
                    : where.equals();
            List<RelationshipPredicate> predicates = relationships == null
                    ? List.of()
                    : relationships.stream().map(RelationshipRequest::toCorePredicate).toList();
            return new EntityInstanceQuery(equals, predicates);
        }
    }

    private record WhereRequest(Map<String, Object> equals) {
    }

    private record RelationshipRequest(
            String associationAzName,
            String direction,
            String entityAzName,
            WhereRequest where
    ) {

        RelationshipPredicate toCorePredicate() {
            Map<String, Object> equals = where == null || where.equals() == null
                    ? Map.of()
                    : where.equals();
            return new RelationshipPredicate(
                    associationAzName,
                    RelationshipDirection.parse(direction),
                    entityAzName,
                    equals
            );
        }
    }

    private record ApiDescriptionResponse(
            String modelAzName,
            String modelVisName,
            String modelVersion,
            List<EntityDescriptionResponse> entities,
            List<AssociationDescriptionResponse> associations
    ) {

        private static ApiDescriptionResponse from(ModelRoot modelRoot) {
            return new ApiDescriptionResponse(
                    modelRoot.azName(),
                    modelRoot.visName(),
                    modelRoot.version().toString(),
                    modelRoot.entities().stream().map(EntityDescriptionResponse::from).toList(),
                    modelRoot.associations().stream().map(AssociationDescriptionResponse::from).toList()
            );
        }
    }

    private record EntityDescriptionResponse(
            String azName,
            String visName,
            List<AttributeDescriptionResponse> attributes,
            Map<String, String> operations,
            Map<String, String> createBodyExample
    ) {

        private static EntityDescriptionResponse from(VEntity entity) {
            Map<String, String> operations = new LinkedHashMap<>();
            operations.put("create", "/data/{modelAzName}/" + entity.azName());
            operations.put("list", "/data/{modelAzName}/" + entity.azName());
            operations.put("read", "/data/{modelAzName}/" + entity.azName() + "/{instanceId}");
            operations.put("query", "/data/{modelAzName}/" + entity.azName() + "/_query");
            Map<String, String> example = new LinkedHashMap<>();
            entity.attributes().forEach(attribute -> example.put(attribute.azName(), exampleValue(attribute)));
            return new EntityDescriptionResponse(
                    entity.azName(),
                    entity.visName(),
                    entity.attributes().stream().map(AttributeDescriptionResponse::from).toList(),
                    operations,
                    example
            );
        }

        private static String exampleValue(VAttribute attribute) {
            return switch (attribute.type()) {
                case TEXT -> "text";
                case NUMERIC -> "123.45";
                case URL -> "https://example.com";
                case DATA -> "data";
            };
        }
    }

    private record AttributeDescriptionResponse(String azName, String visName, String dataType) {

        private static AttributeDescriptionResponse from(VAttribute attribute) {
            return new AttributeDescriptionResponse(attribute.azName(), attribute.visName(), attribute.type().name());
        }
    }

    private record AssociationDescriptionResponse(
            String azName,
            String visName,
            String kind,
            String sourceEntityAzName,
            String targetEntityAzName,
            String cardinality,
            String sourceRoleName,
            String targetRoleName,
            String sourceCardinality,
            String targetCardinality,
            Map<String, String> linkOperations,
            Map<String, String> createBodyExample
    ) {

        private static AssociationDescriptionResponse from(Association association) {
            Map<String, String> operations = new LinkedHashMap<>();
            operations.put("create", "/data/{modelAzName}/_links/" + association.azName());
            operations.put("list", "/data/{modelAzName}/_links/" + association.azName());
            Map<String, String> example = new LinkedHashMap<>();
            example.put("sourceInstanceId", "00000000-0000-0000-0000-000000000000");
            example.put("targetInstanceId", "00000000-0000-0000-0000-000000000000");
            return new AssociationDescriptionResponse(
                    association.azName(),
                    association.visName(),
                    association.kind().name(),
                    association.sourceEntityAzName(),
                    association.targetEntityAzName(),
                    association.cardinality().toString(),
                    association.sourceRoleName(),
                    association.targetRoleName(),
                    association.sourceCardinality() == null ? null : association.sourceCardinality().toString(),
                    association.targetCardinality() == null ? null : association.targetCardinality().toString(),
                    operations,
                    example
            );
        }
    }

    private record EntityInstanceResponse(
            String id,
            String modelAzName,
            String modelVersion,
            String entityAzName,
            Map<String, Object> values
    ) {

        private static EntityInstanceResponse from(EntityInstance instance) {
            Map<String, Object> values = new LinkedHashMap<>();
            instance.values().forEach((key, value) -> values.put(key, rawValue(value)));
            return new EntityInstanceResponse(
                    instance.id().value(),
                    instance.modelAzName(),
                    instance.modelVersion(),
                    instance.entityAzName(),
                    values
            );
        }

        private static Object rawValue(InstanceValue value) {
            return value.value();
        }
    }

    private record LinkResponse(
            String id,
            String modelAzName,
            String associationAzName,
            String sourceInstanceId,
            String targetInstanceId
    ) {

        private static LinkResponse from(AssociationInstanceLink link) {
            return new LinkResponse(
                    link.id(),
                    link.modelAzName(),
                    link.associationAzName(),
                    link.sourceInstanceId().value(),
                    link.targetInstanceId().value()
            );
        }
    }

    private record ErrorResponse(String error) {
    }
}
