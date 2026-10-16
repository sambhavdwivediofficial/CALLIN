-- Connection requests between users (the "add contact" flow), and a
-- minimal, fully anonymous call-analytics counter.
--
-- Deliberately excludes anything richer: no call history, no record
-- of who called whom, no contact lists. CALLIN keeps that on-device
-- only. This table only proves two accounts agreed to connect, and
-- call_stats only proves a call actually connected — nothing links
-- back to who was on it.

CREATE TABLE IF NOT EXISTS connections (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status        VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at  TIMESTAMPTZ,
    CONSTRAINT chk_connections_status CHECK (status IN ('pending', 'accepted', 'rejected')),
    CONSTRAINT uq_connections_pair UNIQUE (requester_id, addressee_id)
);

CREATE INDEX IF NOT EXISTS idx_connections_addressee ON connections (addressee_id, status);
CREATE INDEX IF NOT EXISTS idx_connections_requester ON connections (requester_id, status);

-- One row per call that actually connected (both sides joined).
-- No caller/callee reference at all — just proof the system works,
-- for our own aggregate "how many calls today" number.
CREATE TABLE IF NOT EXISTS call_stats (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connected_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
