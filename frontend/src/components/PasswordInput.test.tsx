import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import PasswordInput from "./PasswordInput";

describe("PasswordInput — voir le mot de passe saisi", () => {
    it("affiche puis masque la saisie, et la masque à l'envoi du formulaire", async () => {
        const user = userEvent.setup();
        render(
            <form onSubmit={(event) => event.preventDefault()}>
                <PasswordInput aria-label="Mot de passe" defaultValue="" />
            </form>,
        );
        const input = screen.getByLabelText("Mot de passe");
        await user.type(input, "secret123");
        expect(input).toHaveAttribute("type", "password");

        await user.click(screen.getByRole("button", { name: "Afficher le mot de passe" }));
        expect(input).toHaveAttribute("type", "text");
        expect(input).toHaveValue("secret123");
        expect(screen.getByRole("button", { name: "Masquer le mot de passe" })).toHaveAttribute("aria-pressed", "true");

        await user.click(screen.getByRole("button", { name: "Masquer le mot de passe" }));
        expect(input).toHaveAttribute("type", "password");

        await user.click(screen.getByRole("button", { name: "Afficher le mot de passe" }));
        fireEvent.submit(input.closest("form")!);
        expect(input).toHaveAttribute("type", "password");
    });
});
