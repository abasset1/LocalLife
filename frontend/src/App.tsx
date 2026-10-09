import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { apiFetch } from "./api/apiClient";
import { clearToken, getPayload, getToken } from "./auth/authStorage";
import ActivityDetailModal from "./components/explorer/ActivityDetailModal";
import ActivityList from "./components/explorer/ActivityList";
import ActivityPreview from "./components/explorer/ActivityPreview";
import { matchesSearchQuery, toLocalDateString } from "./components/explorer/activityUtils";
import { DEFAULT_SEARCH_RADIUS_KM, MARSEILLE_LATITUDE, MARSEILLE_LONGITUDE, NO_DATE_FILTER } from "./components/explorer/constants";
import ContributionModal from "./components/explorer/ContributionModal";
import ExplorerFilters from "./components/explorer/ExplorerFilters";
import ExplorerMap from "./components/explorer/ExplorerMap";
import ExplorerMenu from "./components/explorer/ExplorerMenu";
import ExplorerSearch from "./components/explorer/ExplorerSearch";
import type {
    Activity,
    ActivityDetail,
    ApiErrorBody,
    Category,
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
    const [submitStatus, setSubmitStatus] = useState<"idle" | "success" | "error">("idle");
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [isContributionModalOpen, setIsContributionModalOpen] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);
    const [isLoadingActivities, setIsLoadingActivities] = useState(true);
    const [searchError, setSearchError] = useState<string | null>(null);
    const [userPosition, setUserPosition] = useState<UserPosition | null>(null);
    /** Rayon de la recherche initiale `/nearby` : paramètre utilisateur s'il est défini (voir ci-dessous). */
    const [searchRadiusKm, setSearchRadiusKm] = useState(DEFAULT_SEARCH_RADIUS_KM);
    /** Rayon des paramètres utilisateur, s'il y en a un : la carte est cadrée dessus (ExplorerMap). */
    const [favoriteRadiusKm, setFavoriteRadiusKm] = useState<number | null>(null);
    /**
     * Paramètres utilisateur (catégories, ville, rayon par défaut) appliqués avant
     * la première recherche, pour ne pas charger puis recharger la carte. Visiteur
     * non connecté : rien à attendre.
     */
    const [arePreferencesApplied, setArePreferencesApplied] = useState(() => getPayload() === null);
    const [mapBounds, setMapBounds] = useState<MapBounds | null>(null);
    const [foodTrucks, setFoodTrucks] = useState<FoodTruck[]>([]);
    const [viewMode, setViewMode] = useState<"map" | "list">("map");
    /** LL-EF-017 : recherche libre (titre/ville/adresse/catégorie), filtrage client — voir `matchesSearchQuery`. */
    const [searchQuery, setSearchQuery] = useState("");
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
     * Ville par défaut : remplace la géolocalisation automatique comme point de
     * départ de la carte (« Autour de moi » reste disponible). `fetch` direct avec
     * le JWT plutôt que `apiFetch` : un token expiré ne doit pas rediriger vers
     * /login depuis la carte publique — on garde simplement le comportement standard.
     */
    useEffect(() => {
        if (arePreferencesApplied) {
            requestGeolocation();
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
                    setSelectedCategoryCodes(preferences.defaultCategoryCodes);
                    if (preferences.defaultRadiusKm !== null) {
                        setSearchRadiusKm(preferences.defaultRadiusKm);
                        setFavoriteRadiusKm(preferences.defaultRadiusKm);
                    }
                    if (preferences.defaultLatitude !== null && preferences.defaultLongitude !== null) {
                        hasDefaultCity = true;
                        setUserPosition({
                            latitude: preferences.defaultLatitude,
                            longitude: preferences.defaultLongitude,
                        });
                    }
                }
            } catch {
                // Paramètres indisponibles : comportement standard de la carte.
            } finally {
                if (!abortController.signal.aborted) {
                    setArePreferencesApplied(true);
                    if (!hasDefaultCity) {
                        requestGeolocation();
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
        if (!arePreferencesApplied) {
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

        async function loadActivities() {
            const params = new URLSearchParams();
            let endpoint: string;

            if (mapBounds) {
                // LL-4012 : la zone visible devient la source de vérité (contrat LL-4006).
                endpoint = "/api/v1/activities/within-bounds";
                params.set("swLatitude", String(mapBounds.swLatitude));
                params.set("swLongitude", String(mapBounds.swLongitude));
                params.set("neLatitude", String(mapBounds.neLatitude));
                params.set("neLongitude", String(mapBounds.neLongitude));
            } else {
                // Avant toute interaction avec la carte : rayon fixe (LL-4008/LL-4011),
                // ou celui des paramètres utilisateur.
                endpoint = "/api/v1/activities/nearby";
                const latitude = userPosition?.latitude ?? MARSEILLE_LATITUDE;
                const longitude = userPosition?.longitude ?? MARSEILLE_LONGITUDE;
                params.set("latitude", String(latitude));
                params.set("longitude", String(longitude));
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

                if (response.ok) {
                    const data: Activity[] = await response.json();
                    setActivities(data);
                } else {
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
                if (!abortController.signal.aborted) {
                    setIsLoadingActivities(false);
                }
            }
        }

        void loadActivities();

        return () => abortController.abort();
    }, [arePreferencesApplied, selectedCategoriesKey, selectedDate, selectedDateTo, refreshKey, userPosition, searchRadiusKm, mapBounds]);

    // Récupération des food trucks (LL-6009), isolée de celle des activités — inchangée.
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
     * LL-EF-017 : recherche texte libre appliquée côté client sur les
     * activités déjà chargées (voir la javadoc de `matchesSearchQuery`) —
     * n'affecte ni les paramètres de requête backend ni la liste des
     * catégories disponibles.
     */
    const filteredActivities = useMemo(
        () => activities.filter((activity) => matchesSearchQuery(activity, searchQuery, categories)),
        [activities, searchQuery],
    );

    function requestGeolocation() {
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
                // Permission refusée, position indisponible ou timeout : repli silencieux
                // sur MARSEILLE_LATITUDE/MARSEILLE_LONGITUDE (comportement LL-4008).
            },
        );
    }

    // LL-EF-002 : géolocalisation demandée automatiquement au chargement, sans action utilisateur
    // (après lecture des paramètres utilisateur, sauf ville par défaut — voir leur chargement ci-dessus).
    // ⚠️ Aucune position utilisateur n'est envoyée au backend ni stockée ailleurs qu'en état React local.

    function handleLogout() {
        clearToken();
        setCurrentUser(null);
    }

    // LL-EF-001 : un visiteur non connecté est redirigé vers /login plutôt que de voir le formulaire.
    function handleOpenContributionForm() {
        if (!currentUser) {
            navigate("/login");
            return;
        }
        setIsContributionModalOpen(true);
    }

    function handleCloseContributionForm() {
        setIsContributionModalOpen(false);
        setSubmitStatus("idle");
        setSubmitError(null);
    }

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

        try {
            const response = await apiFetch("/api/v1/activities", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    title,
                    description,
                    categoryCodes: contributionCategoryCodes,
                    // Ancien champ à valeur unique, encore lu par un backend antérieur à `categoryCodes`.
                    categoryCode: contributionCategoryCodes[0],
                    address,
                    startDate: startDate || undefined,
                    endDate: endDate || undefined,
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

            setRefreshKey((current) => current + 1);
            setSubmitStatus("success");
            setTitle("");
            setDescription("");
            setContributionCategoryCodes([]);
            setAddress("");
            setStartDate("");
            setEndDate("");
            setUrl("");
            setPhone("");
            setEmail("");
            setIsContributionModalOpen(false);
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
            setDetailActivity(null);
        } catch {
            setCancelActivityError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsCancellingActivity(false);
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
    }

    /** LL-EF-017, section 8 : sélection depuis un marqueur ou une ligne de liste → aperçu. */
    function handleSelectActivity(activity: Activity) {
        setPreviewActivity(activity);
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
                        <ExplorerSearch onChange={setSearchQuery} value={searchQuery} />
                    </div>

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
                        onSelectedCategoryCodesChange={setSelectedCategoryCodes}
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
                        {isLoadingActivities && <p className="explorer-status explorer-status-floating">Chargement des activités…</p>}
                        {!isLoadingActivities && searchError && (
                            <p className="explorer-status explorer-status-error explorer-status-floating" role="alert">
                                {searchError}
                            </p>
                        )}
                        {!isLoadingActivities && !searchError && filteredActivities.length === 0 && (
                            <p className="explorer-status explorer-status-floating">Aucune activité trouvée dans cette zone.</p>
                        )}
                        <ExplorerMap
                            activities={filteredActivities}
                            foodTrucks={foodTrucks}
                            onBoundsChange={setMapBounds}
                            onSelectActivity={handleSelectActivity}
                            selectedActivity={previewActivity}
                            userPosition={userPosition}
                            favoriteRadiusKm={favoriteRadiusKm}
                        />
                        {previewActivity && (
                            <ActivityPreview
                                activity={previewActivity}
                                categories={categories}
                                onClose={() => setPreviewActivity(null)}
                                onOpenDetail={handleOpenDetail}
                            />
                        )}
                    </div>

                    <div className="explorer-list-panel">
                        <ActivityList
                            activities={filteredActivities}
                            categories={categories}
                            error={searchError}
                            isLoading={isLoadingActivities}
                            onSelectActivity={handleSelectActivity}
                        />
                    </div>
                </div>
            </div>

            {isContributionModalOpen && currentUser && (
                <ContributionModal
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
                    onStartDateChange={setStartDate}
                    onSubmit={(event) => void handleSubmit(event)}
                    onTitleChange={setTitle}
                    onUrlChange={setUrl}
                    phone={phone}
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
                    onClose={() => setDetailActivity(null)}
                    onToggleLike={(activity) => void handleToggleLike(activity)}
                />
            )}
        </div>
    );
}

export default App;
