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
    v_schema TEXT := current_schema();
    v_pk_count INT;
    v_pk_cols INT;
    v_pk_col_name TEXT;
    v_fk_count INT;
    v_count INT;
    v_fk_rec RECORD;
BEGIN
    IF v_schema IS NULL THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: current_schema() trả về NULL.';
    END IF;

    -- ============================================================
    -- 1. KIỂM TRA SỰ TỒN TẠI CỦA CÁC BẢNG TRONG SCHEMA MỤC TIÊU
    -- ============================================================
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = v_schema AND table_name = 'incident_iocs'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_iocs không tồn tại.', v_schema;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = v_schema AND table_name = 'incident_tasks'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_tasks không tồn tại.', v_schema;
    END IF;

    -- ============================================================
    -- 2. KIỂM TRA PRIMARY KEY CHÍNH XÁC (ĐƠN CỘT TRÊN 'id')
    -- ============================================================
    -- 2.1. incident_iocs
    SELECT COUNT(*) INTO v_pk_count
    FROM pg_constraint c
    JOIN pg_class tbl ON c.conrelid = tbl.oid
    JOIN pg_namespace ns ON tbl.relnamespace = ns.oid
    WHERE ns.nspname = v_schema AND tbl.relname = 'incident_iocs' AND c.contype = 'p';

    IF v_pk_count <> 1 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_iocs phải có đúng 1 PRIMARY KEY constraint, phát hiện %.', v_schema, v_pk_count;
    END IF;

    SELECT array_length(c.conkey, 1), att.attname
    INTO v_pk_cols, v_pk_col_name
    FROM pg_constraint c
    JOIN pg_class tbl ON c.conrelid = tbl.oid
    JOIN pg_namespace ns ON tbl.relnamespace = ns.oid
    JOIN pg_attribute att ON att.attrelid = tbl.oid AND att.attnum = c.conkey[1]
    WHERE ns.nspname = v_schema AND tbl.relname = 'incident_iocs' AND c.contype = 'p';

    IF v_pk_cols <> 1 OR v_pk_col_name <> 'id' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_iocs phải có PRIMARY KEY đơn trên cột id, phát hiện % cột (%s).', v_schema, v_pk_cols, v_pk_col_name;
    END IF;

    -- 2.2. incident_tasks
    SELECT COUNT(*) INTO v_pk_count
    FROM pg_constraint c
    JOIN pg_class tbl ON c.conrelid = tbl.oid
    JOIN pg_namespace ns ON tbl.relnamespace = ns.oid
    WHERE ns.nspname = v_schema AND tbl.relname = 'incident_tasks' AND c.contype = 'p';

    IF v_pk_count <> 1 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_tasks phải có đúng 1 PRIMARY KEY constraint, phát hiện %.', v_schema, v_pk_count;
    END IF;

    SELECT array_length(c.conkey, 1), att.attname
    INTO v_pk_cols, v_pk_col_name
    FROM pg_constraint c
    JOIN pg_class tbl ON c.conrelid = tbl.oid
    JOIN pg_namespace ns ON tbl.relnamespace = ns.oid
    JOIN pg_attribute att ON att.attrelid = tbl.oid AND att.attnum = c.conkey[1]
    WHERE ns.nspname = v_schema AND tbl.relname = 'incident_tasks' AND c.contype = 'p';

    IF v_pk_cols <> 1 OR v_pk_col_name <> 'id' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_tasks phải có PRIMARY KEY đơn trên cột id, phát hiện % cột (%s).', v_schema, v_pk_cols, v_pk_col_name;
    END IF;

    -- ============================================================
    -- 3. KIỂM TRA COLUMN CONTRACT & KIỂU DỮ LIỆU CỦA incident_iocs
    -- ============================================================
    -- 3.1. id: BIGINT NOT NULL + cơ chế sinh ID tự động (IDENTITY hoặc nextval/SERIAL)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_iocs' AND column_name = 'id'
          AND data_type = 'bigint' AND is_nullable = 'NO'
          AND (is_identity = 'YES' OR column_default LIKE 'nextval(%')
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_iocs.id phải là BIGINT NOT NULL và có cơ chế sinh ID tự động (IDENTITY hoặc SERIAL/nextval).', v_schema;
    END IF;

    -- 3.2. incident_id: BIGINT NOT NULL (KHÔNG chấp nhận INTEGER)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_iocs' AND column_name = 'incident_id'
          AND data_type = 'bigint' AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_iocs.incident_id phải là BIGINT NOT NULL.', v_schema;
    END IF;

    -- 3.3. type: VARCHAR(>=30) NOT NULL
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_iocs' AND column_name = 'type'
          AND data_type = 'character varying' AND (character_maximum_length >= 30 OR character_maximum_length IS NULL) AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_iocs.type phải là VARCHAR(>=30) NOT NULL.', v_schema;
    END IF;

    -- 3.4. value: VARCHAR(>=255) NOT NULL
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_iocs' AND column_name = 'value'
          AND data_type = 'character varying' AND (character_maximum_length >= 255 OR character_maximum_length IS NULL) AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_iocs.value phải là VARCHAR(>=255) NOT NULL.', v_schema;
    END IF;

    -- 3.5. description: TEXT hoặc VARCHAR, nullable
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_iocs' AND column_name = 'description'
          AND data_type IN ('text', 'character varying') AND is_nullable = 'YES'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_iocs.description phải là kiểu TEXT/VARCHAR và nullable.', v_schema;
    END IF;

    -- 3.6. created_at: TIMESTAMP WITHOUT TIME ZONE NOT NULL có default
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_iocs' AND column_name = 'created_at'
          AND data_type = 'timestamp without time zone' AND is_nullable = 'NO'
          AND column_default IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_iocs.created_at phải là TIMESTAMP WITHOUT TIME ZONE NOT NULL và có default (NOW()).', v_schema;
    END IF;

    -- ============================================================
    -- 4. KIỂM TRA COLUMN CONTRACT & KIỂU DỮ LIỆU CỦA incident_tasks
    -- ============================================================
    -- 4.1. id: BIGINT NOT NULL + cơ chế sinh ID tự động
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_tasks' AND column_name = 'id'
          AND data_type = 'bigint' AND is_nullable = 'NO'
          AND (is_identity = 'YES' OR column_default LIKE 'nextval(%')
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_tasks.id phải là BIGINT NOT NULL và có cơ chế sinh ID tự động (IDENTITY hoặc SERIAL/nextval).', v_schema;
    END IF;

    -- 4.2. incident_id: BIGINT NOT NULL (KHÔNG chấp nhận INTEGER)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_tasks' AND column_name = 'incident_id'
          AND data_type = 'bigint' AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_tasks.incident_id phải là BIGINT NOT NULL.', v_schema;
    END IF;

    -- 4.3. task_name: VARCHAR(>=255) NOT NULL
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_tasks' AND column_name = 'task_name'
          AND data_type = 'character varying' AND (character_maximum_length >= 255 OR character_maximum_length IS NULL) AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_tasks.task_name phải là VARCHAR(>=255) NOT NULL.', v_schema;
    END IF;

    -- 4.4. is_completed: BOOLEAN NOT NULL với default tương đương FALSE
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_tasks' AND column_name = 'is_completed'
          AND data_type = 'boolean' AND is_nullable = 'NO'
          AND column_default IS NOT NULL AND column_default ILIKE '%false%'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_tasks.is_completed phải là BOOLEAN NOT NULL với default là FALSE.', v_schema;
    END IF;

    -- 4.5. completed_at: TIMESTAMP WITHOUT TIME ZONE nullable
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_tasks' AND column_name = 'completed_at'
          AND data_type = 'timestamp without time zone' AND is_nullable = 'YES'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_tasks.completed_at phải là TIMESTAMP WITHOUT TIME ZONE nullable.', v_schema;
    END IF;

    -- 4.6. completed_by: BIGINT nullable (KHÔNG chấp nhận INTEGER)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = v_schema AND table_name = 'incident_tasks' AND column_name = 'completed_by'
          AND data_type = 'bigint' AND is_nullable = 'YES'
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Cột %.incident_tasks.completed_by phải là BIGINT nullable.', v_schema;
    END IF;

    -- ============================================================
    -- 5. KIỂM TRA FOREIGN KEYS CHÍNH XÁC VÀ KHÔNG NHẬP NHẰNG
    -- ============================================================
    -- 5.1. incident_iocs.incident_id -> incidents.id ON DELETE CASCADE
    SELECT COUNT(*) INTO v_fk_count
    FROM pg_constraint c
    JOIN pg_class src_tbl ON c.conrelid = src_tbl.oid
    JOIN pg_namespace src_ns ON src_tbl.relnamespace = src_ns.oid
    JOIN pg_attribute src_att ON src_att.attrelid = src_tbl.oid AND src_att.attnum = ANY(c.conkey)
    WHERE src_ns.nspname = v_schema
      AND src_tbl.relname = 'incident_iocs'
      AND c.contype = 'f'
      AND src_att.attname = 'incident_id';

    IF v_fk_count = 0 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_iocs thiếu Foreign Key trên cột incident_id.', v_schema;
    ELSIF v_fk_count > 1 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Phát hiện % Foreign Key trên cột %.incident_iocs.incident_id (duplicate constraint).', v_fk_count, v_schema;
    END IF;

    SELECT
        c.conname,
        tgt_ns.nspname AS tgt_schema,
        tgt_tbl.relname AS tgt_table,
        tgt_att.attname AS tgt_col,
        array_length(c.conkey, 1) AS src_col_count,
        array_length(c.confkey, 1) AS tgt_col_count,
        c.confdeltype
    INTO v_fk_rec
    FROM pg_constraint c
    JOIN pg_class src_tbl ON c.conrelid = src_tbl.oid
    JOIN pg_namespace src_ns ON src_tbl.relnamespace = src_ns.oid
    JOIN pg_attribute src_att ON src_att.attrelid = src_tbl.oid AND src_att.attnum = c.conkey[1]
    JOIN pg_class tgt_tbl ON c.confrelid = tgt_tbl.oid
    JOIN pg_namespace tgt_ns ON tgt_tbl.relnamespace = tgt_ns.oid
    JOIN pg_attribute tgt_att ON tgt_att.attrelid = tgt_tbl.oid AND tgt_att.attnum = c.confkey[1]
    WHERE src_ns.nspname = v_schema
      AND src_tbl.relname = 'incident_iocs'
      AND c.contype = 'f'
      AND src_att.attname = 'incident_id';

    IF v_fk_rec.src_col_count <> 1 OR v_fk_rec.tgt_col_count <> 1 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_iocs.incident_id không được là composite key.', v_schema;
    END IF;

    IF v_fk_rec.tgt_schema <> v_schema THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_iocs.incident_id trỏ sang schema %, kỳ vọng schema %.', v_schema, v_fk_rec.tgt_schema, v_schema;
    END IF;

    IF v_fk_rec.tgt_table <> 'incidents' OR v_fk_rec.tgt_col <> 'id' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_iocs.incident_id trỏ tới %.%, kỳ vọng incidents(id).', v_schema, v_fk_rec.tgt_table, v_fk_rec.tgt_col;
    END IF;

    IF v_fk_rec.confdeltype <> 'c' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_iocs.incident_id có delete action là %, kỳ vọng CASCADE (c). DBA cần cập nhật constraint sang ON DELETE CASCADE.', v_schema, v_fk_rec.confdeltype;
    END IF;

    -- 5.2. incident_tasks.incident_id -> incidents.id ON DELETE CASCADE
    SELECT COUNT(*) INTO v_fk_count
    FROM pg_constraint c
    JOIN pg_class src_tbl ON c.conrelid = src_tbl.oid
    JOIN pg_namespace src_ns ON src_tbl.relnamespace = src_ns.oid
    JOIN pg_attribute src_att ON src_att.attrelid = src_tbl.oid AND src_att.attnum = ANY(c.conkey)
    WHERE src_ns.nspname = v_schema
      AND src_tbl.relname = 'incident_tasks'
      AND c.contype = 'f'
      AND src_att.attname = 'incident_id';

    IF v_fk_count = 0 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_tasks thiếu Foreign Key trên cột incident_id.', v_schema;
    ELSIF v_fk_count > 1 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Phát hiện % Foreign Key trên cột %.incident_tasks.incident_id (duplicate constraint).', v_fk_count, v_schema;
    END IF;

    SELECT
        c.conname,
        tgt_ns.nspname AS tgt_schema,
        tgt_tbl.relname AS tgt_table,
        tgt_att.attname AS tgt_col,
        array_length(c.conkey, 1) AS src_col_count,
        array_length(c.confkey, 1) AS tgt_col_count,
        c.confdeltype
    INTO v_fk_rec
    FROM pg_constraint c
    JOIN pg_class src_tbl ON c.conrelid = src_tbl.oid
    JOIN pg_namespace src_ns ON src_tbl.relnamespace = src_ns.oid
    JOIN pg_attribute src_att ON src_att.attrelid = src_tbl.oid AND src_att.attnum = c.conkey[1]
    JOIN pg_class tgt_tbl ON c.confrelid = tgt_tbl.oid
    JOIN pg_namespace tgt_ns ON tgt_tbl.relnamespace = tgt_ns.oid
    JOIN pg_attribute tgt_att ON tgt_att.attrelid = tgt_tbl.oid AND tgt_att.attnum = c.confkey[1]
    WHERE src_ns.nspname = v_schema
      AND src_tbl.relname = 'incident_tasks'
      AND c.contype = 'f'
      AND src_att.attname = 'incident_id';

    IF v_fk_rec.src_col_count <> 1 OR v_fk_rec.tgt_col_count <> 1 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_tasks.incident_id không được là composite key.', v_schema;
    END IF;

    IF v_fk_rec.tgt_schema <> v_schema THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_tasks.incident_id trỏ sang schema %, kỳ vọng schema %.', v_schema, v_fk_rec.tgt_schema, v_schema;
    END IF;

    IF v_fk_rec.tgt_table <> 'incidents' OR v_fk_rec.tgt_col <> 'id' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_tasks.incident_id trỏ tới %.%, kỳ vọng incidents(id).', v_schema, v_fk_rec.tgt_table, v_fk_rec.tgt_col;
    END IF;

    IF v_fk_rec.confdeltype <> 'c' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_tasks.incident_id có delete action là %, kỳ vọng CASCADE (c). DBA cần cập nhật constraint sang ON DELETE CASCADE.', v_schema, v_fk_rec.confdeltype;
    END IF;

    -- 5.3. incident_tasks.completed_by -> users.id ON DELETE SET NULL
    SELECT COUNT(*) INTO v_fk_count
    FROM pg_constraint c
    JOIN pg_class src_tbl ON c.conrelid = src_tbl.oid
    JOIN pg_namespace src_ns ON src_tbl.relnamespace = src_ns.oid
    JOIN pg_attribute src_att ON src_att.attrelid = src_tbl.oid AND src_att.attnum = ANY(c.conkey)
    WHERE src_ns.nspname = v_schema
      AND src_tbl.relname = 'incident_tasks'
      AND c.contype = 'f'
      AND src_att.attname = 'completed_by';

    IF v_fk_count = 0 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_tasks thiếu Foreign Key trên cột completed_by.', v_schema;
    ELSIF v_fk_count > 1 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Phát hiện % Foreign Key trên cột %.incident_tasks.completed_by (duplicate constraint).', v_fk_count, v_schema;
    END IF;

    SELECT
        c.conname,
        tgt_ns.nspname AS tgt_schema,
        tgt_tbl.relname AS tgt_table,
        tgt_att.attname AS tgt_col,
        array_length(c.conkey, 1) AS src_col_count,
        array_length(c.confkey, 1) AS tgt_col_count,
        c.confdeltype
    INTO v_fk_rec
    FROM pg_constraint c
    JOIN pg_class src_tbl ON c.conrelid = src_tbl.oid
    JOIN pg_namespace src_ns ON src_tbl.relnamespace = src_ns.oid
    JOIN pg_attribute src_att ON src_att.attrelid = src_tbl.oid AND src_att.attnum = c.conkey[1]
    JOIN pg_class tgt_tbl ON c.confrelid = tgt_tbl.oid
    JOIN pg_namespace tgt_ns ON tgt_tbl.relnamespace = tgt_ns.oid
    JOIN pg_attribute tgt_att ON tgt_att.attrelid = tgt_tbl.oid AND tgt_att.attnum = c.confkey[1]
    WHERE src_ns.nspname = v_schema
      AND src_tbl.relname = 'incident_tasks'
      AND c.contype = 'f'
      AND src_att.attname = 'completed_by';

    IF v_fk_rec.src_col_count <> 1 OR v_fk_rec.tgt_col_count <> 1 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_tasks.completed_by không được là composite key.', v_schema;
    END IF;

    IF v_fk_rec.tgt_schema <> v_schema THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_tasks.completed_by trỏ sang schema %, kỳ vọng schema %.', v_schema, v_fk_rec.tgt_schema, v_schema;
    END IF;

    IF v_fk_rec.tgt_table <> 'users' OR v_fk_rec.tgt_col <> 'id' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_tasks.completed_by trỏ tới %.%, kỳ vọng users(id).', v_schema, v_fk_rec.tgt_table, v_fk_rec.tgt_col;
    END IF;

    IF v_fk_rec.confdeltype <> 'n' THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Foreign Key %.incident_tasks.completed_by có delete action là %, kỳ vọng SET NULL (n). DBA cần cập nhật constraint sang ON DELETE SET NULL.', v_schema, v_fk_rec.confdeltype;
    END IF;

    -- ============================================================
    -- 6. KIỂM TRA INDEXES BẰNG POSTGRESQL CATALOG (CHÍNH XÁC, SCHEMA-SAFE)
    -- ============================================================
    -- 6.1. incident_iocs(incident_id)
    SELECT COUNT(*) INTO v_count
    FROM pg_class idx_cls
    JOIN pg_index i ON i.indexrelid = idx_cls.oid
    JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
    JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
    WHERE ns.nspname = v_schema
      AND idx_cls.relname = 'idx_incident_iocs_incident_id'
      AND (
          tbl_cls.relname <> 'incident_iocs'
          OR NOT (i.indisvalid AND i.indisready)
          OR i.indnkeyatts <> 1
          OR i.indpred IS NOT NULL
          OR i.indexprs IS NOT NULL
          OR (SELECT attname FROM pg_attribute WHERE attrelid = tbl_cls.oid AND attnum = i.indkey[0]) <> 'incident_id'
      );
    IF v_count > 0 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Index %.idx_incident_iocs_incident_id có định nghĩa sai lệch (partial, expression, sai bảng hoặc sai cột).', v_schema;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
        JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
        JOIN pg_attribute att ON att.attrelid = tbl_cls.oid AND att.attnum = i.indkey[0]
        WHERE ns.nspname = v_schema
          AND tbl_cls.relname = 'incident_iocs'
          AND att.attname = 'incident_id'
          AND i.indnkeyatts = 1
          AND i.indisvalid AND i.indisready
          AND i.indpred IS NULL
          AND i.indexprs IS NULL
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_iocs thiếu index hợp lệ trên cột incident_id.', v_schema;
    END IF;

    -- 6.2. incident_iocs(type)
    SELECT COUNT(*) INTO v_count
    FROM pg_class idx_cls
    JOIN pg_index i ON i.indexrelid = idx_cls.oid
    JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
    JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
    WHERE ns.nspname = v_schema
      AND idx_cls.relname = 'idx_incident_iocs_type'
      AND (
          tbl_cls.relname <> 'incident_iocs'
          OR NOT (i.indisvalid AND i.indisready)
          OR i.indnkeyatts <> 1
          OR i.indpred IS NOT NULL
          OR i.indexprs IS NOT NULL
          OR (SELECT attname FROM pg_attribute WHERE attrelid = tbl_cls.oid AND attnum = i.indkey[0]) <> 'type'
      );
    IF v_count > 0 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Index %.idx_incident_iocs_type có định nghĩa sai lệch (partial, expression, sai bảng hoặc sai cột).', v_schema;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
        JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
        JOIN pg_attribute att ON att.attrelid = tbl_cls.oid AND att.attnum = i.indkey[0]
        WHERE ns.nspname = v_schema
          AND tbl_cls.relname = 'incident_iocs'
          AND att.attname = 'type'
          AND i.indnkeyatts = 1
          AND i.indisvalid AND i.indisready
          AND i.indpred IS NULL
          AND i.indexprs IS NULL
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_iocs thiếu index hợp lệ trên cột type.', v_schema;
    END IF;

    -- 6.3. incident_iocs(value)
    SELECT COUNT(*) INTO v_count
    FROM pg_class idx_cls
    JOIN pg_index i ON i.indexrelid = idx_cls.oid
    JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
    JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
    WHERE ns.nspname = v_schema
      AND idx_cls.relname = 'idx_incident_iocs_value'
      AND (
          tbl_cls.relname <> 'incident_iocs'
          OR NOT (i.indisvalid AND i.indisready)
          OR i.indnkeyatts <> 1
          OR i.indpred IS NOT NULL
          OR i.indexprs IS NOT NULL
          OR (SELECT attname FROM pg_attribute WHERE attrelid = tbl_cls.oid AND attnum = i.indkey[0]) <> 'value'
      );
    IF v_count > 0 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Index %.idx_incident_iocs_value có định nghĩa sai lệch (partial, expression, sai bảng hoặc sai cột).', v_schema;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
        JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
        JOIN pg_attribute att ON att.attrelid = tbl_cls.oid AND att.attnum = i.indkey[0]
        WHERE ns.nspname = v_schema
          AND tbl_cls.relname = 'incident_iocs'
          AND att.attname = 'value'
          AND i.indnkeyatts = 1
          AND i.indisvalid AND i.indisready
          AND i.indpred IS NULL
          AND i.indexprs IS NULL
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_iocs thiếu index hợp lệ trên cột value.', v_schema;
    END IF;

    -- 6.4. incident_tasks(incident_id)
    SELECT COUNT(*) INTO v_count
    FROM pg_class idx_cls
    JOIN pg_index i ON i.indexrelid = idx_cls.oid
    JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
    JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
    WHERE ns.nspname = v_schema
      AND idx_cls.relname = 'idx_incident_tasks_incident_id'
      AND (
          tbl_cls.relname <> 'incident_tasks'
          OR NOT (i.indisvalid AND i.indisready)
          OR i.indnkeyatts <> 1
          OR i.indpred IS NOT NULL
          OR i.indexprs IS NOT NULL
          OR (SELECT attname FROM pg_attribute WHERE attrelid = tbl_cls.oid AND attnum = i.indkey[0]) <> 'incident_id'
      );
    IF v_count > 0 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Index %.idx_incident_tasks_incident_id có định nghĩa sai lệch (partial, expression, sai bảng hoặc sai cột).', v_schema;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
        JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
        JOIN pg_attribute att ON att.attrelid = tbl_cls.oid AND att.attnum = i.indkey[0]
        WHERE ns.nspname = v_schema
          AND tbl_cls.relname = 'incident_tasks'
          AND att.attname = 'incident_id'
          AND i.indnkeyatts = 1
          AND i.indisvalid AND i.indisready
          AND i.indpred IS NULL
          AND i.indexprs IS NULL
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_tasks thiếu index hợp lệ trên cột incident_id.', v_schema;
    END IF;

    -- 6.5. incident_tasks(is_completed)
    SELECT COUNT(*) INTO v_count
    FROM pg_class idx_cls
    JOIN pg_index i ON i.indexrelid = idx_cls.oid
    JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
    JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
    WHERE ns.nspname = v_schema
      AND idx_cls.relname = 'idx_incident_tasks_is_completed'
      AND (
          tbl_cls.relname <> 'incident_tasks'
          OR NOT (i.indisvalid AND i.indisready)
          OR i.indnkeyatts <> 1
          OR i.indpred IS NOT NULL
          OR i.indexprs IS NOT NULL
          OR (SELECT attname FROM pg_attribute WHERE attrelid = tbl_cls.oid AND attnum = i.indkey[0]) <> 'is_completed'
      );
    IF v_count > 0 THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Index %.idx_incident_tasks_is_completed có định nghĩa sai lệch (partial, expression, sai bảng hoặc sai cột).', v_schema;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
        JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
        JOIN pg_attribute att ON att.attrelid = tbl_cls.oid AND att.attnum = i.indkey[0]
        WHERE ns.nspname = v_schema
          AND tbl_cls.relname = 'incident_tasks'
          AND att.attname = 'is_completed'
          AND i.indnkeyatts = 1
          AND i.indisvalid AND i.indisready
          AND i.indpred IS NULL
          AND i.indexprs IS NULL
    ) THEN
        RAISE EXCEPTION 'Flyway V4.1 Validation Error: Bảng %.incident_tasks thiếu index hợp lệ trên cột is_completed.', v_schema;
    END IF;
END $$;
