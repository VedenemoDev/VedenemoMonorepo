package org.vedenemo.web.api.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.DuplicateModelRootException;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.script.VedenemoScriptImportResult;
import org.vedenemo.core.script.VedenemoScriptService;
import org.vedenemo.web.api.events.ModelChangeBroadcaster;

import java.util.List;
import java.util.Objects;

public final class ModelsResource {

    private final ModelRegistry modelRegistry;
    private final VedenemoScriptService scriptService;
    private final ModelChangeBroadcaster modelChangeBroadcaster;
    private final ObjectMapper objectMapper;

    public ModelsResource(ModelRegistry modelRegistry) {
        this(modelRegistry, new ModelCommandJournal(), new ModelChangeBroadcaster());
    }

    public ModelsResource(ModelRegistry modelRegistry, ModelCommandJournal commandJournal) {
        this(modelRegistry, commandJournal, new ModelChangeBroadcaster());
    }

    public ModelsResource(
            ModelRegistry modelRegistry,
            ModelCommandJournal commandJournal,
            ModelChangeBroadcaster modelChangeBroadcaster
    ) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
        this.scriptService = new VedenemoScriptService(modelRegistry, Objects.requireNonNull(commandJournal, "commandJournal must not be null"));
        this.modelChangeBroadcaster = Objects.requireNonNull(modelChangeBroadcaster, "modelChangeBroadcaster must not be null");
        this.objectMapper = new ObjectMapper();
    }

    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/models/ping", context -> context
                .status(200)
                .contentType("application/json")
                .result("{\"status\":\"ok\"}"));
        routes.post("/models/add", context -> {
            try {
                AddModelRequest request = objectMapper.readValue(context.body(), AddModelRequest.class);
                ModelRoot modelRoot = modelRegistry.add(ModelRoot.create(request.azName(), request.visName(), request.version()));
                modelChangeBroadcaster.broadcastModelChanged(modelRoot.azName());
                writeJson(context, 201, ModelRootResponse.from(modelRoot));
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            } catch (DuplicateModelRootException exception) {
                writeJson(context, 409, new ErrorResponse(exception.getMessage()));
            }
        });
        routes.get("/models/list", context -> {
            List<ModelRootResponse> models = modelRegistry.list().stream()
                    .map(ModelRootResponse::from)
                    .toList();
            writeJson(context, 200, models);
        });
        routes.get("/models/{modelAzName}/entities", context -> {
            ModelRoot modelRoot = findModel(context.pathParam("modelAzName"));
            if (modelRoot == null) {
                writeJson(context, 404, new ErrorResponse("model not found"));
                return;
            }
            List<EntityResponse> entities = modelRoot.entities().stream()
                    .map(EntityResponse::from)
                    .toList();
            writeJson(context, 200, entities);
        });
        routes.get("/models/{modelAzName}/entities/{entityAzName}/attributes", context -> {
            ModelRoot modelRoot = findModel(context.pathParam("modelAzName"));
            if (modelRoot == null) {
                writeJson(context, 404, new ErrorResponse("model not found"));
                return;
            }
            VEntity entity = findEntity(modelRoot, context.pathParam("entityAzName"));
            if (entity == null) {
                writeJson(context, 404, new ErrorResponse("entity not found"));
                return;
            }
            List<AttributeResponse> attributes = entity.attributes().stream()
                    .map(AttributeResponse::from)
                    .toList();
            writeJson(context, 200, attributes);
        });
        routes.get("/models/{modelAzName}/associations", context -> {
            ModelRoot modelRoot = findModel(context.pathParam("modelAzName"));
            if (modelRoot == null) {
                writeJson(context, 404, new ErrorResponse("model not found"));
                return;
            }
            List<AssociationResponse> associations = modelRoot.associations().stream()
                    .map(AssociationResponse::from)
                    .toList();
            writeJson(context, 200, associations);
        });
        routes.get("/models/{modelAzName}/entities/{entityAzName}/associations", context -> {
            ModelRoot modelRoot = findModel(context.pathParam("modelAzName"));
            if (modelRoot == null) {
                writeJson(context, 404, new ErrorResponse("model not found"));
                return;
            }
            VEntity entity = findEntity(modelRoot, context.pathParam("entityAzName"));
            if (entity == null) {
                writeJson(context, 404, new ErrorResponse("entity not found"));
                return;
            }
            String entityKey = VEntity.uniquenessKey(entity.azName());
            List<AssociationResponse> associations = modelRoot.associations().stream()
                    .filter(association -> VEntity.uniquenessKey(association.sourceEntityAzName()).equals(entityKey)
                            || VEntity.uniquenessKey(association.targetEntityAzName()).equals(entityKey))
                    .map(AssociationResponse::from)
                    .toList();
            writeJson(context, 200, associations);
        });
        routes.get("/models/{modelAzName}/script", context -> {
            try {
                String script = scriptService.exportModel(context.pathParam("modelAzName"));
                context.status(200)
                        .contentType("text/plain; charset=utf-8")
                        .result(script);
            } catch (IllegalArgumentException exception) {
                writeJson(context, 404, new ErrorResponse(exception.getMessage()));
            }
        });
        routes.post("/models/script", context -> {
            try {
                VedenemoScriptImportResult result = scriptService.importModel(
                        context.body(),
                        context.queryParam("modelAzName")
                );
                modelChangeBroadcaster.broadcastModelChanged(result.modelAzName());
                writeJson(context, 201, new ScriptImportResponse(result.modelAzName(), result.commandCount()));
            } catch (IllegalStateException exception) {
                writeJson(context, 409, new ErrorResponse(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            }
        });
    }

    private void writeJson(io.javalin.http.Context context, int status, Object body) throws JsonProcessingException {
        context.status(status)
                .contentType("application/json")
                .result(objectMapper.writeValueAsString(body));
    }

    private ModelRoot findModel(String modelAzName) {
        try {
            return modelRegistry.find(modelAzName).orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static VEntity findEntity(ModelRoot modelRoot, String entityAzName) {
        String targetKey;
        try {
            targetKey = VEntity.uniquenessKey(entityAzName);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return modelRoot.entities().stream()
                .filter(entity -> VEntity.uniquenessKey(entity.azName()).equals(targetKey))
                .findFirst()
                .orElse(null);
    }

    private record AddModelRequest(String azName, String visName, String version) {
    }

    private record ModelRootResponse(String azName, String visName, String version) {
        private static ModelRootResponse from(ModelRoot modelRoot) {
            return new ModelRootResponse(modelRoot.azName(), modelRoot.visName(), modelRoot.version().toString());
        }
    }

    private record EntityResponse(String azName, String visName, String activeSince, String deprecatedSince) {
        private static EntityResponse from(VEntity entity) {
            return new EntityResponse(
                    entity.azName(),
                    entity.visName(),
                    entity.activeSince().toString(),
                    entity.deprecatedSince().map(Object::toString).orElse(null)
            );
        }
    }

    private record AttributeResponse(
            String azName,
            String visName,
            String dataType,
            String activeSince,
            String deprecatedSince
    ) {
        private static AttributeResponse from(VAttribute attribute) {
            return new AttributeResponse(
                    attribute.azName(),
                    attribute.visName(),
                    attribute.type().name(),
                    attribute.activeSince().toString(),
                    attribute.deprecatedSince().map(Object::toString).orElse(null)
            );
        }
    }

    private record AssociationResponse(
            String azName,
            String visName,
            String kind,
            String sourceEntityAzName,
            String targetEntityAzName,
            String cardinality,
            String activeSince,
            String deprecatedSince
    ) {
        private static AssociationResponse from(Association association) {
            return new AssociationResponse(
                    association.azName(),
                    association.visName(),
                    association.kind().name(),
                    association.sourceEntityAzName(),
                    association.targetEntityAzName(),
                    association.cardinality().toString(),
                    association.activeSince().toString(),
                    association.deprecatedSince().map(Object::toString).orElse(null)
            );
        }
    }

    private record ScriptImportResponse(String modelAzName, int commandCount) {
    }

    private record ErrorResponse(String error) {
    }
}
