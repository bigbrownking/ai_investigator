-- Tree Data Table Migration
-- This script creates the tree_data table for storing tree module data
-- 
-- NOTE: This project currently uses Hibernate's ddl-auto=update, 
-- so this table will be created automatically from the @Entity definition.
-- 
-- If you want to use Flyway for migrations, create this as:
-- src/main/resources/db/migration/V1__create_tree_data_table.sql

CREATE TABLE tree_data (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL,
    module_type VARCHAR(50) NOT NULL,
    json_data JSONB,
    version INTEGER NOT NULL DEFAULT 1,
    fetched_at TIMESTAMP NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fetch_success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,

    CONSTRAINT fk_tree_data_case FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE
);

-- Create indexes for better query performance
CREATE INDEX idx_case_module ON tree_data(case_id, module_type);
CREATE INDEX idx_case_fetched ON tree_data(case_id, fetched_at);
CREATE INDEX idx_module_version ON tree_data(module_type, version);

-- Add comments for documentation
COMMENT ON TABLE tree_data IS 'Хранилище древовидных данных для уголовных дел';
COMMENT ON COLUMN tree_data.json_data IS 'JSON данные модуля в формате JSONB для быстрого поиска';
COMMENT ON COLUMN tree_data.version IS 'Версия модуля для отслеживания изменений';
COMMENT ON COLUMN tree_data.fetch_success IS 'Флаг, указывающий успешность загрузки данных';
COMMENT ON COLUMN tree_data.error_message IS 'Сообщение об ошибке при неудачной загрузке';
