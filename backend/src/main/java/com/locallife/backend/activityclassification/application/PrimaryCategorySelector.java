package com.locallife.backend.activityclassification.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Choix de la catégorie principale parmi plusieurs domaines détectés (issue #43) — point unique de
 * cette règle, partagé par le fallback titre ({@link TitleKeywordClassifier}) et les keywords source
 * ({@link ActivityClassifier}).
 *
 * <p>Chaque catégorie détectée porte un score : la priorité maximale de ses signaux reconnus
 * ({@code category_keyword.priority}). Règle :
 * <ol>
 *   <li>la catégorie de score strictement le plus élevé devient principale ;</li>
 *   <li>en cas d'égalité au plus haut score, la catégorie {@code preferred} l'emporte si elle fait
 *       partie des ex aequo (catégorie d'origine de la source) ;</li>
 *   <li>sinon l'égalité est une ambiguïté réelle : aucune catégorie n'est choisie, les ex aequo sont
 *       renvoyés comme candidats ;</li>
 *   <li>les autres catégories deviennent secondaires, par score décroissant puis ordre de détection.</li>
 * </ol>
 *
 * Classe pure, sans état, déterministe.
 */
final class PrimaryCategorySelector {

    /**
     * @param primaryCode    {@code null} si l'égalité n'a pas pu être départagée
     * @param secondaryCodes vide si {@code primaryCode} est {@code null}
     * @param tiedCodes      catégories ex aequo non départagées (vide si {@code primaryCode} est renseigné)
     */
    record Selection(String primaryCode, List<String> secondaryCodes, List<String> tiedCodes) {

        boolean decided() {
            return primaryCode != null;
        }
    }

    private PrimaryCategorySelector() {
    }

    /**
     * @param scores    score par code de catégorie, dans l'ordre de détection (ex. {@code LinkedHashMap}) ;
     *                  non vide
     * @param preferred catégorie qui départage une égalité au plus haut score, ou {@code null}
     */
    static Selection select(Map<String, Integer> scores, String preferred) {
        int best = scores.values().stream().max(Integer::compare).orElseThrow();
        List<String> top = scores.entrySet().stream()
                .filter(entry -> entry.getValue() == best)
                .map(Map.Entry::getKey)
                .toList();
        String primary;
        if (top.size() == 1) {
            primary = top.get(0);
        } else if (preferred != null && top.contains(preferred)) {
            primary = preferred;
        } else {
            return new Selection(null, List.of(), top);
        }
        List<String> order = new ArrayList<>(scores.keySet());
        List<String> secondaries = order.stream()
                .filter(code -> !code.equals(primary))
                .sorted(Comparator.comparing((String code) -> -scores.get(code)).thenComparing(order::indexOf))
                .toList();
        return new Selection(primary, secondaries, List.of());
    }
}
