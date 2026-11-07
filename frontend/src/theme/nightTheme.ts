import { useEffect } from "react";

/**
 * Thème « Night » : quand la carte Night est affichée, tout le site passe en mode nuit (fond violet nuit, accents
 * néon, carte assombrie) et le logo devient le bouc néon (`public/neiri-night.webp`). Le thème est porté par
 * `data-ll-theme="night"` sur `<html>` (styles dans `styles.css`, section « Thème Night ») ; l'icône de l'onglet et la
 * couleur de la barre du navigateur suivent. Tout revient au thème clair dès qu'on quitte la carte Night ou l'Explorer.
 */
export const NIGHT_LOGO_SRC = "/neiri-night.webp";

const NIGHT_THEME_COLOR = "#120726";

export function useNightTheme(enabled: boolean) {
    useEffect(() => {
        if (!enabled) {
            return;
        }
        const root = document.documentElement;
        root.dataset.llTheme = "night";

        const themeColor = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
        const previousThemeColor = themeColor?.content ?? null;
        themeColor?.setAttribute("content", NIGHT_THEME_COLOR);

        const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
        const previousIcon = icon ? { href: icon.getAttribute("href"), type: icon.getAttribute("type") } : null;
        if (icon) {
            icon.setAttribute("href", NIGHT_LOGO_SRC);
            icon.setAttribute("type", "image/webp");
        }

        return () => {
            delete root.dataset.llTheme;
            if (themeColor && previousThemeColor !== null) {
                themeColor.setAttribute("content", previousThemeColor);
            }
            if (icon && previousIcon) {
                if (previousIcon.href !== null) icon.setAttribute("href", previousIcon.href);
                if (previousIcon.type !== null) icon.setAttribute("type", previousIcon.type);
            }
        };
    }, [enabled]);
}
