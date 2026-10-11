package com.locallife.backend.geocoding.application;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Recherche de ville ouverte à tous, depuis la barre de recherche de la carte.
 *
 * <p>Chaque recherche non mise en cache interroge Nominatim, dont la politique d'usage impose au plus
 * une requête par seconde. Comme l'endpoint est public, ce service :
 * <ul>
 *   <li>garde en mémoire les dernières villes trouvées, et celles introuvables ;</li>
 *   <li>espace les appels à Nominatim d'au moins une seconde ;</li>
 *   <li>répond « indisponible » plutôt que de faire patienter longtemps quand trop de recherches
 *       arrivent en même temps.</li>
 * </ul>
 */
@Service
public class CitySearchService {

    private static final int CACHE_SIZE = 500;
    private static final long DEFAULT_MIN_INTERVAL_MS = 1_000;
    private static final long MAX_WAIT_MS = 3_000;

    private final GeocodingService geocodingService;
    private final long minIntervalMs;
    private final ReentrantLock nominatimLock = new ReentrantLock(true);
    private long lastCallAtMs;

    // Résultat vide : ville introuvable (mis en cache aussi, pour ne pas réinterroger Nominatim).
    private final Map<String, Optional<Coordinates>> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<Coordinates>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    @Autowired
    public CitySearchService(GeocodingService geocodingService) {
        this(geocodingService, DEFAULT_MIN_INTERVAL_MS);
    }

    /** Réservé aux tests : intervalle minimal entre deux appels à Nominatim. */
    CitySearchService(GeocodingService geocodingService, long minIntervalMs) {
        this.geocodingService = geocodingService;
        this.minIntervalMs = minIntervalMs;
    }

    /**
     * @throws IllegalArgumentException si le nom est vide
     * @throws CityNotFoundException si aucune ville ne correspond
     * @throws GeocodingUnavailableException si Nominatim échoue ou si trop de recherches sont en cours
     */
    public Coordinates findCity(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de la ville ne peut pas être vide.");
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        Optional<Coordinates> cached = cached(key);
        if (cached != null) {
            return cached.orElseThrow(() -> notFound(name));
        }

        acquireNominatimSlot();
        try {
            // Une recherche identique a pu aboutir pendant l'attente.
            Optional<Coordinates> found = cached(key);
            if (found == null) {
                found = lookup(name);
                synchronized (cache) {
                    cache.put(key, found);
                }
            }
            return found.orElseThrow(() -> notFound(name));
        } finally {
            nominatimLock.unlock();
        }
    }

    private Optional<Coordinates> cached(String key) {
        synchronized (cache) {
            return cache.get(key);
        }
    }

    private Optional<Coordinates> lookup(String name) {
        try {
            return Optional.of(geocodingService.findCity(name));
        } catch (CityNotFoundException exception) {
            return Optional.empty();
        } finally {
            lastCallAtMs = System.currentTimeMillis();
        }
    }

    /** Prend le verrou et attend que l'intervalle minimal depuis le dernier appel soit écoulé. */
    private void acquireNominatimSlot() {
        try {
            if (!nominatimLock.tryLock(MAX_WAIT_MS, TimeUnit.MILLISECONDS)) {
                throw new GeocodingUnavailableException(null);
            }
            long waitMs = lastCallAtMs + minIntervalMs - System.currentTimeMillis();
            if (waitMs > 0) {
                Thread.sleep(waitMs);
            }
        } catch (InterruptedException exception) {
            if (nominatimLock.isHeldByCurrentThread()) {
                nominatimLock.unlock();
            }
            Thread.currentThread().interrupt();
            throw new GeocodingUnavailableException(exception);
        }
    }

    private static CityNotFoundException notFound(String name) {
        return new CityNotFoundException("Ville introuvable : \"" + name.trim() + "\".");
    }
}
