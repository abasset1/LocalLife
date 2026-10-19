import type { FoodTruck } from "../explorer/types";
import { describePresence } from "./foodTruckFormat";

/**
 * Liste « Food trucks » (ADR-0013) : les food trucks présents aujourd'hui dans la zone affichée, une ligne par
 * présence, distincts des activités et des lieux. Consultation libre, sans compte.
 */
function FoodTruckList({ foodTrucks }: { foodTrucks: FoodTruck[] }) {
    if (foodTrucks.length === 0) {
        return <p className="explorer-status">Aucun food truck présent aujourd'hui dans cette zone.</p>;
    }
    const sorted = [...foodTrucks].sort((a, b) => a.name.localeCompare(b.name, "fr")
        || (a.startTime ?? "").localeCompare(b.startTime ?? ""));
    return (
        <div className="explorer-list">
            <ul className="explorer-list-items">
                {sorted.map((foodTruck) => (
                    <li className="explorer-list-item" key={foodTruck.presenceId}>
                        <span className="explorer-list-item-title">🚚 {foodTruck.name}</span>
                        <span className="explorer-list-item-meta">{foodTruck.category}</span>
                        <span className="explorer-list-item-meta">{describePresence(foodTruck)}</span>
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default FoodTruckList;
