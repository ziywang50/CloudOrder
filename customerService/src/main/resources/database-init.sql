DROP TABLE IF EXISTS authorities;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers
(
    id         SERIAL PRIMARY KEY   NOT NULL,
    email      TEXT UNIQUE          NOT NULL,
    enabled    BOOLEAN DEFAULT TRUE NOT NULL,
    password   TEXT                 NOT NULL,
    first_name TEXT,
    last_name  TEXT
);


CREATE TABLE authorities
(
    id        SERIAL PRIMARY KEY NOT NULL,
    email     TEXT               NOT NULL,
    authority TEXT               NOT NULL,
    CONSTRAINT fk_customer FOREIGN KEY (email) REFERENCES customers (email) ON DELETE CASCADE
);

