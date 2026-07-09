package org.vedenemo.web.api.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.registry.DuplicateModelRootException;
import org.vedenemo.core.registry.ModelRegistry;

import java.util.List;
import java.util.Objects;

public final class ModelsResource {

    private final ModelRegistry modelRegistry;
    private final ObjectMapper objectMapper;

    public ModelsResource(ModelRegistry modelRegistry) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
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
    }

    private void writeJson(io.javalin.http.Context context, int status, Object body) throws JsonProcessingException {
        context.status(status)
                .contentType("application/json")
                .result(objectMapper.writeValueAsString(body));
    }

    private record AddModelRequest(String azName, String visName, String version) {
    }

    private record ModelRootResponse(String azName, String visName, String version) {
        private static ModelRootResponse from(ModelRoot modelRoot) {
            return new ModelRootResponse(modelRoot.azName(), modelRoot.visName(), modelRoot.version().toString());
        }
    }

    private record ErrorResponse(String error) {
    }
}
