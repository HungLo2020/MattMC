-- Compressed provenance for the bounded horizontal contributor sidecar (DTO V4).
-- Older rows remain valid with a NULL value and are decoded as having no sidecar.
ALTER TABLE FullData ADD COLUMN SemanticHorizontalContributorData BLOB NULL;
