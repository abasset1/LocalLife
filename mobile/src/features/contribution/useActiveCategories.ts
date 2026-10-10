import { useEffect, useState } from 'react';

import { ApiError, listCategories } from '@/api';
import type { Category } from '@/api';

/** Catégories actives du référentiel, triées par libellé (choix du formulaire de contribution). */
export function useActiveCategories(): { categories: Category[]; error: string | null } {
  const [categories, setCategories] = useState<Category[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listCategories({ signal: controller.signal })
      .then((all) =>
        setCategories(all.filter((c) => c.active).sort((a, b) => a.label.localeCompare(b.label, 'fr'))),
      )
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setError(e instanceof ApiError ? e.message : 'Impossible de charger les catégories.');
      });
    return () => controller.abort();
  }, []);

  return { categories, error };
}
