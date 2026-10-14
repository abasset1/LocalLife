import { useMemo, useState } from "react";
import { APPROXIMATE_LOCATION_LABEL } from "../explorer/activityUtils";
import type { Activity } from "../explorer/types";
import PlaceActivities from "./PlaceActivities";
import { placeTypeLabel } from "./placeTypes";
import type { Place } from "./types";

const NO_CITY = "Commune non renseignée";

/**
 * Liste « Lieux » (ADR-0011, § 2.4) : lieux publiés de la zone affichée, les mêmes que la couche de la carte,
 * filtrables par type et groupés par commune. Un lieu se déplie pour montrer son adresse, son site et ses activités
 * à venir ; un clic sur une activité ouvre son aperçu, comme depuis la carte.
 */
function PlaceList({ places, onSelectActivity }: {
    places: Place[];
    onSelectActivity: (activity: Activity) => void;
}) {
    const [type, setType] = useState("");
    const [openId, setOpenId] = useState<number | null>(null);

    const typeOptions = useMemo(
        () => [...new Set(places.map((place) => place.placeType))]
            .map((value) => ({ value, label: placeTypeLabel(value) }))
            .sort((a, b) => a.label.localeCompare(b.label, "fr")),
        [places],
    );
    const groups = useMemo(() => {
        const byCity = new Map<string, Place[]>();
        places
            .filter((place) => type === "" || place.placeType === type)
            .forEach((place) => {
                const city = place.city ?? NO_CITY;
                byCity.set(city, [...(byCity.get(city) ?? []), place]);
            });
        return [...byCity.entries()]
            .sort(([a], [b]) => (a === NO_CITY ? 1 : b === NO_CITY ? -1 : a.localeCompare(b, "fr")))
            .map(([city, cityPlaces]) =>
                [city, [...cityPlaces].sort((a, b) => a.name.localeCompare(b.name, "fr"))] as const);
    }, [places, type]);

    if (places.length === 0) {
        return <p className="explorer-status">Aucun lieu publié dans cette zone.</p>;
    }

    return (
        <div className="explorer-list">
            <label className="place-list-filter">
                Type de lieu
                <select onChange={(event) => setType(event.target.value)} value={type}>
                    <option value="">Tous ({places.length})</option>
                    {typeOptions.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                </select>
            </label>
            {groups.map(([city, cityPlaces]) => (
                <section className="explorer-list-group" key={city}>
                    <h2 className="explorer-list-city">{city}</h2>
                    <ul className="explorer-list-items">
                        {cityPlaces.map((place) => {
                            const open = openId === place.id;
                            return (
                                <li key={place.id}>
                                    <button
                                        aria-expanded={open}
                                        className="explorer-list-item"
                                        onClick={() => setOpenId(open ? null : place.id)}
                                        type="button"
                                    >
                                        <span className="explorer-list-item-title">🏛 {place.name}</span>
                                        <span className="explorer-list-item-meta">
                                            {placeTypeLabel(place.placeType)}
                                            {place.coordinatePrecision === "APPROXIMATE" ? ` · ${APPROXIMATE_LOCATION_LABEL}` : ""}
                                        </span>
                                    </button>
                                    {open && (
                                        <div className="place-list-details">
                                            {place.address && <p>{place.address}</p>}
                                            {place.description && (
                                                <p>
                                                    {place.description.length > 280
                                                        ? `${place.description.slice(0, 280)}…`
                                                        : place.description}
                                                </p>
                                            )}
                                            {place.website && (
                                                <a href={place.website} rel="noreferrer" target="_blank">Site du lieu</a>
                                            )}
                                            <PlaceActivities onSelectActivity={onSelectActivity} placeId={place.id} />
                                        </div>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                </section>
            ))}
            {groups.length === 0 && <p className="explorer-status">Aucun lieu de ce type dans cette zone.</p>}
        </div>
    );
}

export default PlaceList;
