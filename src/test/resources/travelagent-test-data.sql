-- Test-only TravelAgent account used by the local workflow test.
INSERT INTO customer (id, name, email, phone_number)
VALUES (1, 'TravelAgent', 'travelagent-workflow-test@example.test', '01234567890');

UPDATE hibernate_sequences SET next_val = 1 WHERE sequence_name = 'customer';
