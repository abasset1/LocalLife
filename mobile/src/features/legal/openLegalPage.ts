import * as WebBrowser from 'expo-web-browser';

import { apiUrl } from '@/config/env';

import { LEGAL_PAGES, siteWebUrl, type LegalPage } from './legalLinks';

/** Ouvre un texte légal dans le navigateur intégré. Renvoie un message d'erreur affichable, ou `null`. */
export async function openLegalPage(page: LegalPage): Promise<string | null> {
  const url = siteWebUrl(apiUrl, LEGAL_PAGES[page].path);
  if (!url) return "Adresse du site introuvable dans la configuration de l'application.";
  try {
    await WebBrowser.openBrowserAsync(url);
    return null;
  } catch {
    return `Impossible d'ouvrir la page « ${LEGAL_PAGES[page].label} ».`;
  }
}
