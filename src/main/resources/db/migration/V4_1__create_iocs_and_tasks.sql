-- ================================================================
-- V4.1: Tạo bảng nền cho IoC và Task gắn liền với Incident (Idempotent & Fail-Closed)
-- Ownership & Delete Behavior:
--   - incident_id: ON DELETE CASCADE vì Incident là Aggregate Root sở hữu
--     vòng đời của IoC và Task (khớp CascadeType.ALL & orphanRemoval=true trong JPA).
--   - completed_by: ON DELETE SET NULL để bảo toàn lịch sử xử lý playbook khi user bị xóa.
-- ================================================================

CREATE TABLE IF NOT EXISTS incident_iocs (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    value VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_incident_iocs_incident_id ON incident_iocs(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_iocs_type ON incident_iocs(type);
CREATE INDEX IF NOT EXISTS idx_incident_iocs_value ON incident_iocs(value);

CREATE TABLE IF NOT EXISTS incident_tasks (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    task_name VARCHAR(255) NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP,
    completed_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_incident_tasks_incident_id ON incident_tasks(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_tasks_is_completed ON incident_tasks(is_completed);

-- ================================================================
-- Validation: Fail-closed trước schema legacy không tương thích
-- Ngăn chặn việc ghi nhận V4.1 thành công nếu các object đã tồn tại
-- có cấu trúc bị lệch (schema drift) so với thiết kế chuẩn của JPA.
-- ================================================================
DO $$
DECLARE
    v_fk_rule TEXT;
BEGIN
    -- 1. Kiểm tra Primary Key của incident_iocs
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
            ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
        WHERE tc.table_name = 'incident_iocs' AND tc.constraint_type = 'PRIMARY KEY' AND kcu.column_name = 'id'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_iocs thiếu PRIMARY KEY trên cột id.';
    END IF;

    -- 2. Kiểm tra các cột bắt buộc, kiểu dữ liệu và nullability của incident_iocs
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_iocs' AND column_name = 'id'
          AND data_type IN ('bigint', 'integer') AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_iocs.id phải là BIGINT NOT NULL.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_iocs' AND column_name = 'incident_id'
          AND data_type IN ('bigint', 'integer') AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_iocs.incident_id phải là BIGINT NOT NULL.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_iocs' AND column_name = 'type'
          AND data_type = 'character varying' AND (character_maximum_length >= 30 OR character_maximum_length IS NULL) AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_iocs.type phải là VARCHAR(>=30) NOT NULL.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_iocs' AND column_name = 'value'
          AND data_type = 'character varying' AND (character_maximum_length >= 255 OR character_maximum_length IS NULL) AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_iocs.value phải là VARCHAR(>=255) NOT NULL.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_iocs' AND column_name = 'description'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_iocs thiếu cột description.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_iocs' AND column_name = 'created_at'
          AND data_type LIKE 'timestamp%' AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_iocs.created_at phải là TIMESTAMP NOT NULL.';
    END IF;

    -- 3. Kiểm tra Foreign Key incident_iocs.incident_id -> incidents(id) ON DELETE CASCADE
    SELECT rc.delete_rule INTO v_fk_rule
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
    JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name AND tc.table_schema = rc.constraint_schema
    JOIN information_schema.constraint_column_usage ccu ON rc.unique_constraint_name = ccu.constraint_name AND rc.unique_constraint_schema = ccu.constraint_schema
    WHERE tc.table_name = 'incident_iocs' AND kcu.column_name = 'incident_id' AND ccu.table_name = 'incidents' AND ccu.column_name = 'id';

    IF v_fk_rule IS NULL THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_iocs thiếu Foreign Key từ incident_id tới incidents(id).';
    ELSIF v_fk_rule <> 'CASCADE' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key incident_iocs.incident_id có delete_rule = %, kỳ vọng CASCADE. DBA cần cập nhật constraint sang ON DELETE CASCADE.', v_fk_rule;
    END IF;

    -- 4. Kiểm tra Indexes của incident_iocs
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'incident_iocs' AND indexdef LIKE '%(incident_id)%') THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_iocs thiếu index trên cột incident_id.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'incident_iocs' AND indexdef LIKE '%(type)%') THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_iocs thiếu index trên cột type.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'incident_iocs' AND indexdef LIKE '%(value)%') THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_iocs thiếu index trên cột value.';
    END IF;

    -- 5. Kiểm tra Primary Key của incident_tasks
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
        WHERE tc.table_name = 'incident_tasks' AND tc.constraint_type = 'PRIMARY KEY' AND kcu.column_name = 'id'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_tasks thiếu PRIMARY KEY trên cột id.';
    END IF;

    -- 6. Kiểm tra các cột bắt buộc, kiểu dữ liệu và nullability của incident_tasks
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_tasks' AND column_name = 'id'
          AND data_type IN ('bigint', 'integer') AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_tasks.id phải là BIGINT NOT NULL.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_tasks' AND column_name = 'incident_id'
          AND data_type IN ('bigint', 'integer') AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_tasks.incident_id phải là BIGINT NOT NULL.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_tasks' AND column_name = 'task_name'
          AND data_type = 'character varying' AND (character_maximum_length >= 255 OR character_maximum_length IS NULL) AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_tasks.task_name phải là VARCHAR(>=255) NOT NULL.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_tasks' AND column_name = 'is_completed'
          AND data_type = 'boolean' AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_tasks.is_completed phải là BOOLEAN NOT NULL.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_tasks' AND column_name = 'completed_at'
          AND data_type LIKE 'timestamp%'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_tasks.completed_at phải là kiểu TIMESTAMP.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'incident_tasks' AND column_name = 'completed_by'
          AND data_type IN ('bigint', 'integer')
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột incident_tasks.completed_by phải là kiểu BIGINT.';
    END IF;

    -- 7. Kiểm tra Foreign Key incident_tasks.incident_id -> incidents(id) ON DELETE CASCADE
    SELECT rc.delete_rule INTO v_fk_rule
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
    JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name AND tc.table_schema = rc.constraint_schema
    JOIN information_schema.constraint_column_usage ccu ON rc.unique_constraint_name = ccu.constraint_name AND rc.unique_constraint_schema = ccu.constraint_schema
    WHERE tc.table_name = 'incident_tasks' AND kcu.column_name = 'incident_id' AND ccu.table_name = 'incidents' AND ccu.column_name = 'id';

    IF v_fk_rule IS NULL THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_tasks thiếu Foreign Key từ incident_id tới incidents(id).';
    ELSIF v_fk_rule <> 'CASCADE' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key incident_tasks.incident_id có delete_rule = %, kỳ vọng CASCADE. DBA cần cập nhật constraint sang ON DELETE CASCADE.', v_fk_rule;
    END IF;

    -- 8. Kiểm tra Foreign Key incident_tasks.completed_by -> users(id) ON DELETE SET NULL
    SELECT rc.delete_rule INTO v_fk_rule
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
    JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name AND tc.table_schema = rc.constraint_schema
    JOIN information_schema.constraint_column_usage ccu ON rc.unique_constraint_name = ccu.constraint_name AND rc.unique_constraint_schema = ccu.constraint_schema
    WHERE tc.table_name = 'incident_tasks' AND kcu.column_name = 'completed_by' AND ccu.table_name = 'users' AND ccu.column_name = 'id';

    IF v_fk_rule IS NULL THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_tasks thiếu Foreign Key từ completed_by tới users(id).';
    ELSIF v_fk_rule <> 'SET NULL' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key incident_tasks.completed_by có delete_rule = %, kỳ vọng SET NULL. DBA cần cập nhật constraint sang ON DELETE SET NULL.', v_fk_rule;
    END IF;

    -- 9. Kiểm tra Indexes của incident_tasks
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'incident_tasks' AND indexdef LIKE '%(incident_id)%') THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_tasks thiếu index trên cột incident_id.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'incident_tasks' AND indexdef LIKE '%(is_completed)%') THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng incident_tasks thiếu index trên cột is_completed.';
    END IF;
END $$;
