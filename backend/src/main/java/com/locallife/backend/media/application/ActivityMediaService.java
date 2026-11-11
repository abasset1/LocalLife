package com.locallife.backend.media.application;

import com.locallife.backend.media.domain.ActivityMedia;
import com.locallife.backend.media.infrastructure.ActivityMainImageRepository;
import com.locallife.backend.media.infrastructure.ActivityMediaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Service ActivityMedia (LL-11007) : association entre une activité et
 * un {@code Media}, avec position d'affichage. Délégation simple vers le
 * repository — l'intégrité référentielle (activité/média existants) est
 * garantie par les contraintes {@code FOREIGN KEY} de
 * {@code V22__create_media_tables.sql}, et l'unicité (pas deux fois le
 * même média sur la même activité) par sa contrainte {@code UNIQUE}.
 */
@Service
public class ActivityMediaService {

    private final ActivityMediaRepository activityMediaRepository;
    private final ActivityMainImageRepository mainImageRepository;

    public ActivityMediaService(
            ActivityMediaRepository activityMediaRepository, ActivityMainImageRepository mainImageRepository) {
        this.activityMediaRepository = activityMediaRepository;
        this.mainImageRepository = mainImageRepository;
    }

    /**
     * Url de l'image principale (premier média par position) de chaque activité demandée, en une requête :
     * une activité sans média est absente du résultat.
     */
    public Map<Long, String> findMainImageUrls(Collection<Long> activityIds) {
        return mainImageRepository.findMainImageUrls(activityIds);
    }

    /**
     * Médias d'une activité, dans l'ordre d'affichage (critère
     * d'acceptation « ordre conservé »).
     */
    public List<ActivityMedia> findByActivityId(Long activityId) {
        return activityMediaRepository.findByActivityIdOrderByPosition(activityId);
    }

    public ActivityMedia attach(ActivityMedia activityMedia) {
        return activityMediaRepository.save(activityMedia);
    }

    /**
     * Détache tous les médias d'une activité (LL-11011) : voir la
     * javadoc de {@code ActivityMediaRepository#deleteByActivityId}.
     */
    public void deleteByActivityId(Long activityId) {
        activityMediaRepository.deleteByActivityId(activityId);
    }
}
