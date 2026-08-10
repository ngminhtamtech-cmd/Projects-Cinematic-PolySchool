-- Create CinemaFilms table to link allowed films per cinema
IF OBJECT_ID('CinemaFilms', 'U') IS NULL
BEGIN
    CREATE TABLE CinemaFilms (
        CinemaId INT NOT NULL FOREIGN KEY REFERENCES Cinemas(Id) ON DELETE CASCADE,
        FilmId INT NOT NULL FOREIGN KEY REFERENCES Films(Id) ON DELETE CASCADE,
        PRIMARY KEY (CinemaId, FilmId)
    );
END
GO

-- Seed default initial mappings so existing showtime films stay assigned
INSERT INTO CinemaFilms (CinemaId, FilmId)
SELECT DISTINCT c.Id, f.Id
FROM Cinemas c
CROSS JOIN Films f
WHERE NOT EXISTS (
    SELECT 1 FROM CinemaFilms cf WHERE cf.CinemaId = c.Id AND cf.FilmId = f.Id
);
GO
