-- V38: Persist the configurable seal placement on the business.
--
-- The seal/stamp used to be drawn at a hardcoded size and position inside each
-- template. Owners can now set size, tilt and the distance from the page's
-- right/bottom edge once, and every surface (invoice templates, delivery
-- challans, emailed PDFs and the public share page) renders the seal the same
-- way on every page.
--
-- Stored as JSON so a new seal variant can be added without another column:
--   {"allPages":true,
--    "round":{"sizeMm":21,"tilt":-8,"rightMm":10,"bottomMm":8},
--    "stamp":{"sizeMm":64,"tilt":-6,"rightMm":10,"bottomMm":8}}
-- NULL means "use the built-in defaults above", which mirror the historical
-- size, tilt and bottom-right placement until the owner saves a configuration.
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS seal_config TEXT;

COMMENT ON COLUMN businesses.seal_config IS
    'JSON seal placement/size/tilt per variant (round, stamp); NULL = built-in defaults.';
