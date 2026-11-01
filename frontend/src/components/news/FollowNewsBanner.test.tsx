import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import FollowNewsBanner, { followNewsDismissKey } from "./FollowNewsBanner";

afterEach(() => {
    window.localStorage.clear();
});

function renderBanner(userId: number | null, canFollow: boolean | null) {
    return render(<MemoryRouter><FollowNewsBanner canFollow={canFollow} userId={userId} /></MemoryRouter>);
}

describe("FollowNewsBanner — nouveauté pour les membres gratuits", () => {
    it("s'affiche pour un membre gratuit et mène à la page Nouveautés", () => {
        renderBanner(7, false);
        expect(screen.getByRole("complementary", { name: "Nouveauté" })).toHaveTextContent("Premium est gratuit pour le moment");
        expect(screen.getByRole("link", { name: "Découvrir" })).toHaveAttribute("href", "/nouveautes");
    });

    it("ne s'affiche ni à un visiteur, ni à un membre Premium ou Pro, ni tant que l'offre n'est pas connue", () => {
        const { unmount } = renderBanner(null, false);
        expect(screen.queryByRole("complementary")).toBeNull();
        unmount();
        const premium = renderBanner(7, true);
        expect(screen.queryByRole("complementary")).toBeNull();
        premium.unmount();
        renderBanner(7, null);
        expect(screen.queryByRole("complementary")).toBeNull();
    });

    it("une fois fermé, ne revient plus pour ce compte", async () => {
        const user = userEvent.setup();
        const { unmount } = renderBanner(7, false);
        await user.click(screen.getByRole("button", { name: "Fermer le bandeau" }));
        expect(screen.queryByRole("complementary")).toBeNull();
        expect(window.localStorage.getItem(followNewsDismissKey(7))).toBe("1");
        unmount();
        renderBanner(7, false);
        expect(screen.queryByRole("complementary")).toBeNull();
        // Un autre compte sur le même appareil le voit toujours.
        renderBanner(8, false);
        expect(screen.getByRole("complementary", { name: "Nouveauté" })).toBeInTheDocument();
    });
});
