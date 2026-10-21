import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { apiFetch } from "./api/apiClient";
import { clearToken, getPayload, getToken } from "./auth/authStorage";
import ActivityDetailModal from "./components/explorer/ActivityDetailModal";
import ActivityList from "./components/explorer/ActivityList";
import ActivityPreview from "./components/explorer/ActivityPreview";
import { isInBounds, toLocalDateString } from "./components/explorer/activityUtils";
import { DEFAULT_SEARCH_RADIUS_KM, NO_DATE_FILTER } from "./components/explorer/constants";
import EmptyResultsHelp from "./components/explorer/EmptyResultsHelp";
import ContributionModal, { EMPTY_RECURRENCE, type ContributionRecurrence } from "./components/explorer/ContributionModal";
import ExplorerFilters from "./components/explorer/ExplorerFilters";
import ExplorerMap from "./components/explorer/ExplorerMap";
import ExplorerMenu from "./components/explorer/ExplorerMenu";
import ExplorerSearch from "./components/explorer/ExplorerSearch";
import LocationPrompt from "./components/explorer/LocationPrompt";
import type { GeolocationStatus } from "./components/explorer/LocationPrompt";
import { hasCoordinates, SEARCH_PAGE_SIZE, toActivity } from "./components/explorer/globalSearch";
import SearchResultBanner from "./components/explorer/SearchResultBanner";
import type { AppliedSearch, TextSearchStatus } from "./components/explorer/SearchResultBanner";
import { useActivitySearch } from "./components/explorer/useActivitySearch";
import { formatCityLabel } from "./components/explorer/useCitySuggestions";
import type { CitySuggestion } from "./components/explorer/useCitySuggestions";
import OnboardingModal from "./components/onboarding/OnboardingModal";
import FoodTruckList from "./components/foodtrucks/FoodTruckList";
import PlaceList from "./components/places/PlaceList";
import type { Place } from "./components/places/types";
import type {
    Activity,
    ActivityDetail,
    ApiErrorBody,
    Category,
    Contribution,
    FoodTruck,
    MapBounds,
    UserPosition,
    UserPreferences,
} from "./components/explorer/types";

/**
 * LL-EF-017 — Refonte de l'écran Explorer autour de la carte.
 *
 * `App.tsx` reste le point d'entrée de l'écran ("/", voir main.tsx) et
 * conserve l'intégralité de la logique métier/réseau déjà en place (voir
 * les effets de récupération des activités/food trucks ci-dessous,
 * inchangés) — seule la présentation est déléguée aux composants de
 * `components/explorer/`, selon la hiérarchie demandée en section 12 du
 * ticket : ExplorerMenu, ExplorerSearch, ExplorerFilters, ExplorerMap,
 * ActivityPreview, ActivityList, ActivityDetailModal, ContributionModal.
 */
/**
 * Contenu affiché sur la carte et dans la liste : activités, lieux (ADR-0011, phase B) ou food trucks (ADR-0013),
 * un seul à la fois — les trois superposés rendaient la carte illisible. Le choix est mémorisé dans le navigateur
 * (confort seulement : tout fonctionne sans stockage).
 */
type MapLayer = "activities" | "places" | "foodTrucks";

const MAP_LAYER_STORAGE_KEY = "locallife.mapLayer";

const MAP_LAYERS: { value: MapLayer; label: string; title: string }[] = [
    { value: "activities", label: "📅 Activités", title: "Afficher les activités" },
    { value: "places", label: "🏛 Lieux", title: "Afficher les lieux : musées, monuments, équipements…" },
    { value: "foodTrucks", label: "🚚 Food trucks", title: "Afficher les food trucks présents aujourd'hui" },
];

function readMapLayer(): MapLayer {
    try {
        const stored = window.localStorage.getItem(MAP_LAYER_STORAGE_KEY);
        return MAP_LAYERS.some((layer) => layer.value === stored) ? stored as MapLayer : "activities";
    } catch {
        return "activities";
    }
}

function writeMapLayer(value: MapLayer) {
    try {
        window.localStorage.setItem(MAP_LAYER_STORAGE_KEY, value);
    } catch {
        // Stockage indisponible (navigation privée…) : le choix vaut pour la session seulement.
    }
}

/** Coordonnées utilisables comme point de départ (préférence enregistrée) : nombres finis, dans les bornes. */
/**
 * Accueil de première connexion reporté (« Plus tard », spec P0-C) : mémorisé pour la session du
 * navigateur seulement, par utilisateur — rien n'est enregistré côté serveur, l'accueil reviendra à
 * une prochaine session tant que les préférences ne sont pas configurées.
 */
const ONBOARDING_POSTPONED_STORAGE_KEY = "locallife.onboardingPostponed";

function isOnboardingPostponed(userId: number | undefined): boolean {
    try {
        return userId !== undefined
            && window.sessionStorage.getItem(ONBOARDING_POSTPONED_STORAGE_KEY) === String(userId);
    } catch {
        return false;
    }
}

function rememberOnboardingPostponed(userId: number | undefined) {
    try {
        if (userId !== undefined) {
            window.sessionStorage.setItem(ONBOARDING_POSTPONED_STORAGE_KEY, String(userId));
        }
    } catch {
        // Stockage indisponible : le report vaut jusqu'au rechargement de la page.
    }
}

function isValidPosition(latitude: number | null, longitude: number | null): boolean {
    return latitude !== null
        && longitude !== null
        && Number.isFinite(latitude)
        && Number.isFinite(longitude)
        && Math.abs(latitude) <= 90
        && Math.abs(longitude) <= 180;
}

/** Délai maximal d'une demande de position (« Autour de moi ») avant de proposer une autre méthode. */
const GEOLOCATION_TIMEOUT_MS = 10000;

function App() {
    const navigate = useNavigate();
    const [activities, setActivities] = useState<Activity[]>([]);
    /** Catégories actives du référentiel (`GET /api/v1/categories`) : seules valeurs proposées et affichées. */
    const [categories, setCategories] = useState<Category[]>([]);
    /** Codes des catégories cochées ; vide = pas de filtre. */
    const [selectedCategoryCodes, setSelectedCategoryCodes] = useState<string[]>([]);
    /** Clé stable du filtre catégorie (codes triés) : évite de relancer la recherche sur un simple nouveau tableau. */
    const selectedCategoriesKey = useMemo(() => [...selectedCategoryCodes].sort().join(","), [selectedCategoryCodes]);
    const [selectedDate, setSelectedDate] = useState(NO_DATE_FILTER);
    const [selectedDateTo, setSelectedDateTo] = useState(NO_DATE_FILTER);
    const [currentUser, setCurrentUser] = useState(() => getPayload());
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    /** Formulaire de proposition : codes des catégories du référentiel cochées. */
    const [contributionCategoryCodes, setContributionCategoryCodes] = useState<string[]>([]);
    const [address, setAddress] = useState("");
    const [url, setUrl] = useState("");
    const [phone, setPhone] = useState("");
    const [email, setEmail] = useState("");
    const [startDate, setStartDate] = useState("");
    const [endDate, setEndDate] = useState("");
    const [recurrence, setRecurrence] = useState<ContributionRecurrence>(EMPTY_RECURRENCE);
    /** Contribution en cours de modification (`null` : le formulaire propose une nouvelle activité). */
    const [editingActivityId, setEditingActivityId] = useState<number | null>(null);
    // Statut de la contribution en cours de modification : une activité refusée repart en validation.
    const [editingActivityStatus, setEditingActivityStatus] = useState<string | null>(null);
    const [isDeletingActivity, setIsDeletingActivity] = useState(false);
    const [searchParams, setSearchParams] = useSearchParams();
    const [submitStatus, setSubmitStatus] = useState<"idle" | "success" | "error">("idle");
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [isContributionModalOpen, setIsContributionModalOpen] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);
    const [isLoadingActivities, setIsLoadingActivities] = useState(true);
    const [searchError, setSearchError] = useState<string | null>(null);
    /**
     * Position de recherche : ville choisie (suggestions, préférence enregistrée) ou position obtenue
     * après « Autour de moi ». `null` = aucun lieu choisi : la carte montre une vue d'ensemble, jamais
     * présentée comme la position de l'utilisateur (spec P0-A).
     */
    const [userPosition, setUserPosition] = useState<UserPosition | null>(null);
    /** `userPosition` vient de « Autour de moi » (et non d'une ville) : le bouton l'indique. */
    const [isAroundMe, setIsAroundMe] = useState(false);
    /** Dernière demande de position déclenchée par l'utilisateur (« Autour de moi »). */
    const [geolocationStatus, setGeolocationStatus] = useState<GeolocationStatus>("idle");
    /** Invitation à choisir un lieu refermée par l'utilisateur (pour la session). */
    const [isLocationPromptDismissed, setIsLocationPromptDismissed] = useState(false);
    const searchInputRef = useRef<HTMLInputElement>(null);
    /** Rayon de la recherche initiale `/nearby` : paramètre utilisateur s'il est défini (voir ci-dessous). */
    const [searchRadiusKm, setSearchRadiusKm] = useState(DEFAULT_SEARCH_RADIUS_KM);
    /**
     * Rayon actif : celui des paramètres utilisateur au chargement, puis celui
     * choisi depuis la barre de filtres (« Rayon »), pour la session — les
     * préférences enregistrées ne changent pas. `null` = pas de rayon : la zone
     * visible de la carte fait la recherche.
     */
    const [activeRadiusKm, setActiveRadiusKm] = useState<number | null>(null);
    /**
     * Paramètres utilisateur (catégories, ville, rayon par défaut) appliqués avant
     * la première recherche, pour ne pas charger puis recharger la carte. Visiteur
     * non connecté : rien à attendre.
     */
    const [arePreferencesApplied, setArePreferencesApplied] = useState(() => getPayload() === null);
    /** Première connexion : préférences jamais enregistrées, l'accueil les fait choisir (OnboardingModal). */
    const [needsOnboarding, setNeedsOnboarding] = useState(false);

    /**
     * Applique des préférences à la carte : catégories cochées, rayon de
     * recherche et de cadrage, ville comme point de départ. Retourne `true` si
     * elles contiennent une ville aux coordonnées valides.
     */
    function applyPreferences(preferences: UserPreferences): boolean {
        setSelectedCategoryCodes(preferences.defaultCategoryCodes);
        if (preferences.defaultRadiusKm !== null) {
            setSearchRadiusKm(preferences.defaultRadiusKm);
            setActiveRadiusKm(preferences.defaultRadiusKm);
        }
        if (isValidPosition(preferences.defaultLatitude, preferences.defaultLongitude)) {
            setUserPosition({ latitude: preferences.defaultLatitude!, longitude: preferences.defaultLongitude! });
            setIsAroundMe(false);
            return true;
        }
        return false;
    }
    const [mapBounds, setMapBounds] = useState<MapBounds | null>(null);
    const [foodTrucks, setFoodTrucks] = useState<FoodTruck[]>([]);
    const [mapLayer, setMapLayer] = useState<MapLayer>(() => readMapLayer());
    /** Lieux de la couche « Lieux » pour la zone affichée, chargés par la carte (PlacesLayer). */
    const [places, setPlaces] = useState<Place[]>([]);
    const [viewMode, setViewMode] = useState<"map" | "list">("map");
    /** Saisie de la barre de recherche ; seul un envoi explicite lance une recherche (`handleSearchSubmit`). */
    const [searchQuery, setSearchQuery] = useState("");
    /** Recherche appliquée (ville choisie ou texte envoyé) — la saisie seule ne change rien. */
    const [appliedSearch, setAppliedSearch] = useState<AppliedSearch | null>(null);
    /**
     * Recherche texte globale (#92) : texte envoyé et page. `filtersKey` mémorise les filtres et la
     * position de la page demandée : s'ils changent, la recherche repart de la première page.
     */
    const [textSearch, setTextSearch] = useState<{ query: string; offset: number; filtersKey: string } | null>(null);
    const isTextSearch = textSearch !== null;
    /** Texte envoyé trop court pour la recherche (contrat #91 : 2 caractères, une lettre ou un chiffre). */
    const [searchHint, setSearchHint] = useState<string | null>(null);
    /** Relance la recherche texte à l'identique (« Réessayer », activité annulée ou supprimée). */
    const [searchRetryKey, setSearchRetryKey] = useState(0);
    /** Favoris modifiés depuis un résultat de recherche (la recherche ne renvoie pas cet état). */
    const [likeOverrides, setLikeOverrides] = useState<Record<number, boolean>>({});
    const searchListRef = useRef<HTMLDivElement>(null);
    /** LL-EF-017 : tiroir de menu (mobile) — toujours visible en colonne fixe au-delà du point de rupture desktop (styles.css). */
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    /**
     * LL-EF-017 : aperçu léger (bottom sheet / carte flottante), ouvert par un
     * clic sur un marqueur ou une ligne de liste — distinct de `detailActivity`
     * (fiche complète), ouverte à la demande depuis cet aperçu.
     */
    const [previewActivity, setPreviewActivity] = useState<Activity | null>(null);
    const [detailActivity, setDetailActivity] = useState<Activity | null>(null);
    const [activityDetail, setActivityDetail] = useState<ActivityDetail | null>(null);
    const [isLoadingActivityDetail, setIsLoadingActivityDetail] = useState(false);
    const [activityDetailError, setActivityDetailError] = useState<string | null>(null);
    const [isCancellingActivity, setIsCancellingActivity] = useState(false);
    const [cancelActivityError, setCancelActivityError] = useState<string | null>(null);
    const [isTogglingLike, setIsTogglingLike] = useState(false);

    /**
     * Zone visible utilisée pour la recherche — `null` quand un rayon favori est
     * défini : seules les activités dans ce rayon autour de la ville favorite (ou
     * de la position, après « Autour de moi ») sont alors affichées, quels que
     * soient les déplacements et le zoom de la carte. Sans rayon favori, la zone
     * visible reste la source de vérité dès le premier geste (LL-4012).
     */
    const searchBounds = activeRadiusKm !== null && userPosition !== null ? null : mapBounds;

    const previousSearchDepsRef = useRef<{
        selectedCategoriesKey: string;
        selectedDate: string;
        selectedDateTo: string;
        refreshKey: number;
        userPosition: UserPosition | null;
        searchRadiusKm: number;
    } | null>(null);

    /**
     * Paramètres utilisateur (`GET /api/v1/users/me/preferences`), chargés une fois.
     * Ville par défaut : point de départ de la carte (« Autour de moi » reste
     * disponible). Sans ville, la position n'est reprise que si le navigateur l'a
     * déjà autorisée (aucune demande d'autorisation sans action). `fetch` direct avec
     * le JWT plutôt que `apiFetch` : un token expiré ne doit pas rediriger vers
     * /login depuis la carte publique — on garde simplement le comportement standard.
     */
    useEffect(() => {
        if (arePreferencesApplied) {
            resumeGrantedGeolocation();
            return;
        }
        const token = getToken();
        const abortController = new AbortController();
        let hasDefaultCity = false;

        (async () => {
            try {
                const response = await fetch("/api/v1/users/me/preferences", {
                    headers: { Authorization: `Bearer ${token}` },
                    signal: abortController.signal,
                });
                if (response.ok) {
                    const preferences: UserPreferences = await response.json();
                    hasDefaultCity = applyPreferences(preferences);
                    if (preferences.configured === false && !isOnboardingPostponed(getPayload()?.userId)) {
                        setNeedsOnboarding(true);
                    }
                }
            } catch {
                // Paramètres indisponibles : comportement standard de la carte.
            } finally {
                if (!abortController.signal.aborted) {
                    setArePreferencesApplied(true);
                    if (!hasDefaultCity) {
                        resumeGrantedGeolocation();
                    }
                }
            }
        })();

        return () => abortController.abort();
    }, []);

    // Référentiel des catégories, chargé une fois (lecture publique : `fetch` direct, voir apiClient.ts).
    useEffect(() => {
        const abortController = new AbortController();

        async function loadCategories() {
            try {
                const response = await fetch("/api/v1/categories?locale=fr-FR", { signal: abortController.signal });
                if (response.ok) {
                    const data: Category[] = await response.json();
                    setCategories(
                        data
                            .filter((category) => category.active)
                            .sort((a, b) => a.label.localeCompare(b.label, "fr")),
                    );
                }
            } catch {
                // Référentiel indisponible : le filtre catégorie reste vide, la recherche fonctionne sans lui.
            }
        }

        void loadCategories();

        return () => abortController.abort();
    }, []);

    useEffect(() => {
        // Pendant une recherche texte, la carte et la liste montrent ses résultats : la zone n'est pas
        // rechargée à chaque recadrage. Elle l'est à la sortie de la recherche.
        if (!arePreferencesApplied || isTextSearch) {
            return;
        }
        const abortController = new AbortController();

        // LL-EF-003 : un déplacement/zoom pur de la carte ne doit pas provoquer
        // de coupure visuelle — seul un changement "actif" (filtre, position,
        // nouvelle activité proposée) déclenche l'état de chargement/la purge.
        const previousDeps = previousSearchDepsRef.current;
        const isMapOnlyReload = previousDeps !== null
            && previousDeps.selectedCategoriesKey === selectedCategoriesKey
            && previousDeps.selectedDate === selectedDate
            && previousDeps.selectedDateTo === selectedDateTo
            && previousDeps.refreshKey === refreshKey
            && previousDeps.userPosition === userPosition
            && previousDeps.searchRadiusKm === searchRadiusKm;
        previousSearchDepsRef.current = {
            selectedCategoriesKey, selectedDate, selectedDateTo, refreshKey, userPosition, searchRadiusKm,
        };

        if (!isMapOnlyReload) {
            setIsLoadingActivities(true);
            setSearchError(null);
            setActivities([]);
        }

        // Ni zone affichée ni lieu choisi : aucune requête de proximité autour d'une position inventée.
        // La carte transmet sa zone dès son affichage, la recherche part alors de celle-ci.
        if (!searchBounds && !userPosition) {
            return () => abortController.abort();
        }

        async function loadActivities() {
            const params = new URLSearchParams();
            let endpoint: string;

            if (searchBounds) {
                // LL-4012 : la zone visible devient la source de vérité (contrat LL-4006).
                endpoint = "/api/v1/activities/within-bounds";
                params.set("swLatitude", String(searchBounds.swLatitude));
                params.set("swLongitude", String(searchBounds.swLongitude));
                params.set("neLatitude", String(searchBounds.neLatitude));
                params.set("neLongitude", String(searchBounds.neLongitude));
            } else {
                // Avant que la carte ne transmette sa zone : rayon fixe autour du lieu choisi (LL-4008/LL-4011).
                // Avec un rayon favori : toujours ce rayon, la carte ne fait que l'afficher.
                endpoint = "/api/v1/activities/nearby";
                params.set("latitude", String(userPosition!.latitude));
                params.set("longitude", String(userPosition!.longitude));
                params.set("radius", String(searchRadiusKm));
            }

            // Plusieurs codes du référentiel, séparés par des virgules : une activité est retenue
            // dès qu'elle porte l'une des catégories cochées.
            if (selectedCategoriesKey) {
                params.set("category", selectedCategoriesKey);
            }
            if (selectedDate !== NO_DATE_FILTER) {
                params.set("date", selectedDate);
                if (selectedDateTo !== NO_DATE_FILTER) {
                    params.set("dateTo", selectedDateTo);
                }
            }

            try {
                const response = await fetch(`${endpoint}?${params.toString()}`, {
                    signal: abortController.signal,
                });

                // Spec P0-D : une erreur n'est jamais présentée comme une zone sans activité ; lors d'un
                // simple déplacement de carte, les activités déjà affichées restent visibles.
                if (response.ok) {
                    const data: Activity[] = await response.json();
                    setActivities(data);
                    setSearchError(null);
                } else {
                    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                    setSearchError(
                        response.status < 500 && body?.message
                            ? body.message
                            : "Le chargement des activités a échoué.",
                    );
                    if (!isMapOnlyReload) {
                        setActivities([]);
                    }
                }
            } catch {
                if (!abortController.signal.aborted) {
                    setSearchError("Connexion impossible : vérifie ton réseau.");
                    if (!isMapOnlyReload) {
                        setActivities([]);
                    }
                }
            } finally {
                if (!abortController.signal.aborted) {
                    setIsLoadingActivities(false);
                }
            }
        }

        void loadActivities();

        return () => abortController.abort();
    }, [arePreferencesApplied, isTextSearch, selectedCategoriesKey, selectedDate, selectedDateTo, refreshKey, userPosition, searchRadiusKm, searchBounds]);

    /** « Réessayer » après une erreur de chargement : mêmes filtres, même zone, sans recharger la page. */
    function retryActivities() {
        setRefreshKey((current) => current + 1);
    }

    function chooseMapLayer(layer: MapLayer) {
        setMapLayer(layer);
        writeMapLayer(layer);
    }

    // Récupération des food trucks (LL-6009), isolée de celle des activités. Depuis ADR-0013 : présences du jour.

    useEffect(() => {
        const abortController = new AbortController();

        async function loadFoodTrucks() {
            try {
                const response = await fetch("/api/v1/foodtrucks", { signal: abortController.signal });
                if (response.ok) {
                    const data: FoodTruck[] = await response.json();
                    setFoodTrucks(data);
                }
            } catch {
                // Requête annulée ou serveur injoignable : la carte reste utilisable sans food trucks.
            }
        }

        void loadFoodTrucks();

        return () => abortController.abort();
    }, []);

    /**
     * Activités de la liste hors recherche texte : seulement celles visibles sur la carte. Avec un rayon,
     * la recherche charge tout le rayon : un zoom sur une partie du rayon réduit donc la liste à la zone
     * affichée. Recalculée à chaque déplacement ou zoom (`mapBounds`) et à chaque nouvelle recherche
     * (rayon, filtres). Sans zone connue (carte pas encore affichée), la liste montre tout.
     */
    const listedActivities = useMemo(
        () => (mapBounds ? activities.filter((activity) => isInBounds(activity, mapBounds)) : activities),
        [activities, mapBounds],
    );

    /**
     * Recherche texte globale (#92), `GET /api/v1/activities/search` : filtres catégorie et date actifs,
     * position de référence seulement si elle existe déjà (géolocalisation, ville par défaut ou choisie).
     * Le texte n'est jamais géocodé. Ni rayon ni zone visible : les résultats couvrent tout le territoire.
     */
    const searchFiltersKey = [
        selectedCategoriesKey,
        selectedDate,
        selectedDateTo,
        userPosition ? `${userPosition.latitude},${userPosition.longitude}` : "",
    ].join("|");
    const searchOffset = textSearch && textSearch.filtersKey === searchFiltersKey ? textSearch.offset : 0;
    const searchState = useActivitySearch(
        textSearch
            ? {
                  query: textSearch.query,
                  categoriesKey: selectedCategoriesKey,
                  date: selectedDate,
                  dateTo: selectedDateTo,
                  position: userPosition,
                  offset: searchOffset,
              }
            : null,
        currentUser ? getToken() : null,
        searchRetryKey,
    );
    /** Résultats de la page : même ensemble, dans l'ordre de pertinence, pour la liste et la carte. */
    const searchActivities = useMemo(() => {
        if (searchState.status !== "success") {
            return [];
        }
        const knownById = new Map(activities.map((activity) => [activity.id, activity]));
        return searchState.page.items.map((item) =>
            toActivity(
                item,
                likeOverrides[item.id] ?? (searchState.likedIds.has(item.id) ? true : undefined),
                knownById.get(item.id),
            ));
    }, [searchState, activities, likeOverrides]);

    let textStatus: TextSearchStatus | null = null;
    if (searchHint !== null) {
        textStatus = { status: "invalid", message: searchHint };
    } else if (isTextSearch && searchState.status === "success") {
        textStatus = {
            status: "success",
            total: searchState.page.total,
            offset: searchState.page.offset,
            count: searchActivities.length,
            withoutLocation: searchActivities.filter((activity) => !hasCoordinates(activity)).length,
        };
    } else if (isTextSearch && searchState.status === "error") {
        textStatus = { status: "error", message: searchState.message };
    } else if (isTextSearch) {
        textStatus = { status: "loading" };
    }

    /** Identifiant de la page affichée : la carte se cadre sur ses résultats à chaque nouvelle page. */
    const searchFitKey = searchState.status === "success" && textSearch
        ? `${textSearch.query}|${searchFiltersKey}|${searchOffset}|${searchRetryKey}`
        : null;

    const mapActivities = isTextSearch ? searchActivities : activities;
    const panelActivities = isTextSearch ? searchActivities : listedActivities;
    // Une recherche texte ne renvoie que des activités : elles seules sont affichées tant qu'elle est active.
    const activeLayer: MapLayer = isTextSearch ? "activities" : mapLayer;
    const visibleFoodTrucks = mapBounds
        ? foodTrucks.filter((foodTruck) => foodTruck.latitude >= mapBounds.swLatitude
            && foodTruck.latitude <= mapBounds.neLatitude && foodTruck.longitude >= mapBounds.swLongitude
            && foodTruck.longitude <= mapBounds.neLongitude)
        : foodTrucks;

    let emptyMessage = "Aucune activité trouvée dans cette zone.";
    if (isTextSearch) {
        emptyMessage = textStatus?.status === "success" && textStatus.total > 0
            ? "Aucun résultat sur cette page."
            : `Aucun résultat pour « ${textSearch.query} ».`;
    }

    /** Aucune activité hors recherche texte : actions utiles selon les critères actifs (spec P0-D). */
    const emptyResultsHelp = (
        <EmptyResultsHelp
            hasCategoryFilter={selectedCategoryCodes.length > 0}
            hasDateFilter={selectedDate !== NO_DATE_FILTER}
            onChooseCity={focusCitySearch}
            onClearCategories={() => setSelectedCategoryCodes([])}
            onClearDate={() => {
                setSelectedDate(NO_DATE_FILTER);
                setSelectedDateTo(NO_DATE_FILTER);
            }}
            onRadiusChange={handleRadiusChange}
            radiusKm={activeRadiusKm}
        />
    );

    // Nouvelle page de résultats : la liste repart du haut.
    useEffect(() => {
        if (searchFitKey !== null && searchListRef.current) {
            searchListRef.current.scrollTop = 0;
        }
    }, [searchFitKey]);

    function handleSearchChange(value: string) {
        setSearchQuery(value);
        if (!value.trim()) {
            clearSearch();
        }
    }

    /** Commune choisie dans les suggestions : la carte s'y déplace aussitôt (position déjà connue). */
    function handleSelectCity(city: CitySuggestion, query: string) {
        setSearchQuery(city.name);
        setPreviewActivity(null);
        setTextSearch(null);
        setSearchHint(null);
        setUserPosition({ latitude: city.latitude, longitude: city.longitude });
        setIsAroundMe(false);
        setGeolocationStatus("idle");
        setAppliedSearch({ kind: "city", city: formatCityLabel(city, query) });
    }

    function clearSearch() {
        setSearchQuery("");
        setAppliedSearch(null);
        setTextSearch(null);
        setSearchHint(null);
        setPreviewActivity(null);
    }

    /**
     * « Rechercher » ou Entrée sans suggestion choisie : recherche globale du texte dans les activités
     * (#92). Le texte n'est pas cherché comme une ville : choisir une commune proposée reste le moyen
     * de déplacer la carte. La recherche reste appliquée lors des bascules Carte/Liste et des pages.
     */
    function handleSearchSubmit() {
        const query = searchQuery.trim();
        if (!query) {
            clearSearch();
            return;
        }
        setPreviewActivity(null);
        setAppliedSearch({ kind: "text", query });
        if (query.length < 2 || !/[\p{L}\p{N}]/u.test(query)) {
            // Refusé par le serveur (400) : expliqué tout de suite, la carte garde ses activités.
            setTextSearch(null);
            setSearchHint("Saisis au moins 2 caractères, dont une lettre ou un chiffre.");
            return;
        }
        setSearchHint(null);
        setTextSearch({ query, offset: 0, filtersKey: searchFiltersKey });
        // Même texte renvoyé : la recherche est relancée plutôt qu'ignorée.
        setSearchRetryKey((current) => current + 1);
    }

    function goToSearchPage(offset: number) {
        setPreviewActivity(null);
        setTextSearch((current) =>
            current ? { ...current, offset: Math.max(0, offset), filtersKey: searchFiltersKey } : current);
    }

    /**
     * « Autour de moi » : seule demande d'autorisation de localisation, toujours à la suite d'un geste
     * de l'utilisateur. Un refus, une position indisponible ou un délai dépassé sont expliqués
     * (`LocationPrompt`), avec le choix d'une ville comme autre méthode ; la carte reste utilisable.
     * ⚠️ La position n'est ni envoyée au backend hors recherche, ni stockée ailleurs qu'en état React local.
     */
    function requestGeolocation() {
        if (!("geolocation" in navigator)) {
            setGeolocationStatus("unsupported");
            return;
        }
        setGeolocationStatus("locating");
        navigator.geolocation.getCurrentPosition(
            (position) => {
                setGeolocationStatus("idle");
                setIsAroundMe(true);
                setUserPosition({
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                });
            },
            (error) => {
                setGeolocationStatus(error.code === error.PERMISSION_DENIED ? "denied" : "unavailable");
            },
            { timeout: GEOLOCATION_TIMEOUT_MS, maximumAge: 60000 },
        );
    }

    /**
     * Au chargement, sans ville enregistrée : la position n'est reprise que si le navigateur l'a déjà
     * autorisée pour LocalLife (aucune fenêtre d'autorisation). Un échec reste silencieux : l'invitation
     * à choisir un lieu est déjà affichée.
     */
    function resumeGrantedGeolocation() {
        if (!("geolocation" in navigator) || !navigator.permissions?.query) {
            return;
        }
        navigator.permissions
            .query({ name: "geolocation" })
            .then((permission) => {
                if (permission.state !== "granted") {
                    return;
                }
                navigator.geolocation.getCurrentPosition(
                    (position) => {
                        setUserPosition((current) => current ?? {
                            latitude: position.coords.latitude,
                            longitude: position.coords.longitude,
                        });
                    },
                    () => undefined,
                    { timeout: GEOLOCATION_TIMEOUT_MS, maximumAge: 60000 },
                );
            })
            .catch(() => undefined);
    }

    /** « Choisir ma ville » : la barre de recherche propose les communes dès la saisie. */
    function focusCitySearch() {
        setViewMode("map");
        searchInputRef.current?.focus();
    }

    /**
     * Rayon choisi depuis la barre de filtres. Avec un rayon : la recherche et le
     * cadrage se font dans ce rayon autour de la position de recherche (ville
     * favorite, « Autour de moi »), ou du centre actuel de la carte s'il n'y en a
     * pas (jamais d'une position inventée). Sans rayon : retour à la recherche par zone visible.
     */
    function handleRadiusChange(radiusKm: number | null) {
        setActiveRadiusKm(radiusKm);
        setSearchRadiusKm(radiusKm ?? DEFAULT_SEARCH_RADIUS_KM);
        if (radiusKm !== null && userPosition === null && mapBounds) {
            setUserPosition({
                latitude: (mapBounds.swLatitude + mapBounds.neLatitude) / 2,
                longitude: (mapBounds.swLongitude + mapBounds.neLongitude) / 2,
            });
        }
    }

    function handleLogout() {
        clearToken();
        setCurrentUser(null);
        setNeedsOnboarding(false);
    }

    // LL-EF-001 : un visiteur non connecté est redirigé vers /login plutôt que de voir le formulaire.
    async function handleOpenContributionForm() {
        if (!currentUser) {
            navigate("/login");
            return;
        }
        // 21/10/2026 : proposer une activité est réservé aux offres Premium et Pro ; le serveur reste seul juge.
        try {
            const response = await apiFetch("/api/v1/users/me");
            if (response.ok) {
                const me = (await response.json()) as { canContribute?: boolean };
                if (me.canContribute === false) {
                    navigate("/profile?premium=1");
                    return;
                }
            }
        } catch {
            // Vérification indicative : en cas d'échec, le formulaire s'ouvre et le serveur tranchera à l'envoi.
        }
        if (editingActivityId !== null) {
            resetContributionForm();
        }
        setIsContributionModalOpen(true);
    }

    function handleCloseContributionForm() {
        setIsContributionModalOpen(false);
        setSubmitStatus("idle");
        setSubmitError(null);
        // Un brouillon de proposition est conservé, pas les champs d'une contribution modifiée.
        if (editingActivityId !== null) {
            resetContributionForm();
        }
    }

    function resetContributionForm() {
        setEditingActivityId(null);
        setEditingActivityStatus(null);
        setTitle("");
        setDescription("");
        setContributionCategoryCodes([]);
        setAddress("");
        setStartDate("");
        setEndDate("");
        setRecurrence(EMPTY_RECURRENCE);
        setUrl("");
        setPhone("");
        setEmail("");
    }

    /** Ouvre le formulaire pré-rempli avec une contribution existante (créateur ou administrateur). */
    async function openEditForm(activityId: number) {
        setSubmitStatus("idle");
        setSubmitError(null);
        try {
            const response = await apiFetch(`/api/v1/activities/${activityId}/contribution`);
            if (!response.ok) {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                window.alert(body?.message ?? "Impossible de charger cette activité pour la modifier.");
                return;
            }
            const contribution = (await response.json()) as Contribution;
            setEditingActivityId(contribution.id);
            setEditingActivityStatus(contribution.status);
            setTitle(contribution.title);
            setDescription(contribution.description ?? "");
            setContributionCategoryCodes(contribution.categoryCodes);
            setAddress(contribution.address ?? "");
            setUrl(contribution.url ?? "");
            setPhone(contribution.phone ?? "");
            setEmail(contribution.email ?? "");
            if (contribution.recurrence) {
                setRecurrence({
                    enabled: true,
                    days: contribution.recurrence.daysOfWeek,
                    startTime: contribution.recurrence.startTime.slice(0, 5),
                    endTime: contribution.recurrence.endTime?.slice(0, 5) ?? "",
                });
                setStartDate(contribution.recurrence.from);
                setEndDate(contribution.recurrence.until ?? "");
            } else {
                setRecurrence(EMPTY_RECURRENCE);
                setStartDate(contribution.startDate?.slice(0, 10) ?? "");
                setEndDate(contribution.endDate?.slice(0, 10) ?? "");
            }
            setDetailActivity(null);
            setIsContributionModalOpen(true);
        } catch {
            window.alert("Impossible de contacter le serveur, réessaie plus tard.");
        }
    }

    // « Modifier » depuis « Mes contributions » : /?edit=<id> ouvre le formulaire pré-rempli.
    const editParam = searchParams.get("edit");
    useEffect(() => {
        if (!editParam || !currentUser) {
            return;
        }
        setSearchParams({}, { replace: true });
        const activityId = Number(editParam);
        if (Number.isInteger(activityId)) {
            void openEditForm(activityId);
        }
    }, [editParam, currentUser]);

    // Fermeture au clavier (Échap) : referme l'élément le plus "au-dessus" ouvert
    // (fiche complète, puis aperçu, puis formulaire, puis menu mobile).
    useEffect(() => {
        function handleKeyDown(event: KeyboardEvent) {
            if (event.key !== "Escape") {
                return;
            }
            if (detailActivity) {
                setDetailActivity(null);
            } else if (previewActivity) {
                setPreviewActivity(null);
            } else if (isContributionModalOpen) {
                handleCloseContributionForm();
            } else if (isMenuOpen) {
                setIsMenuOpen(false);
            }
        }

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [detailActivity, previewActivity, isContributionModalOpen, isMenuOpen]);

    /**
     * LL-11015 : charge la fiche détaillée (LL-11013) à l'ouverture de la
     * fiche complète — `GET /api/v1/activities/{id}`, endpoint public,
     * `fetch` directement (même convention que le chargement de la carte).
     */
    useEffect(() => {
        if (!detailActivity) {
            setActivityDetail(null);
            setActivityDetailError(null);
            return;
        }

        // Efface l'erreur d'annulation d'une activité précédemment consultée.
        setCancelActivityError(null);

        const abortController = new AbortController();
        setActivityDetail(null);
        setActivityDetailError(null);
        setIsLoadingActivityDetail(true);

        (async () => {
            try {
                const response = await fetch(`/api/v1/activities/${detailActivity.id}`, {
                    signal: abortController.signal,
                });
                if (!response.ok) {
                    throw new Error("Le détail de cette activité n'a pas pu être chargé.");
                }
                const body: unknown = await response.json();
                // LL-11014 : la réponse peut aussi être un résumé (accès Premium non accordé).
                if (body && typeof body === "object" && "schedules" in body) {
                    setActivityDetail(body as ActivityDetail);
                }
            } catch (error) {
                if (!(error instanceof DOMException && error.name === "AbortError")) {
                    setActivityDetailError("Le détail de cette activité n'a pas pu être chargé.");
                }
            } finally {
                if (!abortController.signal.aborted) {
                    setIsLoadingActivityDetail(false);
                }
            }
        })();

        return () => abortController.abort();
    }, [detailActivity]);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSubmitStatus("idle");
        setSubmitError(null);

        // Des cases à cocher ne peuvent pas porter `required` à elles toutes : vérifié ici.
        if (contributionCategoryCodes.length === 0) {
            setSubmitError("Choisis au moins une catégorie.");
            setSubmitStatus("error");
            return;
        }
        if (recurrence.enabled && recurrence.days.length === 0) {
            setSubmitError("Choisis au moins un jour de passage.");
            setSubmitStatus("error");
            return;
        }

        try {
            const isEditing = editingActivityId !== null;
            const response = await apiFetch(isEditing ? `/api/v1/activities/${editingActivityId}` : "/api/v1/activities", {
                method: isEditing ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    title,
                    description,
                    categoryCodes: contributionCategoryCodes,
                    // Ancien champ à valeur unique, encore lu par un backend antérieur à `categoryCodes`.
                    categoryCode: contributionCategoryCodes[0],
                    address,
                    // Activité récurrente : les dates du formulaire bornent la récurrence.
                    startDate: recurrence.enabled ? undefined : startDate || undefined,
                    endDate: recurrence.enabled ? undefined : endDate || undefined,
                    recurrence: recurrence.enabled
                        ? {
                              daysOfWeek: recurrence.days,
                              startTime: recurrence.startTime,
                              endTime: recurrence.endTime || undefined,
                              from: startDate || undefined,
                              until: endDate || undefined,
                          }
                        : undefined,
                    url: url || undefined,
                    phone: phone || undefined,
                    email: email || undefined,
                }),
            });

            if (!response.ok) {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setSubmitError(body?.message ?? "Impossible de proposer cette activité, réessaie.");
                setSubmitStatus("error");
                return;
            }

            const saved = (await response.json().catch(() => null)) as { status?: string } | null;
            setRefreshKey((current) => current + 1);
            setSubmitStatus("success");
            resetContributionForm();
            setIsContributionModalOpen(false);
            if (!isEditing) {
                window.alert(
                    "Merci pour ta proposition ! Elle est soumise à validation par un modérateur et sera publiée d'ici 2 à 3 jours. Tu peux suivre son statut dans « Mes contributions ».",
                );
            }
            // Une activité publiée reste sur la carte ; seule une activité refusée corrigée repart en validation.
            if (isEditing && editingActivityStatus === "REJECTED" && saved?.status === "PENDING") {
                window.alert(
                    "Modification enregistrée. L'activité est de nouveau soumise à validation : elle sera visible sur la carte une fois validée par un modérateur.",
                );
            }
        } catch {
            setSubmitError("Impossible de contacter le serveur, réessaie plus tard.");
            setSubmitStatus("error");
        }
    }

    async function handleCancelActivity(activity: Activity) {
        const confirmed = window.confirm(
            `Annuler « ${activity.title} » ? Cette activité ne sera plus visible sur la carte.`,
        );
        if (!confirmed) {
            return;
        }

        setIsCancellingActivity(true);
        setCancelActivityError(null);

        try {
            const response = await apiFetch(`/api/v1/activities/${activity.id}/cancel`, { method: "PATCH" });

            if (!response.ok) {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setCancelActivityError(body?.message ?? "Impossible d'annuler cette activité, réessaie.");
                return;
            }

            setRefreshKey((current) => current + 1);
            setSearchRetryKey((current) => current + 1);
            setDetailActivity(null);
        } catch {
            setCancelActivityError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsCancellingActivity(false);
        }
    }

    async function handleDeleteActivity(activity: Activity) {
        const confirmed = window.confirm(`Supprimer définitivement « ${activity.title} » ? Cette action est irréversible.`);
        if (!confirmed) {
            return;
        }

        setIsDeletingActivity(true);
        setCancelActivityError(null);

        try {
            const response = await apiFetch(`/api/v1/activities/${activity.id}`, { method: "DELETE" });

            if (!response.ok) {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setCancelActivityError(body?.message ?? "Impossible de supprimer cette activité, réessaie.");
                return;
            }

            setRefreshKey((current) => current + 1);
            setSearchRetryKey((current) => current + 1);
            setDetailActivity(null);
        } catch {
            setCancelActivityError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsDeletingActivity(false);
        }
    }

    async function handleToggleLike(activity: Activity) {
        const newLikedState = !activity.likedByCurrentUser;
        setIsTogglingLike(true);
        applyLikedState(activity.id, newLikedState);

        try {
            const response = await apiFetch(`/api/v1/activities/${activity.id}/like`, {
                method: newLikedState ? "POST" : "DELETE",
            });
            if (!response.ok) {
                applyLikedState(activity.id, !newLikedState);
            }
        } catch {
            applyLikedState(activity.id, !newLikedState);
        } finally {
            setIsTogglingLike(false);
        }
    }

    function applyLikedState(activityId: number, liked: boolean) {
        setActivities((current) =>
            current.map((item) => (item.id === activityId ? { ...item, likedByCurrentUser: liked } : item)));
        setDetailActivity((current) =>
            current && current.id === activityId ? { ...current, likedByCurrentUser: liked } : current);
        setPreviewActivity((current) =>
            current && current.id === activityId ? { ...current, likedByCurrentUser: liked } : current);
        setLikeOverrides((current) => ({ ...current, [activityId]: liked }));
    }

    /** LL-EF-017, section 8 : sélection depuis un marqueur ou une ligne de liste → aperçu. */
    function handleSelectActivity(activity: Activity) {
        setPreviewActivity(activity);
        // Mobile, vue Liste : l'aperçu et la carte centrée sur l'activité sont dans la vue Carte (#92).
        setViewMode("map");
    }

    /** LL-EF-017, section 9 : "Voir l'activité" → fiche complète, l'aperçu se referme. */
    function handleOpenDetail(activity: Activity) {
        setPreviewActivity(null);
        setDetailActivity(activity);
    }

    function handlePeriodChange(period: string) {
        if (period === "") {
            setSelectedDate(NO_DATE_FILTER);
            setSelectedDateTo(NO_DATE_FILTER);
            return;
        }

        const today = new Date();
        today.setHours(0, 0, 0, 0);

        switch (period) {
            case "this-weekend": {
                const daysToSaturday = 6 - today.getDay();
                const saturday = new Date(today);
                saturday.setDate(today.getDate() + daysToSaturday);
                const sunday = new Date(saturday);
                sunday.setDate(saturday.getDate() + 1);
                setSelectedDate(toLocalDateString(saturday));
                setSelectedDateTo(toLocalDateString(sunday));
                break;
            }
            case "this-week": {
                const daysToMonday = today.getDay() === 0 ? -6 : 1 - today.getDay();
                const monday = new Date(today);
                monday.setDate(today.getDate() + daysToMonday);
                const sunday = new Date(monday);
                sunday.setDate(monday.getDate() + 6);
                setSelectedDate(toLocalDateString(monday));
                setSelectedDateTo(toLocalDateString(sunday));
                break;
            }
            case "this-month": {
                const firstOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);
                const lastOfMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0);
                setSelectedDate(toLocalDateString(firstOfMonth));
                setSelectedDateTo(toLocalDateString(lastOfMonth));
                break;
            }
            case "next-month": {
                const firstOfNextMonth = new Date(today.getFullYear(), today.getMonth() + 1, 1);
                const lastOfNextMonth = new Date(today.getFullYear(), today.getMonth() + 2, 0);
                setSelectedDate(toLocalDateString(firstOfNextMonth));
                setSelectedDateTo(toLocalDateString(lastOfNextMonth));
                break;
            }
            default:
                setSelectedDate(toLocalDateString(today));
                setSelectedDateTo(NO_DATE_FILTER);
        }
    }

    return (
        <div className="explorer-shell">
            <ExplorerMenu
                currentUser={currentUser}
                isOpen={isMenuOpen}
                onClose={() => setIsMenuOpen(false)}
                onLogout={handleLogout}
                onOpenContributionForm={handleOpenContributionForm}
            />

            <div className="explorer-main" data-mobile-view={viewMode}>
                <div className="explorer-controls">
                    <div className="explorer-topbar">
                        <button
                            aria-label="Ouvrir le menu"
                            className="explorer-menu-toggle"
                            onClick={() => setIsMenuOpen(true)}
                            type="button"
                        >
                            ☰
                        </button>
                        <ExplorerSearch
                            inputRef={searchInputRef}
                            isActive={appliedSearch !== null}
                            isSearching={textStatus?.status === "loading"}
                            onChange={handleSearchChange}
                            onClear={clearSearch}
                            onSelectCity={handleSelectCity}
                            onSubmit={handleSearchSubmit}
                            value={searchQuery}
                        />
                    </div>
                    <SearchResultBanner
                        hasActiveFilters={selectedCategoriesKey !== "" || selectedDate !== NO_DATE_FILTER}
                        isLoadingActivities={isLoadingActivities}
                        onClear={clearSearch}
                        onNextPage={() => goToSearchPage(searchOffset + SEARCH_PAGE_SIZE)}
                        onPreviousPage={() => goToSearchPage(searchOffset - SEARCH_PAGE_SIZE)}
                        onRetry={() => setSearchRetryKey((current) => current + 1)}
                        resultCount={listedActivities.length}
                        search={appliedSearch}
                        textStatus={textStatus}
                    />

                    <ExplorerFilters
                        categories={categories}
                        onClearDate={() => {
                            setSelectedDate(NO_DATE_FILTER);
                            setSelectedDateTo(NO_DATE_FILTER);
                        }}
                        onDateChange={(date) => {
                            setSelectedDate(date);
                            setSelectedDateTo(NO_DATE_FILTER);
                        }}
                        onPeriodChange={handlePeriodChange}
                        onRadiusChange={handleRadiusChange}
                        radiusKm={activeRadiusKm}
                        onSelectedCategoryCodesChange={setSelectedCategoryCodes}
                        isAroundMe={isAroundMe}
                        isLocating={geolocationStatus === "locating"}
                        onUseMyLocation={requestGeolocation}
                        onViewModeChange={setViewMode}
                        selectedCategoryCodes={selectedCategoryCodes}
                        selectedDate={selectedDate}
                        selectedDateTo={selectedDateTo}
                        viewMode={viewMode}
                    />
                </div>

                <div className="explorer-content">
                    <div className="explorer-map-wrapper">
                        <ExplorerMap
                            activities={mapActivities}
                            fitKey={searchFitKey}
                            foodTrucks={foodTrucks}
                            layer={activeLayer}
                            onBoundsChange={setMapBounds}
                            onPlacesChange={setPlaces}
                            onSelectActivity={handleSelectActivity}
                            selectedActivity={previewActivity}
                            userPosition={userPosition}
                            radiusKm={activeRadiusKm}
                        />
                        <div aria-label="Afficher sur la carte" className="explorer-layer-switch" role="group">
                            {MAP_LAYERS.map((layer) => (
                                <button
                                    aria-pressed={activeLayer === layer.value}
                                    className={activeLayer === layer.value
                                        ? "explorer-places-toggle explorer-places-toggle-active"
                                        : "explorer-places-toggle"}
                                    disabled={isTextSearch && layer.value !== "activities"}
                                    key={layer.value}
                                    onClick={() => chooseMapLayer(layer.value)}
                                    title={isTextSearch && layer.value !== "activities"
                                        ? "Indisponible pendant une recherche"
                                        : layer.title}
                                    type="button"
                                >
                                    {layer.label}
                                </button>
                            ))}
                        </div>
                        {/*
                          Pendant une recherche texte, le bandeau sous la barre donne son état. Chargement,
                          erreur et absence de résultat sont exclusifs (spec P0-D). Sur téléphone, ces messages
                          et l'invitation à choisir un lieu s'empilent en bas de la carte (styles.css).
                        */}
                        <div className="explorer-map-notices">
                            {activeLayer === "activities" && !isTextSearch && isLoadingActivities && (
                                <p className="explorer-status explorer-status-floating" role="status">
                                    <span aria-hidden="true" className="explorer-search-spinner" />
                                    Chargement des activités…
                                </p>
                            )}
                            {activeLayer === "activities" && !isTextSearch && !isLoadingActivities && searchError && (
                                <div
                                    className="explorer-status explorer-status-error explorer-status-floating explorer-status-card"
                                    role="alert"
                                >
                                    <span>{searchError}</span>
                                    <button className="explorer-chip" onClick={retryActivities} type="button">
                                        Réessayer
                                    </button>
                                </div>
                            )}
                            {activeLayer === "activities" && !isTextSearch && !isLoadingActivities && !searchError && activities.length === 0 && (
                                <div className="explorer-status explorer-status-floating explorer-status-card" role="status">
                                    {emptyResultsHelp}
                                </div>
                            )}
                            {/* Après lecture des préférences : une ville enregistrée évite l'invitation. */}
                            {arePreferencesApplied && !isTextSearch && !previewActivity && (
                                <LocationPrompt
                                    hasLocation={userPosition !== null}
                                    isDismissed={isLocationPromptDismissed}
                                    onChooseCity={focusCitySearch}
                                    onDismiss={() => {
                                        setIsLocationPromptDismissed(true);
                                        setGeolocationStatus("idle");
                                    }}
                                    onUseMyLocation={requestGeolocation}
                                    status={geolocationStatus}
                                />
                            )}
                        </div>
                        {previewActivity && (
                            <ActivityPreview
                                activity={previewActivity}
                                categories={categories}
                                onClose={() => setPreviewActivity(null)}
                                onOpenDetail={handleOpenDetail}
                            />
                        )}
                    </div>

                    <div className="explorer-list-panel" ref={searchListRef}>
                        {activeLayer === "foodTrucks" ? (
                            <FoodTruckList foodTrucks={visibleFoodTrucks} />
                        ) : activeLayer === "places" ? (
                            <PlaceList onSelectActivity={handleSelectActivity} places={places} />
                        ) : (
                        <ActivityList
                            activities={panelActivities}
                            categories={categories}
                            emptyActions={isTextSearch ? undefined : emptyResultsHelp}
                            emptyMessage={emptyMessage}
                            error={isTextSearch ? (textStatus?.status === "error" ? textStatus.message : null) : searchError}
                            // Recherche texte : « Réessayer » est dans le bandeau sous la barre.
                            onRetry={isTextSearch ? undefined : retryActivities}
                            isLoading={isTextSearch ? textStatus?.status === "loading" : isLoadingActivities}
                            label={isTextSearch ? "Résultats de la recherche" : undefined}
                            onSelectActivity={handleSelectActivity}
                            ordered={isTextSearch}
                        />
                        )}
                    </div>
                </div>
            </div>

            {needsOnboarding && currentUser && (
                <OnboardingModal
                    categories={categories}
                    onFinish={() => setNeedsOnboarding(false)}
                    onLogout={handleLogout}
                    onPostpone={() => {
                        rememberOnboardingPostponed(currentUser.userId);
                        setNeedsOnboarding(false);
                    }}
                    onPreferencesSaved={(preferences) => {
                        applyPreferences(preferences);
                    }}
                />
            )}

            {isContributionModalOpen && currentUser && (
                <ContributionModal
                    mode={editingActivityId !== null ? "edit" : "create"}
                    requiresRevalidation={currentUser.role !== "ADMIN"
                        && (editingActivityStatus === "REJECTED" || editingActivityStatus === "PUBLISHED")}
                    address={address}
                    categories={categories}
                    description={description}
                    email={email}
                    endDate={endDate}
                    onAddressChange={setAddress}
                    onSelectedCategoryCodesChange={setContributionCategoryCodes}
                    selectedCategoryCodes={contributionCategoryCodes}
                    onClose={handleCloseContributionForm}
                    onDescriptionChange={setDescription}
                    onEmailChange={setEmail}
                    onEndDateChange={setEndDate}
                    onPhoneChange={setPhone}
                    onRecurrenceChange={setRecurrence}
                    onStartDateChange={setStartDate}
                    onSubmit={(event) => void handleSubmit(event)}
                    onTitleChange={setTitle}
                    onUrlChange={setUrl}
                    phone={phone}
                    recurrence={recurrence}
                    startDate={startDate}
                    submitError={submitError}
                    submitStatus={submitStatus}
                    title={title}
                    url={url}
                />
            )}

            {detailActivity && (
                <ActivityDetailModal
                    activity={detailActivity}
                    categories={categories}
                    cancelError={cancelActivityError}
                    currentUser={currentUser}
                    detail={activityDetail}
                    detailError={activityDetailError}
                    isCancelling={isCancellingActivity}
                    isLoadingDetail={isLoadingActivityDetail}
                    isTogglingLike={isTogglingLike}
                    onCancel={(activity) => void handleCancelActivity(activity)}
                    isDeleting={isDeletingActivity}
                    onDelete={(activity) => void handleDeleteActivity(activity)}
                    onEdit={(activity) => void openEditForm(activity.id)}
                    onClose={() => setDetailActivity(null)}
                    onToggleLike={(activity) => void handleToggleLike(activity)}
                />
            )}
        </div>
    );
}

export default App;
