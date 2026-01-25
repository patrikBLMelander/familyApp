-- Make event_id nullable in collected_food table to support bonus food
-- Bonus food (from parent giving bonus XP) doesn't have an associated event

-- Drop the existing foreign key constraint
ALTER TABLE collected_food 
DROP FOREIGN KEY collected_food_ibfk_2;

-- Make the column nullable
ALTER TABLE collected_food 
MODIFY COLUMN event_id VARCHAR(36) NULL;

-- Re-add the foreign key constraint (now allowing NULL)
ALTER TABLE collected_food 
ADD CONSTRAINT collected_food_event_fk 
FOREIGN KEY (event_id) REFERENCES calendar_event(id) ON DELETE CASCADE;
