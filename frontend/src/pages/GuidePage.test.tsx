import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import GuidePage, { GUIDE_STEP_DURATION_MS, GUIDE_STEPS } from "./GuidePage";

function renderGuide() {
    return render(
        <MemoryRouter>
            <GuidePage />
        </MemoryRouter>,
    );
}

afterEach(() => {
    vi.useRealTimers();
});

describe("GuidePage — didacticiel animé", () => {
    it("navigue entre les étapes avec les boutons, les points et le clavier", async () => {
        const user = userEvent.setup();
        renderGuide();

        expect(screen.getByRole("heading", { level: 1, name: "Bienvenue sur LocalLife" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "← Précédent" })).toBeDisabled();

        await user.click(screen.getByRole("button", { name: "Suivant →" }));
        expect(screen.getByRole("heading", { level: 1, name: GUIDE_STEPS[1].title })).toBeInTheDocument();
        expect(screen.getByText(`Étape 2 sur ${GUIDE_STEPS.length}`)).toBeInTheDocument();

        await user.keyboard("{ArrowRight}");
        expect(screen.getByRole("heading", { level: 1, name: GUIDE_STEPS[2].title })).toBeInTheDocument();
        await user.keyboard("{ArrowLeft}");
        expect(screen.getByRole("heading", { level: 1, name: GUIDE_STEPS[1].title })).toBeInTheDocument();

        const last = GUIDE_STEPS.length - 1;
        await user.click(screen.getByRole("button", { name: `Étape ${last + 1} : ${GUIDE_STEPS[last].title}` }));
        expect(screen.getByRole("heading", { level: 1, name: "À toi de jouer !" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Suivant →" })).toBeDisabled();
        expect(screen.getByRole("link", { name: "Explorer la carte" })).toHaveAttribute("href", "/");

        await user.click(screen.getByRole("button", { name: "Revoir le guide" }));
        expect(screen.getByRole("heading", { level: 1, name: "Bienvenue sur LocalLife" })).toBeInTheDocument();
    });

    it("avance tout seul en lecture automatique, et s'arrête en pause", () => {
        vi.useFakeTimers();
        renderGuide();

        act(() => {
            vi.advanceTimersByTime(GUIDE_STEP_DURATION_MS);
        });
        expect(screen.getByRole("heading", { level: 1, name: GUIDE_STEPS[1].title })).toBeInTheDocument();

        act(() => {
            screen.getByRole("button", { name: "Mettre en pause" }).click();
        });
        act(() => {
            vi.advanceTimersByTime(GUIDE_STEP_DURATION_MS * 2);
        });
        expect(screen.getByRole("heading", { level: 1, name: GUIDE_STEPS[1].title })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Lecture automatique" })).toBeInTheDocument();
    });
});
