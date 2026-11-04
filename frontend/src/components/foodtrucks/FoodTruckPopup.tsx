import { useState } from "react";
import type { FoodTruck } from "../explorer/types";
import { cuisineLabel, markerOf } from "./cuisines";
import { SPOT_KIND_LABELS, formatHours } from "./foodTruckFormat";

interface FoodTruckPopupProps {
    /** Food trucks de la pastille cliquée (même emplacement ou groupe de marqueurs). */
    foodTrucks: FoodTruck[];
    onOpenDetail: (foodTruck: FoodTruck) => void;
}

/**
 * Popup d'une pastille food truck : même présentation que celle des activités (`ClusterActivitiesPopup`) — photo,
 * horaires du jour, nom, emplacement et cuisine, bouton vers la fiche, flèches pour passer d'un food truck à l'autre.
 */
function FoodTruckPopup({ foodTrucks, onOpenDetail }: FoodTruckPopupProps) {
    const [index, setIndex] = useState(0);
    const total = foodTrucks.length;
    const foodTruck = foodTrucks[Math.min(index, total - 1)];
    if (!foodTruck) {
        return null;
    }
    const hours = formatHours(foodTruck);
    const spot = [SPOT_KIND_LABELS[foodTruck.spotKind] ?? null, foodTruck.spotName].filter(Boolean).join(" · ");
    const cuisine = [cuisineLabel(foodTruck.cuisineType), foodTruck.category].filter(Boolean).join(" · ");
    const move = (step: number) => setIndex((current) => (current + step + total) % total);

    return (
        <div aria-label="Food trucks de cet emplacement" className="explorer-cluster-popup" role="group">
            {foodTruck.photoUrl && (
                <img alt="" className="explorer-cluster-popup-thumbnail" loading="lazy" src={foodTruck.photoUrl} />
            )}
            <span className="explorer-cluster-popup-date">
                {hours ? `Aujourd'hui · ${hours}` : "Présent aujourd'hui"}
            </span>
            <strong className="explorer-cluster-popup-title">
                <span aria-hidden="true">{markerOf(foodTruck).emoji} </span>{foodTruck.name}
            </strong>
            <span className="explorer-cluster-popup-meta">
                {spot ? `📍 ${spot}` : ""}
                {spot && cuisine ? " · " : ""}
                {cuisine}
            </span>
            <button className="explorer-cluster-popup-cta" onClick={() => onOpenDetail(foodTruck)} type="button">
                Voir le food truck →
            </button>
            {total > 1 && (
                <div className="explorer-cluster-popup-nav">
                    <button aria-label="Food truck précédent" onClick={() => move(-1)} type="button">‹</button>
                    <span aria-live="polite">{index + 1} / {total}</span>
                    <button aria-label="Food truck suivant" onClick={() => move(1)} type="button">›</button>
                </div>
            )}
        </div>
    );
}

export default FoodTruckPopup;
