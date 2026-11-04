/** Plus grand côté d'une photo envoyée : assez pour une fiche, léger à charger sur la carte. */
const MAX_SIDE = 1280;

/**
 * Photo choisie par le professionnel, réduite et réencodée en JPEG dans le navigateur avant l'envoi (une photo de
 * téléphone pèse souvent plusieurs Mo). Renvoie une adresse `data:image/jpeg;base64,…`.
 */
export async function resizeImage(file: File): Promise<string> {
    if (!file.type.startsWith("image/")) {
        throw new Error("Choisis une image (JPEG, PNG ou WebP).");
    }
    const source = await new Promise<HTMLImageElement>((resolve, reject) => {
        const url = URL.createObjectURL(file);
        const image = new Image();
        image.onload = () => {
            URL.revokeObjectURL(url);
            resolve(image);
        };
        image.onerror = () => {
            URL.revokeObjectURL(url);
            reject(new Error("Image illisible."));
        };
        image.src = url;
    });
    const scale = Math.min(1, MAX_SIDE / Math.max(source.naturalWidth, source.naturalHeight));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(source.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(source.naturalHeight * scale));
    const context = canvas.getContext("2d");
    if (!context) {
        throw new Error("Image illisible.");
    }
    // Fond blanc : une image PNG transparente ne devient pas noire en JPEG.
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(source, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL("image/jpeg", 0.85);
}
