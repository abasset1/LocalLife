/**
 * Fond de carte du site : Plan IGN (Géoplateforme de l'IGN, service public), en remplacement des tuiles
 * d'OpenStreetMap (`tile.openstreetmap.org`). Ces dernières sont financées par des dons pour les contributeurs
 * d'OpenStreetMap et leur politique d'usage exclut un service public à fort trafic : risque de blocage à la mise
 * en public de Neiri (11/11/2026).
 *
 * Plan IGN : gratuit, sans clé ni quota annoncé pour cet usage, Licence Ouverte (Etalab 2.0), zooms 0 à 19. Il
 * couvre la France (métropole et outre-mer) : hors de France, le fond reste vide, ce qui convient au périmètre de
 * Neiri. Le thème Night assombrit ces tuiles par un filtre CSS (`.leaflet-tile-pane`, styles.css), comme avant.
 *
 * Tuiles WMTS en projection Web Mercator (`TILEMATRIXSET=PM`) : `{z}`, `{y}`, `{x}` remplis par Leaflet.
 */
export const BASEMAP_URL = "https://data.geopf.fr/wmts?REQUEST=GetTile&SERVICE=WMTS&VERSION=1.0.0&STYLE=normal"
    + "&TILEMATRIXSET=PM&FORMAT=image/png&LAYER=GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2"
    + "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}";

/** Dernier niveau publié par la Géoplateforme pour le Plan IGN (au-delà : 404). */
export const BASEMAP_MAX_ZOOM = 19;

/** Mention exigée par la Licence Ouverte : source des données. */
export const BASEMAP_ATTRIBUTION = '&copy; <a href="https://www.ign.fr/">IGN</a> – Plan IGN';
