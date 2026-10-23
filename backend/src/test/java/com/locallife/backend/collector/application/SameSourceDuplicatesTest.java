package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedTiming;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SameSourceDuplicatesTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2027, 9, 18, 9, 0);

    private static final LocalDateTime HALF_TWELVE = LocalDateTime.of(2027, 9, 18, 12, 30);

    private static final CollectedLocation PLACE = new CollectedLocation(
            "Place des Trois Rois", "Place des Trois Rois, 60180 Nogent sur Oise", "Nogent-sur-Oise", "60180",
            null, "FR", 49.27, 2.47, null);

    private CollectedActivity item(String id, String title, List<CollectedTiming> timings, CollectedLocation place) {
        return new CollectedActivity(title, "description", null, "https://openagenda.com/" + id, id, "VélOOise",
                null, null, null, null, place, timings, null, List.of(), List.of(), null, List.of(), null);
    }

    private CollectedActivity workshop(String id) {
        return item(id, "Atelier d'aide à la réparation - Nogent-sur-Oise",
                List.of(new CollectedTiming(NINE, HALF_TWELVE)), PLACE);
    }

    @Test
    void groups_ShouldGatherIdenticalItemsPublishedUnderDifferentIds() {
        List<List<String>> groups = SameSourceDuplicates.groups(
                List.of(workshop("a-1"), workshop("a-2"), workshop("a-3")), CollectedActivity::externalId);

        assertEquals(List.of(List.of("a-1", "a-2", "a-3")), groups);
    }

    @Test
    void groups_ShouldIgnoreCaseAndSpacesInTheTitle() {
        CollectedActivity shouting = item("a-2", "  ATELIER d'aide à la   réparation - Nogent-sur-Oise ",
                List.of(new CollectedTiming(NINE, HALF_TWELVE)), PLACE);

        assertEquals(1, SameSourceDuplicates.groups(List.of(workshop("a-1"), shouting),
                CollectedActivity::externalId).size());
    }

    @Test
    void groups_ShouldKeepDistinctSlotsAndPlaces() {
        CollectedActivity afternoon = item("a-2", "Atelier d'aide à la réparation - Nogent-sur-Oise",
                List.of(new CollectedTiming(NINE.withHour(14), HALF_TWELVE.withHour(17))), PLACE);
        CollectedActivity elsewhere = item("a-3", "Atelier d'aide à la réparation - Nogent-sur-Oise",
                List.of(new CollectedTiming(NINE, HALF_TWELVE)),
                new CollectedLocation(null, "Mairie", "Creil", "60100", null, "FR", 49.26, 2.48, null));
        CollectedActivity otherEnd = item("a-4", "Atelier d'aide à la réparation - Nogent-sur-Oise",
                List.of(new CollectedTiming(NINE, HALF_TWELVE.withHour(13))), PLACE);

        assertTrue(SameSourceDuplicates.groups(List.of(workshop("a-1"), afternoon, elsewhere, otherEnd),
                CollectedActivity::externalId).isEmpty());
    }

    @Test
    void groups_ShouldCompareAllSlots_InAnyOrder() {
        CollectedTiming first = new CollectedTiming(NINE, HALF_TWELVE);
        CollectedTiming second = new CollectedTiming(NINE.plusDays(7), HALF_TWELVE.plusDays(7));
        CollectedActivity both = item("a-1", "Atelier", List.of(first, second), PLACE);
        CollectedActivity reversed = item("a-2", "Atelier", List.of(second, first), PLACE);
        CollectedActivity firstOnly = item("a-3", "Atelier", List.of(first), PLACE);

        assertEquals(List.of(List.of("a-1", "a-2")),
                SameSourceDuplicates.groups(List.of(both, reversed, firstOnly), CollectedActivity::externalId));
    }

    @Test
    void groups_ShouldNeverGatherItemsWithoutSlotOrPlace() {
        CollectedActivity noSlot = item("a-1", "Atelier", List.of(), PLACE);
        CollectedActivity noSlotToo = item("a-2", "Atelier", List.of(), PLACE);
        CollectedActivity noPlace = item("a-3", "Atelier", List.of(new CollectedTiming(NINE, null)), null);
        CollectedActivity noPlaceToo = item("a-4", "Atelier", List.of(new CollectedTiming(NINE, null)),
                new CollectedLocation(null, " ", null, null, null, "FR", null, null, null));

        assertTrue(SameSourceDuplicates.groups(List.of(noSlot, noSlotToo, noPlace, noPlaceToo),
                CollectedActivity::externalId).isEmpty());
    }

    @Test
    void groups_ShouldNotTreatTheSameKeyCollectedTwiceAsADuplicate() {
        assertTrue(SameSourceDuplicates.groups(List.of(workshop("a-1"), workshop("a-1")),
                CollectedActivity::externalId).isEmpty());
    }
}
