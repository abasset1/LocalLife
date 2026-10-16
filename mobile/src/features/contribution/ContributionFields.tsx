import type { Category } from '@/api';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ChannelSwitch, ChipGroup, toggleCode } from '@/features/settings';

import { WEEK_DAYS, type ContributionForm } from './form';

interface ContributionFieldsProps {
  form: ContributionForm;
  categories: readonly Category[];
  onChange: (form: ContributionForm) => void;
}

/**
 * Champs du formulaire de contribution, partagés par la proposition (onglet Contribution) et la
 * modification (« Mes contributions »). Une case « Revient chaque semaine » remplace les dates par des
 * jours de passage et des horaires (food truck, marché…).
 */
export function ContributionFields({ form, categories, onChange }: ContributionFieldsProps) {
  const set = (patch: Partial<ContributionForm>) => onChange({ ...form, ...patch });

  return (
    <>
      <TextField label="Titre" value={form.title} onChangeText={(title) => set({ title })} maxLength={255} />
      <TextField
        label="Description"
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
      <TextField
        label="Adresse"
        value={form.address}
        onChangeText={(address) => set({ address })}
        placeholder="Ex : 10 rue de la République, Marseille"
        autoComplete="street-address"
      />

      <ChannelSwitch
        label="Revient chaque semaine (food truck, marché…)"
        value={form.recurring}
        // La date de fin d'une activité ponctuelle (souvent le même jour) bornerait la récurrence à
        // une seule journée : elle est vidée en passant en récurrent.
        onValueChange={(recurring) => set(recurring ? { recurring, endDate: '' } : { recurring })}
      />
      {form.recurring && (
        <>
          <ChipGroup
            label="Jours de passage (au moins un)"
            options={WEEK_DAYS}
            selected={form.days}
            onToggle={(day) => set({ days: toggleCode(form.days, day) })}
          />
          <TextField
            label="Heure d'arrivée"
            value={form.startTime}
            onChangeText={(startTime) => set({ startTime })}
            placeholder="HH:MM, ex. 11:30"
            keyboardType="numbers-and-punctuation"
          />
          <TextField
            label="Heure de départ (facultatif)"
            value={form.endTime}
            onChangeText={(endTime) => set({ endTime })}
            placeholder="HH:MM, ex. 14:00"
            keyboardType="numbers-and-punctuation"
          />
        </>
      )}
      <TextField
        label={form.recurring ? "À partir du (facultatif, aujourd'hui par défaut)" : 'Date de début (facultatif)'}
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

      <ThemedText type="heading">Contact (facultatif)</ThemedText>
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
    </>
  );
}
