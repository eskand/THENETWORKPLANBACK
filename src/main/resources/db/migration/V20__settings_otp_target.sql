-- ============================================================
--  V20 — deux réglages que l'OCC lit désormais.
--
--  Règle du module Settings : une clé nomme le service qui la
--  consomme. Ces deux-là sont lues par le tableau de dispatch,
--  qui les renvoie à l'écran OCC.
-- ============================================================

INSERT INTO platform.settings (id, tenant_id, category, setting_key, setting_value, value_type, unit,
                               label, description, read_by, editable, source_type, source_ref)
SELECT md5('set-' || s.setting_key)::uuid,
       '00000000-0000-0000-0000-000000000001',
       s.category, s.setting_key, s.setting_value, s.value_type, s.unit,
       s.label, s.description, s.read_by, true,
       'seed', 'Operations manual'
FROM (VALUES
    ('OPS', 'ops.otp-target', '95', 'INTEGER', 'percent',
     'On-time performance target',
     'The figure the operator holds itself to, shown beside the measured one. Measured OTP counts only the legs that actually departed; a day with no departure shows no sample rather than 100 percent.',
     'OpsProperties, DispatchBoardService'),
    ('OPS', 'ops.weather-stale-after', '90', 'INTEGER', 'minutes',
     'Observation considered stale',
     'Beyond this, a METAR is shown as stale with its age instead of being presented as the current weather.',
     'WeatherProperties, WeatherService')
) AS s(category, setting_key, setting_value, value_type, unit, label, description, read_by)
ON CONFLICT ON CONSTRAINT uq_setting DO NOTHING;
