import { defineConfig } from "vite";
import preact from "@preact/preset-vite";
import monacoEditorPlugin from "vite-plugin-monaco-editor-esm";
import { resolve } from "path";
import svgr from "vite-plugin-svgr";

export default defineConfig({
  plugins: [
    preact(),
    svgr(),
    monacoEditorPlugin({
      languageWorkers: ["json", "html", "typescript"],
      globalAPI: false,
      customDistPath: () => resolve(__dirname, "../dist/monaco-editor/"),
      publicPath: "monaco-editor",
    }),
  ],
  build: {
    chunkSizeWarningLimit: 2250,
    rollupOptions: {
      output: {
        chunkFileNames: "js/[name]-[hash].js",
        entryFileNames: "js/[name]-[hash].js",
        assetFileNames: "assets/[name]-[hash].[ext]",
        manualChunks: {
          "react-vendor": ["preact", "react", "react-dom"],
          "utils-vendor": ["@microsoft/signalr"],
          "ui-vendor": ["antd"],
          "editor-vendor": ["monaco-editor"],
          "markdown-vendor": ["lowlight", "react-markdown", "remark-gfm"],
        },
      },
      treeshake: {
        moduleSideEffects: false,
        propertyReadSideEffects: false,
        tryCatchDeoptimization: false,
      },
    },
  },
  optimizeDeps: {
    include: [
      `monaco-editor/esm/vs/language/json/json.worker`,
      `monaco-editor/esm/vs/language/css/css.worker`,
      `monaco-editor/esm/vs/language/html/html.worker`,
      `monaco-editor/esm/vs/language/typescript/ts.worker`,
      `monaco-editor/esm/vs/editor/editor.worker`,
      "preact",
      "react",
      "react-dom",
    ],
  },
  server: {
    port: parseInt(process.env["PORT"]!),
    proxy: {
      "/api/messages": {
        target: process.env["services__messages__http__0"],
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
        ws: true,
      },
      "/api/server": {
        target: `http://localhost:${process.env["SERVER_PORT"]}`,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/server/, ""),
      },
      "/api/uihelper": {
        target: process.env["services__uihelper__http__0"],
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/uihelper/, ""),
      },
    },
  },
});
