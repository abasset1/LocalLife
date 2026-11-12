import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import ContributionModal, { EMPTY_RECURRENCE } from "./ContributionModal";

function renderModal(props: Partial<ComponentProps<typeof ContributionModal>> = {}) {
    const noop = vi.fn();
    render(
        <MemoryRouter>
            <ContributionModal
                address="" categories={[]} description="" email="" endDate="" mode="create" phone=""
                recurrence={EMPTY_RECURRENCE} requiresRevalidation={false} selectedCategoryCodes={[]} startDate=""
                submitError={null} submitStatus="idle" title="" url=""
                onAddressChange={noop} onClose={noop} onDescriptionChange={noop} onEmailChange={noop}
                onEndDateChange={noop} onPhoneChange={noop}
                onSelectedCategoryCodesChange={noop} onStartDateChange={noop} onSubmit={noop} onTitleChange={noop}
                onUrlChange={noop}
                {...props}
            />
        </MemoryRouter>,
    );
}

describe("ContributionModal", () => {
    it("ne propose plus de rendre une nouvelle activité hebdomadaire", () => {
        renderModal();
        expect(screen.queryByLabelText(/Revient chaque semaine/)).not.toBeInTheDocument();
        expect(screen.queryByText("Jours de passage (au moins un)")).not.toBeInTheDocument();
        expect(screen.getByLabelText("Date de début")).toBeInTheDocument();
    });

    it("n'affiche pas non plus les jours et horaires en modification d'une contribution récurrente", () => {
        renderModal({ mode: "edit", recurrence: { enabled: true, days: ["MO"], startTime: "11:30", endTime: "" } });
        expect(screen.queryByText("Jours de passage (au moins un)")).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Heure d'arrivée")).not.toBeInTheDocument();
        expect(screen.getByLabelText("Jusqu'au (facultatif)")).toBeInTheDocument();
    });

    it("n'affiche « Proposé par un de mes lieux » qu'avec des lieux publiés gérés", () => {
        renderModal({ onHostPlaceIdChange: vi.fn() });
        expect(screen.queryByLabelText(/Proposé par un de mes lieux/)).not.toBeInTheDocument();
    });

    it("choisit un lieu hôte, ou aucun", async () => {
        const onHostPlaceIdChange = vi.fn();
        renderModal({
            hostPlaceId: 42,
            hostPlaces: [{ id: 42, name: "Musée Calvet" }, { id: 43, name: "Halles d'Avignon" }],
            onHostPlaceIdChange,
        });
        const select = screen.getByLabelText(/Proposé par un de mes lieux/);
        expect(select).toHaveValue("42");

        await userEvent.selectOptions(select, "43");
        expect(onHostPlaceIdChange).toHaveBeenLastCalledWith(43);
        await userEvent.selectOptions(select, "");
        expect(onHostPlaceIdChange).toHaveBeenLastCalledWith(null);
    });

    it("affiche le compteur Premium du mois et bloque une nouvelle proposition à la limite", () => {
        renderModal({ contributionLimits: { maxLikes: null, maxAlerts: 10, monthlyContributions: 2, contributionsThisMonth: 2 } });
        expect(screen.getByText("Contributions ce mois-ci : 2 / 2")).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent("Tu as atteint la limite de 2 contributions ce mois-ci");
        expect(screen.getByRole("button", { name: "Proposer l'activité" })).toBeDisabled();
    });

    it("ne compte pas une modification", () => {
        renderModal({ mode: "edit",
            contributionLimits: { maxLikes: null, maxAlerts: 10, monthlyContributions: 2, contributionsThisMonth: 2 } });
        expect(screen.queryByText(/Contributions ce mois-ci/)).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Enregistrer les modifications" })).toBeEnabled();
    });
});
