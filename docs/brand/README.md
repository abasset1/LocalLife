# Identité Neiri

LocalLife devient **Neiri** — signature : **« Your world, closer. »** (« closer. » en orange).
Planche de référence : [`neiri-planche-identite.png`](neiri-planche-identite.png).

## Logo

| Fichier | Usage |
| --- | --- |
| `frontend/public/neiri-mark.svg` (= `favicon.svg`) | Repère : épingle en dégradé avec le « N » et le point du « i » |
| `frontend/public/neiri-wordmark.svg` | Logotype « NEiRI » bleu nuit, point du i orange |
| `frontend/src/components/BrandLogo.tsx` | Mêmes tracés en composant React (`BrandLogo`, `BrandMark`, `BrandWordmark`) |
| `mobile/src/components/brand-mark.tsx` | Logo de l'application (images `neiri-mark.png`, `neiri-wordmark(-light).png`) |
| `mobile/assets/images/icon.png`, `android-icon-*`, `splash-icon.png`, `favicon.png` | Icônes et écran de démarrage |
| `frontend/public/apple-touch-icon.png`, `icon-512.png` | Icônes web (iOS, partage) |

Les PNG sont générés depuis les SVG (rendu Chromium) : modifier d'abord les SVG, puis régénérer.

## Couleurs

| Rôle | Couleur |
| --- | --- |
| Orange (début du dégradé, accent de la signature) | `#FBA91E` |
| Rose (milieu du dégradé) | `#EC3B6E` |
| Rose des boutons (texte blanc, contraste AA) | `#D6295F` |
| Violet (fin du dégradé) | `#7B2CB5` |
| Bleu nuit (texte, menus, en-têtes) | `#1C2150` |
| Lavande | `#8C84D8` |
| Lavande pâle (fonds) | `#E4E0F7` / `#EFEDFB` |

Dégradé de marque : `linear-gradient(135deg, #FBA91E 0%, #EC3B6E 50%, #7B2CB5 100%)`.
Site : variables `--ll-*` de `frontend/src/styles.css` (le préfixe historique est conservé). Application :
`Brand` et `Colors` de `mobile/src/constants/theme.ts`.

## Ce qui garde l'ancien nom (identifiants techniques)

Pour ne casser ni l'API, ni les comptes, ni les installations existantes : packages Java `com.locallife`,
rôle `LOCALLIFE` (affiché « Neiri »), champ `suspendedByLocalLife`, clés de stockage `locallife.*` (session,
préférences), identifiants d'application `com.locallife.mobile`, slug Expo `locallife-mobile` et schéma
d'URL `locallife`. Les changer est une décision à part (nouvelle fiche sur les stores, déconnexion des
utilisateurs).
