CREATE TABLE ride_tracks
(
    ride_track_id  SERIAL PRIMARY KEY,
    shared_ride_id int   NOT NULL,
    gps_points     jsonb NOT NULL,
    created_at     timestamp DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shared_ride_id) REFERENCES shared_rides (shared_ride_id)
);
