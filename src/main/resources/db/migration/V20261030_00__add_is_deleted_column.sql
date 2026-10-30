ALTER TABLE public.reporting_zonal_templates
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;