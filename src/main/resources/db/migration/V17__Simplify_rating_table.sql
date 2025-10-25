ALTER TABLE ratings
    DROP CONSTRAINT IF EXISTS chk_safety_score_range;
ALTER TABLE ratings
    DROP CONSTRAINT IF EXISTS chk_punctuality_score_range;
ALTER TABLE ratings
    DROP CONSTRAINT IF EXISTS chk_communication_score_range;

ALTER TABLE ratings
    DROP COLUMN IF EXISTS safety_score;
ALTER TABLE ratings
    DROP COLUMN IF EXISTS communication_score;
ALTER TABLE ratings
    DROP COLUMN IF EXISTS punctuality_score;