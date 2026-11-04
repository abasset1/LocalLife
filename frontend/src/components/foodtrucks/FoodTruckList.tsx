import type { FoodTruck } from "../explorer/types";
import { cuisineLabel, markerOf } from "./cuisines";
import { describePresence, spotAddressLine } from "./foodTruckFormat";

/**
 * Liste « Food trucks » (ADR-0013) : les food trucks présents aujourd'hui dans la zone affichée, une ligne par
 * présence, distincts des activités et des lieux. Consultation libre, sans compte. Même présentation que la liste
 * des activités : photo, nom, emplacement, cuisine ; un clic ouvre la fiche du food truck.
 */
function FoodTruckList({ foodTrucks, onSelectFoodTruck }: {
    foodTrucks: FoodTruck[];
    onSelectFoodTruck?: (foodTruck: FoodTruck) => void;
}) {
    if (foodTrucks.length === 0) {
        return <p className="explorer-status">Aucun food truck présent aujourd'hui dans cette zone.</p>;
    }
    const sorted = [...foodTrucks].sort((a, b) => a.name.localeCompare(b.name, "fr")
        || (a.startTime ?? "").localeCompare(b.startTime ?? ""));
    return (
        <div className="explorer-list">
            <ul className="explorer-list-items">
                {sorted.map((foodTruck) => (
                    <li key={foodTruck.presenceId}>
                        <button
                            className="explorer-list-item"
                            disabled={!onSelectFoodTruck}
                            onClick={() => onSelectFoodTruck?.(foodTruck)}
                            type="button"
                        >
                            {foodTruck.photoUrl && (
                                <img alt="" className="explorer-list-item-thumbnail" loading="lazy" src={foodTruck.photoUrl} />
                            )}
                            <span className="explorer-list-item-title">{markerOf(foodTruck).emoji} {foodTruck.name}</span>
                            <span className="explorer-list-item-meta">
                                {[cuisineLabel(foodTruck.cuisineType), foodTruck.category].filter(Boolean).join(" · ")}
                            </span>
                            <span className="explorer-list-item-meta">{describePresence(foodTruck)}</span>
                            {spotAddressLine(foodTruck) && (
                                <span className="explorer-list-item-meta">🏠 {spotAddressLine(foodTruck)}</span>
                            )}
                            {foodTruck.description && (
                                <span className="explorer-list-item-description">{foodTruck.description}</span>
                            )}
                        </button>
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default FoodTruckList;
