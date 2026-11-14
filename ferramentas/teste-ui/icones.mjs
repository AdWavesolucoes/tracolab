// Gera os ícones PNG da PWA a partir da marca (o mesmo desenho do favicon.svg), com o Chromium do
// Playwright. Roda a partir de ferramentas/teste-ui (onde está o playwright-core):
//
//   cd ferramentas/teste-ui && CHROME=/caminho/do/chrome node icones.mjs
import { chromium } from "playwright-core";
import { writeFileSync } from "node:fs";

const CHROME = process.env.CHROME;
if (!CHROME) { console.error("defina CHROME=/caminho/do/chrome"); process.exit(2); }
const marca = (tam, raio, margem) => `<!doctype html><html><body style="margin:0;background:transparent">
<svg xmlns="http://www.w3.org/2000/svg" width="${tam}" height="${tam}" viewBox="0 0 96 96">
  <rect x="${margem}" y="${margem}" width="${96 - 2 * margem}" height="${96 - 2 * margem}" rx="${raio}" fill="#17233A" stroke="#2B3B5A" stroke-width="2"/>
  <path d="M18 32 C26 18 40 18 48 32 S70 46 78 32" fill="none" stroke="#FF6B3D" stroke-width="7" stroke-linecap="round"/>
  <path d="M48 32 V62" fill="none" stroke="#F5F5F0" stroke-width="7" stroke-linecap="round"/>
  <circle cx="48" cy="75" r="5.5" fill="#FF6B3D"/>
</svg></body></html>`;

const nav = await chromium.launch({ executablePath: CHROME, args: ["--no-sandbox"] });
const p = await nav.newPage({ viewport: { width: 512, height: 512 }, deviceScaleFactor: 1 });
for (const [arquivo, tam, raio, margem] of [["icone-192.png", 192, 23, 1], ["icone-512.png", 512, 23, 1], ["icone-maskable-512.png", 512, 0, -20]]) {
  await p.setViewportSize({ width: tam, height: tam });
  await p.setContent(marca(tam, raio, margem));
  writeFileSync(new URL("../../web/" + arquivo, import.meta.url), await p.screenshot({ omitBackground: margem >= 0, clip: { x: 0, y: 0, width: tam, height: tam } }));
  console.log("ok", arquivo);
}
await nav.close();
