-- Recherches faites à chaque élément importé (ImportService → LocationService/MediaService#findOrCreate) : sans
-- index, chacune parcourait toute la table, et l'import ralentissait à mesure que la base grossissait.

-- LocationRepository#findByAddressAndCityAndPostalCodeOrderByIdAsc. La ville reste hors de l'index : avec
-- l'adresse (512 caractères au plus), elle pourrait dépasser la taille maximale d'une entrée d'index btree.
CREATE INDEX idx_location_postal_code_address ON location (postal_code, address);

-- LocationRepository#findByLatitudeAndLongitudeOrderByIdAsc.
CREATE INDEX idx_location_latitude_longitude ON location (latitude, longitude);

-- MediaRepository#findFirstByUrlOrderByIdAsc. Index hash : une URL (1 024 caractères au plus) peut dépasser la
-- taille maximale d'une entrée btree ; la recherche n'est qu'une égalité.
CREATE INDEX idx_media_url ON media USING HASH (url);
