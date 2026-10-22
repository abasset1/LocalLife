import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { describe, expect, it, vi } from "vitest";
import ContributionModal, { EMPTY_RECURRENCE } from "./ContributionModal";

function renderModal(props: Partial<ComponentProps<typeof ContributionModal>> = {}) {
    const noop = vi.fn();
    render(
        <ContributionModal
            address="" categories={[]} description="" email="" endDate="" mode="create" phone=""
            recurrence={EMPTY_RECURRENCE} requiresRevalidation={false} selectedCategoryCodes={[]} startDate=""
            submitError={null} submitStatus="idle" title="" url=""
            onAddressChange={noop} onClose={noop} onDescriptionChange={noop} onEmailChange={noop}
            onEndDateChange={noop} onPhoneChange={noop} onRecurrenceChange={noop}
            onSelectedCategoryCodesChange={noop} onStartDateChange={noop} onSubmit={noop} onTitleChange={noop}
            onUrlChange={noop}
            {...props}
        />,
    );
}

describe("ContributionModal", () => {
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
});
