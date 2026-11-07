import { renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { NIGHT_LOGO_SRC, useNightTheme } from "./nightTheme";

afterEach(() => {
    document.head.innerHTML = "";
});

describe("useNightTheme", () => {
    it("change l'icône de l'onglet et la couleur du navigateur, puis les rétablit", () => {
        document.head.innerHTML = '<meta name="theme-color" content="#1c2150" />'
            + '<link rel="icon" type="image/svg+xml" href="/favicon.svg" />';
        const icon = document.querySelector('link[rel="icon"]')!;
        const color = document.querySelector('meta[name="theme-color"]')!;

        const { rerender } = renderHook(({ on }) => useNightTheme(on), { initialProps: { on: true } });
        expect(document.documentElement.dataset.llTheme).toBe("night");
        expect(icon.getAttribute("href")).toBe(NIGHT_LOGO_SRC);
        expect(color.getAttribute("content")).toBe("#120726");

        rerender({ on: false });
        expect(document.documentElement.dataset.llTheme).toBeUndefined();
        expect(icon.getAttribute("href")).toBe("/favicon.svg");
        expect(icon.getAttribute("type")).toBe("image/svg+xml");
        expect(color.getAttribute("content")).toBe("#1c2150");
    });
});
