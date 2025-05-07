import { defineConfig } from "vite";
import preact from "@preact/preset-vite";

export default defineConfig({
  plugins: [preact()],
  server: {
    port: parseInt(process.env["UI_PORT"]!),
    proxy: {
      "/api/messages": {
        target: process.env["services__messages__http__0"],
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
        ws: true,
      },
      "/api/server": {
        target: process.env["services__server__http__0"],
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
