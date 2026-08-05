"use client";

import { FormEvent, useEffect, useState } from "react";
import { apiRequest, login, Role, Session } from "./lib/api";

type View =
  | "overview"
  | "clients"
  | "orders"
  | "boxes"
  | "batches"
  | "manifests"
  | "notifications"
  | "audit"
  | "settings";

type ClientProfile = {
  client_code: string;
  email: string;
  first_name: string;
  last_name: string;
  phone: string | null;
  address: string | null;
  email_notifications: boolean;
  push_notifications: boolean;
};

type PlatformSettings = {
  prefix: string;
  warehouse: string;
  country: string;
};

type StaffUser = {
  id: string;
  login: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
};

type StaffClient = {
  guid: string;
  client_code: string;
  first_name: string;
  last_name: string;
  full_name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  package_shoes: number;
  bag_package: number;
  orders_count: number;
};

type BatchSummary = {
  guid: string;
  partial_code: string;
  partial_name: string;
  warehouse_name: string;
  boxCount: number;
  gross_weight: number;
  status: number;
};

type ManifestOrder = {
  guid: string;
  tracking_number: string;
  client_code: string | null;
  client_name: string | null;
  client_first_name: string | null;
  client_last_name: string | null;
  client_email: string | null;
  client_phone: string | null;
  client_address: string | null;
  composition: string | null;
  description: string | null;
  declared_value: number;
  currency: string | null;
  gross_weight: number;
  box_tracking_code: string;
};

type ManifestRecord = {
  guid: string;
  manifest_code: string;
  partial_guid: string;
  partial_code: string;
  partial_name: string;
  warehouse_name: string;
  country: string;
  box_count: number;
  order_count: number;
  gross_weight: number;
  created_by: { login: string; role: Role };
  created_at: string;
  orders: ManifestOrder[];
};

const warehouseNav: Array<[View, string, string]> = [
  ["overview", "Обзор", "⌂"],
  ["clients", "Клиенты", "◎"],
  ["orders", "Заказы", "▤"],
  ["boxes", "Коробки", "□"],
  ["batches", "Партии", "≡"],
  ["manifests", "Манифесты", "◇"],
  ["notifications", "Оповещения", "●"],
  ["audit", "Журнал", "↻"],
  ["settings", "Настройки", "⚙"],
];

const workerNav: Array<[View, string, string]> = [
  ["overview", "Обзор", "⌂"],
  ["clients", "Клиенты", "◎"],
  ["orders", "Заказы", "▤"],
  ["boxes", "Коробки", "□"],
  ["batches", "Партии", "≡"],
  ["manifests", "Манифесты", "◇"],
];

const clientNav: Array<[View, string, string]> = [
  ["overview", "Мой кабинет", "⌂"],
  ["orders", "Мои заказы", "▤"],
  ["notifications", "Оповещения", "●"],
  ["settings", "Профиль", "⚙"],
];

const demoOrders = [
  { tracking: "1Z84A77E039218", client: "KG482731", status: "На складе", weight: "2,40 кг", time: "10:42" },
  { tracking: "CA912770346ES", client: "KG105824", status: "Упакован", weight: "6,85 кг", time: "09:18" },
  { tracking: "003404349102", client: "FS793160", status: "В партии", weight: "4,20 кг", time: "Вчера" },
  { tracking: "LX274910583CN", client: "KG482731", status: "На складе", weight: "1,10 кг", time: "Вчера" },
];

const demoClients = [
  { code: "KG482731", name: "Anna Petrova", orders: 12, weight: "36,4 кг", state: "Активен" },
  { code: "KG105824", name: "Miguel Santos", orders: 8, weight: "21,8 кг", state: "Активен" },
  { code: "FS793160", name: "Olena Koval", orders: 4, weight: "9,2 кг", state: "Новый" },
];

export default function FastScanWeb() {
  const [session, setSession] = useState<Session | null>(null);
  const [demo, setDemo] = useState(false);
  const [view, setView] = useState<View>("overview");
  const [menuOpen, setMenuOpen] = useState(false);
  const [loginMode, setLoginMode] = useState<"warehouse" | "client">("warehouse");
  const [loginValue, setLoginValue] = useState("admin");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [platform, setPlatform] = useState<PlatformSettings>({ prefix: "KG", warehouse: "BMG", country: "España" });
  const [profile, setProfile] = useState<ClientProfile>({
    client_code: "",
    email: "",
    first_name: "",
    last_name: "",
    phone: "",
    address: "",
    email_notifications: true,
    push_notifications: true,
  });
  const [notice, setNotice] = useState("");

  useEffect(() => {
    const stored = sessionStorage.getItem("fastscan-session");
    if (stored) {
      queueMicrotask(() => setSession(JSON.parse(stored) as Session));
    }
  }, []);

  useEffect(() => {
    if (!session) return;
    if (session.role === "CLIENT") {
      apiRequest<ClientProfile>("/api/client/profile", {}, session)
        .then(setProfile)
        .catch((exception) => setNotice(exception instanceof Error ? exception.message : "Не удалось загрузить профиль"));
      return;
    }
    if (session.role === "ADMIN") {
      apiRequest<PlatformSettings>("/api/admin/client-code-settings", {}, session)
        .then(setPlatform)
        .catch((exception) => setNotice(exception instanceof Error ? exception.message : "Не удалось загрузить настройки"));
    }
  }, [session]);

  const role: Role = demo ? "ADMIN" : session?.role || "ADMIN";
  const nav = role === "CLIENT" ? clientNav : role === "WORKER" ? workerNav : warehouseNav;

  async function submitLogin(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await login(loginMode, loginValue, password);
      const next = {
        accessToken: result.accessToken,
        refreshToken: result.refreshToken,
        role: result.role,
      };
      sessionStorage.setItem("fastscan-session", JSON.stringify(next));
      setSession(next);
      setView("overview");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Не удалось войти");
    } finally {
      setBusy(false);
    }
  }

  function openDemo() {
    setDemo(true);
    setView("overview");
  }

  function logout() {
    sessionStorage.removeItem("fastscan-session");
    setSession(null);
    setDemo(false);
  }

  if (!session && !demo) {
    return (
      <main className="login-shell">
        <section className="brand-panel">
          <div className="brand-mark"><span>F</span></div>
          <p className="eyebrow">WAREHOUSE OPERATIONS</p>
          <h1>FastScan</h1>
          <p className="brand-copy">
            Приёмка, упаковка и движение отправлений — в одном рабочем пространстве.
          </p>
          <div className="signal-card">
            <span className="signal-dot" />
            <div><strong>Система готова</strong><small>API и складские модули доступны</small></div>
          </div>
          <p className="version-note">Warehouse Platform · Web 0.1</p>
        </section>
        <section className="login-panel">
          <form className="login-card" onSubmit={submitLogin}>
            <p className="eyebrow dark">БЕЗОПАСНЫЙ ДОСТУП</p>
            <h2>Вход в систему</h2>
            <p className="muted">Выберите рабочую область и введите данные.</p>
            <div className="mode-switch" role="tablist" aria-label="Тип кабинета">
              <button type="button" className={loginMode === "warehouse" ? "active" : ""} onClick={() => setLoginMode("warehouse")}>Склад</button>
              <button type="button" className={loginMode === "client" ? "active" : ""} onClick={() => setLoginMode("client")}>Клиент</button>
            </div>
            <label>
              <span>{loginMode === "client" ? "Email" : "Логин"}</span>
              <input value={loginValue} onChange={(event) => setLoginValue(event.target.value)} autoComplete="username" />
            </label>
            <label>
              <span>Пароль</span>
              <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" />
            </label>
            {error && <p className="form-error">{error}</p>}
            <button className="primary-button" disabled={busy}>{busy ? "Проверка…" : "Войти →"}</button>
            <button className="demo-button" type="button" onClick={openDemo}>Открыть демо-интерфейс</button>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <aside className={menuOpen ? "sidebar open" : "sidebar"}>
        <div className="sidebar-brand">
          <div className="brand-mark small"><span>F</span></div>
          <div><strong>FastScan</strong><small>Warehouse Platform</small></div>
        </div>
        <nav>
          {nav.map(([key, label, icon]) => (
            <button key={key} className={view === key ? "active" : ""} onClick={() => { setView(key); setMenuOpen(false); }}>
              <span>{icon}</span>{label}
            </button>
          ))}
        </nav>
        <div className="sidebar-foot">
          <span className="signal-dot" /> API подключён
          <button onClick={logout}>Выйти</button>
        </div>
      </aside>
      <section className="workspace">
        <header className="topbar">
          <button className="menu-button" onClick={() => setMenuOpen(!menuOpen)} aria-label="Открыть меню">☰</button>
          <div>
            <p className="eyebrow dark">{role === "CLIENT" ? "ЛИЧНЫЙ КАБИНЕТ" : "ОПЕРАЦИОННЫЙ ЦЕНТР"}</p>
            <h2>{nav.find(([key]) => key === view)?.[1]}</h2>
          </div>
          <div className="user-chip">
            <span>{role.slice(0, 1)}</span>
            <div><strong>{demo ? "Demo User" : loginValue}</strong><small>{role}</small></div>
          </div>
        </header>
        <div className="content">
          {view === "overview" && <Overview client={role === "CLIENT"} />}
          {view === "clients" && (session && (role === "ADMIN" || role === "WORKER") ? <StaffClients session={session} /> : <Clients />)}
          {view === "orders" && <Orders client={role === "CLIENT"} />}
          {view === "boxes" && <Boxes />}
          {view === "batches" && <Batches />}
          {view === "manifests" && <Manifests session={session} />}
          {view === "notifications" && <Notifications client={role === "CLIENT"} />}
          {view === "audit" && <Audit />}
          {view === "settings" && (
            <Settings
              client={role === "CLIENT"}
              platform={platform}
              setPlatform={setPlatform}
              profile={profile}
              setProfile={setProfile}
              session={session}
              demo={demo}
              notice={notice}
              save={async () => {
                if (!session || demo) { setNotice("Демо: настройка сохранена локально"); return; }
                setNotice("");
                try {
                  if (role === "CLIENT") {
                    const updated = await apiRequest<ClientProfile>("/api/client/profile", {
                      method: "PUT",
                      body: JSON.stringify({
                        first_name: profile.first_name,
                        last_name: profile.last_name,
                        phone: profile.phone,
                        address: profile.address,
                        email_notifications: profile.email_notifications,
                        push_notifications: profile.push_notifications,
                      }),
                    }, session);
                    setProfile(updated);
                    setNotice("Профиль сохранён. Код клиента не изменялся.");
                  } else {
                    const updated = await apiRequest<PlatformSettings>("/api/admin/client-code-settings", {
                      method: "PUT",
                      body: JSON.stringify(platform),
                    }, session);
                    setPlatform(updated);
                    setNotice("Настройки склада сохранены");
                  }
                } catch (exception) {
                  setNotice(exception instanceof Error ? exception.message : "Не удалось сохранить настройки");
                }
              }}
            />
          )}
        </div>
      </section>
    </main>
  );
}

function Overview({ client }: { client: boolean }) {
  const metrics = client
    ? [["Всего заказов", "24", "+3 за неделю"], ["На складе", "5", "Ожидают упаковки"], ["Общий вес", "68,4 кг", "Все отправления"], ["Оповещения", "2", "Непрочитанные"]]
    : [["Заказы сегодня", "148", "+12% к вчера"], ["Открытые коробки", "23", "7 почти заполнены"], ["Активные партии", "6", "2 готовы к отправке"], ["Вес сегодня", "386 кг", "Средний 2,6 кг"]];
  return (
    <>
      <section className="metric-grid">
        {metrics.map(([label, value, note], index) => (
          <article className="metric-card" key={label}>
            <span className={`metric-index i${index}`}>0{index + 1}</span>
            <p>{label}</p><strong>{value}</strong><small>{note}</small>
          </article>
        ))}
      </section>
      <section className="dashboard-grid">
        <article className="panel activity-panel">
          <div className="panel-title"><div><p className="eyebrow dark">ПОТОК</p><h3>Активность за неделю</h3></div><span>Последние 7 дней</span></div>
          <div className="bar-chart" aria-label="График активности">
            {[42, 68, 51, 82, 73, 96, 62].map((height, index) => (
              <div key={index}><span style={{ height: `${height}%` }} /><small>{["Пн","Вт","Ср","Чт","Пт","Сб","Вс"][index]}</small></div>
            ))}
          </div>
        </article>
        <article className="panel queue-panel">
          <div className="panel-title"><div><p className="eyebrow dark">СЕЙЧАС</p><h3>Очередь операций</h3></div><b>12</b></div>
          {["Проверить вес коробки BX-1048", "Закрыть партию ML-0726", "Упаковать 7 новых заказов"].map((item, index) => (
            <div className="queue-item" key={item}><span>{index + 1}</span><p>{item}<small>{index === 0 ? "Высокий приоритет" : "Рабочая очередь"}</small></p><b>→</b></div>
          ))}
        </article>
      </section>
      <OrdersTable compact />
    </>
  );
}

function Clients() {
  return (
    <section className="panel table-panel">
      <div className="panel-title"><div><p className="eyebrow dark">БАЗА</p><h3>Клиенты</h3></div><button className="action-button">+ Новый клиент</button></div>
      <div className="table-scroll"><table><thead><tr><th>Код</th><th>Клиент</th><th>Заказы</th><th>Вес</th><th>Статус</th></tr></thead>
        <tbody>{demoClients.map((client) => <tr key={client.code}><td><code>{client.code}</code></td><td><strong>{client.name}</strong></td><td>{client.orders}</td><td>{client.weight}</td><td><span className="status good">{client.state}</span></td></tr>)}</tbody>
      </table></div>
    </section>
  );
}

function StaffClients({ session }: { session: Session }) {
  const [clients, setClients] = useState<StaffClient[]>([]);
  const [editing, setEditing] = useState<StaffClient | null>(null);
  const [message, setMessage] = useState("");

  useEffect(() => {
    apiRequest<StaffClient[]>("/api/staff/clients", {}, session)
      .then(setClients)
      .catch((exception) => setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить клиентов"));
  }, [session]);

  function updateEditing(patch: Partial<StaffClient>) {
    setEditing((current) => current ? { ...current, ...patch } : current);
  }

  async function saveClient() {
    if (!editing) return;
    setMessage("");
    try {
      const updated = await apiRequest<StaffClient>(`/api/staff/clients/${editing.guid}`, {
        method: "PUT",
        body: JSON.stringify({
          first_name: editing.first_name,
          last_name: editing.last_name,
          email: editing.email,
          phone: editing.phone,
          address: editing.address,
          package_shoes: editing.package_shoes,
          bag_package: editing.bag_package,
        }),
      }, session);
      setClients((current) => current.map((client) => client.guid === updated.guid ? updated : client));
      setEditing(updated);
      setMessage("Данные клиента сохранены");
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось сохранить клиента");
    }
  }

  return <>
    <section className="panel table-panel">
      <div className="panel-title"><div><p className="eyebrow dark">БАЗА</p><h3>Клиенты</h3></div><span>{clients.length} записей</span></div>
      <div className="table-scroll"><table><thead><tr><th>Код</th><th>Клиент</th><th>Email</th><th>Телефон</th><th>Заказы</th><th></th></tr></thead>
        <tbody>{clients.map((client) => <tr key={client.guid}><td><code>{client.client_code}</code></td><td><strong>{client.full_name}</strong></td><td>{client.email || "—"}</td><td>{client.phone || "—"}</td><td>{client.orders_count}</td><td><button className="table-action" onClick={() => { setEditing(client); setMessage(""); }}>Изменить</button></td></tr>)}</tbody>
      </table></div>
      {!clients.length && <p className="empty-state">Клиенты пока не найдены.</p>}
    </section>
    {editing && <section className="panel editor-card">
      <div className="panel-title"><div><p className="eyebrow dark">РЕДАКТИРОВАНИЕ</p><h3>{editing.full_name}</h3></div><button className="action-button" onClick={() => setEditing(null)}>Закрыть</button></div>
      <div className="form-columns">
        <label><span>Код клиента</span><input className="readonly-field" value={editing.client_code} readOnly /></label>
        <label><span>Email</span><input type="email" value={editing.email || ""} onChange={(event) => updateEditing({ email: event.target.value })} /></label>
        <label><span>Имя</span><input value={editing.first_name} onChange={(event) => updateEditing({ first_name: event.target.value })} /></label>
        <label><span>Фамилия</span><input value={editing.last_name} onChange={(event) => updateEditing({ last_name: event.target.value })} /></label>
        <label><span>Телефон</span><input type="tel" value={editing.phone || ""} onChange={(event) => updateEditing({ phone: event.target.value })} /></label>
        <label><span>Адрес</span><input value={editing.address || ""} onChange={(event) => updateEditing({ address: event.target.value })} /></label>
        <label><span>Упаковка обуви</span><input type="number" value={editing.package_shoes} onChange={(event) => updateEditing({ package_shoes: Number(event.target.value) })} /></label>
        <label><span>Пакет</span><input type="number" value={editing.bag_package} onChange={(event) => updateEditing({ bag_package: Number(event.target.value) })} /></label>
      </div>
      <p className="field-note">Код клиента нельзя изменить: он связан с заказами и кабинетом.</p>
      <button className="primary-button" onClick={saveClient}>Сохранить изменения</button>
      {message && <p className="save-notice">{message}</p>}
    </section>}
  </>;
}

function Orders({ client }: { client: boolean }) {
  return <OrdersTable title={client ? "Мои заказы" : "Все заказы"} />;
}

function OrdersTable({ compact = false, title = "Последние заказы" }: { compact?: boolean; title?: string }) {
  return (
    <section className="panel table-panel">
      <div className="panel-title"><div><p className="eyebrow dark">ТРЕКИНГ</p><h3>{title}</h3></div>{!compact && <button className="action-button">Экспорт CSV</button>}</div>
      <div className="table-scroll"><table><thead><tr><th>Трек</th><th>Клиент</th><th>Статус</th><th>Вес</th><th>Обновлён</th></tr></thead>
        <tbody>{demoOrders.map((order) => <tr key={order.tracking}><td><strong>{order.tracking}</strong></td><td><code>{order.client}</code></td><td><span className={`status ${order.status === "Упакован" ? "good" : ""}`}>{order.status}</span></td><td>{order.weight}</td><td>{order.time}</td></tr>)}</tbody>
      </table></div>
    </section>
  );
}

function Boxes() {
  return <section className="card-list">{["BX-1048","BX-1047","BX-1046"].map((box, i) => <article className="box-card" key={box}><p className="eyebrow dark">КОРОБКА</p><h3>{box}</h3><div className="box-visual"><span style={{ width: `${72 - i * 13}%` }} /></div><p><strong>{[18,12,7][i]}</strong> заказов <b>{[24.8,17.2,9.6][i]} кг</b></p><small>Создана сегодня · BMG</small><button>Открыть →</button></article>)}</section>;
}

function Batches() {
  return <section className="panel"><div className="panel-title"><div><p className="eyebrow dark">ОТПРАВКА</p><h3>Активные партии</h3></div><button className="action-button">+ Создать партию</button></div>{["ML-0726 · Málaga","MD-0725 · Madrid","BC-0724 · Barcelona"].map((name, i) => <div className="batch-row" key={name}><span>0{i + 1}</span><div><strong>{name}</strong><small>{[8,5,3][i]} коробок · {[126,84,41][i]} кг</small></div><div className="progress"><i style={{ width: `${[82,55,34][i]}%` }} /></div><b>{[82,55,34][i]}%</b></div>)}</section>;
}

function Manifests({ session }: { session: Session | null }) {
  const [batches, setBatches] = useState<BatchSummary[]>([]);
  const [manifests, setManifests] = useState<ManifestRecord[]>([]);
  const [selectedBatch, setSelectedBatch] = useState("");
  const [active, setActive] = useState<ManifestRecord | null>(null);
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!session) return;
    Promise.all([
      apiRequest<BatchSummary[]>("/api/order/partial?typePartial=1", {}, session),
      apiRequest<ManifestRecord[]>("/api/staff/manifests", {}, session),
    ]).then(([loadedBatches, loadedManifests]) => {
      setBatches(loadedBatches);
      setManifests(loadedManifests);
    }).catch((exception) => setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить манифесты"));
  }, [session]);

  async function createManifest() {
    if (!session || !selectedBatch) return;
    setMessage("");
    try {
      const result = await apiRequest<{ success: boolean; created: boolean; manifest: ManifestRecord }>("/api/staff/manifests", {
        method: "POST",
        body: JSON.stringify({ partial_guid: selectedBatch }),
      }, session);
      setActive(result.manifest);
      setManifests((current) => [result.manifest, ...current.filter((item) => item.guid !== result.manifest.guid)]);
      setMessage(result.created ? "Манифест создан" : "Для этой партии манифест уже существует");
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось создать манифест");
    }
  }

  async function openManifest(guid: string) {
    if (!session) return;
    try {
      setActive(await apiRequest<ManifestRecord>(`/api/staff/manifests/${guid}`, {}, session));
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось открыть манифест");
    }
  }

  if (!session) return <section className="panel"><h3>Манифесты</h3><p className="empty-state">Для работы с манифестами войдите как администратор или сотрудник.</p></section>;

  return <>
    <section className="panel manifest-create">
      <div className="panel-title"><div><p className="eyebrow dark">ОТПРАВКА</p><h3>Создать манифест</h3></div></div>
      <p className="muted">Выберите завершённую партию. В манифест попадут все заказы из всех её коробок.</p>
      <div className="manifest-controls">
        <label><span>Завершённая партия</span><select value={selectedBatch} onChange={(event) => setSelectedBatch(event.target.value)}><option value="">Выберите партию</option>{batches.map((batch) => <option key={batch.guid} value={batch.guid}>{batch.partial_code} · {batch.partial_name} · {batch.boxCount} коробок</option>)}</select></label>
        <button className="primary-button" disabled={!selectedBatch} onClick={createManifest}>Создать манифест</button>
      </div>
      {message && <p className="save-notice">{message}</p>}
    </section>
    <section className="panel table-panel">
      <div className="panel-title"><div><p className="eyebrow dark">АРХИВ</p><h3>Манифесты</h3></div><span>{manifests.length} документов</span></div>
      <div className="table-scroll"><table><thead><tr><th>Номер</th><th>Партия</th><th>Склад</th><th>Коробки</th><th>Заказы</th><th>Вес</th><th></th></tr></thead><tbody>{manifests.map((manifest) => <tr key={manifest.guid}><td><code>{manifest.manifest_code}</code></td><td>{manifest.partial_code}</td><td>{manifest.warehouse_name}</td><td>{manifest.box_count}</td><td>{manifest.order_count}</td><td>{manifest.gross_weight.toFixed(3)} кг</td><td><button className="table-action" onClick={() => openManifest(manifest.guid)}>Открыть</button></td></tr>)}</tbody></table></div>
      {!manifests.length && <p className="empty-state">Манифесты ещё не созданы.</p>}
    </section>
    {active && <section className="panel manifest-detail">
      <div className="panel-title"><div><p className="eyebrow dark">МАНИФЕСТ</p><h3>{active.manifest_code}</h3></div><button className="action-button" onClick={() => setActive(null)}>Закрыть</button></div>
      <div className="summary-strip"><div><small>Партия</small><strong>{active.partial_code}</strong></div><div><small>Склад</small><strong>{active.warehouse_name}</strong></div><div><small>Коробки</small><strong>{active.box_count}</strong></div><div><small>Заказы</small><strong>{active.order_count}</strong></div><div><small>Общий вес</small><strong>{active.gross_weight.toFixed(3)} кг</strong></div></div>
      <div className="table-scroll"><table><thead><tr><th>Трек заказа</th><th>Трек коробки</th><th>Имя</th><th>Фамилия</th><th>Адрес</th><th>Состав</th><th>Описание товара</th><th>Вес</th><th>Стоимость</th><th>Email</th><th>Телефон</th></tr></thead><tbody>{active.orders.map((order) => <tr key={order.guid}><td><strong>{order.tracking_number}</strong></td><td><code>{order.box_tracking_code}</code></td><td>{order.client_first_name || "—"}</td><td>{order.client_last_name || "—"}</td><td>{order.client_address || "—"}</td><td>{order.composition || "—"}</td><td>{order.description || "—"}</td><td>{order.gross_weight.toFixed(3)} кг</td><td>{order.declared_value.toFixed(2)} {order.currency || "EUR"}</td><td>{order.client_email || "—"}</td><td>{order.client_phone || "—"}</td></tr>)}</tbody></table></div>
    </section>}
  </>;
}

function Notifications({ client }: { client: boolean }) {
  const items = client ? ["Ваш заказ CA912770346ES упакован", "Новый заказ принят на складе"] : ["Партия ML-0726 готова к закрытию", "Коробка BX-1048 превысила рекомендуемый вес", "3 новых клиента зарегистрированы"];
  return <section className="panel"><div className="panel-title"><div><p className="eyebrow dark">СОБЫТИЯ</p><h3>Оповещения</h3></div>{!client && <button className="action-button">+ Отправить</button>}</div>{items.map((item, i) => <div className="notice-row" key={item}><span className={i === 0 ? "unread" : ""} /><div><strong>{item}</strong><small>{i === 0 ? "12 минут назад" : "Сегодня"}</small></div><button>Прочитано</button></div>)}</section>;
}

function Audit() {
  return <section className="panel table-panel"><div className="panel-title"><div><p className="eyebrow dark">АУДИТ</p><h3>Журнал операций</h3></div><span>Последние 100 записей</span></div><div className="audit-list">{["POST /api/order/set-user","POST /api/order/box-order","PUT /api/admin/client-code-settings","DELETE /api/order/partial-box"].map((route, i) => <div key={route}><code>{route}</code><span>{["admin","worker.malaga","admin","worker.madrid"][i]}</span><small>{i * 7 + 2} мин назад</small><b>SUCCESS</b></div>)}</div></section>;
}

function Settings({ client, platform, setPlatform, profile, setProfile, session, demo, save, notice }: {
  client: boolean;
  platform: PlatformSettings;
  setPlatform: (value: PlatformSettings) => void;
  profile: ClientProfile;
  setProfile: (value: ClientProfile) => void;
  session: Session | null;
  demo: boolean;
  save: () => void;
  notice: string;
}) {
  return <section className="settings-grid"><article className="panel settings-card">
    <p className="eyebrow dark">{client ? "ПРОФИЛЬ" : "НАСТРОЙКИ СКЛАДА"}</p>
    <h3>{client ? "Личные данные" : "Параметры платформы"}</h3>
    <p className="muted">{client ? "Контактные данные и настройки оповещений." : "Эти значения используются для склада и новых клиентских номеров."}</p>
    {client ? <>
      <div className="form-columns">
        <label><span>Код клиента</span><input className="readonly-field" value={profile.client_code} readOnly aria-readonly="true" /></label>
        <label><span>Email</span><input className="readonly-field" value={profile.email} readOnly aria-readonly="true" /></label>
        <label><span>Имя</span><input value={profile.first_name} onChange={(event) => setProfile({ ...profile, first_name: event.target.value })} /></label>
        <label><span>Фамилия</span><input value={profile.last_name} onChange={(event) => setProfile({ ...profile, last_name: event.target.value })} /></label>
        <label><span>Телефон</span><input type="tel" value={profile.phone || ""} onChange={(event) => setProfile({ ...profile, phone: event.target.value })} /></label>
        <label><span>Адрес</span><input value={profile.address || ""} onChange={(event) => setProfile({ ...profile, address: event.target.value })} /></label>
      </div>
      <p className="field-note">Код клиента назначается системой и доступен только для просмотра.</p>
      <label className="check"><input type="checkbox" checked={profile.email_notifications} onChange={(event) => setProfile({ ...profile, email_notifications: event.target.checked })} /> Email-оповещения</label>
      <label className="check"><input type="checkbox" checked={profile.push_notifications} onChange={(event) => setProfile({ ...profile, push_notifications: event.target.checked })} /> Push-оповещения</label>
    </> : <>
      <div className="form-columns">
        <label><span>Склад</span><input value={platform.warehouse} maxLength={120} onChange={(event) => setPlatform({ ...platform, warehouse: event.target.value })} /></label>
        <label><span>Страна</span><input value={platform.country} maxLength={120} onChange={(event) => setPlatform({ ...platform, country: event.target.value })} /></label>
        <label><span>Префикс клиента</span><input value={platform.prefix} maxLength={12} onChange={(event) => setPlatform({ ...platform, prefix: event.target.value.toUpperCase() })} /></label>
      </div>
      <div className="code-preview"><small>Формат клиентского номера</small><strong>{platform.prefix || "KG"}######</strong><span>Пример: {platform.prefix || "KG"}482731</span></div>
    </>}
    <button className="primary-button" onClick={save}>Сохранить</button>
    {notice && <p className="save-notice">{notice}</p>}
    {!client && <StaffUsers session={session} demo={demo} />}
  </article><article className="panel system-card"><p className="eyebrow dark">СИСТЕМА</p><h3>FastScan API</h3><dl><div><dt>Склад</dt><dd>{platform.warehouse || "BMG"}</dd></div><div><dt>Страна</dt><dd>{platform.country || "España"}</dd></div><div><dt>API</dt><dd>v0.4.0</dd></div><div><dt>Состояние</dt><dd><span className="signal-dot" /> Работает</dd></div></dl></article></section>;
}

function StaffUsers({ session, demo }: { session: Session | null; demo: boolean }) {
  const [users, setUsers] = useState<StaffUser[]>([]);
  const [loginValue, setLoginValue] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function loadUsers() {
    if (!session || demo) return;
    try {
      setUsers(await apiRequest<StaffUser[]>("/api/admin/users", {}, session));
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить сотрудников");
    }
  }

  useEffect(() => {
    loadUsers();
  }, [session, demo]);

  async function createWorker(event: FormEvent) {
    event.preventDefault();
    if (!session || demo) {
      setMessage("Демо: сотрудник не создан на сервере");
      return;
    }
    setBusy(true);
    setMessage("");
    try {
      await apiRequest("/api/admin/users", {
        method: "POST",
        body: JSON.stringify({
          login: loginValue.trim().toLowerCase(),
          password,
          role: "WORKER",
        }),
      }, session);
      setLoginValue("");
      setPassword("");
      setMessage("Сотрудник создан");
      await loadUsers();
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось создать сотрудника");
    } finally {
      setBusy(false);
    }
  }

  async function setEnabled(user: StaffUser, enabled: boolean) {
    if (!session || demo) {
      setMessage("Демо: доступ изменен локально");
      setUsers((current) => current.map((item) => item.id === user.id ? { ...item, enabled } : item));
      return;
    }
    setMessage("");
    try {
      const updated = await apiRequest<StaffUser>(`/api/admin/users/${user.id}/enabled`, {
        method: "PUT",
        body: JSON.stringify({ enabled }),
      }, session);
      setUsers((current) => current.map((item) => item.id === updated.id ? updated : item));
      setMessage(enabled ? "Сотрудник разблокирован" : "Сотрудник заблокирован");
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось изменить доступ");
    }
  }

  return <section className="staff-users">
    <div className="section-title"><div><p className="eyebrow dark">ДОСТУП</p><h4>Сотрудники</h4></div><button className="action-button" type="button" onClick={loadUsers}>Обновить</button></div>
    <form className="staff-create" onSubmit={createWorker}>
      <label><span>Логин сотрудника</span><input value={loginValue} onChange={(event) => setLoginValue(event.target.value)} autoComplete="off" /></label>
      <label><span>Временный пароль</span><input type="password" value={password} minLength={8} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" /></label>
      <button className="primary-button" disabled={busy || !loginValue.trim() || password.length < 8}>{busy ? "Создание..." : "Создать WORKER"}</button>
    </form>
    <div className="staff-list">
      {users.map((user) => <div className="staff-row" key={user.id}>
        <div><strong>{user.login}</strong><small>{user.role} · {user.enabled ? "доступ активен" : "заблокирован"}</small></div>
        <span className={user.enabled ? "status good" : "status blocked"}>{user.enabled ? "Активен" : "Блок"}</span>
        <button className="table-action" disabled={user.role === "ADMIN"} onClick={() => setEnabled(user, !user.enabled)}>
          {user.enabled ? "Заблокировать" : "Разблокировать"}
        </button>
      </div>)}
    </div>
    {!users.length && <p className="empty-state">Сотрудники пока не загружены.</p>}
    {message && <p className="save-notice">{message}</p>}
  </section>;
}
