package org.vedenemo.core.command;

/**
 * Marker interface for future Vedenemo commands.
 *
 * Real command types are intentionally not implemented yet.
 */
public sealed interface Command permits NoOpCommand {
}
