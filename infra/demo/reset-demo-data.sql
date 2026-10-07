-- Resets the database to a clean, realistic demo dataset.
--
-- Keeps reference data (roles, queues, categories, SLA policies) and replaces
-- everything else: users, tickets, comments, attachments, audit history,
-- notifications, refresh tokens and login attempts.
--
-- Usage (with the docker-compose stack running):
--   docker exec -i servicedesk-postgres-1 psql -U servicedesk -d servicedesk -v ON_ERROR_STOP=1 < infra/demo/reset-demo-data.sql
--
-- All demo accounts use the password: Password123!

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

TRUNCATE notifications, audit_entries, attachments, comments, tickets,
         refresh_tokens, login_attempts, queue_members, user_roles, users
    RESTART IDENTITY;

ALTER SEQUENCE ticket_reference_seq RESTART WITH 1;

SELECT setseed(0.2026);

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
-- Agent emails are kept as-is so DevDataSeeder (local profile) still sees the
-- data as seeded and does not add random tickets on top of it.
CREATE TEMP TABLE demo_users (email TEXT, full_name TEXT, role TEXT, queue TEXT) ON COMMIT DROP;

INSERT INTO demo_users VALUES
    ('agent@tcc.sa',          'System Administrator', 'ROLE_ADMIN',     NULL),
    ('manager@tcc.sa',        'Maya Khoury',          'ROLE_MANAGER',   NULL),
    ('sara.agent@tcc.sa',     'Sara Haddad',          'ROLE_AGENT',     'Service Desk'),
    ('omar.agent@tcc.sa',     'Omar Nasser',          'ROLE_AGENT',     'Service Desk'),
    ('lina.agent@tcc.sa',     'Lina Fares',           'ROLE_AGENT',     'Network'),
    ('yousef.agent@tcc.sa',   'Yousef Saleh',         'ROLE_AGENT',     'Applications'),
    ('noor.agent@tcc.sa',     'Noor Aziz',            'ROLE_AGENT',     'Applications'),
    ('noor.agent@tcc.sa',     'Noor Aziz',            'ROLE_AGENT',     'Network'),
    ('ahmad.youssef@tcc.sa',  'Ahmad Youssef',        'ROLE_REQUESTER', NULL),
    ('rana.qasem@tcc.sa',     'Rana Qasem',           'ROLE_REQUESTER', NULL),
    ('khalid.odeh@tcc.sa',    'Khalid Odeh',          'ROLE_REQUESTER', NULL),
    ('hiba.saad@tcc.sa',      'Hiba Saad',            'ROLE_REQUESTER', NULL),
    ('tariq.amer@tcc.sa',     'Tariq Amer',           'ROLE_REQUESTER', NULL),
    ('dana.khalil@tcc.sa',    'Dana Khalil',          'ROLE_REQUESTER', NULL),
    ('faris.jaber@tcc.sa',    'Faris Jaber',          'ROLE_REQUESTER', NULL),
    ('lama.suleiman@tcc.sa',  'Lama Suleiman',        'ROLE_REQUESTER', NULL),
    ('bilal.hourani@tcc.sa',  'Bilal Hourani',        'ROLE_REQUESTER', NULL),
    ('mona.rasheed@tcc.sa',   'Mona Rasheed',         'ROLE_REQUESTER', NULL),
    ('reem.mansour@tcc.sa',   'Reem Mansour',         'ROLE_REQUESTER', NULL),
    ('zaid.barakat@tcc.sa',   'Zaid Barakat',         'ROLE_REQUESTER', NULL),
    ('salma.nimer@tcc.sa',    'Salma Nimer',          'ROLE_REQUESTER', NULL),
    ('hamza.darwish@tcc.sa',  'Hamza Darwish',        'ROLE_REQUESTER', NULL);

INSERT INTO users (email, password_hash, full_name, active, created_at, updated_at)
SELECT DISTINCT ON (email)
       email,
       (SELECT crypt('Password123!', gen_salt('bf', 10))),
       full_name,
       TRUE,
       NOW() - INTERVAL '180 days',
       NOW() - INTERVAL '180 days'
FROM demo_users
ORDER BY email;

INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT u.id, r.id
FROM demo_users d
JOIN users u ON u.email = d.email
JOIN roles r ON r.name = d.role;

INSERT INTO queue_members (queue_id, user_id)
SELECT q.id, u.id
FROM demo_users d
JOIN users u ON u.email = d.email
JOIN queues q ON q.name = d.queue;

-- ---------------------------------------------------------------------------
-- Ticket catalog: realistic requests per category
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE demo_catalog (
    id          SERIAL,
    category    TEXT,
    title       TEXT,
    description TEXT,
    resolution  TEXT,
    priority    TEXT
) ON COMMIT DROP;

INSERT INTO demo_catalog (category, title, description, resolution, priority) VALUES
-- Service Desk / Hardware
('Hardware', 'Laptop battery drains within an hour',
 'My laptop goes from 100% to 10% in about an hour, even with only Outlook and Teams open. It started last week after the BIOS update.',
 'Battery health report showed 41% capacity. Replaced the battery under warranty and recalibrated it.', 'MEDIUM'),
('Hardware', 'Second monitor not detected after docking',
 'When I connect my laptop to the docking station at my desk, the external monitor stays black. The laptop screen works fine.',
 'Updated the dock firmware and display driver. Both screens are now detected after docking.', 'MEDIUM'),
('Hardware', 'Printer on 2nd floor jamming repeatedly',
 'The printer near the finance area jams on almost every print job. Several people on the floor are affected.',
 'Replaced the worn pickup roller and cleared debris from the fuser. Printed 50 test pages without a jam.', 'HIGH'),
('Hardware', 'Request for replacement keyboard and mouse',
 'Several keys on my keyboard stick (E, R and the space bar). Requesting a replacement keyboard and mouse.',
 'Delivered a new wireless keyboard and mouse set to the requester''s desk.', 'LOW'),
('Hardware', 'Laptop overheating and shutting down',
 'My laptop shuts down without warning when I run large Excel reports. The bottom of the laptop gets very hot.',
 'Cleaned the fan and heatsink and reapplied thermal paste. A one-hour stress test passed without shutdown.', 'HIGH'),
-- Service Desk / Software Installation
('Software Installation', 'Install Power BI Desktop',
 'I need Power BI Desktop to build the monthly KPI dashboard. My manager has approved the request.',
 'Installed the latest Power BI Desktop through the company portal.', 'LOW'),
('Software Installation', 'Adobe Acrobat Pro license request',
 'I need to edit and sign PDF contracts. Please install Acrobat Pro and assign me a license.',
 'Assigned an Acrobat Pro license from the pool and installed the application.', 'LOW'),
('Software Installation', 'Microsoft Teams crashes on startup',
 'Teams closes immediately after the splash screen. Reinstalling it from the company portal did not help.',
 'Cleared the Teams cache and reinstalled the new Teams client. It is working normally now.', 'MEDIUM'),
('Software Installation', 'Need Python and VS Code for data analysis',
 'Requesting Python 3.12 and Visual Studio Code for analysing customer data exports.',
 'Installed Python 3.12, VS Code and the approved extensions from the software catalog.', 'LOW'),
-- Service Desk / Account Access
('Account Access', 'Account locked after password change',
 'I changed my password this morning and now I am locked out of Windows and of Outlook on my phone.',
 'Unlocked the account. The old password cached in the phone''s mail app was causing the lockouts, so we updated it on the phone.', 'HIGH'),
('Account Access', 'MFA prompts not arriving on new phone',
 'I got a new phone and the Microsoft Authenticator approvals no longer arrive, so I cannot sign in to the portal.',
 'Reset the MFA registration and guided the user through enrolling Authenticator on the new phone.', 'HIGH'),
('Account Access', 'Access to Finance shared drive',
 'Please grant me read/write access to the Finance Budget 2027 folder on the shared drive. My department head has approved it.',
 'Added the user to the FIN-Budget-RW security group and confirmed access.', 'MEDIUM'),
('Account Access', 'New employee account setup - Procurement',
 'A new employee joins the Procurement team on Sunday. They need an AD account, a mailbox and a laptop.',
 'Created the AD account and mailbox, prepared the laptop and handed the credentials to the line manager.', 'MEDIUM'),
('Account Access', 'Cannot reset password through self-service portal',
 'The self-service password reset page says my security info is not registered, but I set it up last year.',
 'The security info registration was incomplete. Re-registered it with the user and completed the password reset.', 'MEDIUM'),
-- Service Desk / Other
('Other', 'Meeting room display not casting',
 'The display in Meeting Room B on the 3rd floor does not show my laptop screen through the wireless casting dongle.',
 'Replaced the faulty casting dongle and re-paired it with the room display.', 'MEDIUM'),
('Other', 'Move workstation to a new desk',
 'I am moving from desk 2.08 to desk 4.21 next week. Please move my PC, phone and monitors.',
 'Relocated the workstation and patched the network port at the new desk.', 'LOW'),
('Other', 'Desk phone not ringing for incoming calls',
 'My desk phone (ext. 4417) does not ring for incoming calls, but outgoing calls work.',
 'Call forwarding to an old number was enabled. Removed the forwarding rule and tested incoming calls.', 'LOW'),
-- Network / VPN
('VPN', 'VPN disconnects every few minutes',
 'When I work from home, the VPN drops every 5-10 minutes and I lose my ERP session.',
 'Moved the user to the updated VPN profile with DTLS enabled. The connection has been stable for two days.', 'HIGH'),
('VPN', 'VPN rejects my credentials',
 'The VPN says my credentials or the SSL VPN configuration are wrong, but I can still sign in to email.',
 'The user was missing from the VPN-Users group after a department move. Added them back and verified the connection.', 'HIGH'),
('VPN', 'VPN access for external consultant',
 'Our consultant from the audit firm needs VPN access for 4 weeks to reach the reporting server.',
 'Created a VPN account that expires in 30 days and is restricted to the reporting subnet.', 'MEDIUM'),
('VPN', 'Slow file access over VPN',
 'Opening files from the shared drive over the VPN takes several minutes. It is fine in the office.',
 'Enabled split tunnelling for Microsoft 365 traffic and moved the user to the closer VPN gateway. Throughput improved about 4x.', 'MEDIUM'),
-- Network / Wi-Fi
('Wi-Fi', 'Weak Wi-Fi in the 3rd-floor east wing',
 'The Wi-Fi keeps dropping in the east wing meeting rooms on the 3rd floor, and video calls freeze.',
 'Installed an additional access point in the east wing and rebalanced the channels. Signal strength is now good across the area.', 'MEDIUM'),
('Wi-Fi', 'Guest Wi-Fi for visiting partner team',
 'We have 12 visitors from a partner company tomorrow from 9:00 to 15:00. Please provide guest Wi-Fi access.',
 'Generated 12 one-day guest vouchers and sent them to the requester.', 'LOW'),
('Wi-Fi', 'Laptop cannot join corporate Wi-Fi',
 'Since my laptop came back from repair, it shows "Can''t connect to this network" for the corporate Wi-Fi.',
 'The machine certificate had expired. Renewed it and re-applied the Wi-Fi profile.', 'MEDIUM'),
-- Network / Firewall Request
('Firewall Request', 'Allow HTTPS from integration server to payment API',
 'The integration server needs outbound HTTPS to the payment provider''s API for the new payment integration.',
 'Added the rule after the security review and tested connectivity from the integration server.', 'MEDIUM'),
('Firewall Request', 'Supplier portal blocked by web filter',
 'The supplier portal we use for purchase orders is blocked by the web filter as "Uncategorized".',
 'Reviewed the site and added it to the web filter allow list.', 'LOW'),
('Firewall Request', 'Allow SFTP from bank to file gateway',
 'The bank is changing its SFTP source IPs this week. The new range must be allowed or the daily statement transfer will fail.',
 'Updated the firewall object with the bank''s new IP range. The daily statement transfer succeeded.', 'HIGH'),
-- Applications / ERP
('ERP', 'ERP invoice posting fails - period closed',
 'When I post supplier invoices in the ERP I get "Period is closed for posting", but the September period should still be open.',
 'Reopened the September AP period with finance approval. The invoices posted successfully.', 'URGENT'),
('ERP', 'Purchase order approval stuck',
 'A purchase order has been waiting for approval for 3 days, but the approver does not see it in their inbox.',
 'The approver''s delegation had expired. Reassigned the workflow and the PO is now approved.', 'HIGH'),
('ERP', 'Inventory valuation report runs very slowly',
 'The monthly inventory valuation report takes over 40 minutes to run and sometimes times out.',
 'Rebuilt the indexes on the inventory tables and scheduled the report off-peak. It now runs in about 6 minutes.', 'MEDIUM'),
('ERP', 'New cost center needed in ERP',
 'Please create a new cost center for the Digital Transformation program, effective next month.',
 'Created cost center CC-4120 and linked it to the program budget.', 'LOW'),
('ERP', 'Payroll bank file export failing',
 'The payroll bank file export fails with a format error. Salaries must be transferred by the end of the day.',
 'Corrected the IBAN format on two employee records. The bank file was generated and accepted by the bank.', 'URGENT'),
-- Applications / Email
('Email', 'Mailbox full - cannot send emails',
 'Outlook says my mailbox is full and I cannot send emails. I need to send quotations to customers today.',
 'Enabled the online archive and applied the retention policy. The mailbox is now 45% full.', 'HIGH'),
('Email', 'Suspicious phishing email received',
 'I received an email that claims to be from IT and asks me to verify my password through a link. I did not click it.',
 'Confirmed it was phishing. Purged the message from all mailboxes and blocked the sender domain.', 'URGENT'),
('Email', 'Shared mailbox access for HR team',
 'Please give Dana and Lama access to the HR shared mailbox.',
 'Granted Full Access and Send As permissions. The mailbox now appears in Outlook.', 'LOW'),
('Email', 'Emails to external partner bouncing',
 'Emails to the partner''s domain bounce back with error 550 5.7.1.',
 'The partner''s mail server rejected our new SPF record. Updated the SPF record and confirmed delivery with the partner''s IT team.', 'HIGH'),
('Email', 'Outlook calendar not syncing on mobile',
 'Meetings I accept on my laptop do not appear in the calendar on my phone.',
 'Removed and re-added the account on the mobile device. The calendar sync is working now.', 'LOW'),
-- Applications / Internal Tools
('Internal Tools', 'HR portal error when submitting leave',
 'When I submit a leave request in the HR portal I get "Something went wrong" and the request is not saved.',
 'Fixed a validation bug affecting half-day requests and deployed hotfix 2.4.1.', 'HIGH'),
('Internal Tools', 'Access to project tracking tool',
 'I joined the PMO team and need editor access to the project tracking tool.',
 'Added the user to the PMO editors group in the project tracking tool.', 'LOW'),
('Internal Tools', 'Intranet search returns no results',
 'Since yesterday, searching the intranet for policies returns no results.',
 'The search index crawler had stopped. Restarted the service and re-indexed the content.', 'MEDIUM'),
('Internal Tools', 'Expense tool rejects PDF receipts',
 'Uploading receipts to the expense tool fails for PDF files larger than 2 MB.',
 'Raised the upload limit to 10 MB in the expense tool configuration.', 'MEDIUM');

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
CREATE FUNCTION pg_temp.audit(p_ticket BIGINT, p_actor BIGINT, p_field TEXT,
                              p_old TEXT, p_new TEXT, p_at TIMESTAMPTZ) RETURNS VOID AS $$
    INSERT INTO audit_entries (ticket_id, actor_id, field, old_value, new_value, created_at)
    VALUES (p_ticket, p_actor, p_field, p_old, p_new, p_at);
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.add_comment(p_ticket BIGINT, p_author BIGINT, p_body TEXT,
                                    p_internal BOOLEAN, p_at TIMESTAMPTZ) RETURNS VOID AS $$
    INSERT INTO comments (ticket_id, author_id, body, internal, created_at, updated_at)
    VALUES (p_ticket, p_author, p_body, p_internal, p_at, p_at);
    INSERT INTO audit_entries (ticket_id, actor_id, field, old_value, new_value, created_at)
    VALUES (p_ticket, p_author, 'comment', NULL,
            CASE WHEN p_internal THEN 'internal comment added' ELSE 'public comment added' END,
            p_at);
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.pick(arr TEXT[]) RETURNS TEXT AS $$
    SELECT arr[1 + floor(random() * array_length(arr, 1))::INT];
$$ LANGUAGE sql VOLATILE;

-- ---------------------------------------------------------------------------
-- Tickets, comments and audit history
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    status_plan TEXT[];
    catalog_ids INT[];
    st TEXT;
    n INT := 0;

    c RECORD;
    cat_id BIGINT;
    q_id BIGINT;
    prio TEXT;
    fr INT;
    res INT;
    requester RECORD;
    agent RECORD;
    manager_id BIGINT;
    assigner_id BIGINT;

    created TIMESTAMPTZ;
    assigned TIMESTAMPTZ;
    started TIMESTAMPTZ;
    pend_start TIMESTAMPTZ;
    pend_end TIMESTAMPTZ;
    resolved TIMESTAMPTZ;
    closed TIMESTAMPTZ;
    reopened TIMESTAMPTZ;
    updated TIMESTAMPTZ;
    pending_since_v TIMESTAMPTZ;

    frm DOUBLE PRECISION;
    work DOUBLE PRECISION;
    elapsed DOUBLE PRECISION;
    pend_total INT;
    fr_breach BOOLEAN;
    res_breach BOOLEAN;
    warn BOOLEAN;
    tid BIGINT;

    greetings TEXT[] := ARRAY[
        'Hi %s, thanks for reporting this. I''m looking into it now and will keep you updated here.',
        'Hello %s, I''ve picked up this ticket and started investigating.',
        'Hi %s, thanks for the details. I''m checking this now.',
        'Hi %s, I''m on it. I''ll reach out here if I need anything from you.'];
    questions TEXT[] := ARRAY[
        'Could you please share the exact error message and the time it last happened?',
        'Can you let me know a good time today for a short remote session so I can check this on your machine?',
        'Could you please get approval from your line manager and reply here once you have it?',
        'Can you confirm whether anyone else in your team is seeing the same issue?'];
    replies TEXT[] := ARRAY[
        'It happened again around 9:30 this morning with the same error.',
        'Any time after 1 pm works for me.',
        'My manager has approved this and I forwarded you the approval email.',
        'Yes, two colleagues in my team have the same problem.'];
    notes TEXT[] := ARRAY[
        'Checked the logs. The same pattern shows up on two other devices in this department, so I''m keeping an eye on it.',
        'Escalated to the vendor''s support team. Waiting for their analysis.',
        'Known issue since last week''s update. Applying the documented workaround.',
        'Needs a change window. Coordinated with the infrastructure team for tonight.'];
    thanks TEXT[] := ARRAY[
        'Thank you, everything works now.',
        'Confirmed fixed. Thanks for the quick help!',
        'All good on my side. Thanks!'];
    reopen_msgs TEXT[] := ARRAY[
        'The issue came back this morning. Could you please take another look?',
        'Unfortunately this has been happening again since yesterday.'];
BEGIN
    SELECT u.id INTO manager_id FROM users u WHERE u.email = 'manager@tcc.sa';

    status_plan :=
          array_fill('NEW'::TEXT,               ARRAY[9])
       || array_fill('ASSIGNED'::TEXT,          ARRAY[12])
       || array_fill('IN_PROGRESS'::TEXT,       ARRAY[18])
       || array_fill('PENDING_REQUESTER'::TEXT, ARRAY[10])
       || array_fill('RESOLVED'::TEXT,          ARRAY[24])
       || array_fill('CLOSED'::TEXT,            ARRAY[60])
       || array_fill('REOPENED'::TEXT,          ARRAY[5]);

    SELECT array_agg(id ORDER BY random()) INTO catalog_ids FROM demo_catalog;

    FOREACH st IN ARRAY status_plan LOOP
        n := n + 1;

        SELECT * INTO c FROM demo_catalog
        WHERE id = catalog_ids[1 + (n % array_length(catalog_ids, 1))];

        SELECT cat.id, cat.queue_id INTO cat_id, q_id
        FROM categories cat WHERE cat.name = c.category;

        prio := c.priority;
        IF random() < 0.2 THEN
            prio := pg_temp.pick(ARRAY['LOW', 'MEDIUM', 'MEDIUM', 'HIGH']);
        END IF;

        SELECT s.first_response_minutes, s.resolution_minutes INTO fr, res
        FROM sla_policies s WHERE s.priority = prio;

        SELECT u.id, u.email, split_part(u.full_name, ' ', 1) AS first_name INTO requester
        FROM users u
        JOIN user_roles ur ON ur.user_id = u.id
        JOIN roles r ON r.id = ur.role_id AND r.name = 'ROLE_REQUESTER'
        ORDER BY random() LIMIT 1;

        SELECT u.id, u.email INTO agent
        FROM users u JOIN queue_members qm ON qm.user_id = u.id
        WHERE qm.queue_id = q_id
        ORDER BY random() LIMIT 1;

        assigner_id := CASE WHEN random() < 0.5 THEN manager_id ELSE agent.id END;

        assigned := NULL; started := NULL; pend_start := NULL; pend_end := NULL;
        resolved := NULL; closed := NULL; reopened := NULL; pending_since_v := NULL;
        pend_total := 0; fr_breach := FALSE; res_breach := FALSE; warn := FALSE;

        -- First-response time in minutes: ~10% breach the SLA target
        frm := fr * CASE WHEN random() < 0.1 THEN 1.1 + random() * 0.9
                         ELSE 0.15 + random() * 0.8 END;

        IF st = 'NEW' THEN
            created := NOW() - (fr * (0.05 + random() * 0.6)) * INTERVAL '1 minute';
            updated := created;

        ELSIF st IN ('ASSIGNED', 'IN_PROGRESS', 'PENDING_REQUESTER') THEN
            -- Open work: ~15% already over the resolution SLA
            created := NOW() - (res * CASE WHEN random() < 0.15 THEN 1.05 + random() * 0.5
                                            ELSE 0.1 + random() * 0.75 END) * INTERVAL '1 minute';
            elapsed := EXTRACT(EPOCH FROM NOW() - created) / 60;
            frm := LEAST(frm, elapsed * 0.3);
            assigned := created + frm * INTERVAL '1 minute';
            updated := assigned;

            IF st <> 'ASSIGNED' THEN
                started := assigned + (NOW() - assigned) * (0.05 + random() * 0.3);
                updated := started;
            END IF;
            IF st = 'PENDING_REQUESTER' THEN
                pend_start := started + (NOW() - started) * (0.2 + random() * 0.5);
                pending_since_v := pend_start;
                updated := pend_start;
            END IF;

            res_breach := elapsed - pend_total >= res;
            warn := elapsed - pend_total >= res * 0.8;

        ELSE
            -- RESOLVED / CLOSED / REOPENED: work time first, then place it on the timeline
            IF random() < 0.3 THEN
                pend_total := (30 + random() * 600)::INT;
            END IF;
            work := GREATEST(res * CASE WHEN random() < 0.12 THEN 1.05 + random() * 0.6
                                        ELSE 0.15 + random() * 0.75 END,
                             frm + 30);

            IF st = 'RESOLVED' THEN
                resolved := NOW() - (0.2 + random() * 3) * INTERVAL '1 day';
            ELSIF st = 'CLOSED' THEN
                resolved := NOW() - (3 + random() * 55) * INTERVAL '1 day';
                closed := LEAST(resolved + (1 + random() * 2) * INTERVAL '1 day',
                                NOW() - INTERVAL '30 minutes');
            ELSE
                resolved := NOW() - (1 + random() * 7) * INTERVAL '1 day';
                reopened := resolved + (NOW() - resolved) * (0.3 + random() * 0.5);
            END IF;

            created := resolved - (work + pend_total) * INTERVAL '1 minute';
            assigned := created + frm * INTERVAL '1 minute';
            started := assigned + LEAST(5 + random() * 40, (work - frm) * 0.2) * INTERVAL '1 minute';
            IF pend_total > 0 THEN
                pend_start := started + ((resolved - started) - pend_total * INTERVAL '1 minute') * 0.4;
                pend_end := pend_start + pend_total * INTERVAL '1 minute';
            END IF;

            res_breach := work > res;
            updated := COALESCE(closed, reopened, resolved);
        END IF;

        IF assigned IS NOT NULL THEN
            fr_breach := frm > fr;
        END IF;

        INSERT INTO tickets (
            reference, title, description, status, priority, category_id, queue_id,
            requester_id, assignee_id, created_at, updated_at, first_response_at,
            resolved_at, closed_at, first_response_due_at, sla_due_at, pending_since,
            pending_minutes_total, first_response_breached, resolution_breached,
            breach_warning_sent)
        VALUES (
            'TMP-' || n, c.title, c.description, st, prio, cat_id, q_id,
            requester.id, CASE WHEN st = 'NEW' THEN NULL ELSE agent.id END,
            created, updated, assigned,
            resolved, closed,
            created + fr * INTERVAL '1 minute', created + res * INTERVAL '1 minute',
            pending_since_v, pend_total, fr_breach, res_breach, warn)
        RETURNING id INTO tid;

        CONTINUE WHEN st = 'NEW';

        -- Assignment and first response
        PERFORM pg_temp.audit(tid, assigner_id, 'assignee', NULL, agent.email, assigned);
        PERFORM pg_temp.audit(tid, assigner_id, 'status', 'NEW', 'ASSIGNED', assigned);
        PERFORM pg_temp.add_comment(tid, agent.id,
            format(pg_temp.pick(greetings), requester.first_name), FALSE,
            assigned + LEAST(INTERVAL '3 minutes', (NOW() - assigned) / 2));

        CONTINUE WHEN started IS NULL;
        PERFORM pg_temp.audit(tid, agent.id, 'status', 'ASSIGNED', 'IN_PROGRESS', started);

        IF random() < 0.3 THEN
            PERFORM pg_temp.add_comment(tid, agent.id, pg_temp.pick(notes), TRUE,
                started + (COALESCE(pend_start, resolved, NOW()) - started) * 0.5);
        END IF;

        IF pend_start IS NOT NULL THEN
            PERFORM pg_temp.audit(tid, agent.id, 'status', 'IN_PROGRESS', 'PENDING_REQUESTER', pend_start);
            PERFORM pg_temp.add_comment(tid, agent.id, pg_temp.pick(questions), FALSE, pend_start);
        END IF;
        IF pend_end IS NOT NULL THEN
            PERFORM pg_temp.add_comment(tid, requester.id, pg_temp.pick(replies), FALSE,
                pend_end - INTERVAL '2 minutes');
            PERFORM pg_temp.audit(tid, agent.id, 'status', 'PENDING_REQUESTER', 'IN_PROGRESS', pend_end);
        END IF;

        CONTINUE WHEN resolved IS NULL;
        PERFORM pg_temp.add_comment(tid, agent.id, c.resolution, FALSE, resolved - INTERVAL '1 minute');
        PERFORM pg_temp.audit(tid, agent.id, 'status', 'IN_PROGRESS', 'RESOLVED', resolved);

        IF closed IS NOT NULL THEN
            IF random() < 0.4 THEN
                PERFORM pg_temp.add_comment(tid, requester.id, pg_temp.pick(thanks), FALSE,
                    resolved + (closed - resolved) * 0.3);
            END IF;
            PERFORM pg_temp.audit(tid, CASE WHEN random() < 0.5 THEN requester.id ELSE agent.id END,
                'status', 'RESOLVED', 'CLOSED', closed);
        END IF;

        IF reopened IS NOT NULL THEN
            PERFORM pg_temp.add_comment(tid, requester.id, pg_temp.pick(reopen_msgs), FALSE,
                reopened - INTERVAL '1 minute');
            PERFORM pg_temp.audit(tid, requester.id, 'status', 'RESOLVED', 'REOPENED', reopened);
        END IF;
    END LOOP;
END $$;

-- References follow creation order, like tickets created through the app
UPDATE tickets t
SET reference = format('TKT-%s-%s', EXTRACT(YEAR FROM t.created_at)::INT, lpad(x.rn::TEXT, 5, '0'))
FROM (SELECT id, row_number() OVER (ORDER BY created_at) AS rn FROM tickets) x
WHERE x.id = t.id;

SELECT setval('ticket_reference_seq', (SELECT COUNT(*) FROM tickets));

DROP EXTENSION pgcrypto;

COMMIT;

-- Summary
SELECT status, COUNT(*) FROM tickets GROUP BY status ORDER BY status;
