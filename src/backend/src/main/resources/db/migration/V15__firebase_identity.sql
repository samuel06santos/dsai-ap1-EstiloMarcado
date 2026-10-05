ALTER TABLE usuario ADD COLUMN firebase_uid VARCHAR(128);
ALTER TABLE usuario ADD COLUMN senha_firebase BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE usuario SET senha_firebase = TRUE WHERE senha_hash IS NOT NULL;
CREATE UNIQUE INDEX uk_usuario_firebase_uid ON usuario (firebase_uid)
    WHERE firebase_uid IS NOT NULL;

-- O hash legado permanece somente até a auditoria e o corte da migração.
