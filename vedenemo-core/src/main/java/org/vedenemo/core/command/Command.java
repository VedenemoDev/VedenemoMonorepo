package org.vedenemo.core.command;

/**
 * Marker interface for Vedenemo commands.
 */
public sealed interface Command permits ClearAttributeValueSetCommand, CreateAssociationCommand, CreateAttributeCommand, CreateEntityCommand, CreateValueSetCommand, DeleteAssociationCommand, DeleteAttributeCommand, DeleteEntityCommand, DeleteValueSetCommand, NoOpCommand, SetAttributeValueSetCommand {
}
