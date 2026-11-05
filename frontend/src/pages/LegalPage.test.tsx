import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import AppRoutes from "../AppRoutes";
import { splitPlaceholders } from "../legal/legalContent";

describe("Textes légaux", () => {
    it.each([
        ["/confidentialite", "Politique de confidentialité", "1. Responsable du traitement"],
        ["/conditions", "Conditions d'utilisation", "1. Objet"],
        ["/mentions-legales", "Mentions légales", "1. Éditeur du service"],
    ])("la route %s affiche « %s » et son contenu", (path, title, firstSection) => {
        render(<MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter>);

        expect(screen.getByRole("heading", { level: 1, name: title })).toBeInTheDocument();
        expect(screen.getByRole("heading", { level: 2, name: firstSection })).toBeInTheDocument();
        expect(screen.queryByText("Document en cours de rédaction.")).toBeNull();
        expect(screen.getByText("Version du 05/11/2026")).toBeInTheDocument();
    });

    it("surligne les informations restant à compléter par l'éditeur", () => {
        render(<MemoryRouter initialEntries={["/mentions-legales"]}><AppRoutes /></MemoryRouter>);

        const marks = document.querySelectorAll("mark.legal-placeholder");
        expect(marks.length).toBeGreaterThan(0);
        marks.forEach((mark) => expect(mark.textContent).toMatch(/^\[À compléter/));
    });

    it("isole les champs à compléter dans un texte", () => {
        expect(splitPlaceholders("E-mail : [À compléter : adresse] (contact).")).toEqual([
            { text: "E-mail : ", placeholder: false },
            { text: "[À compléter : adresse]", placeholder: true },
            { text: " (contact).", placeholder: false },
        ]);
    });
});
