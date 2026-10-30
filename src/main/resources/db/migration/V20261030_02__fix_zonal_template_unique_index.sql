-- Проверяем дубли среди неудалённых шаблонов.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM reporting_zonal_templates
        WHERE is_deleted = false
        GROUP BY name_key
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate active zonal template names found';
END IF;
END $$;

-- Удаляем старое ограничение.
ALTER TABLE reporting_zonal_templates
DROP CONSTRAINT IF EXISTS uq_reporting_zonal_templates_name;

-- Создаём частичный уникальный индекс.
CREATE UNIQUE INDEX IF NOT EXISTS
    uq_reporting_zonal_templates_name
    ON reporting_zonal_templates (name_key)
    WHERE is_deleted = false;