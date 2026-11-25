-- Metadata only. Sample content is created explicitly by the demo profile.
CREATE TABLE demo_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    dataset_version INTEGER NOT NULL,
    initialized_at TIMESTAMP WITH TIME ZONE NOT NULL
);
