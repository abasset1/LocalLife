// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

module.exports = defineConfig([
  expoConfig,
  {
    ignores: ['dist/*', '.expo/*'],
  },
  {
    // Les hooks de chargement repassent à « loading » au début de leur effet :
    // signalé sans bloquer, le temps de les revoir.
    rules: { 'react-hooks/set-state-in-effect': 'warn' },
  },
  {
    // Carte Google native : gratuite sans Map ID (SKU « Maps SDK »), payante
    // avec (SKU « Dynamic Maps »). Le Map ID ne doit donc jamais être passé.
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='googleMapId']",
          message: 'Pas de Map ID : la carte passerait au tarif payant « Dynamic Maps » de Google.',
        },
      ],
    },
  },
]);
