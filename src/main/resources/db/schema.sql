-- =====================================================================
-- Smart Academic Calendar - SQLite schema
-- Dates are stored as TEXT in ISO format (yyyy-MM-dd) and times as HH:mm.
-- SQLite has no real DATE type, and ISO text sorts correctly.
-- =====================================================================

PRAGMA foreign_keys = ON;

-- Departments (NULL department on an event means "whole university")
CREATE TABLE IF NOT EXISTS departments (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    code      TEXT NOT NULL UNIQUE,        -- e.g. 'ICT', 'CSE'
    name_en   TEXT NOT NULL,
    name_bn   TEXT
);

-- Users: ADMIN can edit everything; STUDENT/TEACHER are view-only
-- (plus their own personal events).
CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,            -- BCrypt hash, never plain text
    full_name     TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('ADMIN','TEACHER','STUDENT')),
    department_id INTEGER REFERENCES departments(id),
    year          INTEGER,                  -- students only: 1..4
    semester      INTEGER,                  -- students only: 1..2
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Events: holidays, class tests, exams, etc. all live in ONE table
CREATE TABLE IF NOT EXISTS events (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    title            TEXT NOT NULL,
    title_bn         TEXT,
    type             TEXT NOT NULL CHECK (type IN
                       ('HOLIDAY','CLASS_TEST','EXAM','SEMESTER_BREAK',
                        'ASSIGNMENT_DEADLINE','DEPARTMENT_EVENT','NOTICE')),
    start_date       TEXT NOT NULL,         -- yyyy-MM-dd
    end_date         TEXT NOT NULL,         -- same as start_date for 1-day events
    start_time       TEXT,                  -- HH:mm (optional)
    end_time         TEXT,                  -- HH:mm (optional)
    department_id    INTEGER REFERENCES departments(id) ON DELETE SET NULL,
    year             INTEGER,               -- NULL = all years
    semester         INTEGER,               -- NULL = all semesters
    course_code      TEXT,                  -- e.g. 'ICT-2207'
    description      TEXT,
    location         TEXT,
    reminder_minutes INTEGER,               -- 1440 = 1 day, 60 = 1 hour, NULL = none
    class_off        INTEGER NOT NULL DEFAULT 0,   -- 1 = classes are off
    office_off       INTEGER NOT NULL DEFAULT 0,   -- 1 = offices are off
    owner_user_id    INTEGER REFERENCES users(id) ON DELETE CASCADE,
                                            -- NULL = official event, else personal
    created_by       INTEGER REFERENCES users(id),
    created_at       TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at       TEXT NOT NULL DEFAULT (datetime('now')),
    CHECK (end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS idx_events_dates ON events(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_events_dept  ON events(department_id, year, semester);
CREATE INDEX IF NOT EXISTS idx_events_type  ON events(type);

-- Bangla (Bangabda) and Hijri month names printed on the calendar,
-- one row per Gregorian month. Day-level dates are computed in code.
CREATE TABLE IF NOT EXISTS calendar_months (
    year          INTEGER NOT NULL,
    month         INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    bangla_months TEXT NOT NULL,            -- e.g. 'পৌষ – মাঘ'
    hijri_months  TEXT NOT NULL,            -- e.g. 'রজব – শাবান'
    PRIMARY KEY (year, month)
);

-- Which reminders were already shown (so we don't pop up twice)
CREATE TABLE IF NOT EXISTS reminder_log (
    event_id     INTEGER NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    minutes_before INTEGER NOT NULL,
    shown_at     TEXT NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (event_id, user_id, minutes_before)
);

-- Simple key/value settings (theme, language, weekly off days...)
CREATE TABLE IF NOT EXISTS app_settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT OR IGNORE INTO app_settings(key, value) VALUES
    ('theme', 'light'),
    ('language', 'en'),
    ('weekly_off_days', 'FRIDAY,SATURDAY');

INSERT OR IGNORE INTO departments(code, name_en, name_bn) VALUES
    ('ICT', 'Information and Communication Technology', 'তথ্য ও যোগাযোগ প্রযুক্তি'),
    ('CSE', 'Computer Science and Engineering', 'কম্পিউটার বিজ্ঞান ও প্রকৌশল'),
    ('EEE', 'Electrical and Electronic Engineering', 'ইলেকট্রিক্যাল ও ইলেকট্রনিক ইঞ্জিনিয়ারিং');
