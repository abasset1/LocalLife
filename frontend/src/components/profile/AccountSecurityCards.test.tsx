import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PasswordCard } from "./AccountSecurityCards";

describe("PasswordCard — voir les mots de passe saisis", () => {
    it("propose « Afficher » sur le mot de passe actuel et sur le nouveau", async () => {
        const user = userEvent.setup();
        render(<PasswordCard hasPassword />);

        const current = screen.getByLabelText("Mot de passe actuel");
        const next = screen.getByLabelText(/Nouveau mot de passe/);
        const toggles = screen.getAllByRole("button", { name: "Afficher le mot de passe" });
        expect(toggles).toHaveLength(2);

        await user.click(toggles[1]);
        expect(next).toHaveAttribute("type", "text");
        expect(current).toHaveAttribute("type", "password");
    });

    it("sans mot de passe (compte Google), seul le nouveau mot de passe a le bouton", () => {
        render(<PasswordCard hasPassword={false} />);
        expect(screen.getAllByRole("button", { name: "Afficher le mot de passe" })).toHaveLength(1);
    });
});
