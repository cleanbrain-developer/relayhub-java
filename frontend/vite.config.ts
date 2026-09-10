import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Builds straight into Spring Boot's static resource directory — `mvn`/`gradle bootJar` then
// just needs `npm run build` to have run first (see the Dockerfile), no separate copy step.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static",
    emptyOutDir: true,
  },
  server: {
    // Local dev only: proxies API/ingress/actuator calls to a `./gradlew bootRun` instance
    // running on 8080, so `npm run dev` gets live reload without CORS friction.
    proxy: {
      "/api": "http://localhost:8080",
      "/ingress": "http://localhost:8080",
      "/actuator": "http://localhost:8080",
    },
  },
});
