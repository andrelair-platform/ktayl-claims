-- GlobalCore seed — a small Property policy book (mirrors the StubGlobalCoreAdapter so behaviour is
-- identical when the ACL swaps stub → real SOAP). Claims start empty (created via the SOAP FNOL op).
USE globalcore;

INSERT INTO gc_policy (policy_number, holder_name, effective_date, expiry_date) VALUES
    ('POL-PROP-0001', 'Durand SARL',        '2026-01-01', '2026-12-31'),
    ('POL-PROP-0002', 'Boulangerie Lemoine','2026-03-01', '2027-02-28');

INSERT INTO gc_policy_peril (policy_number, peril) VALUES
    ('POL-PROP-0001', 'FIRE'),
    ('POL-PROP-0001', 'WATER_DAMAGE'),
    ('POL-PROP-0001', 'STORM'),
    ('POL-PROP-0001', 'THEFT'),
    ('POL-PROP-0002', 'FIRE'),
    ('POL-PROP-0002', 'WATER_DAMAGE');
