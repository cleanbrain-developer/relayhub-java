// ESLint flat config (self-review finding, 2026-10-02: frontend had no lint tooling at all).
// Deliberately minimal and non-opinionated — type-checking already lives in `tsc --noEmit`
// (see package.json's "build" script and ci.yml's frontend-test job), so this only adds the
// checks that catch real bugs tsc can't (React Hooks rule violations) and dead-code hygiene
// (no-unused-vars), not style/formatting rules this project has no prior convention for.
import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";

export default tseslint.config(
  { ignores: ["node_modules", "../src/main/resources/static", "dist"] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "react-refresh/only-export-components": ["warn", { allowConstantExport: true }],
      // Named-but-unused catch/destructure params (e.g. a deliberately-ignored error) are a
      // common, legitimate pattern in this codebase's existing catch blocks — only flag unused
      // vars that aren't prefixed with _, not every unused binding.
      "@typescript-eslint/no-unused-vars": ["warn", { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }],
      // allowTernary: LivePage.tsx uses `cond ? doA() : doB();` as a statement-level conditional
      // call in several places (an established, working pattern there, not a bug) — the base rule
      // would otherwise flag every one of those as an "unused expression".
      "@typescript-eslint/no-unused-expressions": ["error", { allowTernary: true }],
    },
  },
);
