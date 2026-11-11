import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ExplorerFilters from "./ExplorerFilters";

function renderFilters(props: Partial<Parameters<typeof ExplorerFilters>[0]> = {}) {
    return render(
        <ExplorerFilters
            categories={[]}
            onClearDate={vi.fn()}
            onDateChange={vi.fn()}
            onFreeOnlyChange={vi.fn()}
            onPeriodChange={vi.fn()}
            onRadiusChange={vi.fn()}
            onSelectedCategoryCodesChange={vi.fn()}
            onUseMyLocation={vi.fn()}
            onViewModeChange={vi.fn()}
            radiusKm={null}
            selectedCategoryCodes={[]}
            selectedDate=""
            selectedDateTo=""
            viewMode="map"
            {...props}
        />,
    );
}

describe("ExplorerFilters", () => {
    it("affiche Catégories et Gratuit pour les activités", () => {
        renderFilters();

        expect(screen.getByRole("button", { name: /Catégories/ })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Gratuit" })).toBeInTheDocument();
    });

    it("masque Catégories et Gratuit sur la couche food trucks, garde le type de cuisine", () => {
        renderFilters({
            showActivityFilters: false,
            cuisineFilter: { options: [], selected: "", onChange: vi.fn() },
        });

        expect(screen.queryByRole("button", { name: /Catégories/ })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Gratuit" })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: /cuisine/i })).toBeInTheDocument();
    });
});
