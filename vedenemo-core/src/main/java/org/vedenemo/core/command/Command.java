package org.vedenemo.core.command;

/**
 * Marker interface for Vedenemo commands.
 */
public sealed interface Command permits CreateAttributeCommand, CreateEntityCommand, DeleteAttributeCommand, DeleteEntityCommand, NoOpCommand {
}
