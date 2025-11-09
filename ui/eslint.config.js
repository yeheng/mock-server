import js from "@eslint/js"
import vue from "eslint-plugin-vue"
import prettier from "eslint-config-prettier"

export default [
  js.configs.recommended,
  ...vue.configs["flat/essential"],
  prettier,
  {
    ignores: [
      "dist/**",
      "node_modules/**",
      "*.min.js",
      "*.min.css",
      ".env*"
    ]
  },
  {
    files: ["**/*.vue"],
    languageOptions: {
      parserOptions: {
        ecmaVersion: 2020,
        sourceType: "module"
      },
      globals: {
        console: "readonly",
        fetch: "readonly",
        URLSearchParams: "readonly",
        Response: "readonly",
        alert: "readonly"
      }
    },
    rules: {
      "vue/multi-word-component-names": "off",
      "vue/no-unused-vars": "error",
      "no-undef": "warn",
      "no-unused-vars": "error",
      "no-console": "off",
      "no-debugger": "warn"
    }
  },
  {
    files: ["**/*.js"],
    languageOptions: {
      ecmaVersion: 2020,
      sourceType: "module",
      globals: {
        console: "readonly",
        fetch: "readonly",
        URLSearchParams: "readonly",
        __dirname: "readonly",
        global: "readonly",
        window: "readonly",
        document: "readonly",
        alert: "readonly"
      }
    },
    rules: {
      "no-undef": "warn",
      "no-unused-vars": "error",
      "no-console": "off",
      "no-debugger": "warn"
    }
  }
]
