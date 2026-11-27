INSERT INTO transaction_entity (id, amount, category, created_at, description, source, user_id)
VALUES (UNHEX('00000000000000000000000000000001'), 2599, 'GROCERIES',
        '2025-01-02 03:04:05.123456', 'Legacy groceries', 'MANUAL',
        UNHEX('00000000000000000000000000000010')),
       (UNHEX('00000000000000000000000000000004'), 1099, 'GROCERIES',
        NULL, 'Legacy transaction with no date', 'AI',
        UNHEX('00000000000000000000000000000010'));

INSERT INTO conversation_sessions (id, expires_at, last_category, last_period,
    pending_amount_in_cents, pending_category, pending_confirmation_token,
    pending_description, pending_intent, pending_period, user_id)
VALUES (UNHEX('00000000000000000000000000000002'), '2025-01-02 04:04:05.654321',
        0, '2025-01', 1099, 'PHARMA', UNHEX('00000000000000000000000000000003'),
        'Legacy pharmacy', 'CREATE', '2025-01', UNHEX('00000000000000000000000000000010'));
