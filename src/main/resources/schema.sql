CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS account (
 id uuid PRIMARY KEY, token_hash varchar(64) UNIQUE NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS knowledge_base (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES account(id), name varchar(100) NOT NULL, created_at timestamptz DEFAULT now()
);
CREATE TABLE IF NOT EXISTS job (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES account(id), kind varchar(20) NOT NULL,
 kb_id uuid REFERENCES knowledge_base(id), status varchar(20) NOT NULL DEFAULT 'QUEUED',
 input_text text NOT NULL, source_name varchar(200), result text, attempts integer NOT NULL DEFAULT 0,
 published boolean NOT NULL DEFAULT false, created_at timestamptz DEFAULT now(), updated_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS job_dispatch ON job(status, published);
ALTER TABLE job ADD COLUMN IF NOT EXISTS file_data bytea;
CREATE TABLE IF NOT EXISTS chunk (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES account(id), kb_id uuid NOT NULL REFERENCES knowledge_base(id),
 job_id uuid NOT NULL REFERENCES job(id), source_name varchar(200), ordinal integer NOT NULL, content text NOT NULL,
 embedding vector(1024) NOT NULL, UNIQUE(job_id, ordinal)
);
CREATE INDEX IF NOT EXISTS chunk_hnsw ON chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS chunk_scope ON chunk(owner_id, kb_id);
CREATE TABLE IF NOT EXISTS interview (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES account(id), direction varchar(40) NOT NULL,
 level varchar(20) NOT NULL, status varchar(20) NOT NULL DEFAULT 'ACTIVE', turn integer NOT NULL DEFAULT 0,
 resume_context text NOT NULL DEFAULT '', report text, created_at timestamptz DEFAULT now()
);
CREATE TABLE IF NOT EXISTS message (
 id bigserial PRIMARY KEY, session_id uuid NOT NULL REFERENCES interview(id), role varchar(20) NOT NULL,
 content text NOT NULL, created_at timestamptz DEFAULT now()
);
