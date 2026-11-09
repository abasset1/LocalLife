import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BrandLogo } from "./BrandLogo";

describe("BrandLogo", () => {
    it("affiche « The OutGoat » sous Neiri en thème Night", () => {
        const { container } = render(<BrandLogo night tone="light" />);
        expect(container.querySelector(".neiri-night-caption")?.textContent).toBe("The OutGoat");
        expect(screen.getByText("Neiri Night, The OutGoat")).toBeTruthy();
    });

    it("ne l'affiche pas hors du thème Night", () => {
        const { container } = render(<BrandLogo tone="light" />);
        expect(container.textContent).not.toContain("OutGoat");
    });
});
