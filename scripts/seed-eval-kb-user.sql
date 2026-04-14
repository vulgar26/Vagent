-- 评测灌库专用用户：id 与 EvalStableUserId.forDefaultVagentTarget() 一致（target_id=vagent）。
-- 勿在 PowerShell 里直接粘贴多行 SQL；请用 psql -f 本文件，或在 DBeaver / pgAdmin 中执行。
-- 明文密码（仅本地联调）：VagentEvalKb2026!a7
--
-- Flyway 把表建在 schema **public** 下。请连接 **库名 vagent**（勿与 travel-ai 共用的 ragent 混用）。
-- DBeaver：数据库 vagent → 模式 → **public** → 表；再执行本脚本。

SET search_path TO public;

INSERT INTO public.users (id, username, password_hash, created_at)
VALUES (
  '74bf947a-34f6-379c-9aee-4169e03e7623'::uuid,
  'eval_kb_vagent',
  '$2b$10$yin/sCBTD4waBI0ACXzxjuB8JbMRykbZbxtX9vf2AfF/BaYww6h4y',
  NOW()
)
ON CONFLICT (id) DO UPDATE SET
  username = EXCLUDED.username,
  password_hash = EXCLUDED.password_hash;
