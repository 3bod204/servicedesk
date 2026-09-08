
INSERT INTO roles (name) VALUES
    ('ROLE_REQUESTER'),
    ('ROLE_AGENT'),
    ('ROLE_MANAGER'),
    ('ROLE_ADMIN');

INSERT INTO queues (name, description) VALUES
    ('Service Desk',  'General IT support requests'),
    ('Network',       'Connectivity, VPN, and firewall issues'),
    ('Applications',  'Business application support');

INSERT INTO categories (name, queue_id)
SELECT c.name, q.id
FROM queues q
JOIN (VALUES
    ('Service Desk', 'Hardware'),
    ('Service Desk', 'Software Installation'),
    ('Service Desk', 'Account Access'),
    ('Service Desk', 'Other'),
    ('Network',      'VPN'),
    ('Network',      'Wi-Fi'),
    ('Network',      'Firewall Request'),
    ('Applications', 'ERP'),
    ('Applications', 'Email'),
    ('Applications', 'Internal Tools')
) AS c (queue_name, name) ON c.queue_name = q.name;

INSERT INTO sla_policies (priority, first_response_minutes, resolution_minutes) VALUES
    ('URGENT',  15,   240),
    ('HIGH',    60,   480),
    ('MEDIUM',  240,  1440),
    ('LOW',     480,  4320);