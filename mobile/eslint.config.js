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
]);
