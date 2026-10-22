import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import AppRoutes from "../AppRoutes";

describe("Textes légaux", () => {
    it.each([
        ["/confidentialite", "Politique de confidentialité"],
        ["/conditions", "Conditions d'utilisation"],
        ["/mentions-legales", "Mentions légales"],
    ])("la route %s affiche « %s »", (path, title) => {
        render(<MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter>);

        expect(screen.getByRole("heading", { level: 1, name: title })).toBeInTheDocument();
        expect(screen.getByText("Document en cours de rédaction.")).toBeInTheDocument();
        expect(screen.getByText("Version du 22/10/2026")).toBeInTheDocument();
    });
});
