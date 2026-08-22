import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const pageUrl = new URL("../app/page.tsx", import.meta.url);
const layoutUrl = new URL("../app/layout.tsx", import.meta.url);
const apiUrl = new URL("../app/lib/api.ts", import.meta.url);

test("contains the FastScan warehouse and client entry points", async () => {
  const [page, layout] = await Promise.all([
    readFile(pageUrl, "utf8"),
    readFile(layoutUrl, "utf8"),
  ]);

  assert.match(layout, /FastScan Warehouse Platform/);
  assert.match(page, /warehouseNav/);
  assert.match(page, /clientNav/);
  assert.match(page, /login\(/);
  assert.doesNotMatch(page, /device license|лицензи/i);
  assert.match(page, /Сохранить оригинальную упаковку этого заказа/);
  assert.match(page, /Сохранять обувные коробки/);
  assert.match(page, /Сохранять упаковку сумок/);
  assert.match(page, /Войти через Telegram/);
  assert.match(page, /fastscan_login/);
  assert.doesNotMatch(page, /Забыли пароль\?/);
  assert.match(page, /Telegram-бот не подключён к FastScan API/);
});

test("uses a configurable API URL", async () => {
  const api = await readFile(apiUrl, "utf8");

  assert.match(api, /NEXT_PUBLIC_API_URL/);
  assert.match(api, /http:\/\/localhost:8080/);
  assert.match(api, /Authorization/);
});
