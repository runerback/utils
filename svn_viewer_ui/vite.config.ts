import { defineConfig } from "vite";
import preact from "@preact/preset-vite";
import monacoEditorPlugin from "vite-plugin-monaco-editor-esm";
import { resolve } from "path";

export default defineConfig({
  plugins: [
    preact(),
    monacoEditorPlugin({
      languageWorkers: ["json", "html", "typescript"],
      globalAPI: true,
      // 打包地址
      customDistPath: () => resolve(__dirname, "../dist/monaco-editor/"),
      // 路由前缀
      publicPath: "monaco-editor",
    }),
  ],
  optimizeDeps: {
    include: [
      `monaco-editor/esm/vs/language/json/json.worker`,
      `monaco-editor/esm/vs/language/css/css.worker`,
      `monaco-editor/esm/vs/language/html/html.worker`,
      `monaco-editor/esm/vs/language/typescript/ts.worker`,
      `monaco-editor/esm/vs/editor/editor.worker`,
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
