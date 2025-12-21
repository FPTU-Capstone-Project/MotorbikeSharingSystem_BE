-- Add REJECTED status to driver_profiles check constraint
-- This fixes the constraint violation when rejecting driver verifications

ALTER TABLE driver_profiles
    DROP CONSTRAINT IF EXISTS chk_driver_status;

ALTER TABLE driver_profiles
    ADD CONSTRAINT chk_driver_status CHECK (
        status IN (
            'PENDING',
            'ACTIVE',
            'INACTIVE',
            'REJECTED',
            'SUSPENDED'
        )
    );

