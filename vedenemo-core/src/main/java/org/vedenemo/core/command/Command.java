package org.vedenemo.core.command;

/**
 * Marker interface for Vedenemo commands.
 */
public sealed interface Command permits CreateAssociationCommand, CreateAttributeCommand, CreateEntityCommand, DeleteAssociationCommand, DeleteAttributeCommand, DeleteEntityCommand, NoOpCommand {
}
