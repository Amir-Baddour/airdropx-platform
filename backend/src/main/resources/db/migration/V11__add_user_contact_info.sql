-- Adds the personal contact fields that were missing from V2: the original brief and ERD scoped
-- "profile management" to the company only, but COMPANY_OWNER / COMPANY_USER implies multiple people
-- per account, and each person needs their own identity fields, separate from the company's.
ALTER TABLE users
    ADD COLUMN phone   VARCHAR(20),
    ADD COLUMN address TEXT;
