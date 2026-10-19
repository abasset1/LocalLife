import { useEffect, useState } from "react";
import type { FoodTruck } from "../explorer/types";
import { formatHours } from "../foodtrucks/foodTruckFormat";

/**
 * Food trucks présents aujourd'hui à un lieu (ADR-0013) : sur le lieu lui-même ou sur un emplacement qui lui est
 * relié. Rien n'est affiché s'il n'y en a pas ou si la requête échoue.
 */
function PlaceFoodTrucks({ placeId }: { placeId: number }) {
    const [foodTrucks, setFoodTrucks] = useState<FoodTruck[]>([]);

    useEffect(() => {
        const controller = new AbortController();
        fetch(`/api/v1/places/${placeId}/foodtrucks`, { signal: controller.signal })
            .then(async (response) => {
                if (response.ok) {
                    setFoodTrucks((await response.json()) as FoodTruck[]);
                }
            })
            .catch(() => {
                // Annulé ou serveur injoignable : la fiche du lieu reste affichée sans food trucks.
            });
        return () => controller.abort();
    }, [placeId]);

    if (foodTrucks.length === 0) {
        return null;
    }
    return (
        <div className="place-food-trucks">
            <strong>Food trucks présents aujourd'hui</strong>
            <ul>
                {foodTrucks.map((foodTruck) => (
                    <li key={foodTruck.presenceId}>
                        🚚 {foodTruck.name} · {foodTruck.category}
                        {formatHours(foodTruck) ? ` · ${formatHours(foodTruck)}` : ""}
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default PlaceFoodTrucks;
