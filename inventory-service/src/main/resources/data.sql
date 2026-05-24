-- Seed initial stock levels. ON CONFLICT DO NOTHING makes this safe to run on every restart.
INSERT INTO inventory (product_id, available_quantity, reserved_quantity, version)
VALUES
    ('PROD-001', 100, 0, 0),
    ('PROD-002', 50,  0, 0),
    ('PROD-OUT', 0,   0, 0)
ON CONFLICT (product_id) DO NOTHING;