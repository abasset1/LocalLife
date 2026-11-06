/**
 * Types de cuisine d'un food truck (`FoodTruckCuisine` backend) : liste fermée pour filtrer la carte et la liste,
 * et pastille personnalisée (emoji, couleur) choisie par son propriétaire. Mêmes valeurs que sur le site.
 */
export const CUISINE_TYPES = [
  { code: 'BURGER', label: 'Burgers', emoji: '🍔' },
  { code: 'PIZZA', label: 'Pizzas', emoji: '🍕' },
  { code: 'ASIAN', label: 'Asiatique', emoji: '🍜' },
  { code: 'MEXICAN', label: 'Mexicain', emoji: '🌮' },
  { code: 'CREPES', label: 'Crêpes et galettes', emoji: '🥞' },
  { code: 'SANDWICH', label: 'Sandwichs', emoji: '🥪' },
  { code: 'FRENCH', label: 'Cuisine française', emoji: '🥖' },
  { code: 'ITALIAN', label: 'Italien', emoji: '🍝' },
  { code: 'WORLD', label: 'Cuisine du monde', emoji: '🌍' },
  { code: 'VEGETARIAN', label: 'Végétarien', emoji: '🥗' },
  { code: 'DESSERT', label: 'Desserts et glaces', emoji: '🍦' },
  { code: 'DRINKS', label: 'Boissons et café', emoji: '☕' },
  { code: 'OTHER', label: 'Autre', emoji: '🍽️' },
] as const;

export function cuisineLabel(code: string | null | undefined): string | null {
  return CUISINE_TYPES.find((cuisine) => cuisine.code === code)?.label ?? null;
}

export const DEFAULT_MARKER_EMOJI = '🚚';
export const DEFAULT_MARKER_COLOR = '#ec3b6e';

/** Emojis proposés pour la pastille. */
export const MARKER_EMOJIS = [
  '🚚', '🍔', '🍕', '🌮', '🌯', '🍜', '🍣', '🥟', '🥞', '🧇', '🥪', '🌭', '🍟', '🥗', '🍝', '🥖', '🧀',
  '🍗', '🥩', '🍦', '🍩', '🧁', '☕', '🍺', '🍹', '🌍', '🔥', '⭐',
];

/** Couleurs proposées pour la pastille. */
export const MARKER_COLORS = [
  '#ec3b6e', '#fba91e', '#7b2cb5', '#e11d48', '#f97316', '#eab308', '#16a34a', '#0d9488', '#2563eb', '#7c3aed', '#db2777', '#1f2937',
];

/** Pastille effective : celle choisie, sinon l'emoji du type de cuisine, sinon le camion. */
export function markerOf(foodTruck: {
  markerEmoji?: string | null;
  markerColor?: string | null;
  cuisineType?: string | null;
}): { emoji: string; color: string } {
  const cuisineEmoji = CUISINE_TYPES.find((cuisine) => cuisine.code === foodTruck.cuisineType)?.emoji;
  return {
    emoji: foodTruck.markerEmoji || cuisineEmoji || DEFAULT_MARKER_EMOJI,
    color:
      foodTruck.markerColor && /^#[0-9a-fA-F]{6}$/.test(foodTruck.markerColor)
        ? foodTruck.markerColor
        : DEFAULT_MARKER_COLOR,
  };
}

/** Types de cuisine présents parmi ces food trucks, dans l'ordre de la liste. */
export function availableCuisines(foodTrucks: readonly { cuisineType?: string | null }[]) {
  return CUISINE_TYPES.filter((cuisine) => foodTrucks.some((truck) => truck.cuisineType === cuisine.code));
}
