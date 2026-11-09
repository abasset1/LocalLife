import { useLaunchFeatures } from "../../features/launchFeatures";
import type { MapLayer } from "../explorer/types";
import { Chip, ChipGroup } from "./ProfileUi";

const MAP_LAYER_OPTIONS: { value: MapLayer; label: string }[] = [
    { value: "activities", label: "📅 Activités" },
    { value: "places", label: "🏛 Lieux" },
    { value: "foodTrucks", label: "🚚 Food trucks" },
    { value: "night", label: "🍸 Night" },
];

interface MapLayerFieldProps {
    name: string;
    /** `null` : aucune carte imposée, l'Explorer reprend la dernière choisie dans le navigateur. */
    value: MapLayer | null;
    onChange: (value: MapLayer | null) => void;
    legend?: string;
    hint?: string;
    /** Propose « Dernière utilisée » (`null`), pour revenir au comportement standard. */
    allowLastUsed?: boolean;
}

/**
 * Carte ouverte à l'arrivée sur l'Explorer : activités, lieux, food trucks ou « Night ». Seules les cartes ouvertes au public
 * sont proposées (lancement progressif, voir `launchFeatures.tsx`) ; la carte déjà enregistrée reste affichée
 * même si elle a été refermée depuis.
 */
export function useAvailableMapLayers(): MapLayer[] {
    const features = useLaunchFeatures();
    return MAP_LAYER_OPTIONS.map((option) => option.value).filter(
        (layer) => layer === "activities" || layer === "night"
            || (layer === "places" ? features.places : features.foodTrucks),
    );
}

function MapLayerField({
    name,
    value,
    onChange,
    legend = "Carte à l'ouverture",
    hint,
    allowLastUsed = false,
}: MapLayerFieldProps) {
    const availableLayers = useAvailableMapLayers();
    const options = MAP_LAYER_OPTIONS.filter((option) => availableLayers.includes(option.value) || option.value === value);

    return (
        <ChipGroup hint={hint} legend={legend}>
            {allowLastUsed && (
                <Chip checked={value === null} name={name} onChange={() => onChange(null)} type="radio">
                    Dernière utilisée
                </Chip>
            )}
            {options.map((option) => (
                <Chip
                    checked={value === option.value}
                    key={option.value}
                    name={name}
                    onChange={() => onChange(option.value)}
                    type="radio"
                >
                    {option.label}
                </Chip>
            ))}
        </ChipGroup>
    );
}

export default MapLayerField;
