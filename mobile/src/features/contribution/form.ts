import type { Contribution, CreateActivityRequest, ManagedPlace } from '@/api';

/**
 * Formulaire de contribution (création et modification), sans dépendance à React Native : état,
 * validation et conversion vers le corps de `POST`/`PUT /api/v1/activities` — mêmes règles que le
 * formulaire web (`ContributionModal`) et que le backend (`ContributionRecurrence`).
 */
export interface ContributionForm {
  title: string;
  description: string;
  categoryCodes: string[];
  address: string;
  /** `yyyy-MM-dd` ; avec une récurrence : premier jour possible (aujourd'hui si vide). */
  startDate: string;
  /** `yyyy-MM-dd` ; avec une récurrence : dernier jour possible (facultatif). */
  endDate: string;
  recurring: boolean;
  days: string[];
  /** `HH:mm`. */
  startTime: string;
  endTime: string;
  url: string;
  phone: string;
  email: string;
  /** Lieu géré qui propose l'activité (« Proposé par… »), `null` sinon. */
  hostPlaceId: number | null;
}

export const EMPTY_FORM: ContributionForm = {
  title: '',
  description: '',
  categoryCodes: [],
  address: '',
  startDate: '',
  endDate: '',
  recurring: false,
  days: [],
  startTime: '',
  endTime: '',
  url: '',
  phone: '',
  email: '',
  hostPlaceId: null,
};

/** Jours de passage, dans l'ordre de la semaine (codes RFC 5545 attendus par le backend). */
export const WEEK_DAYS = [
  { value: 'MO', label: 'Lundi' },
  { value: 'TU', label: 'Mardi' },
  { value: 'WE', label: 'Mercredi' },
  { value: 'TH', label: 'Jeudi' },
  { value: 'FR', label: 'Vendredi' },
  { value: 'SA', label: 'Samedi' },
  { value: 'SU', label: 'Dimanche' },
] as const;

const DAY_NAMES: Record<string, string> = {
  MO: 'lundis',
  TU: 'mardis',
  WE: 'mercredis',
  TH: 'jeudis',
  FR: 'vendredis',
  SA: 'samedis',
  SU: 'dimanches',
};

export const STATUS_LABELS: Record<string, string> = {
  PENDING: 'En attente de validation',
  PUBLISHED: 'Publiée',
  REJECTED: 'Refusée',
  CANCELLED: 'Annulée',
  ARCHIVED: 'Archivée',
};

/** Statuts modifiables côté backend (`ContributionService`) : une activité refusée peut être corrigée. */
export function isEditable(status: string): boolean {
  return status === 'PENDING' || status === 'PUBLISHED' || status === 'REJECTED';
}

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

/** `"9h30"`, `"9:30"`, `"0930"` → `"09:30"` ; renvoyé tel quel s'il n'est pas reconnu. */
export function normalizeTime(value: string): string {
  const match = value.trim().match(/^(\d{1,2})\s*[:hH.]?\s*(\d{2})?$/);
  if (!match) return value.trim();
  const hours = match[1].padStart(2, '0');
  const minutes = match[2] ?? '00';
  return `${hours}:${minutes}`;
}

function optional(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}

export type FormResult = { ok: true; request: CreateActivityRequest } | { ok: false; error: string };

/** Valide le formulaire et construit le corps de requête, ou renvoie le premier problème à corriger. */
export function toRequest(form: ContributionForm): FormResult {
  if (form.title.trim() === '') return { ok: false, error: 'Le titre est obligatoire.' };
  if (form.address.trim() === '') return { ok: false, error: "L'adresse est obligatoire." };
  if (form.categoryCodes.length === 0) return { ok: false, error: 'Choisissez au moins une catégorie.' };
  for (const [label, value] of [
    ['de début', form.startDate],
    ['de fin', form.endDate],
  ] as const) {
    if (value.trim() !== '' && !DATE_PATTERN.test(value.trim())) {
      return { ok: false, error: `La date ${label} doit être au format AAAA-MM-JJ.` };
    }
  }

  const base: CreateActivityRequest = {
    title: form.title.trim(),
    description: optional(form.description),
    categoryCodes: form.categoryCodes,
    address: form.address.trim(),
    url: optional(form.url),
    phone: optional(form.phone),
    email: optional(form.email),
    // Toujours renvoyé à la modification : absent, le serveur retirerait le lieu déjà choisi.
    hostPlaceId: form.hostPlaceId ?? undefined,
  };

  if (!form.recurring) {
    return { ok: true, request: { ...base, startDate: optional(form.startDate), endDate: optional(form.endDate) } };
  }

  if (form.days.length === 0) return { ok: false, error: 'Choisissez au moins un jour de passage.' };
  const startTime = normalizeTime(form.startTime);
  if (!TIME_PATTERN.test(startTime)) {
    return { ok: false, error: "L'heure d'arrivée est obligatoire (ex. 11:30)." };
  }
  const endTime = form.endTime.trim() === '' ? null : normalizeTime(form.endTime);
  if (endTime !== null && !TIME_PATTERN.test(endTime)) {
    return { ok: false, error: "L'heure de départ doit être au format HH:MM (ex. 14:00)." };
  }
  const days = WEEK_DAYS.map((day) => day.value).filter((day) => form.days.includes(day));
  return {
    ok: true,
    request: {
      ...base,
      recurrence: {
        daysOfWeek: days,
        startTime,
        endTime,
        from: optional(form.startDate) ?? null,
        until: optional(form.endDate) ?? null,
      },
    },
  };
}

/** Formulaire pré-rempli avec une contribution existante (modification). */
export function fromContribution(contribution: Contribution): ContributionForm {
  const common = {
    ...EMPTY_FORM,
    title: contribution.title,
    description: contribution.description ?? '',
    categoryCodes: contribution.categoryCodes,
    address: contribution.address ?? '',
    url: contribution.url ?? '',
    phone: contribution.phone ?? '',
    email: contribution.email ?? '',
    hostPlaceId: contribution.hostPlaceId ?? null,
  };
  const recurrence = contribution.recurrence;
  if (recurrence) {
    return {
      ...common,
      recurring: true,
      days: recurrence.daysOfWeek,
      startTime: recurrence.startTime.slice(0, 5),
      endTime: recurrence.endTime?.slice(0, 5) ?? '',
      startDate: recurrence.from ?? '',
      endDate: recurrence.until ?? '',
    };
  }
  return {
    ...common,
    startDate: contribution.startDate?.slice(0, 10) ?? '',
    endDate: contribution.endDate?.slice(0, 10) ?? '',
  };
}

/** `["TU", "TH"]`, `"11:30"` → `"Tous les mardis et jeudis à 11h30"`. */
export function formatRecurrence(daysOfWeek: readonly string[], startTime: string): string {
  const names = WEEK_DAYS.filter((day) => daysOfWeek.includes(day.value)).map((day) => DAY_NAMES[day.value]);
  const list = names.length > 1 ? `${names.slice(0, -1).join(', ')} et ${names[names.length - 1]}` : names[0];
  return `Tous les ${list} à ${startTime.slice(0, 5).replace(':', 'h')}`;
}

/** Quand a lieu une contribution, pour la liste « Mes contributions ». */
export function formatWhen(contribution: Contribution): string {
  if (contribution.recurrence) {
    return formatRecurrence(contribution.recurrence.daysOfWeek, contribution.recurrence.startTime);
  }
  if (!contribution.startDate) return 'Date non renseignée';
  const [year, month, day] = contribution.startDate.slice(0, 10).split('-');
  return `Le ${day}/${month}/${year}`;
}

/** Valeur du choix « Aucun » parmi les lieux qui proposent l'activité. */
export const NO_HOST_PLACE = 'none';

/** Choix « Proposé par » : aucun lieu, les lieux gérés publiés, et le lieu déjà choisi s'il n'en fait plus partie. */
export function hostPlaceOptions(
  hostPlaces: readonly Pick<ManagedPlace, 'id' | 'name'>[],
  chosen: number | null,
): { value: string; label: string }[] {
  const options = [
    { value: NO_HOST_PLACE, label: 'Aucun' },
    ...hostPlaces.map((place) => ({ value: String(place.id), label: place.name })),
  ];
  if (chosen !== null && !hostPlaces.some((place) => place.id === chosen)) {
    options.push({ value: String(chosen), label: 'Lieu déjà choisi' });
  }
  return options;
}
