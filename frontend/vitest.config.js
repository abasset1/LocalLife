import { defineConfig, mergeConfig } from "vitest/config";
import viteConfig from "./vite.config.js";

// Tests du frontend (Vitest + jsdom). Configuration distincte de vite.config.js : le build n'en dépend pas.
export default mergeConfig(
    viteConfig,
    defineConfig({
        test: {
            environment: "jsdom",
            include: ["src/**/*.test.{ts,tsx}"],
            restoreMocks: true,
            setupFiles: ["./src/test/setup.ts"],
        },
    }),
);
