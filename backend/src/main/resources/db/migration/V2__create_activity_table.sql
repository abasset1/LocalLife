CREATE TABLE activity (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    description TEXT,
    category VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    status VARCHAR(50)
);
