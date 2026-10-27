CREATE TRIGGER audit_event_no_update_delete
    BEFORE UPDATE, DELETE ON audit_event
    FOR EACH ROW
    CALL 'io.github.codebyjuliamendes.agentledger.infrastructure.persistence.H2AppendOnlyTrigger';
