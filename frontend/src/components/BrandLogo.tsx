/**
 * Logo Neiri : repère (épingle en dégradé orange → rose → violet avec le « N » et le point du « i ») suivi du logotype « NEiRI ».
 * Dessiné en SVG (mêmes tracés que `public/neiri-mark.svg` et `public/neiri-wordmark.svg`) pour rester net à
 * toutes les tailles sans dépendre d'une police. `tone="light"` sert sur un fond bleu nuit (menu, bandeaux).
 */
export function BrandMark({ className }: { className?: string }) {
    return (
        <svg aria-hidden="true" className={className ?? "neiri-mark"} focusable="false" viewBox="0 0 100 120">
            <defs>
                <linearGradient id="neiri-mark-gradient" x1="0.1" x2="0.75" y1="0.05" y2="1">
                    <stop offset="0" stopColor="#FBA91E" />
                    <stop offset="0.5" stopColor="#EC3B6E" />
                    <stop offset="1" stopColor="#7B2CB5" />
                </linearGradient>
            </defs>
            <path d="M50 116C38 101 6 80 6 47a44 44 0 0 1 88 0c0 33-32 54-44 69Z" fill="url(#neiri-mark-gradient)" />
            <path
                d="M30 66V32L60 64V45"
                fill="none"
                stroke="#FFFFFF"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="11.5"
            />
            <circle cx="60" cy="29.5" fill="#FFFFFF" r="6.6" />
        </svg>
    );
}

export function BrandWordmark({ tone = "dark" }: { tone?: "dark" | "light" }) {
    return (
        <svg aria-hidden="true" className="neiri-wordmark" focusable="false" viewBox="-2 -6 344 108">
            <g
                fill="none"
                stroke={tone === "light" ? "#FFFFFF" : "#1C2150"}
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="20"
            >
                <path d="M10 90V10L70 90V10" />
                <path d="M158 10H110V90H158M110 50H150" />
                <path d="M195 44V90" />
                <path d="M238 90V10H268a20 20 0 0 1 0 40H238M262 50L292 90" />
                <path d="M330 10V90" />
            </g>
            <circle cx="195" cy="12" fill="#FBA91E" r="12.5" />
        </svg>
    );
}

/** Repère du thème « Night » : le bouc néon à lunettes (`public/neiri-night.webp`). */
export function NightMark() {
    return <img alt="" aria-hidden="true" className="neiri-night-mark" src="/neiri-night.webp" />;
}

/** Nom du bouc néon, affiché sous « NEiRI » en thème « Night ». */
export const NIGHT_MASCOT_NAME = "The OutGoat";

/**
 * Repère + logotype. Le nom reste lisible par les lecteurs d'écran (texte masqué visuellement). `night` remplace le
 * repère par le bouc néon de la carte Night, avec le logotype en clair et « The OutGoat » en dessous.
 */
export function BrandLogo({ tone = "dark", night = false }: { tone?: "dark" | "light"; night?: boolean }) {
    if (night) {
        return (
            <span className="neiri-logo neiri-logo--light neiri-logo--night">
                <NightMark />
                <span className="neiri-night-text">
                    <BrandWordmark tone="light" />
                    <span aria-hidden="true" className="neiri-night-caption">{NIGHT_MASCOT_NAME}</span>
                </span>
                <span className="neiri-visually-hidden">Neiri Night, {NIGHT_MASCOT_NAME}</span>
            </span>
        );
    }
    return (
        <span className={`neiri-logo neiri-logo--${tone}`}>
            <BrandMark />
            <BrandWordmark tone={tone} />
            <span className="neiri-visually-hidden">Neiri</span>
        </span>
    );
}
