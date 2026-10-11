import { act, fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";
import { installControllableFetch } from "../../test/fetchMock";
import type { PendingRequest } from "../../test/fetchMock";
import ExplorerSearch from "./ExplorerSearch";
import type { CitySuggestion } from "./useCitySuggestions";

const LYON: CitySuggestion = { name: "Lyon", postalCodes: ["69001", "69009"], department: "69", latitude: 45.758, longitude: 4.835 };
const LYONS_LA_FORET: CitySuggestion = {
    name: "Lyons-la-Forêt",
    postalCodes: ["27480"],
    department: "27",
    latitude: 49.398,
    longitude: 1.476,
};

let requests: PendingRequest[];
let onSelectCity: Mock<(city: CitySuggestion, query: string) => void>;
let onSubmit: Mock<() => void>;

/** Barre contrôlée, comme dans App.tsx. */
function Harness() {
    const [value, setValue] = useState("");
    return (
        <ExplorerSearch
            isActive={false}
            isSearching={false}
            onChange={setValue}
            onClear={() => setValue("")}
            onSelectCity={onSelectCity}
            onSubmit={onSubmit}
            value={value}
        />
    );
}

beforeEach(() => {
    vi.useFakeTimers();
    requests = installControllableFetch();
    onSelectCity = vi.fn<(city: CitySuggestion, query: string) => void>();
    onSubmit = vi.fn<() => void>();
    render(<Harness />);
});

afterEach(() => {
    vi.useRealTimers();
});

const combobox = () => screen.getByRole("combobox", { name: "Rechercher une activité, un lieu ou une ville" });

async function type(text: string) {
    await act(async () => {
        fireEvent.change(combobox(), { target: { value: text } });
    });
}

async function wait(ms: number) {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(ms);
    });
}

async function respond(index: number, body: CitySuggestion[]) {
    await act(async () => {
        requests[index].respond(body);
        await vi.runAllTimersAsync();
    });
}

async function submit() {
    await act(async () => {
        fireEvent.submit(combobox().closest("form") as HTMLFormElement);
    });
}

async function showSuggestionsFor(text: string, body: CitySuggestion[]) {
    fireEvent.focus(combobox());
    await type(text);
    await wait(250);
    await respond(requests.length - 1, body);
}

describe("ExplorerSearch — suggestions de ville", () => {
    it("une suggestion de l'ancienne saisie, surlignée puis rendue obsolète par la frappe, n'est pas sélectionnable", async () => {
        await showSuggestionsFor("lyo", [LYONS_LA_FORET, LYON]);
        fireEvent.keyDown(combobox(), { key: "ArrowDown" });
        expect(screen.getByRole("option", { selected: true })).toHaveTextContent("Lyons-la-Forêt");

        await type("lyon");
        expect(screen.queryAllByRole("option")).toHaveLength(0);
        await submit();
        expect(onSelectCity).not.toHaveBeenCalled();
        expect(onSubmit).toHaveBeenCalledTimes(1);
    });

    it("réponse tardive de l'ancienne saisie : rien n'apparaît ni n'est sélectionnable ; la nouvelle réponse l'est", async () => {
        await showSuggestionsFor("lyo", [LYONS_LA_FORET]);
        await type("lyon");
        await wait(250);
        // Ancienne requête (déjà terminée) : on simule une seconde réponse tardive pour l'avant-dernière saisie.
        await type("lyons");
        await respond(1, [LYONS_LA_FORET]);
        expect(screen.queryAllByRole("option")).toHaveLength(0);
        expect(combobox()).toHaveAttribute("aria-expanded", "false");
        fireEvent.keyDown(combobox(), { key: "ArrowDown" });
        expect(combobox()).not.toHaveAttribute("aria-activedescendant");

        await wait(250);
        await respond(2, [LYON]);
        fireEvent.keyDown(combobox(), { key: "ArrowDown" });
        await submit();
        expect(onSelectCity).toHaveBeenCalledTimes(1);
        expect(onSelectCity).toHaveBeenCalledWith(LYON, "lyons");
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it("clavier : flèches, Entrée pour choisir, Échap pour fermer, frappe pour rouvrir", async () => {
        await showSuggestionsFor("lyo", [LYON, LYONS_LA_FORET]);
        expect(combobox()).toHaveAttribute("aria-expanded", "true");

        fireEvent.keyDown(combobox(), { key: "ArrowDown" });
        fireEvent.keyDown(combobox(), { key: "ArrowDown" });
        fireEvent.keyDown(combobox(), { key: "ArrowDown" });
        expect(screen.getByRole("option", { selected: true })).toHaveTextContent("Lyon");
        fireEvent.keyDown(combobox(), { key: "ArrowUp" });
        expect(screen.getByRole("option", { selected: true })).toHaveTextContent("Lyons-la-Forêt");

        fireEvent.keyDown(combobox(), { key: "Escape" });
        expect(combobox()).toHaveAttribute("aria-expanded", "false");
        expect(screen.queryByRole("listbox")).toBeNull();

        await type("lyon");
        await wait(250);
        await respond(1, [LYON]);
        fireEvent.keyDown(combobox(), { key: "ArrowDown" });
        await submit();
        expect(onSelectCity).toHaveBeenCalledWith(LYON, "lyon");
        expect(onSubmit).not.toHaveBeenCalled();
        expect(screen.queryByRole("listbox")).toBeNull();
    });

    it("accessibilité : combobox relié à la liste, option active annoncée", async () => {
        expect(combobox()).toHaveAttribute("aria-autocomplete", "list");
        expect(combobox()).toHaveAttribute("aria-expanded", "false");
        expect(combobox()).not.toHaveAttribute("aria-activedescendant");

        await showSuggestionsFor("lyo", [LYON, LYONS_LA_FORET]);
        const listbox = screen.getByRole("listbox", { name: "Villes proposées" });
        expect(combobox()).toHaveAttribute("aria-controls", listbox.id);
        expect(screen.getAllByRole("option")).toHaveLength(2);

        fireEvent.keyDown(combobox(), { key: "ArrowDown" });
        const active = combobox().getAttribute("aria-activedescendant");
        expect(active).toBeTruthy();
        expect(document.getElementById(active as string)).toHaveAttribute("aria-selected", "true");
    });

    it("souris / toucher : l'appui choisit la ville sans faire perdre le focus au champ", async () => {
        await showSuggestionsFor("lyo", [LYON, LYONS_LA_FORET]);
        combobox().focus();
        const option = screen.getAllByRole("option")[1];
        const mouseDown = fireEvent.mouseDown(option);
        fireEvent.mouseUp(option);
        fireEvent.click(option);

        expect(mouseDown).toBe(false); // preventDefault : le champ garde le focus
        expect(onSelectCity).toHaveBeenCalledTimes(1);
        expect(onSelectCity).toHaveBeenCalledWith(LYONS_LA_FORET, "lyo");
    });

    it("activation par clic seul (technologies d'assistance, sans mousedown) : la ville est choisie", async () => {
        await showSuggestionsFor("lyo", [LYON]);
        fireEvent.click(screen.getByRole("option"));
        expect(onSelectCity).toHaveBeenCalledWith(LYON, "lyo");
    });
});
