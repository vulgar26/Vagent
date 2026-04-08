-- 将业务主键与外键从 VARCHAR 转为原生 UUID（与 Java/JWT 规范小写连字符形式一致）。

CREATE OR REPLACE FUNCTION vagent_varchar_to_uuid(input text) RETURNS uuid AS $$
  SELECT (
    CASE
      WHEN length(trim(lower(input))) = 32 AND strpos(trim(lower(input)), '-') = 0 THEN
        (substring(trim(lower(input)), 1, 8) || '-' ||
         substring(trim(lower(input)), 9, 4) || '-' ||
         substring(trim(lower(input)), 13, 4) || '-' ||
         substring(trim(lower(input)), 17, 4) || '-' ||
         substring(trim(lower(input)), 21, 12))::uuid
      ELSE trim(lower(input))::uuid
    END
  );
$$ LANGUAGE sql IMMUTABLE;

ALTER TABLE kb_chunks DROP CONSTRAINT IF EXISTS kb_chunks_document_id_fkey;
ALTER TABLE kb_chunks DROP CONSTRAINT IF EXISTS kb_chunks_user_id_fkey;
ALTER TABLE kb_documents DROP CONSTRAINT IF EXISTS kb_documents_user_id_fkey;
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_conversation_id_fkey;
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_user_id_fkey;
ALTER TABLE conversations DROP CONSTRAINT IF EXISTS conversations_user_id_fkey;

ALTER TABLE users
    ALTER COLUMN id TYPE uuid USING vagent_varchar_to_uuid(id::text);

ALTER TABLE conversations
    ALTER COLUMN id TYPE uuid USING vagent_varchar_to_uuid(id::text),
    ALTER COLUMN user_id TYPE uuid USING vagent_varchar_to_uuid(user_id::text);

ALTER TABLE messages
    ALTER COLUMN id TYPE uuid USING vagent_varchar_to_uuid(id::text),
    ALTER COLUMN conversation_id TYPE uuid USING vagent_varchar_to_uuid(conversation_id::text),
    ALTER COLUMN user_id TYPE uuid USING vagent_varchar_to_uuid(user_id::text);

ALTER TABLE kb_documents
    ALTER COLUMN id TYPE uuid USING vagent_varchar_to_uuid(id::text),
    ALTER COLUMN user_id TYPE uuid USING vagent_varchar_to_uuid(user_id::text);

ALTER TABLE kb_chunks
    ALTER COLUMN id TYPE uuid USING vagent_varchar_to_uuid(id::text),
    ALTER COLUMN document_id TYPE uuid USING vagent_varchar_to_uuid(document_id::text),
    ALTER COLUMN user_id TYPE uuid USING vagent_varchar_to_uuid(user_id::text);

ALTER TABLE conversations
    ADD CONSTRAINT conversations_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE messages
    ADD CONSTRAINT messages_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    ADD CONSTRAINT messages_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE kb_documents
    ADD CONSTRAINT kb_documents_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE kb_chunks
    ADD CONSTRAINT kb_chunks_document_id_fkey FOREIGN KEY (document_id) REFERENCES kb_documents (id) ON DELETE CASCADE,
    ADD CONSTRAINT kb_chunks_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);

DROP FUNCTION IF EXISTS vagent_varchar_to_uuid(text);
