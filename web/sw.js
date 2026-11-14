"use strict";
// Service worker da PWA (ADR 0019). Só para a página abrir rápido e instalar na tela inicial:
// os arquivos próprios vêm da rede primeiro (uma versão nova nunca fica presa no cache) e do
// cache quando a rede falha; vendor/ e fontes, que têm versão no nome, vêm do cache primeiro.
// A API nunca passa pelo cache.
const CACHE = "tracolab-v1";

self.addEventListener("install", (ev) => { ev.waitUntil(self.skipWaiting()); });
self.addEventListener("activate", (ev) => {
  ev.waitUntil((async () => {
    for (const k of await caches.keys()) if (k !== CACHE) await caches.delete(k);
    await self.clients.claim();
  })());
});

self.addEventListener("fetch", (ev) => {
  const url = new URL(ev.request.url);
  if (ev.request.method !== "GET" || url.origin !== location.origin || url.pathname.includes("/api/")) return;
  const estavel = url.pathname.includes("/vendor/");
  ev.respondWith((async () => {
    const cache = await caches.open(CACHE);
    if (estavel) {
      const guardado = await cache.match(ev.request);
      if (guardado) return guardado;
    }
    try {
      const resp = await fetch(ev.request);
      if (resp.ok && resp.type === "basic") cache.put(ev.request, resp.clone());
      return resp;
    } catch (e) {
      return (await cache.match(ev.request)) || new Response("Sem conexão com o servidor.", { status: 503, headers: { "Content-Type": "text/plain; charset=utf-8" } });
    }
  })());
});
