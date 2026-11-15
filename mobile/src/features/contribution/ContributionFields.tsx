import type { Category, ManagedPlace } from '@/api';
import { TextField } from '@/components/text-field';
import { FieldRow, FormSection, Hint } from '@/components/form-parts';
import { ChipGroup, toggleCode } from '@/features/settings';

import { ActivityPhotoField, type PhotoChange } from './ActivityPhotoField';
import { NO_HOST_PLACE, formatRecurrence, hostPlaceOptions, type ContributionForm } from './form';

interface ContributionFieldsProps {
  form: ContributionForm;
  categories: readonly Category[];
  onChange: (form: ContributionForm) => void;
  /** Lieux publiés gérés par l'auteur (accès pro) : choix facultatif « Proposé par un de mes lieux ». */
  hostPlaces?: readonly ManagedPlace[];
  /** Image facultative : déjà enregistrée (`currentPhotoUrl`) et changement demandé. */
  photo?: {
    currentPhotoUrl: string | null | undefined;
    change: PhotoChange;
    onChange: (change: PhotoChange) => void;
    onError: (message: string) => void;
  };
}

/**
 * Champs du formulaire de contribution, partagés par la proposition (onglet Contribution) et la
 * modification (« Mes contributions ») : dates et heures de début et de fin facultatives, image facultative.
 * Pas de récurrence : une contribution déjà hebdomadaire garde son passage, seules ses dates se modifient.
 */
export function ContributionFields({ form, categories, onChange, hostPlaces = [], photo }: ContributionFieldsProps) {
  const set = (patch: Partial<ContributionForm>) => onChange({ ...form, ...patch });

  return (
    <>
      <FormSection title="L’activité" first>
        <TextField
          label="Titre"
          placeholder="Marché nocturne, concert, vide-grenier…"
          value={form.title}
          onChangeText={(title) => set({ title })}
          maxLength={255}
        />
        <TextField
          label="Description"
          placeholder="Ce qui s’y passe, pour qui, ce qu’il faut savoir…"
          value={form.description}
          onChangeText={(description) => set({ description })}
          multiline
        />
        <ChipGroup
          label="Catégories (au moins une)"
          options={categories.map((c) => ({ value: c.code, label: c.label }))}
          selected={form.categoryCodes}
          onToggle={(code) => set({ categoryCodes: toggleCode(form.categoryCodes, code) })}
        />
        {photo ? (
          <ActivityPhotoField
            currentPhotoUrl={photo.currentPhotoUrl}
            change={photo.change}
            onChange={photo.onChange}
            onError={photo.onError}
          />
        ) : null}
      </FormSection>

      <FormSection title="Où">
        <TextField
          label="Adresse"
          value={form.address}
          onChangeText={(address) => set({ address })}
          placeholder="Ex : 10 rue de la République, Marseille"
          autoComplete="street-address"
        />
        {hostPlaces.length > 0 || form.hostPlaceId !== null ? (
          <ChipGroup
            label="Proposé par un de mes lieux (facultatif)"
            options={hostPlaceOptions(hostPlaces, form.hostPlaceId)}
            selected={[form.hostPlaceId === null ? NO_HOST_PLACE : String(form.hostPlaceId)]}
            onToggle={(value) => set({ hostPlaceId: value === NO_HOST_PLACE ? null : Number(value) })}
            single
          />
        ) : null}
      </FormSection>

      <FormSection title="Quand">
        {/*
          Plus de récurrence dans le formulaire (comme sur le site) : ni case « Revient chaque semaine », ni jours
          ou horaires de passage. Une contribution déjà hebdomadaire garde son passage tel quel ; seules ses dates
          (« À partir du », « Jusqu'au ») se modifient.
        */}
        {form.recurring && form.days.length > 0 ? (
          <Hint>{formatRecurrence(form.days, form.startTime)} : passage hebdomadaire conservé.</Hint>
        ) : null}
        <FieldRow>
          <TextField
            label={form.recurring ? 'À partir du (facultatif)' : 'Date de début (facultatif)'}
            value={form.startDate}
            onChangeText={(startDate) => set({ startDate })}
            placeholder="AAAA-MM-JJ"
            keyboardType="numbers-and-punctuation"
          />
          <TextField
            label={form.recurring ? "Jusqu'au (facultatif)" : 'Date de fin (facultatif)'}
            value={form.endDate}
            onChangeText={(endDate) => set({ endDate })}
            placeholder="AAAA-MM-JJ"
            keyboardType="numbers-and-punctuation"
          />
        </FieldRow>
        {!form.recurring && (
          <FieldRow>
            <TextField
              label="Heure de début (facultatif)"
              value={form.startTime}
              onChangeText={(startTime) => set({ startTime })}
              placeholder="20:30"
              keyboardType="numbers-and-punctuation"
            />
            <TextField
              label="Heure de fin (facultatif)"
              value={form.endTime}
              onChangeText={(endTime) => set({ endTime })}
              placeholder="23:00"
              keyboardType="numbers-and-punctuation"
            />
          </FieldRow>
        )}
        {form.recurring ? (
          <Hint>Sans date de début, la récurrence commence aujourd’hui.</Hint>
        ) : (
          <Hint>Sans date de fin, l’activité se termine le jour du début (le lendemain si l’heure de fin est avant celle du début).</Hint>
        )}
      </FormSection>

      <FormSection title="Contact" hint="Facultatif : affiché sur la fiche de l’activité.">
        <TextField
          label="Site web"
          value={form.url}
          onChangeText={(url) => set({ url })}
          placeholder="https://…"
          autoCapitalize="none"
          keyboardType="url"
        />
        <TextField
          label="Téléphone"
          value={form.phone}
          onChangeText={(phone) => set({ phone })}
          keyboardType="phone-pad"
        />
        <TextField
          label="E-mail"
          value={form.email}
          onChangeText={(email) => set({ email })}
          autoCapitalize="none"
          keyboardType="email-address"
        />
      </FormSection>
    </>
  );
}
