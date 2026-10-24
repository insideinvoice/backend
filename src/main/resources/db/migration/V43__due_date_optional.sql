-- Due date is commercial, not statutory (Rule 46): make it optional.
ALTER TABLE invoices ALTER COLUMN due_date DROP NOT NULL;
