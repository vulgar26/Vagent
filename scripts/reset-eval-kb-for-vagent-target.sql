-- 清空评测租户（X-Eval-Target-Id=vagent）在 public 下的知识库行，使 rag/empty 等用例易得到 0 命中。
-- user_id 与 EvalStableUserId.forDefaultVagentTarget()、scripts/seed-eval-kb-user.sql 中插入的 id 一致。
--
-- 用法：psql -U postgres -d vagent -f scripts/reset-eval-kb-for-vagent-target.sql
-- 说明：kb_chunks 对 kb_documents 为 ON DELETE CASCADE，删文档即可连带删分块。

SET search_path TO public;

DELETE FROM public.kb_documents
WHERE user_id = '74bf947a-34f6-379c-9aee-4169e03e7623'::uuid;
