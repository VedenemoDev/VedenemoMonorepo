# Model Instance Dump Format

This document is a placeholder for the future `.vdmp` model-instance data dump
format.

The format is not implemented or finalized yet. When development-time model
instance dump support is implemented, this document should define:

- dump file version and encoding;
- model metadata used for compatibility checks;
- model-instance root metadata;
- entity instance record structure;
- association link structure;
- value representation for each supported `DataType`;
- import validation and failure behavior;
- version/schema compatibility rules;
- CLI and browser/cloud storage behavior.

Until then, `.vdmp` files are planning-only and are not part of the current
runtime feature set.
