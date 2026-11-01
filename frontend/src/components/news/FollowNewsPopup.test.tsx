import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import FollowNewsPopup from "./FollowNewsPopup";
import { followNewsDismissKey, followNewsPopupKey } from "./newsStorage";

afterEach(() => {
    window.localStorage.clear();
});

function renderPopup(props: { userId: number | null; canFollow: boolean | null; blocked?: boolean }) {
    return render(
        <MemoryRouter>
            <FollowNewsPopup blocked={props.blocked ?? false} canFollow={props.canFollow} userId={props.userId} />
        </MemoryRouter>,
    );
}

describe("FollowNewsPopup — popup de nouveauté, une seule fois", () => {
    it("s'affiche une fois à un membre gratuit, puis plus jamais pour ce compte", async () => {
        const user = userEvent.setup();
        const { unmount } = renderPopup({ userId: 7, canFollow: false });
        const dialog = screen.getByRole("dialog", { name: "Suis tes activités et tes lieux préférés" });
        expect(dialog).toHaveTextContent("Premium est gratuit pour le moment");
        expect(window.localStorage.getItem(followNewsPopupKey(7))).toBe("1");

        await user.click(screen.getByRole("button", { name: "Plus tard" }));
        expect(screen.queryByRole("dialog")).toBeNull();
        // « Plus tard » laisse le bandeau de la carte comme rappel.
        expect(window.localStorage.getItem(followNewsDismissKey(7))).toBeNull();

        unmount();
        renderPopup({ userId: 7, canFollow: false });
        expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("« Découvrir » mène à la page Nouveautés et ferme aussi le bandeau", async () => {
        const user = userEvent.setup();
        renderPopup({ userId: 7, canFollow: false });
        const link = screen.getByRole("link", { name: "Découvrir" });
        expect(link).toHaveAttribute("href", "/nouveautes");
        await user.click(link);
        expect(window.localStorage.getItem(followNewsDismissKey(7))).toBe("1");
    });

    it("Échap la ferme", async () => {
        const user = userEvent.setup();
        renderPopup({ userId: 7, canFollow: false });
        await user.keyboard("{Escape}");
        expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("attend qu'aucune autre fenêtre ne soit ouverte, et ne s'affiche ni aux visiteurs ni aux membres Premium", () => {
        const blocked = renderPopup({ userId: 7, canFollow: false, blocked: true });
        expect(screen.queryByRole("dialog")).toBeNull();
        expect(window.localStorage.getItem(followNewsPopupKey(7))).toBeNull();
        blocked.unmount();
        const visitor = renderPopup({ userId: null, canFollow: false });
        expect(screen.queryByRole("dialog")).toBeNull();
        visitor.unmount();
        renderPopup({ userId: 7, canFollow: true });
        expect(screen.queryByRole("dialog")).toBeNull();
    });
});
