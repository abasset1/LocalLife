import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import type { LatLngExpression } from "leaflet";
import L from "leaflet";
import { MapContainer, Marker, Popup, TileLayer, useMap, useMapEvents } from "react-leaflet";
import MarkerClusterGroup from "react-leaflet-cluster";
import { Link, useNavigate } from "react-router-dom";
import { apiFetch } from "./api/apiClient";
import { clearToken, getPayload } from "./auth/authStorage";

/**
 * `sourceName` ajouté en LL-8006 : jusqu'ici le backend n'exposait que
 * `sourceId` (identifiant technique, retiré de la réponse au profit de
 * `sourceName` — voir `ActivityResponse` côté backend), inexploitable
 * pour l'affichage demandé par le critère d'acceptation du ticket
 * (« titre, date, lieu, source correctement affichés »).
 *
 * `address`/`city` ajoutés en LL-EF-008 : afficher des coordonnées GPS
 * brutes à l'utilisateur final n'étant pas viable (demande d'Alex),
 * `latitude`/`longitude` ne servent plus désormais qu'au positionnement
 * du marqueur — voir `ActivityResponse` côté backend pour le détail de
 * leur résolution (géocodage à l'écriture, jamais à la lecture).
 * Toutes deux nullables : activités existantes non re-géocodées/
 * ré-importées, ou source ne fournissant pas cette donnée — voir
 * `formatActivityLocation`/`formatActivityCity` ci-dessous pour le repli
 * appliqué dans ce cas.
 */
interface Activity {
    id: number;
    title: string;
    category: string;
    latitude: number;
    longitude: number;
    startDate: string;
    sourceName: string;
    address: string | null;
    city: string | null;
}

/**
 * Food truck (LL-6009) : deuxième type de marqueur affiché sur la même
 * carte qu'`Activity`, depuis un module backend séparé
 * (`docs/02_Architecture/FOOD_TRUCK_CONTRACT.md`). Volontairement plus
 * simple qu'`Activity` côté frontend aussi : pas de filtres, pas de date
 * — voir la section « Ajouter un nouveau type de point sur la carte »
 * plus bas pour le patron à suivre si un troisième type doit être ajouté.
 */
interface FoodTruck {
    id: number;
    name: string;
    category: string;
    latitude: number;
    longitude: number;
}

interface ApiErrorBody {
    message: string;
}

interface UserPosition {
    latitude: number;
    longitude: number;
}

/**
 * Zone actuellement visible sur la carte (LL-4012), au format attendu par
 * le contrat LL-4006 (`docs/02_Architecture/BOUNDING_BOX_SEARCH_CONTRACT.md`).
 * `null` tant que l'utilisateur n'a pas encore déplacé/zoomé la carte —
 * dans ce cas la recherche reste pilotée par `/nearby` (LL-4008/LL-4011).
 */
interface MapBounds {
    swLatitude: number;
    swLongitude: number;
    neLatitude: number;
    neLongitude: number;
}

/** Délai de neutralisation (ms) entre la fin d'un geste sur la carte et le déclenchement
 * effectif d'une nouvelle recherche — absorbe une rafale de `moveend` rapprochés (ex.
 * glisser/relâcher/glisser à nouveau rapidement), en plus du fait que `moveend`/`zoomend`
 * ne se déclenchent déjà qu'une fois à la fin du geste (pas en continu pendant le
 * déplacement/zoom, contrairement à `move`/`zoom`). */
const MAP_BOUNDS_DEBOUNCE_MS = 400;

/**
 * Composant enfant sans rendu visuel, monté à l'intérieur de
 * `<MapContainer>` : c'est la seule façon d'écouter les événements de la
 * carte avec react-leaflet — `useMapEvents` doit être appelé depuis un
 * descendant de `MapContainer`, qui n'expose pas de props
 * `onMoveEnd`/`onZoomEnd` directement.
 */
function MapBoundsWatcher({ onBoundsChange }: { onBoundsChange: (bounds: MapBounds) => void }) {
    const debounceTimer = useRef<number | null>(null);

    const map = useMapEvents({
        moveend: () => scheduleBoundsUpdate(),
        zoomend: () => scheduleBoundsUpdate(),
    });

    function scheduleBoundsUpdate() {
        if (debounceTimer.current !== null) {
            window.clearTimeout(debounceTimer.current);
        }
        debounceTimer.current = window.setTimeout(() => {
            const bounds = map.getBounds();
            onBoundsChange({
                swLatitude: bounds.getSouthWest().lat,
                swLongitude: bounds.getSouthWest().lng,
                neLatitude: bounds.getNorthEast().lat,
                neLongitude: bounds.getNorthEast().lng,
            });
        }, MAP_BOUNDS_DEBOUNCE_MS);
    }

    useEffect(() => {
        return () => {
            if (debounceTimer.current !== null) {
                window.clearTimeout(debounceTimer.current);
            }
        };
    }, []);

    return null;
}

/**
 * Composant enfant sans rendu visuel (LL-7007, même patron que
 * `MapBoundsWatcher` ci-dessus) : `MapContainer` ne respecte sa prop
 * `center` qu'au montage initial — un changement ultérieur de
 * `userPosition` (LL-4010) ne recentre donc jamais visuellement la
 * carte. `useMap()` (accessible uniquement depuis un descendant de
 * `MapContainer`) donne accès à l'instance Leaflet pour appeler
 * `setView` explicitement lorsqu'une position est obtenue.
 */
function MapRecenterOnUserPosition({ position }: { position: UserPosition | null }) {
    const map = useMap();

    useEffect(() => {
        if (position) {
            map.setView([position.latitude, position.longitude], map.getZoom());
        }
    }, [position, map]);

    return null;
}

const MARSEILLE_LATITUDE = 43.2965;
const MARSEILLE_LONGITUDE = 5.3698;
const MARSEILLE_COORDINATES: LatLngExpression = [MARSEILLE_LATITUDE, MARSEILLE_LONGITUDE];

/**
 * Rayon de recherche (LL-4001 : en kilomètres, max 50), utilisé pour la
 * recherche `/nearby` — repli par défaut tant que l'utilisateur n'a pas
 * interagi avec la carte (`mapBounds === null`, voir `MapBounds`
 * ci-dessus). Une fois que la carte a été déplacée/zoomée (LL-4012), ce
 * rayon fixe n'entre plus en jeu : la recherche passe sur
 * `/within-bounds`, pilotée par la zone réellement visible.
 */
const DEFAULT_SEARCH_RADIUS_KM = 50;

/** Valeur du filtre catégorie représentant « pas de filtre ». */
const ALL_CATEGORIES = "";

/**
 * Valeur du filtre date représentant « pas de filtre explicite ». Un
 * `<input type="date">` HTML renvoie nativement une chaîne vide quand il
 * est effacé, et sinon déjà au format ISO-8601 `yyyy-MM-dd` attendu par
 * le contrat LL-4005 — aucune conversion nécessaire avant de la passer
 * telle quelle en paramètre `date`. Depuis LL-9001, ne pas envoyer ce
 * paramètre ne veut plus dire "aucun filtre de date" côté backend : la
 * date du jour est appliquée par défaut (voir
 * {@code ActivityService#findNearby} côté backend) — pour voir les
 * activités passées ou futures, l'utilisateur doit choisir une date
 * explicitement via ce filtre.
 */
const NO_DATE_FILTER = "";

/**
 * Icône dédiée au marqueur food truck (LL-6009, critère « distinction
 * visuelle [...] suffisante avec une activité ») : `divIcon` (HTML/CSS,
 * classe `.food-truck-marker` dans `styles.css`) plutôt qu'une image
 * externe — évite tout problème de résolution d'assets Leaflet avec Vite
 * (icône par défaut de `react-leaflet` déjà utilisée telle quelle pour
 * les activités, sans configuration particulière ; ajouter une deuxième
 * image nécessiterait de résoudre ce problème pour un seul marqueur,
 * disproportionné pour ce ticket).
 *
 * --- Ajouter un nouveau type de point sur la carte ---
 * `FoodTruck` est pensé comme un patron reproductible pour un futur
 * troisième type (décision Alex, LL-6009) : côté backend, un nouveau
 * module autonome (`domain`/`application`/`infrastructure`/`api`, voir
 * `docs/02_Architecture/FOOD_TRUCK_CONTRACT.md` pour le raisonnement
 * complet) plutôt qu'une extension d'un module existant. Côté frontend,
 * le patron est : (1) une interface TypeScript dédiée (voir `FoodTruck`
 * ci-dessus) — champs minimaux, pas de réutilisation forcée du type
 * `Activity` ; (2) un état + un `useEffect` de récupération isolé (voir
 * plus bas, indépendant de celui des activités — pas de filtres
 * partagés tant qu'aucun besoin réel ne l'exige) ; (3) une icône
 * `divIcon` dédiée, même schéma que `FOOD_TRUCK_MARKER_ICON` ; (4) un
 * bloc `{items.map(...)}` de marqueurs supplémentaire dans le même
 * `<MapContainer>` (jamais un second composant carte). Pas d'abstraction
 * générique (« couche de carte » configurable) construite par
 * anticipation ici : avec seulement deux types de points, une telle
 * abstraction ajouterait de la complexité sans bénéfice mesurable — à
 * réévaluer si un troisième type concret est effectivement demandé.
 */
const FOOD_TRUCK_MARKER_ICON = L.divIcon({
    className: "food-truck-marker",
    html: "🚚",
    iconSize: [32, 32],
    iconAnchor: [16, 16],
});

function buildCategoryOptions(items: Activity[]): string[] {
    // LL-7007 : des activités importées depuis OpenAgenda peuvent avoir une
    // catégorie absente (`null`/vide) — sans ce filtre, `.localeCompare`
    // plantait sur ces valeurs et cassait la vue par défaut (voir LL-7004).
    const categories = items.map((item) => item.category).filter((category): category is string => Boolean(category));
    return Array.from(new Set(categories)).sort((a, b) => a.localeCompare(b, "fr"));
}

/** Repli affiché quand une activité n'a pas de ville résolue (LL-EF-008, voir Activity ci-dessus). */
const UNKNOWN_CITY_LABEL = "Ville non renseignée";

/**
 * Repli affiché quand une activité n'a pas d'adresse résolue (LL-10007,
 * critère d'acceptation « l'adresse est visible dans la liste ») — même
 * convention que {@link UNKNOWN_CITY_LABEL} : jamais de valeur déduite ou
 * approximative (règle explicite de `LOCATION_CONTRACT.md`), un texte de
 * repli honnête plutôt qu'un champ vide ou une valeur construite à partir
 * de `city`/des coordonnées.
 */
const UNKNOWN_ADDRESS_LABEL = "Adresse non renseignée";

/**
 * Lieu affiché à l'utilisateur (LL-EF-008, demande d'Alex : des
 * coordonnées GPS brutes ne sont pas viables). Ordre de repli :
 * `address` (la plus précise et la plus lisible) puis `city` seule, puis
 * enfin les coordonnées — uniquement pour les activités antérieures à ce
 * ticket, jamais re-géocodées/ré-importées, qui n'ont ni l'une ni
 * l'autre.
 */
function formatActivityLocation(activity: Activity): string {
    if (activity.address) {
        return activity.address;
    }
    if (activity.city) {
        return activity.city;
    }
    return `${activity.latitude.toFixed(4)}, ${activity.longitude.toFixed(4)}`;
}

/**
 * Regroupe les activités par ville puis trie chaque groupe par date
 * (LL-EF-008, critères d'acceptation « triées par ville » / « à
 * l'intérieur d'une ville, triées par date »). Les groupes eux-mêmes sont
 * triés alphabétiquement (locale "fr", comme `buildCategoryOptions`
 * ci-dessus) ; les activités sans ville connue sont regroupées sous
 * `UNKNOWN_CITY_LABEL`, toujours affiché en dernier plutôt qu'intercalé
 * alphabétiquement (une ville non renseignée n'est pas une vraie ville).
 */
function groupActivitiesByCity(items: Activity[]): Array<[string, Activity[]]> {
    const groups = new Map<string, Activity[]>();
    for (const activity of items) {
        const city = activity.city ?? UNKNOWN_CITY_LABEL;
        const group = groups.get(city);
        if (group) {
            group.push(activity);
        } else {
            groups.set(city, [activity]);
        }
    }

    const sortByDate = (a: Activity, b: Activity) => a.startDate.localeCompare(b.startDate);
    for (const group of groups.values()) {
        group.sort(sortByDate);
    }

    return Array.from(groups.entries()).sort(([cityA], [cityB]) => {
        if (cityA === UNKNOWN_CITY_LABEL) {
            return cityB === UNKNOWN_CITY_LABEL ? 0 : 1;
        }
        if (cityB === UNKNOWN_CITY_LABEL) {
            return -1;
        }
        return cityA.localeCompare(cityB, "fr");
    });
}

function App() {
    const navigate = useNavigate();
    const [activities, setActivities] = useState<Activity[]>([]);
    const [availableCategories, setAvailableCategories] = useState<string[]>([]);
    const [selectedCategory, setSelectedCategory] = useState(ALL_CATEGORIES);
    const [selectedDate, setSelectedDate] = useState(NO_DATE_FILTER);
    const [currentUser, setCurrentUser] = useState(() => getPayload());
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    const [category, setCategory] = useState("");
    const [address, setAddress] = useState("");
    const [submitStatus, setSubmitStatus] = useState<"idle" | "success" | "error">("idle");
    const [submitError, setSubmitError] = useState<string | null>(null);
    /**
     * LL-EF-001 : le formulaire de saisie d'une activité passe d'un bandeau
     * permanent (visible même pour un visiteur non connecté) à une fenêtre
     * modale, ouverte explicitement via un bouton — voir `handleOpenContributionForm`.
     */
    const [isContributionModalOpen, setIsContributionModalOpen] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);
    const [isLoadingActivities, setIsLoadingActivities] = useState(true);
    const [searchError, setSearchError] = useState<string | null>(null);
    const [userPosition, setUserPosition] = useState<UserPosition | null>(null);
    const [mapBounds, setMapBounds] = useState<MapBounds | null>(null);
    const [foodTrucks, setFoodTrucks] = useState<FoodTruck[]>([]);
    /**
     * LL-EF-008 : bascule Carte ↔ Liste, dans le même bandeau que le filtre
     * catégorie (voir le rendu plus bas) — la liste est une seconde vue des
     * mêmes activités déjà chargées/filtrées (`activities`), pas une
     * requête séparée : les filtres catégorie/date s'appliquent donc
     * naturellement aux deux vues (critère d'acceptation « les filtres par
     * catégorie restent cohérents avec l'affichage en liste »).
     */
    const [viewMode, setViewMode] = useState<"map" | "list">("map");
    /**
     * Activité dont le détail est actuellement consulté depuis la liste
     * (LL-EF-008, critère d'acceptation « consulter le détail d'une
     * activité depuis la liste ») — `null` quand aucun détail n'est
     * ouvert. Réutilise le patron `modal-overlay`/`modal-dialog` déjà en
     * place pour le formulaire de contribution (LL-EF-001).
     */
    const [selectedActivity, setSelectedActivity] = useState<Activity | null>(null);
    /**
     * LL-EF-003 : mémorise les dépendances de la recherche précédente pour
     * distinguer, au sein du même effet, un déplacement/zoom pur de la
     * carte (seul `mapBounds` a changé) d'un changement « actif »
     * (filtre catégorie/date, position utilisateur obtenue, nouvelle
     * activité proposée) — voir l'effet de récupération des activités
     * plus bas pour l'utilisation.
     */
    const previousSearchDepsRef = useRef<{
        selectedCategory: string;
        selectedDate: string;
        refreshKey: number;
        userPosition: UserPosition | null;
    } | null>(null);

    useEffect(() => {
        const abortController = new AbortController();

        // LL-EF-003 : un déplacement/zoom pur de la carte (seuls les bounds
        // changent, tout le reste de la recherche est identique à l'appel
        // précédent) ne doit pas provoquer de coupure visuelle — l'ancienne
        // carte de LL-4012 supprimait systématiquement les marqueurs avant
        // même d'avoir reçu la réponse, ce qui faisait disparaître puis
        // réapparaître les marqueurs à chaque geste. On ne fait cela que
        // pour un changement « actif » (filtre, position, nouvelle
        // activité proposée) : dans ce cas l'utilisateur vient d'agir
        // explicitement, un état de chargement clair reste approprié.
        const previousDeps = previousSearchDepsRef.current;
        const isMapOnlyReload = previousDeps !== null
            && previousDeps.selectedCategory === selectedCategory
            && previousDeps.selectedDate === selectedDate
            && previousDeps.refreshKey === refreshKey
            && previousDeps.userPosition === userPosition;
        previousSearchDepsRef.current = { selectedCategory, selectedDate, refreshKey, userPosition };

        if (!isMapOnlyReload) {
            setIsLoadingActivities(true);
            setSearchError(null);
            // Suppression immédiate des anciens marqueurs (critère d'acceptation
            // de LL-4012), conservée pour un changement actif (filtre, position,
            // nouvelle activité) — seul le cas d'un déplacement/zoom pur de la
            // carte (voir `isMapOnlyReload` ci-dessus) évite désormais cette
            // coupure visuelle.
            setActivities([]);
        }

        async function loadActivities() {
            const params = new URLSearchParams();
            let endpoint: string;

            if (mapBounds) {
                // LL-4012 : une fois que l'utilisateur a déplacé/zoomé la carte, la zone
                // réellement visible devient la source de vérité pour la recherche —
                // remplace le rayon fixe de LL-4008/LL-4011 — conformément au contrat
                // LL-4006 (docs/02_Architecture/BOUNDING_BOX_SEARCH_CONTRACT.md).
                endpoint = "/api/v1/activities/within-bounds";
                params.set("swLatitude", String(mapBounds.swLatitude));
                params.set("swLongitude", String(mapBounds.swLongitude));
                params.set("neLatitude", String(mapBounds.neLatitude));
                params.set("neLongitude", String(mapBounds.neLongitude));
            } else {
                // Avant toute interaction avec la carte : comportement LL-4008/LL-4011
                // inchangé (rayon fixe autour de la position utilisateur ou de Marseille).
                endpoint = "/api/v1/activities/nearby";
                const latitude = userPosition?.latitude ?? MARSEILLE_LATITUDE;
                const longitude = userPosition?.longitude ?? MARSEILLE_LONGITUDE;
                params.set("latitude", String(latitude));
                params.set("longitude", String(longitude));
                params.set("radius", String(DEFAULT_SEARCH_RADIUS_KM));
            }

            if (selectedCategory !== ALL_CATEGORIES) {
                params.set("category", selectedCategory);
            }
            if (selectedDate !== NO_DATE_FILTER) {
                params.set("date", selectedDate);
            }

            try {
                const response = await fetch(`${endpoint}?${params.toString()}`, {
                    signal: abortController.signal,
                });

                if (response.ok) {
                    const data: Activity[] = await response.json();
                    // Que ce soit un rechargement silencieux (carte) ou actif (filtre) :
                    // un seul remplacement de la liste, une fois les données prêtes —
                    // c'est ce remplacement (pas une suppression préalable) qui met à
                    // jour les marqueurs affichés.
                    setActivities(data);
                    // La liste des catégories disponibles n'est reconstruite que quand
                    // aucun filtre catégorie/date n'est actif : sinon elle se réduirait au
                    // fil des sélections (une fois qu'un filtre est actif, la réponse ne
                    // contient plus que ce qui correspond) et l'utilisateur ne pourrait
                    // plus revenir en arrière.
                    // Depuis LL-9001, l'absence de filtre date explicite ne veut plus dire
                    // "réponse non filtrée" côté backend (qui applique désormais la date du
                    // jour par défaut, voir ActivityService#findNearby) : cette liste ne
                    // reflète donc que les catégories des activités en cours aujourd'hui,
                    // pas l'historique complet. Effet de bord jugé cohérent avec l'objectif
                    // du ticket (ne pas proposer un filtre qui ne renverrait rien) — à
                    // confirmer avec Alex si un comportement différent est souhaité.
                    if (selectedCategory === ALL_CATEGORIES && selectedDate === NO_DATE_FILTER) {
                        setAvailableCategories(buildCategoryOptions(data));
                    }
                } else {
                    // LL-4013 : état « erreur » distinct de l'état « aucun résultat » —
                    // un échec de la requête (ex. 400/500) ne doit pas être présenté comme
                    // une recherche qui a simplement abouti à zéro activité.
                    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                    setSearchError(body?.message ?? "Impossible de charger les activités, réessaie.");
                    setActivities([]);
                }
            } catch {
                if (!abortController.signal.aborted) {
                    setSearchError("Impossible de contacter le serveur, réessaie plus tard.");
                    setActivities([]);
                }
            } finally {
                // Ne pas repasser `isLoadingActivities` à false pour une requête déjà
                // annulée : la requête suivante (déclenchée par le même changement de
                // dépendance) l'a déjà remis à true, on ne veut pas l'écraser.
                if (!abortController.signal.aborted) {
                    setIsLoadingActivities(false);
                }
            }
        }

        void loadActivities();

        return () => abortController.abort();
    }, [selectedCategory, selectedDate, refreshKey, userPosition, mapBounds]);

    /**
     * Récupération des food trucks (LL-6009), isolée de celle des
     * activités ci-dessus — pas de filtres, pas de zone géographique
     * (critères d'acceptation du ticket : consultation simple), un seul
     * appel au montage. Voir la javadoc de `FOOD_TRUCK_MARKER_ICON` pour
     * le patron à suivre si un type de point supplémentaire est ajouté
     * plus tard.
     */
    useEffect(() => {
        const abortController = new AbortController();

        async function loadFoodTrucks() {
            try {
                const response = await fetch("/api/v1/foodtrucks", { signal: abortController.signal });
                if (response.ok) {
                    const data: FoodTruck[] = await response.json();
                    setFoodTrucks(data);
                }
                // Pas de gestion d'erreur dédiée (contrairement à `searchError` pour les
                // activités) : les food trucks restent un contenu secondaire de la carte,
                // un échec ne doit pas bloquer l'affichage des activités.
            } catch {
                // Requête annulée (démontage) ou serveur injoignable : la carte reste
                // utilisable sans food trucks, même choix que ci-dessus.
            }
        }

        void loadFoodTrucks();

        return () => abortController.abort();
    }, []);

    function handleLogout() {
        clearToken();
        setCurrentUser(null);
    }

    /**
     * LL-EF-001 : point d'entrée unique du bouton « Proposer une activité »,
     * toujours visible (décision Alex) — un visiteur non connecté est
     * redirigé vers /login plutôt que de voir le formulaire.
     */
    function handleOpenContributionForm() {
        if (!currentUser) {
            navigate("/login");
            return;
        }
        setIsContributionModalOpen(true);
    }

    function handleCloseContributionForm() {
        setIsContributionModalOpen(false);
        // Un message d'un envoi précédent ne doit pas réapparaître à la
        // prochaine ouverture de la fenêtre.
        setSubmitStatus("idle");
        setSubmitError(null);
    }

    // Fermeture au clavier (Échap), en plus du bouton de fermeture et du
    // clic sur l'arrière-plan (voir le rendu de la modale plus bas) —
    // critère d'acceptation « la fermeture de la fenêtre fonctionne
    // correctement ».
    useEffect(() => {
        if (!isContributionModalOpen) {
            return;
        }

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") {
                handleCloseContributionForm();
            }
        }

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [isContributionModalOpen]);

    // LL-EF-008 : même comportement de fermeture au clavier (Échap) que la
    // modale de contribution ci-dessus, pour la modale de détail d'activité.
    useEffect(() => {
        if (!selectedActivity) {
            return;
        }

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") {
                setSelectedActivity(null);
            }
        }

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [selectedActivity]);

    /**
     * LL-EF-002 : la géolocalisation est désormais demandée automatiquement
     * au chargement de la page (plus de bandeau « Utiliser la
     * localisation » ni de clic explicite requis, voir critère
     * d'acceptation « ne pas demander une action utilisateur inutile »).
     * Ne s'exécute qu'une fois au montage (tableau de dépendances vide).
     *
     * Si la géolocalisation n'est pas disponible, si l'utilisateur refuse
     * la permission, ou en cas d'erreur/timeout, `userPosition` reste
     * `null` et la recherche continue de se rappuyer silencieusement sur
     * `MARSEILLE_LATITUDE`/`MARSEILLE_LONGITUDE` (comportement de repli
     * déjà en place depuis LL-4008) — plus aucun message n'est affiché à
     * l'utilisateur dans ces cas, conformément à la suppression du
     * bandeau.
     *
     * ⚠️ Aucune position utilisateur n'est envoyée au backend ni stockée
     * ailleurs qu'en état React local (`userPosition`) — perdue à chaque
     * rechargement de page, conformément au critère d'acceptation
     * « aucune position utilisateur persistée en base » de LL-4010.
     */
    useEffect(() => {
        if (!("geolocation" in navigator)) {
            return;
        }

        navigator.geolocation.getCurrentPosition(
            (position) => {
                setUserPosition({
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                });
            },
            () => {
                // Permission refusée, position indisponible ou timeout : pas de
                // message affiché (plus de bandeau), repli silencieux sur
                // MARSEILLE_LATITUDE/MARSEILLE_LONGITUDE.
            },
        );
    }, []);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSubmitStatus("idle");
        setSubmitError(null);

        try {
            const response = await apiFetch("/api/v1/activities", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ title, description, category, address }),
            });

            if (!response.ok) {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setSubmitError(body?.message ?? "Impossible de proposer cette activité, réessaie.");
                setSubmitStatus("error");
                return;
            }

            // Ne pas se contenter d'ajouter `created` localement (comme avant LL-4008) :
            // la liste affichée est désormais filtrée par zone/catégorie via /nearby, et
            // l'activité créée pourrait ne pas correspondre au filtre actif (catégorie
            // différente, hors du rayon de recherche) — un simple refetch reste la
            // source de vérité la plus simple et la plus sûre ici.
            setRefreshKey((current) => current + 1);
            setSubmitStatus("success");
            setTitle("");
            setDescription("");
            setCategory("");
            setAddress("");
            setIsContributionModalOpen(false);
        } catch {
            setSubmitError("Impossible de contacter le serveur, réessaie plus tard.");
            setSubmitStatus("error");
        }
    }

    return (
        <main className="application-shell">
            <header className="application-header">
                <h1>LocalLife</h1>
                <div className="header-actions">
                    {/*
                      LL-EF-001 : bouton toujours visible, y compris pour un visiteur non
                      connecté (décision Alex) — voir `handleOpenContributionForm` pour la
                      redirection vers /login dans ce cas.
                    */}
                    <button
                        className="header-add-activity-button"
                        onClick={handleOpenContributionForm}
                        type="button"
                    >
                        Proposer une activité
                    </button>
                    {currentUser ? (
                        <div className="header-user">
                            <span>Bonjour, {currentUser.email}</span>
                            {/*
                              LL-EF-006 : accès au profil, pour tout utilisateur connecté
                              (contrairement au lien Administration ci-dessous, réservé au
                              rôle ADMIN) — critère d'acceptation « un utilisateur connecté
                              peut accéder à son interface utilisateur ».
                            */}
                            <Link className="header-profile-link" to="/profile">
                                Mon profil
                            </Link>
                            {/*
                              LL-EF-004 : lien affiché uniquement pour un utilisateur avec le
                              rôle ADMIN — confort de navigation, pas une protection : l'accès
                              réel est vérifié à l'ouverture de /admin (AdminPage) et, de toute
                              façon, déjà appliqué côté backend sur chaque appel
                              (SecurityConfig, rôle ADMIN requis).
                            */}
                            {currentUser.role === "ADMIN" && (
                                <Link className="header-admin-link" to="/admin">
                                    Administration
                                </Link>
                            )}
                            <button className="header-logout-button" onClick={handleLogout} type="button">
                                Déconnexion
                            </button>
                        </div>
                    ) : (
                        <Link className="header-login-link" to="/login">
                            Se connecter
                        </Link>
                    )}
                </div>
            </header>
            <div className="activity-filters">
                <label htmlFor="category-filter">Filtrer par catégorie</label>
                <select
                    id="category-filter"
                    onChange={(event) => setSelectedCategory(event.target.value)}
                    value={selectedCategory}
                >
                    <option value={ALL_CATEGORIES}>Toutes les catégories</option>
                    {availableCategories.map((availableCategory) => (
                        <option key={availableCategory} value={availableCategory}>
                            {availableCategory}
                        </option>
                    ))}
                </select>
                <label htmlFor="date-filter">Filtrer par date</label>
                <input
                    id="date-filter"
                    onChange={(event) => setSelectedDate(event.target.value)}
                    type="date"
                    value={selectedDate}
                />
                {selectedDate !== NO_DATE_FILTER && (
                    <button
                        aria-label="Effacer le filtre par date"
                        onClick={() => setSelectedDate(NO_DATE_FILTER)}
                        type="button"
                    >
                        ✕
                    </button>
                )}
                {/*
                  LL-EF-008 : bascule Carte ↔ Liste, dans le même bandeau que
                  « Filtrer par catégorie » (critère d'acceptation explicite du
                  ticket) — `margin-left: auto` (voir styles.css) la pousse en
                  haut à droite du bandeau, comme demandé. `aria-pressed`
                  identifie clairement la vue active (critère d'acceptation
                  « l'état actif de la vue doit être clairement identifiable »).
                */}
                <div className="view-mode-toggle" role="group" aria-label="Choix de l'affichage">
                    <button
                        aria-pressed={viewMode === "map"}
                        className={viewMode === "map" ? "view-mode-button view-mode-button-active" : "view-mode-button"}
                        onClick={() => setViewMode("map")}
                        type="button"
                    >
                        Carte
                    </button>
                    <button
                        aria-pressed={viewMode === "list"}
                        className={viewMode === "list" ? "view-mode-button view-mode-button-active" : "view-mode-button"}
                        onClick={() => setViewMode("list")}
                        type="button"
                    >
                        Liste
                    </button>
                </div>
            </div>
            {/*
              LL-EF-001 : formulaire de saisie sous forme de fenêtre modale (overlay),
              ouverte uniquement pour un utilisateur connecté (voir
              `handleOpenContributionForm`) — remplace le bandeau permanent précédent.
              Fermeture possible de trois façons : bouton ✕, clic sur l'arrière-plan
              (`handleOverlayClick` ci-dessous, arrêté par `stopPropagation` sur la boîte
              de dialogue elle-même pour ne pas se fermer au clic à l'intérieur), et
              touche Échap (voir le `useEffect` plus haut).
            */}
            {isContributionModalOpen && currentUser && (
                <div
                    className="modal-overlay"
                    onClick={handleCloseContributionForm}
                    role="presentation"
                >
                    <div
                        aria-labelledby="contribution-modal-title"
                        aria-modal="true"
                        className="modal-dialog"
                        onClick={(event) => event.stopPropagation()}
                        role="dialog"
                    >
                        <div className="modal-header">
                            <h2 id="contribution-modal-title">Proposer une activité</h2>
                            <button
                                aria-label="Fermer"
                                className="modal-close-button"
                                onClick={handleCloseContributionForm}
                                type="button"
                            >
                                ✕
                            </button>
                        </div>
                        <form className="contribution-form" onSubmit={(event) => void handleSubmit(event)}>
                            <div className="form-field">
                                <label htmlFor="activity-title">Titre</label>
                                <input
                                    id="activity-title"
                                    onChange={(event) => setTitle(event.target.value)}
                                    required
                                    type="text"
                                    value={title}
                                />
                            </div>
                            <div className="form-field">
                                <label htmlFor="activity-description">Description</label>
                                <input
                                    id="activity-description"
                                    onChange={(event) => setDescription(event.target.value)}
                                    required
                                    type="text"
                                    value={description}
                                />
                            </div>
                            <div className="form-field">
                                <label htmlFor="activity-category">Catégorie</label>
                                <input
                                    id="activity-category"
                                    onChange={(event) => setCategory(event.target.value)}
                                    required
                                    type="text"
                                    value={category}
                                />
                            </div>
                            <div className="form-field">
                                <label htmlFor="activity-address">Adresse</label>
                                <input
                                    id="activity-address"
                                    onChange={(event) => setAddress(event.target.value)}
                                    placeholder="Ex : 10 rue de la République, Marseille"
                                    required
                                    type="text"
                                    value={address}
                                />
                            </div>
                            <div className="modal-actions">
                                <button type="submit">Proposer l'activité</button>
                            </div>
                            {submitStatus === "success" && <span className="form-message form-message-success">Activité proposée !</span>}
                            {submitStatus === "error" && <span className="form-message form-message-error">{submitError}</span>}
                        </form>
                    </div>
                </div>
            )}
            {/*
              LL-EF-008 : modale de détail d'une activité sélectionnée depuis la
              liste (critère d'acceptation « consulter le détail d'une activité
              depuis la liste ») — même patron que la modale de contribution
              ci-dessus (overlay cliquable, stopPropagation sur la boîte de
              dialogue, fermeture Échap déjà branchée plus haut).
            */}
            {selectedActivity && (
                <div
                    className="modal-overlay"
                    onClick={() => setSelectedActivity(null)}
                    role="presentation"
                >
                    <div
                        aria-labelledby="activity-detail-title"
                        aria-modal="true"
                        className="modal-dialog"
                        onClick={(event) => event.stopPropagation()}
                        role="dialog"
                    >
                        <div className="modal-header">
                            <h2 id="activity-detail-title">{selectedActivity.title}</h2>
                            <button
                                aria-label="Fermer"
                                className="modal-close-button"
                                onClick={() => setSelectedActivity(null)}
                                type="button"
                            >
                                ✕
                            </button>
                        </div>
                        <div className="activity-detail-body">
                            <p className="activity-detail-row">
                                <strong>Lieu :</strong> {formatActivityLocation(selectedActivity)}
                            </p>
                            <p className="activity-detail-row">
                                <strong>Date :</strong>{" "}
                                {new Date(selectedActivity.startDate).toLocaleString("fr-FR", {
                                    dateStyle: "long",
                                    timeStyle: "short",
                                })}
                            </p>
                            {selectedActivity.category && (
                                <p className="activity-detail-row">
                                    <strong>Catégorie :</strong> {selectedActivity.category}
                                </p>
                            )}
                            <p className="activity-detail-row">
                                <strong>Source :</strong> {selectedActivity.sourceName}
                            </p>
                        </div>
                    </div>
                </div>
            )}
            {/* LL-EF-008 : vue Carte, affichée uniquement quand viewMode === "map" (voir le bouton bascule du bandeau ci-dessus). */}
            {viewMode === "map" && (
                <div className="map-area">
                    {/*
                      LL-4013 : 4 états distincts, chacun visible et compréhensible séparément
                      (chargement / résultats / aucun résultat / erreur) — l'état « résultats »
                      n'a pas besoin de message dédié : les marqueurs sur la carte en tiennent
                      lieu.
                    */}
                    {isLoadingActivities && <p className="activities-status">Chargement des activités…</p>}
                    {!isLoadingActivities && searchError && (
                        <p className="activities-status activities-status-error" role="alert">
                            {searchError}
                        </p>
                    )}
                    {!isLoadingActivities && !searchError && activities.length === 0 && (
                        <p className="activities-status">Aucune activité trouvée dans cette zone.</p>
                    )}
                    <MapContainer
                        center={MARSEILLE_COORDINATES}
                        className="map"
                        zoom={13}
                        zoomControl
                    >
                        <TileLayer
                            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                        />
                        <MapBoundsWatcher onBoundsChange={setMapBounds} />
                        <MapRecenterOnUserPosition position={userPosition} />
                        {/*
                          Clustering (demande explicite d'Alex, hors ticket de sprint) : au-delà
                          d'un volume normal d'activités importées (LL-8009, pagination
                          OpenAgenda), un marqueur par activité rendait la carte inutilisable.
                          `MarkerClusterGroup` (react-leaflet-cluster, au-dessus de
                          leaflet.markercluster) regroupe les marqueurs proches en un badge
                          « +N » qui se sépare au zoom — solution standard avec Leaflet, aucun
                          changement côté backend (le volume de données transmises reste le
                          même, seul le rendu change). Seules les activités sont regroupées :
                          les food trucks restent des marqueurs individuels, cohérent avec leur
                          isolement déjà documenté (voir `FOOD_TRUCK_MARKER_ICON` ci-dessus) —
                          leur volume n'a pas posé ce problème.
                        */}
                        <MarkerClusterGroup>
                            {activities.map((activity) => (
                                <Marker
                                    key={activity.id}
                                    position={[activity.latitude, activity.longitude]}
                                >
                                    <Popup>
                                        <strong>{activity.title}</strong>
                                        <br />
                                        {activity.category}
                                        <br />
                                        {new Date(activity.startDate).toLocaleDateString("fr-FR")}
                                        <br />
                                        {/* LL-8006/LL-EF-008 : lieu (adresse si disponible, repli sur la ville puis
                                            les coordonnées — voir formatActivityLocation) et source (nom lisible
                                            résolu depuis sourceId par ActivityResponse) — critère d'acceptation
                                            « titre, date, lieu, source correctement affichés ». */}
                                        Lieu : {formatActivityLocation(activity)}
                                        <br />
                                        Source : {activity.sourceName}
                                    </Popup>
                                </Marker>
                            ))}
                        </MarkerClusterGroup>
                        {/*
                          LL-6009 : food trucks, deuxième type de marqueur sur la même carte
                          (« sans créer un second système cartographique »). Icône dédiée
                          (FOOD_TRUCK_MARKER_ICON) pour la distinction visuelle avec une
                          activité ; popup sans date (un food truck n'est pas un événement
                          daté, voir FOOD_TRUCK_CONTRACT.md) — distinction fonctionnelle.
                        */}
                        {foodTrucks.map((foodTruck) => (
                            <Marker
                                icon={FOOD_TRUCK_MARKER_ICON}
                                key={`food-truck-${foodTruck.id}`}
                                position={[foodTruck.latitude, foodTruck.longitude]}
                            >
                                <Popup>
                                    <strong>{foodTruck.name}</strong>
                                    <br />
                                    {foodTruck.category}
                                </Popup>
                            </Marker>
                        ))}
                    </MapContainer>
                </div>
            )}
            {/*
              LL-EF-008 : vue Liste — seconde vue des mêmes activités déjà chargées/
              filtrées (voir la javadoc de `viewMode` plus haut), regroupées par ville
              puis triées par date (`groupActivitiesByCity`). Les food trucks ne sont
              pas des activités datées (voir FOOD_TRUCK_CONTRACT.md) et n'apparaissent
              donc pas ici, cohérent avec leur périmètre déjà limité à la carte.
            */}
            {viewMode === "list" && (
                <div className="list-area">
                    {isLoadingActivities && <p className="activities-status">Chargement des activités…</p>}
                    {!isLoadingActivities && searchError && (
                        <p className="activities-status activities-status-error" role="alert">
                            {searchError}
                        </p>
                    )}
                    {!isLoadingActivities && !searchError && activities.length === 0 && (
                        <p className="activities-status">Aucune activité trouvée dans cette zone.</p>
                    )}
                    {!isLoadingActivities && !searchError && activities.length > 0 && (
                        <div className="activity-list">
                            {groupActivitiesByCity(activities).map(([city, cityActivities]) => (
                                <section className="activity-list-group" key={city}>
                                    <h2 className="activity-list-city">{city}</h2>
                                    <ul className="activity-list-items">
                                        {cityActivities.map((activity) => (
                                            <li key={activity.id}>
                                                {/*
                                                  Critère d'acceptation « consulter le détail d'une activité
                                                  depuis la liste » : chaque ligne est un bouton (pas un <li>
                                                  cliquable directement, pour rester accessible au clavier)
                                                  qui ouvre la modale de détail définie plus haut.
                                                */}
                                                <button
                                                    className="activity-list-item"
                                                    onClick={() => setSelectedActivity(activity)}
                                                    type="button"
                                                >
                                                    <span className="activity-list-item-title">{activity.title}</span>
                                                    {/*
                                                      LL-10007 : critère d'acceptation explicite « l'adresse est
                                                      visible » dans la liste (jusqu'ici affichée uniquement dans
                                                      la modale de détail, LL-EF-008). La ville reste portée par
                                                      l'en-tête de groupe (`activity-list-city`) juste au-dessus :
                                                      pas de doublon, `activity.address` seul ici (jamais de repli
                                                      sur `city`, contrairement à `formatActivityLocation` utilisée
                                                      pour la carte/modale — la ville est déjà visible autrement
                                                      dans la liste).
                                                    */}
                                                    <span className="activity-list-item-address">
                                                        {activity.address ?? UNKNOWN_ADDRESS_LABEL}
                                                    </span>
                                                    <span className="activity-list-item-meta">
                                                        {new Date(activity.startDate).toLocaleDateString("fr-FR")}
                                                        {/* Heure affichée seulement si elle est significative (voir
                                                            critère d'acceptation « éventuellement l'heure ») : une
                                                            activité créée sans heure précise (formulaire de
                                                            contribution) reçoit minuit comme heure de soumission,
                                                            qu'il serait trompeur d'afficher comme horaire réel. */}
                                                        {new Date(activity.startDate).getHours() !== 0
                                                            || new Date(activity.startDate).getMinutes() !== 0
                                                            ? ` · ${new Date(activity.startDate).toLocaleTimeString("fr-FR", {
                                                                hour: "2-digit",
                                                                minute: "2-digit",
                                                            })}`
                                                            : ""}
                                                        {activity.category ? ` · ${activity.category}` : ""}
                                                    </span>
                                                </button>
                                            </li>
                                        ))}
                                    </ul>
                                </section>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </main>
    );
}

export default App;
