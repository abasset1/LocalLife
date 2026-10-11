import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import SearchResultBanner from "./SearchResultBanner";
import type { AppliedSearch, TextSearchStatus } from "./SearchResultBanner";

const TEXT: AppliedSearch = { kind: "text", query: "fete" };

function success(offset: number, count: number, total = 45, withoutLocation = 0): TextSearchStatus {
    return { status: "success", total, offset, count, withoutLocation };
}

function renderBanner(textStatus: TextSearchStatus | null, overrides: Partial<Parameters<typeof SearchResultBanner>[0]> = {}) {
    const props = {
        search: TEXT,
        resultCount: 0,
        isLoadingActivities: false,
        textStatus,
        hasActiveFilters: false,
        onClear: vi.fn(),
        onRetry: vi.fn(),
        onPreviousPage: vi.fn(),
        onNextPage: vi.fn(),
        ...overrides,
    };
    render(<SearchResultBanner {...props} />);
    return props;
}

/** Bandeau piloté comme dans App : un clic de page passe en chargement, le test fournit la réponse. */
function Harness({ initial }: { initial: TextSearchStatus }) {
    const [status, setStatus] = useState<TextSearchStatus>(initial);
    harness.set = setStatus;
    return (
        <SearchResultBanner
            hasActiveFilters={false}
            isLoadingActivities={false}
            onClear={() => undefined}
            onNextPage={() => setStatus({ status: "loading" })}
            onPreviousPage={() => setStatus({ status: "loading" })}
            onRetry={() => undefined}
            resultCount={0}
            search={TEXT}
            textStatus={status}
        />
    );
}
const harness: { set: (status: TextSearchStatus) => void } = { set: () => undefined };

describe("SearchResultBanner — états et accessibilité", () => {
    it("chargement : annoncé en role=status, effaçable", () => {
        renderBanner({ status: "loading" });
        expect(screen.getByRole("status")).toHaveTextContent("Recherche de « fete »…");
        expect(screen.getByRole("button", { name: "Effacer ✕" })).toBeInTheDocument();
    });

    it("erreur : annoncée en role=alert, avec Réessayer", async () => {
        const props = renderBanner({ status: "error", message: "Service indisponible" });
        expect(screen.getByRole("alert")).toHaveTextContent("La recherche de « fete » a échoué : Service indisponible");
        await userEvent.click(screen.getByRole("button", { name: "Réessayer" }));
        expect(props.onRetry).toHaveBeenCalledOnce();
    });

    it("aucun résultat : conseil selon les filtres actifs", () => {
        renderBanner(success(0, 0, 0), { hasActiveFilters: true });
        expect(screen.getByRole("status")).toHaveTextContent("Aucun résultat pour « fete ». Essaie un autre mot ou retire des filtres.");
    });

    it("texte refusé avant appel", () => {
        renderBanner({ status: "invalid", message: "Saisis au moins 2 caractères." });
        expect(screen.getByRole("status")).toHaveTextContent("« fete » : Saisis au moins 2 caractères.");
    });

    it("pagination : navigation nommée, bornes désactivées, résultats sans lieu signalés", () => {
        renderBanner(success(0, 20, 45, 2));
        expect(screen.getByRole("navigation", { name: "Pages de résultats" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "‹ Précédents" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "Suivants ›" })).toBeEnabled();
        expect(screen.getByRole("status")).toHaveTextContent("1–20 sur 45");
        expect(screen.getByRole("status")).toHaveTextContent("2 sans lieu précis, dans la liste seulement");
    });

    it("une seule page : pas de pagination", () => {
        renderBanner(success(0, 3, 3));
        expect(screen.queryByRole("navigation")).toBeNull();
        expect(screen.getByRole("status")).toHaveTextContent("3 résultats pour « fete »");
    });

    it("ville choisie : nombre d'activités de la zone", () => {
        renderBanner(null, { search: { kind: "city", city: "Lyon (69003)" }, resultCount: 4 });
        expect(screen.getByRole("status")).toHaveTextContent("📍 Lyon (69003) — 4 activités sur la carte");
    });
});

describe("SearchResultBanner — focus clavier après un changement de page", () => {
    it("« Suivants » au clavier : le focus revient sur « Suivants » une fois la page affichée", async () => {
        const user = userEvent.setup();
        render(<Harness initial={success(0, 20)} />);
        screen.getByRole("button", { name: "Suivants ›" }).focus();
        await user.keyboard("{Enter}");
        expect(screen.queryByRole("button", { name: "Suivants ›" })).toBeNull();

        await act(async () => harness.set(success(20, 20)));
        expect(screen.getByRole("button", { name: "Suivants ›" })).toHaveFocus();
    });

    it("dernière page : « Suivants » désactivé, le focus va sur « Précédents »", async () => {
        const user = userEvent.setup();
        render(<Harness initial={success(20, 20)} />);
        screen.getByRole("button", { name: "Suivants ›" }).focus();
        await user.keyboard("{Enter}");

        await act(async () => harness.set(success(40, 5)));
        expect(screen.getByRole("button", { name: "‹ Précédents" })).toHaveFocus();
    });

    it("page en erreur : le focus va sur « Réessayer »", async () => {
        const user = userEvent.setup();
        render(<Harness initial={success(0, 20)} />);
        screen.getByRole("button", { name: "Suivants ›" }).focus();
        await user.keyboard("{Enter}");

        await act(async () => harness.set({ status: "error", message: "Service indisponible" }));
        expect(screen.getByRole("button", { name: "Réessayer" })).toHaveFocus();
    });
});
