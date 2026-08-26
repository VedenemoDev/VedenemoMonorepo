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
import org.vedenemo.core.instance.LocationValue;
import org.vedenemo.core.instance.ModelInstanceRoot;
import org.vedenemo.core.instance.ModelInstanceService;
import org.vedenemo.core.instance.RelationshipDirection;
import org.vedenemo.core.instance.RelationshipPredicate;
import org.vedenemo.core.instance.ScalarComparison;
import org.vedenemo.core.instance.ScalarComparisonOperator;
import org.vedenemo.core.instance.dump.DumpAssociationLink;
import org.vedenemo.core.instance.dump.DumpEntityGroup;
import org.vedenemo.core.instance.dump.DumpEntityRecord;
import org.vedenemo.core.instance.dump.DumpModel;
import org.vedenemo.core.instance.dump.DumpRoot;
import org.vedenemo.core.instance.dump.ModelInstanceDump;
import org.vedenemo.core.instance.dump.ModelInstanceDumpImportResult;
import org.vedenemo.core.instance.dump.ModelInstanceDumpPrecheckResult;
import org.vedenemo.core.instance.dump.ModelInstanceDumpService;
import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.model.ValueSet;
import org.vedenemo.core.model.ValueSetEntry;
import org.vedenemo.core.spi.dump.ModelInstanceDumpContent;
import org.vedenemo.core.spi.dump.ModelInstanceDumpDescriptor;
import org.vedenemo.core.spi.dump.ModelInstanceDumpStore;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InstanceDataResource {

    private static final TypeReference<LinkedHashMap<String, Object>> ATTRIBUTE_MAP_TYPE = new TypeReference<>() {
    };

    private final ModelInstanceService instanceService;
    private final ModelInstanceDumpService dumpService;
    private final Optional<ModelInstanceDumpStore> dumpStore;
    private final String dumpScope;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public InstanceDataResource(ModelInstanceService instanceService) {
        this(instanceService, Optional.empty(), "dev", Clock.systemUTC());
    }

    public InstanceDataResource(
            ModelInstanceService instanceService,
            Optional<ModelInstanceDumpStore> dumpStore,
            String dumpScope,
            Clock clock
    ) {
        this.instanceService = Objects.requireNonNull(instanceService, "instanceService must not be null");
        this.dumpService = new ModelInstanceDumpService(instanceService);
        this.dumpStore = Objects.requireNonNull(dumpStore, "dumpStore must not be null");
        this.dumpScope = requireText(dumpScope, "dumpScope");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
        routes.get("/data/{modelAzName}/roots", context -> {
            try {
                List<ModelInstanceRootResponse> roots = instanceService.listRoots(context.pathParam("modelAzName"))
                        .stream()
                        .map(ModelInstanceRootResponse::from)
                        .toList();
                writeJson(context, 200, roots);
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.post("/data/{modelAzName}/roots", context -> {
            try {
                CreateModelInstanceRootRequest request = context.body().isBlank()
                        ? new CreateModelInstanceRootRequest(null)
                        : objectMapper.readValue(context.body(), CreateModelInstanceRootRequest.class);
                ModelInstanceRoot root = instanceService.createRoot(context.pathParam("modelAzName"), request.visName());
                writeJson(context, 201, ModelInstanceRootResponse.from(root));
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.get("/data/{modelAzName}/roots/{instanceRootId}", context -> {
            try {
                ModelInstanceRoot root = instanceService.readRoot(
                        context.pathParam("modelAzName"),
                        context.pathParam("instanceRootId")
                );
                writeJson(context, 200, ModelInstanceRootResponse.from(root));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.put("/data/{modelAzName}/roots/{instanceRootId}", context -> {
            try {
                RenameModelInstanceRootRequest request = objectMapper.readValue(context.body(), RenameModelInstanceRootRequest.class);
                ModelInstanceRoot root = instanceService.renameRoot(
                        context.pathParam("modelAzName"),
                        context.pathParam("instanceRootId"),
                        request.visName()
                );
                writeJson(context, 200, ModelInstanceRootResponse.from(root));
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.get("/data/{modelAzName}/roots/{instanceRootId}/_api", context -> {
            try {
                ModelRoot modelRoot = instanceService.describeApi(context.pathParam("modelAzName"));
                instanceService.readRoot(context.pathParam("modelAzName"), context.pathParam("instanceRootId"));
                writeJson(context, 200, ApiDescriptionResponse.from(modelRoot));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.get("/data/{modelAzName}/roots/{instanceRootId}/dump", context -> {
            try {
                ModelInstanceDump dump = dumpService.exportDump(
                        context.pathParam("modelAzName"),
                        context.pathParam("instanceRootId"),
                        Instant.now(clock)
                );
                writeJson(context, 200, DumpResponse.from(dump));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.post("/data/{modelAzName}/dumps/_precheck", context -> {
            try {
                ModelInstanceDump dump = objectMapper.readValue(context.body(), DumpRequest.class).toCore();
                writeJson(context, 200, DumpPrecheckResponse.from(dumpService.precheck(context.pathParam("modelAzName"), dump)));
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.post("/data/{modelAzName}/dumps", context -> {
            try {
                ImportDumpRequest request = objectMapper.readValue(context.body(), ImportDumpRequest.class);
                ModelInstanceDumpImportResult result = dumpService.importDump(
                        context.pathParam("modelAzName"),
                        request.dump().toCore(),
                        request.confirmVersionMismatch()
                );
                writeJson(context, 201, DumpImportResponse.from(result));
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.get("/data/{modelAzName}/dumps", context -> {
            try {
                ModelInstanceDumpStore store = configuredDumpStore();
                List<DumpSummaryResponse> dumps = store.listDumps(dumpScope, context.pathParam("modelAzName"))
                        .stream()
                        .map(DumpSummaryResponse::from)
                        .toList();
                writeJson(context, 200, dumps);
            } catch (IOException exception) {
                writeJson(context, 500, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.put("/data/{modelAzName}/roots/{instanceRootId}/dumps/{dumpName}", context -> {
            try {
                ModelInstanceDumpStore store = configuredDumpStore();
                ModelInstanceDump dump = dumpService.exportDump(
                        context.pathParam("modelAzName"),
                        context.pathParam("instanceRootId"),
                        Instant.now(clock)
                );
                String content = objectMapper.writeValueAsString(DumpResponse.from(dump));
                ModelInstanceDumpDescriptor descriptor = descriptorFor(
                        context.pathParam("dumpName"),
                        dump
                );
                writeJson(context, 200, DumpSummaryResponse.from(store.writeDump(
                        dumpScope,
                        dump.model().azName(),
                        context.pathParam("dumpName"),
                        content,
                        descriptor
                )));
            } catch (IOException exception) {
                writeJson(context, 500, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.post("/data/{modelAzName}/dumps/{dumpKey}/_precheck", context -> {
            try {
                ModelInstanceDump dump = readStoredDump(context.pathParam("dumpKey"));
                writeJson(context, 200, DumpPrecheckResponse.from(dumpService.precheck(context.pathParam("modelAzName"), dump)));
            } catch (IOException exception) {
                writeJson(context, 500, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.post("/data/{modelAzName}/dumps/{dumpKey}/load", context -> {
            try {
                LoadStoredDumpRequest request = context.body().isBlank()
                        ? new LoadStoredDumpRequest(false)
                        : objectMapper.readValue(context.body(), LoadStoredDumpRequest.class);
                ModelInstanceDump dump = readStoredDump(context.pathParam("dumpKey"));
                ModelInstanceDumpImportResult result = dumpService.importDump(
                        context.pathParam("modelAzName"),
                        dump,
                        request.confirmVersionMismatch()
                );
                writeJson(context, 201, DumpImportResponse.from(result));
            } catch (IOException exception) {
                writeJson(context, 500, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.post("/data/{modelAzName}/roots/{instanceRootId}/_links/{associationAzName}", context -> {
            try {
                CreateLinkRequest request = objectMapper.readValue(context.body(), CreateLinkRequest.class);
                request.requireComplete();
                AssociationInstanceLink link = instanceService.createAssociationLink(
                        context.pathParam("modelAzName"),
                        context.pathParam("instanceRootId"),
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
        routes.get("/data/{modelAzName}/roots/{instanceRootId}/_links/{associationAzName}", context -> {
            try {
                List<LinkResponse> links = instanceService
                        .listAssociationLinks(
                                context.pathParam("modelAzName"),
                                context.pathParam("instanceRootId"),
                                context.pathParam("associationAzName")
                        )
                        .stream()
                        .map(LinkResponse::from)
                        .toList();
                writeJson(context, 200, links);
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.post("/data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/_query", context -> {
            try {
                QueryRequest request = objectMapper.readValue(context.body(), QueryRequest.class);
                EntityInstanceQuery query = request.toCoreQuery();
                List<EntityInstanceResponse> instances = instanceService
                        .queryEntityInstances(
                                context.pathParam("modelAzName"),
                                context.pathParam("instanceRootId"),
                                context.pathParam("entityAzName"),
                                query
                        )
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
        routes.post("/data/{modelAzName}/roots/{instanceRootId}/{entityAzName}", context -> {
            try {
                LinkedHashMap<String, Object> values = objectMapper.readValue(context.body(), ATTRIBUTE_MAP_TYPE);
                EntityInstance instance = instanceService.createEntityInstance(
                        context.pathParam("modelAzName"),
                        context.pathParam("instanceRootId"),
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
        routes.get("/data/{modelAzName}/roots/{instanceRootId}/{entityAzName}", context -> {
            try {
                List<EntityInstanceResponse> instances = instanceService
                        .listEntityInstances(
                                context.pathParam("modelAzName"),
                                context.pathParam("instanceRootId"),
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
        routes.get("/data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/_count", context -> {
            try {
                int count = instanceService.countEntityInstances(
                        context.pathParam("modelAzName"),
                        context.pathParam("instanceRootId"),
                        context.pathParam("entityAzName")
                );
                writeJson(context, 200, new CountResponse(count));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.get("/data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/{instanceId}", context -> {
            try {
                EntityInstance instance = instanceService.readEntityInstance(
                        context.pathParam("modelAzName"),
                        context.pathParam("instanceRootId"),
                        context.pathParam("entityAzName"),
                        context.pathParam("instanceId")
                );
                writeJson(context, 200, EntityInstanceResponse.from(instance));
            } catch (IllegalArgumentException exception) {
                writeError(context, statusFor(exception), exception);
            }
        });
        routes.put("/data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/{instanceId}", context -> {
            try {
                LinkedHashMap<String, Object> values = objectMapper.readValue(context.body(), ATTRIBUTE_MAP_TYPE);
                EntityInstance instance = instanceService.updateEntityInstance(
                        context.pathParam("modelAzName"),
                        context.pathParam("instanceRootId"),
                        context.pathParam("entityAzName"),
                        context.pathParam("instanceId"),
                        values
                );
                writeJson(context, 200, EntityInstanceResponse.from(instance));
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
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
                || "instance not found".equals(message)
                || "model instance root not found".equals(message)) {
            return 404;
        }
        return 400;
    }

    private void writeJson(Context context, int status, Object body) throws JsonProcessingException {
        context.status(status)
                .contentType("application/json")
                .result(objectMapper.writeValueAsString(body));
    }

    private ModelInstanceDumpStore configuredDumpStore() throws IOException {
        return dumpStore.orElseThrow(() -> new IOException("Cloud dump store is not configured."));
    }

    private ModelInstanceDump readStoredDump(String dumpKey) throws IOException {
        ModelInstanceDumpContent content = configuredDumpStore().readDump(dumpScope, dumpKey)
                .orElseThrow(() -> new IOException("cloud dump not found: " + dumpKey));
        try {
            return objectMapper.readValue(content.content(), DumpRequest.class).toCore();
        } catch (JsonProcessingException exception) {
            throw new IOException("cloud dump parse failed: " + exception.getMessage(), exception);
        }
    }

    private static ModelInstanceDumpDescriptor descriptorFor(String dumpName, ModelInstanceDump dump) {
        return new ModelInstanceDumpDescriptor(
                dump.model().azName() + "/" + dumpName + ".vdmp",
                dump.model().azName(),
                dump.model().visName(),
                dump.model().version(),
                dump.root().visName(),
                dump.entities().stream().mapToInt(group -> group.records().size()).sum(),
                dump.links().size(),
                dump.savedAt()
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
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
            List<ScalarComparison> comparisons = where == null || where.comparisons() == null
                    ? List.of()
                    : where.comparisons().stream().map(ComparisonRequest::toCoreComparison).toList();
            List<RelationshipPredicate> predicates = relationships == null
                    ? List.of()
                    : relationships.stream().map(RelationshipRequest::toCorePredicate).toList();
            return new EntityInstanceQuery(equals, comparisons, predicates);
        }
    }

    private record WhereRequest(Map<String, Object> equals, List<ComparisonRequest> comparisons) {
    }

    private record ComparisonRequest(String attributeAzName, String operator, Object value) {

        ScalarComparison toCoreComparison() {
            if (attributeAzName == null || attributeAzName.isBlank()) {
                throw new IllegalArgumentException("comparison attributeAzName is required");
            }
            if (value == null) {
                throw new IllegalArgumentException("comparison value is required");
            }
            return new ScalarComparison(
                    attributeAzName,
                    ScalarComparisonOperator.parse(operator),
                    value
            );
        }
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
            List<ScalarComparison> comparisons = where == null || where.comparisons() == null
                    ? List.of()
                    : where.comparisons().stream().map(ComparisonRequest::toCoreComparison).toList();
            return new RelationshipPredicate(
                    associationAzName,
                    RelationshipDirection.parse(direction),
                    entityAzName,
                    equals,
                    comparisons
            );
        }
    }

    private record RenameModelInstanceRootRequest(String visName) {
    }

    private record CreateModelInstanceRootRequest(String visName) {
    }

    private record ImportDumpRequest(DumpRequest dump, boolean confirmVersionMismatch) {

        private ImportDumpRequest {
            Objects.requireNonNull(dump, "dump must not be null");
        }
    }

    private record LoadStoredDumpRequest(boolean confirmVersionMismatch) {
    }

    private record DumpRequest(
            String format,
            int formatVersion,
            String savedAt,
            DumpModelRequest model,
            DumpRootRequest root,
            List<DumpEntityGroupRequest> entities,
            List<DumpAssociationLinkRequest> links
    ) {

        private ModelInstanceDump toCore() {
            return new ModelInstanceDump(
                    format,
                    formatVersion,
                    Instant.parse(savedAt),
                    model.toCore(),
                    root.toCore(),
                    entities == null ? List.of() : entities.stream().map(DumpEntityGroupRequest::toCore).toList(),
                    links == null ? List.of() : links.stream().map(DumpAssociationLinkRequest::toCore).toList()
            );
        }
    }

    private record DumpModelRequest(String azName, String visName, String version) {

        private DumpModel toCore() {
            return new DumpModel(azName, visName, version);
        }
    }

    private record DumpRootRequest(String sourceInstanceRootId, String visName) {

        private DumpRoot toCore() {
            return new DumpRoot(sourceInstanceRootId, visName);
        }
    }

    private record DumpEntityGroupRequest(String entityAzName, List<DumpEntityRecordRequest> records) {

        private DumpEntityGroup toCore() {
            return new DumpEntityGroup(
                    entityAzName,
                    records == null ? List.of() : records.stream().map(DumpEntityRecordRequest::toCore).toList()
            );
        }
    }

    private record DumpEntityRecordRequest(String dumpId, Map<String, Object> values) {

        private DumpEntityRecord toCore() {
            return new DumpEntityRecord(dumpId, values == null ? Map.of() : values);
        }
    }

    private record DumpAssociationLinkRequest(String associationAzName, String sourceDumpId, String targetDumpId) {

        private DumpAssociationLink toCore() {
            return new DumpAssociationLink(associationAzName, sourceDumpId, targetDumpId);
        }
    }

    private record DumpResponse(
            String format,
            int formatVersion,
            String savedAt,
            DumpModel model,
            DumpRoot root,
            List<DumpEntityGroup> entities,
            List<DumpAssociationLink> links
    ) {

        private static DumpResponse from(ModelInstanceDump dump) {
            return new DumpResponse(
                    dump.format(),
                    dump.formatVersion(),
                    dump.savedAt().toString(),
                    dump.model(),
                    dump.root(),
                    dump.entities(),
                    dump.links()
            );
        }
    }

    private record DumpPrecheckResponse(
            boolean importable,
            boolean confirmationRequired,
            List<String> warnings,
            List<String> diagnostics
    ) {

        private static DumpPrecheckResponse from(ModelInstanceDumpPrecheckResult result) {
            return new DumpPrecheckResponse(
                    result.importable(),
                    result.confirmationRequired(),
                    result.warnings(),
                    result.diagnostics()
            );
        }
    }

    private record DumpImportResponse(
            ModelInstanceRootResponse root,
            Map<String, Integer> createdEntityCounts,
            int createdAssociationLinkCount,
            int skippedDuplicateLinkCount,
            List<String> warnings,
            List<String> failedInserts
    ) {

        private static DumpImportResponse from(ModelInstanceDumpImportResult result) {
            return new DumpImportResponse(
                    ModelInstanceRootResponse.from(result.root()),
                    result.createdEntityCounts(),
                    result.createdAssociationLinkCount(),
                    result.skippedDuplicateLinkCount(),
                    result.warnings(),
                    result.failedInserts()
            );
        }
    }

    private record DumpSummaryResponse(
            String key,
            String modelAzName,
            String modelVisName,
            String modelVersion,
            String rootVisName,
            int entityRecordCount,
            int associationLinkCount,
            String savedAt
    ) {

        private static DumpSummaryResponse from(ModelInstanceDumpDescriptor descriptor) {
            return new DumpSummaryResponse(
                    descriptor.key(),
                    descriptor.modelAzName(),
                    descriptor.modelVisName(),
                    descriptor.modelVersion(),
                    descriptor.rootVisName(),
                    descriptor.entityRecordCount(),
                    descriptor.associationLinkCount(),
                    descriptor.savedAt().toString()
            );
        }
    }

    private record ModelInstanceRootResponse(String instanceRootId, String modelAzName, String modelVersion, String visName) {

        private static ModelInstanceRootResponse from(ModelInstanceRoot root) {
            return new ModelInstanceRootResponse(root.instanceRootId(), root.modelAzName(), root.modelVersion(), root.visName());
        }
    }

    private record ApiDescriptionResponse(
            String modelAzName,
            String modelVisName,
            String modelVersion,
            List<ValueSetDescriptionResponse> valueSets,
            List<EntityDescriptionResponse> entities,
            List<AssociationDescriptionResponse> associations
    ) {

        private static ApiDescriptionResponse from(ModelRoot modelRoot) {
            return new ApiDescriptionResponse(
                    modelRoot.azName(),
                    modelRoot.visName(),
                    modelRoot.version().toString(),
                    modelRoot.valueSets().stream().map(ValueSetDescriptionResponse::from).toList(),
                    modelRoot.entities().stream().map(EntityDescriptionResponse::from).toList(),
                    modelRoot.associations().stream().map(AssociationDescriptionResponse::from).toList()
            );
        }
    }

    private record ValueSetDescriptionResponse(String azName, String dataType, List<ValueSetEntryDescriptionResponse> entries) {

        private static ValueSetDescriptionResponse from(ValueSet valueSet) {
            return new ValueSetDescriptionResponse(
                    valueSet.azName(),
                    valueSet.type().name(),
                    valueSet.entries().stream().map(ValueSetEntryDescriptionResponse::from).toList()
            );
        }
    }

    private record ValueSetEntryDescriptionResponse(Object technicalValue, String visName) {

        private static ValueSetEntryDescriptionResponse from(ValueSetEntry entry) {
            return new ValueSetEntryDescriptionResponse(entry.technicalValue(), entry.visName());
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
            operations.put("create", "/data/{modelAzName}/roots/{instanceRootId}/" + entity.azName());
            operations.put("list", "/data/{modelAzName}/roots/{instanceRootId}/" + entity.azName());
            operations.put("read", "/data/{modelAzName}/roots/{instanceRootId}/" + entity.azName() + "/{instanceId}");
            operations.put("update", "/data/{modelAzName}/roots/{instanceRootId}/" + entity.azName() + "/{instanceId}");
            operations.put("query", "/data/{modelAzName}/roots/{instanceRootId}/" + entity.azName() + "/_query");
            operations.put("count", "/data/{modelAzName}/roots/{instanceRootId}/" + entity.azName() + "/_count");
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
                case DATE -> "2026-08-12";
                case TIME -> "18:30:00";
                case DATETIME -> "2026-08-12T18:30";
                case LOCATION -> "{\"latitude\":62.1234567,\"longitude\":30.1234567}";
            };
        }
    }

    private record AttributeDescriptionResponse(String azName, String visName, String dataType, String valueSetAzName) {

        private static AttributeDescriptionResponse from(VAttribute attribute) {
            return new AttributeDescriptionResponse(attribute.azName(), attribute.visName(), attribute.type().name(), attribute.valueSetAzName());
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
            operations.put("create", "/data/{modelAzName}/roots/{instanceRootId}/_links/" + association.azName());
            operations.put("list", "/data/{modelAzName}/roots/{instanceRootId}/_links/" + association.azName());
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
            if (value.value() instanceof LocationValue locationValue) {
                Map<String, Object> location = new LinkedHashMap<>();
                location.put("latitude", locationValue.latitude());
                location.put("longitude", locationValue.longitude());
                return location;
            }
            return value.value();
        }
    }

    private record CountResponse(int count) {
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
