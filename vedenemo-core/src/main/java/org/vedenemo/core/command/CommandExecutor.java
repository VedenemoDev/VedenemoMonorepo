package org.vedenemo.core.command;

import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.AssociationKind;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.OwnershipAssociation;
import org.vedenemo.core.model.ReferenceAssociation;
import org.vedenemo.core.model.RelationAssociation;
import org.vedenemo.core.model.RelationEnd;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.spi.storage.ModelStorage;
import org.vedenemo.core.session.Session;

import java.util.Objects;
import java.util.Optional;

/**
 * Executes commands in the context of one active session.
 */
public final class CommandExecutor {

    private final ModelStorage modelStorage;
    private final ModelRegistry modelRegistry;
    private final ModelCommandJournal commandJournal;
    private final Session session;

    public CommandExecutor(ModelStorage modelStorage, ModelRegistry modelRegistry, Session session) {
        this(modelStorage, modelRegistry, new ModelCommandJournal(), session);
    }

    public CommandExecutor(
            ModelStorage modelStorage,
            ModelRegistry modelRegistry,
            ModelCommandJournal commandJournal,
            Session session
    ) {
        this.modelStorage = Objects.requireNonNull(modelStorage, "modelStorage must not be null");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
        this.commandJournal = Objects.requireNonNull(commandJournal, "commandJournal must not be null");
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    public void execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        apply(command);
        session.record(command);
        commandJournal.record(command);
    }

    public UndoResult undoLatest() {
        Optional<Command> latestCommand = session.latestCommand();
        if (latestCommand.isEmpty()) {
            return UndoResult.NOTHING_TO_UNDO;
        }
        UndoResult result = undo(latestCommand.orElseThrow());
        session.removeLatestCommand();
        commandJournal.removeLatest(latestCommand.orElseThrow());
        return result;
    }

    private UndoResult undo(Command command) {
        if (command instanceof CreateEntityCommand createEntityCommand) {
            apply(new DeleteEntityCommand(createEntityCommand.modelAzName(), createEntityCommand.entityAzName()));
            return UndoResult.undoneCreateEntity(createEntityCommand.modelAzName(), createEntityCommand.entityAzName());
        }
        if (command instanceof CreateAttributeCommand createAttributeCommand) {
            apply(new DeleteAttributeCommand(
                    createAttributeCommand.modelAzName(),
                    createAttributeCommand.entityAzName(),
                    createAttributeCommand.attributeAzName()
            ));
            return UndoResult.undoneCreateAttribute(
                    createAttributeCommand.modelAzName(),
                    createAttributeCommand.entityAzName(),
                    createAttributeCommand.attributeAzName()
            );
        }
        if (command instanceof CreateAssociationCommand createAssociationCommand) {
            apply(new DeleteAssociationCommand(
                    createAssociationCommand.modelAzName(),
                    createAssociationCommand.associationAzName()
            ));
            return UndoResult.undoneCreateAssociation(
                    createAssociationCommand.modelAzName(),
                    createAssociationCommand.associationAzName()
            );
        }
        throw new IllegalStateException("command has no undo counterpart: " + command.getClass().getSimpleName());
    }

    private void apply(Command command) {
        if (command instanceof CreateEntityCommand createEntityCommand) {
            applyCreateEntity(createEntityCommand);
        } else if (command instanceof CreateAttributeCommand createAttributeCommand) {
            applyCreateAttribute(createAttributeCommand);
        } else if (command instanceof CreateAssociationCommand createAssociationCommand) {
            applyCreateAssociation(createAssociationCommand);
        } else if (command instanceof DeleteAssociationCommand deleteAssociationCommand) {
            applyDeleteAssociation(deleteAssociationCommand);
        } else if (command instanceof DeleteAttributeCommand deleteAttributeCommand) {
            applyDeleteAttribute(deleteAttributeCommand);
        } else if (command instanceof DeleteEntityCommand deleteEntityCommand) {
            applyDeleteEntity(deleteEntityCommand);
        } else if (command instanceof NoOpCommand) {
            // NoOpCommand intentionally has no model-changing behavior.
        } else {
            throw new IllegalArgumentException("unsupported command: " + command.getClass().getSimpleName());
        }
    }

    private void applyCreateEntity(CreateEntityCommand command) {
        ModelRoot modelRoot = selectedModel(command.modelAzName());
        modelRoot.addEntity(new VEntity(command.entityAzName(), command.entityVisName(), modelRoot.version()));
    }

    private void applyCreateAttribute(CreateAttributeCommand command) {
        ModelRoot modelRoot = selectedModel(command.modelAzName());
        VEntity entity = findEntity(modelRoot, command.entityAzName());
        entity.addAttribute(new VAttribute(
                command.attributeAzName(),
                command.attributeVisName(),
                command.dataType(),
                modelRoot.version()
        ));
    }

    private void applyCreateAssociation(CreateAssociationCommand command) {
        ModelRoot modelRoot = selectedModel(command.modelAzName());
        modelRoot.addAssociation(toAssociation(command, modelRoot));
    }

    private void applyDeleteAttribute(DeleteAttributeCommand command) {
        ModelRoot modelRoot = modelRegistry.find(command.modelAzName())
                .orElseThrow(() -> new IllegalStateException("selected model not found"));
        VEntity entity = findEntity(modelRoot, command.entityAzName());
        entity.removeAttribute(command.attributeAzName())
                .orElseThrow(() -> new IllegalStateException("attribute not found"));
    }

    private void applyDeleteAssociation(DeleteAssociationCommand command) {
        ModelRoot modelRoot = modelRegistry.find(command.modelAzName())
                .orElseThrow(() -> new IllegalStateException("selected model not found"));
        modelRoot.removeAssociation(command.associationAzName())
                .orElseThrow(() -> new IllegalStateException("association not found"));
    }

    private ModelRoot selectedModel(String commandModelAzName) {
        String selectedModelAzName = session.selectedModelAzName()
                .orElseThrow(() -> new IllegalStateException("no selected model"));
        if (!ModelRoot.uniquenessKey(selectedModelAzName).equals(ModelRoot.uniquenessKey(commandModelAzName))) {
            throw new IllegalStateException("command target model is not selected");
        }
        return modelRegistry.find(commandModelAzName)
                .orElseThrow(() -> new IllegalStateException("selected model not found"));
    }

    private void applyDeleteEntity(DeleteEntityCommand command) {
        ModelRoot modelRoot = modelRegistry.find(command.modelAzName())
                .orElseThrow(() -> new IllegalStateException("selected model not found"));
        modelRoot.removeEntity(command.entityAzName())
                .orElseThrow(() -> new IllegalStateException("entity not found"));
    }

    private static VEntity findEntity(ModelRoot modelRoot, String entityAzName) {
        String targetKey = VEntity.uniquenessKey(entityAzName);
        return modelRoot.entities().stream()
                .filter(entity -> VEntity.uniquenessKey(entity.azName()).equals(targetKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("entity not found"));
    }

    private static Association toAssociation(CreateAssociationCommand command, ModelRoot modelRoot) {
        if (command.kind() == AssociationKind.OWNERSHIP) {
            return new OwnershipAssociation(
                    command.associationAzName(),
                    command.associationVisName(),
                    command.sourceEntityAzName(),
                    command.targetEntityAzName(),
                    command.cardinality(),
                    modelRoot.version()
            );
        }
        if (command.kind() == AssociationKind.REFERENCE) {
            return new ReferenceAssociation(
                    command.associationAzName(),
                    command.associationVisName(),
                    command.sourceEntityAzName(),
                    command.targetEntityAzName(),
                    command.cardinality(),
                    modelRoot.version()
            );
        }
        if (command.kind() == AssociationKind.RELATION) {
            return new RelationAssociation(
                    command.associationAzName(),
                    command.associationVisName(),
                    new RelationEnd(command.sourceEntityAzName(), command.sourceRoleName(), command.sourceCardinality()),
                    new RelationEnd(command.targetEntityAzName(), command.targetRoleName(), command.targetCardinality()),
                    modelRoot.version()
            );
        }
        throw new IllegalArgumentException("unsupported association kind: " + command.kind());
    }

    public ModelStorage modelStorage() {
        return modelStorage;
    }

    public ModelRegistry modelRegistry() {
        return modelRegistry;
    }

    public ModelCommandJournal commandJournal() {
        return commandJournal;
    }

    public Session session() {
        return session;
    }
}
