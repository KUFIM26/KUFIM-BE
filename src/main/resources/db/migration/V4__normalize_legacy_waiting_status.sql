UPDATE waiting SET status = 'ENTERED', close_reason = 'LEGACY_COMPLETED'
WHERE status = 'COMPLETED';

UPDATE waiting SET status = 'AUTO_CANCELED', close_reason = 'LEGACY_EXPIRED'
WHERE status = 'EXPIRED';

UPDATE waiting_event SET from_status = 'ENTERED' WHERE from_status = 'COMPLETED';
UPDATE waiting_event SET to_status = 'ENTERED' WHERE to_status = 'COMPLETED';
UPDATE waiting_event SET from_status = 'AUTO_CANCELED' WHERE from_status = 'EXPIRED';
UPDATE waiting_event SET to_status = 'AUTO_CANCELED' WHERE to_status = 'EXPIRED';
