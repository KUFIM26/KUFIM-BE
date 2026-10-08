UPDATE booth SET auto_cancel_minutes = 10 WHERE auto_cancel_minutes <> 10;
ALTER TABLE booth ADD CONSTRAINT ck_booth_auto_cancel_ten CHECK (auto_cancel_minutes = 10);
