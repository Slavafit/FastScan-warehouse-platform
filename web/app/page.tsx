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
  has_account: boolean;
  account_enabled: boolean;
  package_shoes: number;
  bag_package: number;
  orders_count: number;
};

type ClientDraft = {
  first_name: string;
  last_name: string;
  email: string;
  password: string;
  phone: string;
  address: string;
  package_shoes: number;
  bag_package: number;
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

type BoxSummary = {
  guid: string;
  codeid: string;
  partial: string;
  tracking_code: string;
  warehouse: string;
  warehouse_name: string;
  warehouse_guid: string | null;
  width: number;
  height: number;
  length: number;
  v_weight: number;
  gross_weight: number;
  date_created: string;
  orderCount: number;
};

type BoxOrder = {
  guid: string;
  tracking_number: string;
  client: string | null;
  client_code: string | null;
  with_box: string;
  gross_weight: number | null;
  user_guid: string | null;
  length: number | null;
  width: number | null;
  height: number | null;
  composition_guid: string | null;
  composition: string | null;
  description: string | null;
  declared_value: number | null;
  currency: string | null;
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

type ClientOrder = {
  guid: string;
  tracking_number: string;
  status_name: string;
  gross_weight: number | null;
  with_box: boolean;
  box_tracking_code: string | null;
  composition_guid: string | null;
  composition: string | null;
  description: string | null;
  declared_value: number | null;
  currency: string | null;
  created_at: string;
  updated_at: string;
};

type ClientAnalytics = {
  total_orders: number;
  packed_orders: number;
  unpacked_orders: number;
  total_weight: number;
  boxes_count: number;
  status_breakdown: Record<string, number>;
  unread_notifications: number;
};

type ClientOrderDraft = {
  tracking_number: string;
  composition_guid: string;
  description: string;
  declared_value: string;
  currency: string;
};

type DictionaryType =
  | "WAREHOUSE"
  | "COUNTRY"
  | "ORDER_STATUS"
  | "CURRENCY"
  | "PACKAGING_TYPE";

type DictionaryItem = {
  guid: string;
  type?: DictionaryType;
  code: string;
  name: string;
  enabled: boolean;
  sort_order: number;
  used_count: number;
};

type DictionaryDraft = {
  guid?: string;
  code: string;
  name: string;
  sort_order: number;
};

const dictionaryTypes: Array<[DictionaryType, string]> = [
  ["WAREHOUSE", "Склады"],
  ["COUNTRY", "Страны"],
  ["ORDER_STATUS", "Статусы"],
  ["CURRENCY", "Валюты"],
  ["PACKAGING_TYPE", "Упаковка"],
];

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
          {view === "orders" && <Orders client={role === "CLIENT"} session={session} demo={demo} />}
          {view === "boxes" && <Boxes session={session} demo={demo} />}
          {view === "batches" && <Batches session={session} demo={demo} />}
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
  const [clientOrders, setClientOrders] = useState<ClientOrder[]>([]);
  const [clientAnalytics, setClientAnalytics] = useState<ClientAnalytics | null>(null);
  const [resetPassword, setResetPassword] = useState("");
  const [draft, setDraft] = useState<ClientDraft>({
    first_name: "",
    last_name: "",
    email: "",
    password: "",
    phone: "",
    address: "",
    package_shoes: 0,
    bag_package: 0,
  });
  const [message, setMessage] = useState("");

  async function loadClients() {
    apiRequest<StaffClient[]>("/api/staff/clients", {}, session)
      .then(setClients)
      .catch((exception) => setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить клиентов"));
  }

  useEffect(() => {
    loadClients();
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

  async function createClient(event: FormEvent) {
    event.preventDefault();
    setMessage("");
    try {
      const created = await apiRequest<StaffClient>("/api/staff/clients", {
        method: "POST",
        body: JSON.stringify({
          ...draft,
          email: draft.email.trim() || undefined,
          password: draft.password || undefined,
        }),
      }, session);
      setClients((current) => [created, ...current]);
      setDraft({ first_name: "", last_name: "", email: "", password: "", phone: "", address: "", package_shoes: 0, bag_package: 0 });
      setMessage("Клиент создан");
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось создать клиента");
    }
  }

  async function openClient(client: StaffClient) {
    setEditing(client);
    setResetPassword("");
    setMessage("");
    try {
      const [orders, analytics] = await Promise.all([
        apiRequest<ClientOrder[]>(`/api/staff/clients/${client.guid}/orders`, {}, session),
        apiRequest<ClientAnalytics>(`/api/staff/clients/${client.guid}/analytics`, {}, session),
      ]);
      setClientOrders(orders);
      setClientAnalytics(analytics);
    } catch (exception) {
      setClientOrders([]);
      setClientAnalytics(null);
      setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить данные клиента");
    }
  }

  async function toggleClientAccess() {
    if (!editing || !editing.has_account) return;
    setMessage("");
    try {
      const updated = await apiRequest<StaffClient>(`/api/staff/clients/${editing.guid}/enabled`, {
        method: "PUT",
        body: JSON.stringify({ enabled: !editing.account_enabled }),
      }, session);
      setClients((current) => current.map((client) => client.guid === updated.guid ? updated : client));
      setEditing(updated);
      setMessage(updated.account_enabled ? "Кабинет клиента разблокирован" : "Кабинет клиента заблокирован");
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось изменить доступ клиента");
    }
  }

  async function saveResetPassword() {
    if (!editing || resetPassword.length < 8) return;
    setMessage("");
    try {
      await apiRequest(`/api/staff/clients/${editing.guid}/reset-password`, {
        method: "POST",
        body: JSON.stringify({ password: resetPassword }),
      }, session);
      setResetPassword("");
      setMessage("Пароль клиента обновлен");
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось сбросить пароль");
    }
  }

  return <>
    <section className="panel editor-card">
      <div className="panel-title"><div><p className="eyebrow dark">НОВЫЙ КЛИЕНТ</p><h3>Создать клиента</h3></div></div>
      <form className="form-columns" onSubmit={createClient}>
        <label><span>Имя</span><input value={draft.first_name} onChange={(event) => setDraft({ ...draft, first_name: event.target.value })} /></label>
        <label><span>Фамилия</span><input value={draft.last_name} onChange={(event) => setDraft({ ...draft, last_name: event.target.value })} /></label>
        <label><span>Email кабинета</span><input type="email" value={draft.email} onChange={(event) => setDraft({ ...draft, email: event.target.value.toLowerCase() })} /></label>
        <label><span>Временный пароль</span><input type="password" value={draft.password} minLength={8} onChange={(event) => setDraft({ ...draft, password: event.target.value })} /></label>
        <label><span>Телефон</span><input value={draft.phone} onChange={(event) => setDraft({ ...draft, phone: event.target.value })} /></label>
        <label><span>Адрес</span><input value={draft.address} onChange={(event) => setDraft({ ...draft, address: event.target.value })} /></label>
        <label><span>Упаковка обуви</span><input type="number" value={draft.package_shoes} onChange={(event) => setDraft({ ...draft, package_shoes: Number(event.target.value) })} /></label>
        <label><span>Пакет</span><input type="number" value={draft.bag_package} onChange={(event) => setDraft({ ...draft, bag_package: Number(event.target.value) })} /></label>
        <button className="primary-button" disabled={!draft.first_name.trim() || !draft.last_name.trim() || (!!draft.email.trim() && draft.password.length < 8)}>Создать клиента</button>
      </form>
      <p className="field-note">Код клиента генерируется автоматически и не редактируется клиентом.</p>
    </section>
    <section className="panel table-panel">
      <div className="panel-title"><div><p className="eyebrow dark">БАЗА</p><h3>Клиенты</h3></div><span>{clients.length} записей</span></div>
      <div className="table-scroll"><table><thead><tr><th>Код</th><th>Клиент</th><th>Email</th><th>Телефон</th><th>Кабинет</th><th>Заказы</th><th></th></tr></thead>
        <tbody>{clients.map((client) => <tr key={client.guid}><td><code>{client.client_code}</code></td><td><strong>{client.full_name}</strong></td><td>{client.email || "—"}</td><td>{client.phone || "—"}</td><td>{client.has_account ? <span className={client.account_enabled ? "status good" : "status blocked"}>{client.account_enabled ? "Активен" : "Блок"}</span> : "—"}</td><td>{client.orders_count}</td><td><button className="table-action" onClick={() => openClient(client)}>Открыть</button></td></tr>)}</tbody>
      </table></div>
      {!clients.length && <p className="empty-state">Клиенты пока не найдены.</p>}
    </section>
    {editing && <section className="panel editor-card">
      <div className="panel-title"><div><p className="eyebrow dark">РЕДАКТИРОВАНИЕ</p><h3>{editing.full_name}</h3></div><div className="button-row">{editing.has_account && <button className="action-button" onClick={toggleClientAccess}>{editing.account_enabled ? "Заблокировать" : "Разблокировать"}</button>}<button className="action-button" onClick={() => setEditing(null)}>Закрыть</button></div></div>
      <div className="summary-strip"><div><small>Заказы</small><strong>{clientAnalytics?.total_orders ?? editing.orders_count}</strong></div><div><small>Упаковано</small><strong>{clientAnalytics?.packed_orders ?? 0}</strong></div><div><small>Без коробки</small><strong>{clientAnalytics?.unpacked_orders ?? 0}</strong></div><div><small>Коробки</small><strong>{clientAnalytics?.boxes_count ?? 0}</strong></div><div><small>Вес</small><strong>{formatWeight(clientAnalytics?.total_weight ?? 0)}</strong></div></div>
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
      {editing.has_account && <div className="inline-form"><label><span>Новый пароль</span><input type="password" value={resetPassword} minLength={8} onChange={(event) => setResetPassword(event.target.value)} /></label><button className="action-button" disabled={resetPassword.length < 8} onClick={saveResetPassword}>Сбросить пароль</button></div>}
      <div className="table-scroll"><table><thead><tr><th>Трек</th><th>Статус</th><th>Состав</th><th>Стоимость</th><th>Вес</th><th>Коробка</th></tr></thead><tbody>{clientOrders.map((order) => <tr key={order.guid}><td><strong>{order.tracking_number}</strong></td><td><span className={order.with_box ? "status good" : "status"}>{formatStatus(order.status_name)}</span></td><td>{order.composition || "—"}</td><td>{formatMoney(order.declared_value, order.currency)}</td><td>{formatWeight(order.gross_weight)}</td><td>{order.box_tracking_code ? <code>{order.box_tracking_code}</code> : "—"}</td></tr>)}</tbody></table></div>
      {!clientOrders.length && <p className="empty-state">История заказов пока пустая.</p>}
      {message && <p className="save-notice">{message}</p>}
    </section>}
  </>;
}

function Orders({ client, session, demo }: { client: boolean; session: Session | null; demo: boolean }) {
  if (client && session && !demo) return <ClientOrders session={session} />;
  return <OrdersTable title={client ? "Мои заказы" : "Все заказы"} />;
}

function ClientOrders({ session }: { session: Session }) {
  const [orders, setOrders] = useState<ClientOrder[]>([]);
  const [analytics, setAnalytics] = useState<ClientAnalytics | null>(null);
  const [compositions, setCompositions] = useState<DictionaryItem[]>([]);
  const [draft, setDraft] = useState<ClientOrderDraft>({
    tracking_number: "",
    composition_guid: "",
    description: "",
    declared_value: "",
    currency: "EUR",
  });
  const [queryDraft, setQueryDraft] = useState("");
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    setMessage("");
    try {
      const [loadedOrders, loadedAnalytics] = await Promise.all([
        apiRequest<ClientOrder[]>("/api/client/orders", {}, session),
        apiRequest<ClientAnalytics>("/api/client/analytics", {}, session),
      ]);
      setOrders(loadedOrders);
      setAnalytics(loadedAnalytics);
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить заказы");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    apiRequest<DictionaryItem[]>("/api/client/order-compositions", {}, session)
      .then(setCompositions)
      .catch(() => setCompositions([]));
  }, [session]);

  function updateTrackSearch(value: string) {
    setQueryDraft(value);
    const normalized = value.trim();
    setQuery(normalized.length >= 3 ? normalized : "");
  }

  function applyTrackSearch() {
    setQuery(queryDraft.trim());
  }

  async function registerOrder(event: FormEvent) {
    event.preventDefault();
    setMessage("");
    const trackingNumber = draft.tracking_number.trim();
    if (!trackingNumber) {
      setMessage("Введите трек или номер заказа");
      return;
    }
    try {
      await apiRequest<ClientOrder>("/api/client/orders", {
        method: "POST",
        body: JSON.stringify({
          tracking_number: trackingNumber,
          composition_guid: draft.composition_guid || undefined,
          description: draft.description.trim() || undefined,
          declared_value: draft.declared_value.trim() ? Number(draft.declared_value) : undefined,
          currency: draft.currency.trim().toUpperCase() || "EUR",
        }),
      }, session);
      setDraft({
        tracking_number: "",
        composition_guid: "",
        description: "",
        declared_value: "",
        currency: "EUR",
      });
      setMessage("Отправление зарегистрировано");
      await load();
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось зарегистрировать отправление");
    }
  }

  const normalizedQuery = query.trim().toLowerCase();
  const visibleOrders = normalizedQuery
    ? orders.filter((order) => order.tracking_number.toLowerCase().includes(normalizedQuery))
    : orders;
  const statusItems = Object.entries(analytics?.status_breakdown ?? {});

  return <>
    <section className="metric-grid client-analytics">
      <article className="metric-card"><span className="metric-index i0">01</span><p>Всего заказов</p><strong>{analytics?.total_orders ?? 0}</strong><small>{analytics?.unpacked_orders ?? 0} без коробки</small></article>
      <article className="metric-card"><span className="metric-index i1">02</span><p>Упаковано</p><strong>{analytics?.packed_orders ?? 0}</strong><small>{analytics?.boxes_count ?? 0} коробок</small></article>
      <article className="metric-card"><span className="metric-index i2">03</span><p>Общий вес</p><strong>{(analytics?.total_weight ?? 0).toFixed(2)} кг</strong><small>По всем заказам</small></article>
      <article className="metric-card"><span className="metric-index i3">04</span><p>Уведомления</p><strong>{analytics?.unread_notifications ?? 0}</strong><small>Непрочитанные</small></article>
    </section>
    <section className="panel table-panel">
      <div className="panel-title">
        <div><p className="eyebrow dark">ТРЕКИНГ</p><h3>Мои заказы</h3></div>
        <button className="action-button" type="button" onClick={load}>Обновить</button>
      </div>
      <form className="client-order-form" onSubmit={registerOrder}>
        <label><span>Трек или номер заказа</span><input value={draft.tracking_number} onChange={(event) => setDraft({ ...draft, tracking_number: event.target.value.trim().toUpperCase() })} /></label>
        <label><span>Состав</span><select value={draft.composition_guid} onChange={(event) => setDraft({ ...draft, composition_guid: event.target.value })}><option value="">Не выбран</option>{compositions.map((item) => <option key={item.guid} value={item.guid}>{item.name}</option>)}</select></label>
        <label><span>Стоимость</span><input type="number" min="0" step="0.01" value={draft.declared_value} onChange={(event) => setDraft({ ...draft, declared_value: event.target.value })} /></label>
        <label><span>Валюта</span><input maxLength={3} value={draft.currency} onChange={(event) => setDraft({ ...draft, currency: event.target.value.toUpperCase() })} /></label>
        <label className="wide"><span>Описание товара</span><input value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
        <button className="primary-button" disabled={!draft.tracking_number.trim()}>Зарегистрировать</button>
      </form>
      <div className="order-tools">
        <label><span>Поиск по треку</span><input value={queryDraft} onChange={(event) => updateTrackSearch(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") applyTrackSearch(); }} placeholder="Минимум 3 символа или Enter" /></label>
        <div className="status-pills">
          {statusItems.length ? statusItems.map(([status, count]) => <span key={status}>{status || "Без статуса"} · {count}</span>) : <span>Статусы появятся после загрузки заказов</span>}
        </div>
      </div>
      {message && <p className="form-error">{message}</p>}
      <div className="table-scroll"><table><thead><tr><th>Трек</th><th>Статус</th><th>Состав</th><th>Описание</th><th>Стоимость</th><th>Вес</th><th>Коробка</th><th>Обновлен</th></tr></thead>
        <tbody>{visibleOrders.map((order) => <tr key={order.guid}><td><strong>{order.tracking_number}</strong></td><td><span className={`status ${order.with_box ? "good" : ""}`}>{formatStatus(order.status_name)}</span></td><td>{order.composition || "—"}</td><td>{order.description || "—"}</td><td>{formatMoney(order.declared_value, order.currency)}</td><td>{formatWeight(order.gross_weight)}</td><td>{order.box_tracking_code ? <code>{order.box_tracking_code}</code> : "—"}</td><td>{formatDate(order.updated_at)}</td></tr>)}</tbody>
      </table></div>
      {loading && <p className="empty-state">Загружаем заказы...</p>}
      {!loading && !visibleOrders.length && <p className="empty-state">{query.trim() ? "По этому треку заказ не найден." : "Заказы пока не найдены."}</p>}
    </section>
  </>;
}

function formatWeight(value: number | null) {
  return value == null ? "—" : `${value.toFixed(3)} кг`;
}

function formatMoney(value: number | null, currency: string | null) {
  return value == null ? "—" : `${value.toFixed(2)} ${currency || "EUR"}`;
}

function formatStatus(status: string | null | undefined) {
  const labels: Record<string, string> = {
    CLIENT_REGISTERED: "Ожидает поступления",
    AT_WAREHOUSE: "На складе",
    REGISTERED: "На складе",
    PACKED: "Упакован",
    IN_BATCH: "В партии",
  };
  return labels[status || ""] || status || "На складе";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ru", { day: "2-digit", month: "2-digit", year: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function escapeHtml(value: unknown) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function manifestCurrencyTotals(manifest: ManifestRecord) {
  const totals = new Map<string, number>();
  for (const order of manifest.orders) {
    if (order.declared_value == null) continue;
    const currency = order.currency || "EUR";
    totals.set(currency, (totals.get(currency) ?? 0) + order.declared_value);
  }
  return [...totals.entries()].sort(([left], [right]) => left.localeCompare(right));
}

function manifestRowsHtml(manifest: ManifestRecord) {
  return manifest.orders.map((order, index) => `<tr>
    <td>${index + 1}</td>
    <td>${escapeHtml(order.tracking_number)}</td>
    <td>${escapeHtml(order.box_tracking_code)}</td>
    <td>${escapeHtml(order.client_code)}</td>
    <td>${escapeHtml(order.client_first_name)}</td>
    <td>${escapeHtml(order.client_last_name)}</td>
    <td>${escapeHtml(order.client_address)}</td>
    <td>${escapeHtml(order.composition)}</td>
    <td>${escapeHtml(order.description)}</td>
    <td>${order.gross_weight.toFixed(3)}</td>
    <td>${order.declared_value.toFixed(2)}</td>
    <td>${escapeHtml(order.currency || "EUR")}</td>
    <td>${escapeHtml(order.client_email)}</td>
    <td>${escapeHtml(order.client_phone)}</td>
  </tr>`).join("");
}

function downloadTextFile(filename: string, content: string, type: string) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function downloadManifestExcel(manifest: ManifestRecord) {
  const totals = manifestCurrencyTotals(manifest)
    .map(([currency, value]) => `${escapeHtml(currency)} ${value.toFixed(2)}`)
    .join("; ");
  const html = `<!doctype html><html><head><meta charset="utf-8" /></head><body>
    <table>
      <tr><th colspan="14">Manifest ${escapeHtml(manifest.manifest_code)}</th></tr>
      <tr><td>Number</td><td>${escapeHtml(manifest.manifest_code)}</td><td>Date</td><td>${escapeHtml(formatDate(manifest.created_at))}</td></tr>
      <tr><td>Warehouse</td><td>${escapeHtml(manifest.warehouse_name)}</td><td>Country</td><td>${escapeHtml(manifest.country)}</td></tr>
      <tr><td>Boxes</td><td>${manifest.box_count}</td><td>Orders</td><td>${manifest.order_count}</td><td>Weight</td><td>${manifest.gross_weight.toFixed(3)}</td><td>Value totals</td><td>${totals}</td></tr>
      <tr><th>#</th><th>Order tracking</th><th>Box tracking</th><th>Client code</th><th>First name</th><th>Last name</th><th>Address</th><th>Composition</th><th>Description</th><th>Weight kg</th><th>Declared value</th><th>Currency</th><th>Email</th><th>Phone</th></tr>
      ${manifestRowsHtml(manifest)}
    </table>
  </body></html>`;
  downloadTextFile(`${manifest.manifest_code}.xls`, html, "application/vnd.ms-excel;charset=utf-8");
}

function printManifest(manifest: ManifestRecord) {
  const totals = manifestCurrencyTotals(manifest)
    .map(([currency, value]) => `${escapeHtml(currency)} ${value.toFixed(2)}`)
    .join(", ") || "—";
  const html = `<!doctype html><html><head><meta charset="utf-8" />
    <title>${escapeHtml(manifest.manifest_code)}</title>
    <style>
      body { font-family: Arial, sans-serif; color: #111; margin: 24px; }
      h1 { margin: 0 0 6px; font-size: 22px; }
      .meta { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin: 16px 0; }
      .meta div { border: 1px solid #ddd; padding: 8px; }
      .meta small { display: block; color: #555; margin-bottom: 4px; }
      table { width: 100%; border-collapse: collapse; font-size: 10px; }
      th, td { border: 1px solid #ddd; padding: 5px; text-align: left; vertical-align: top; }
      th { background: #f2f2f2; }
      @media print { body { margin: 10mm; } }
    </style></head><body>
    <h1>Manifest ${escapeHtml(manifest.manifest_code)}</h1>
    <p>Immutable snapshot created ${escapeHtml(formatDate(manifest.created_at))}</p>
    <section class="meta">
      <div><small>Batch</small><strong>${escapeHtml(manifest.partial_code)}</strong></div>
      <div><small>Warehouse</small><strong>${escapeHtml(manifest.warehouse_name)}</strong></div>
      <div><small>Country</small><strong>${escapeHtml(manifest.country)}</strong></div>
      <div><small>Total declared value</small><strong>${totals}</strong></div>
      <div><small>Boxes</small><strong>${manifest.box_count}</strong></div>
      <div><small>Orders</small><strong>${manifest.order_count}</strong></div>
      <div><small>Gross weight</small><strong>${manifest.gross_weight.toFixed(3)} kg</strong></div>
      <div><small>Created by</small><strong>${escapeHtml(manifest.created_by.login)}</strong></div>
    </section>
    <table><thead><tr><th>#</th><th>Order tracking</th><th>Box tracking</th><th>Client code</th><th>First name</th><th>Last name</th><th>Address</th><th>Composition</th><th>Description</th><th>Weight kg</th><th>Value</th><th>Currency</th><th>Email</th><th>Phone</th></tr></thead><tbody>${manifestRowsHtml(manifest)}</tbody></table>
    <script>window.addEventListener("load", () => window.print());</script>
  </body></html>`;
  const popup = window.open("", "_blank");
  if (!popup) return;
  popup.document.write(html);
  popup.document.close();
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

function Boxes({ session, demo }: { session: Session | null; demo: boolean }) {
  const [boxes, setBoxes] = useState<BoxSummary[]>([]);
  const [active, setActive] = useState<BoxSummary | null>(null);
  const [orders, setOrders] = useState<BoxOrder[]>([]);
  const [queryDraft, setQueryDraft] = useState("");
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState("");

  async function load() {
    if (!session || demo) return;
    setMessage("");
    try {
      setBoxes(await apiRequest<BoxSummary[]>("/api/order/box", {}, session));
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить коробки");
    }
  }

  useEffect(() => {
    load();
  }, [session, demo]);

  async function openBox(box: BoxSummary) {
    if (!session || demo) return;
    setActive(box);
    setMessage("");
    try {
      setOrders(await apiRequest<BoxOrder[]>(`/api/order/order-tracking-code?boxId=${box.guid}`, {}, session));
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить состав коробки");
    }
  }

  function updateSearch(value: string) {
    setQueryDraft(value);
    const normalized = value.trim();
    setQuery(normalized.length >= 3 ? normalized : "");
  }

  const visibleBoxes = filterBoxes(boxes, query);

  if (!session || demo) {
    return <OrdersTable title="Коробки" />;
  }

  return <>
    <section className="panel table-panel">
      <div className="panel-title"><div><p className="eyebrow dark">КОРОБКИ</p><h3>Список коробок</h3></div><button className="action-button" onClick={load}>Обновить</button></div>
      <div className="order-tools">
        <label><span>Поиск по треку или складу</span><input value={queryDraft} onChange={(event) => updateSearch(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") setQuery(queryDraft.trim()); }} placeholder="Минимум 3 символа или Enter" /></label>
        <div className="status-pills"><span>{boxes.length} всего</span><span>{boxes.filter((box) => !box.partial).length} без партии</span></div>
      </div>
      {message && <p className="form-error">{message}</p>}
      <div className="table-scroll"><table><thead><tr><th>Трек</th><th>Склад</th><th>Заказы</th><th>Вес</th><th>Размеры</th><th>Партия</th><th></th></tr></thead><tbody>{visibleBoxes.map((box) => <tr key={box.guid}><td><strong>{box.tracking_code}</strong></td><td>{box.warehouse_name || box.warehouse}</td><td>{box.orderCount}</td><td>{formatWeight(box.gross_weight)}</td><td>{box.length}×{box.width}×{box.height}</td><td>{box.partial ? <span className="status good">В партии</span> : <span className="status">Открыта</span>}</td><td><button className="table-action" onClick={() => openBox(box)}>Открыть</button></td></tr>)}</tbody></table></div>
      {!visibleBoxes.length && <p className="empty-state">Коробки не найдены.</p>}
    </section>
    {active && <section className="panel editor-card">
      <div className="panel-title"><div><p className="eyebrow dark">СОСТАВ</p><h3>{active.tracking_code}</h3></div><button className="action-button" onClick={() => setActive(null)}>Закрыть</button></div>
      <div className="summary-strip"><div><small>Склад</small><strong>{active.warehouse_name || active.warehouse}</strong></div><div><small>Заказы</small><strong>{orders.length}</strong></div><div><small>Вес</small><strong>{formatWeight(active.gross_weight)}</strong></div><div><small>Размеры</small><strong>{active.length}×{active.width}×{active.height}</strong></div><div><small>Объемный вес</small><strong>{formatWeight(active.v_weight)}</strong></div></div>
      <div className="table-scroll"><table><thead><tr><th>Трек</th><th>Клиент</th><th>Состав</th><th>Описание</th><th>Стоимость</th><th>Вес</th></tr></thead><tbody>{orders.map((order) => <tr key={order.guid}><td><strong>{order.tracking_number}</strong></td><td>{order.client_code ? <code>{order.client_code}</code> : "—"} {order.client || ""}</td><td>{order.composition || "—"}</td><td>{order.description || "—"}</td><td>{formatMoney(order.declared_value, order.currency)}</td><td>{formatWeight(order.gross_weight)}</td></tr>)}</tbody></table></div>
      {!orders.length && <p className="empty-state">В коробке пока нет заказов.</p>}
    </section>}
  </>;
}

function Batches({ session, demo }: { session: Session | null; demo: boolean }) {
  const [batches, setBatches] = useState<BatchSummary[]>([]);
  const [active, setActive] = useState<BatchSummary | null>(null);
  const [boxes, setBoxes] = useState<BoxSummary[]>([]);
  const [queryDraft, setQueryDraft] = useState("");
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState("");

  async function load() {
    if (!session || demo) return;
    setMessage("");
    try {
      setBatches(await apiRequest<BatchSummary[]>("/api/order/partial", {}, session));
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить партии");
    }
  }

  useEffect(() => {
    load();
  }, [session, demo]);

  async function openBatch(batch: BatchSummary) {
    if (!session || demo) return;
    setActive(batch);
    setMessage("");
    try {
      setBoxes(await apiRequest<BoxSummary[]>(`/api/order/partial-box?guid=${batch.guid}`, {}, session));
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить коробки партии");
    }
  }

  async function completeBatch(batch: BatchSummary) {
    if (!session || demo) return;
    setMessage("");
    try {
      await apiRequest("/api/order/send-partial", {
        method: "POST",
        body: JSON.stringify({ partial: batch.guid }),
      }, session);
      await load();
      setActive(null);
      setMessage("Партия завершена");
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось завершить партию");
    }
  }

  function updateSearch(value: string) {
    setQueryDraft(value);
    const normalized = value.trim();
    setQuery(normalized.length >= 3 ? normalized : "");
  }

  const visibleBatches = filterBatches(batches, query);

  if (!session || demo) {
    return <section className="panel"><div className="panel-title"><div><p className="eyebrow dark">ОТПРАВКА</p><h3>Активные партии</h3></div></div></section>;
  }

  return <>
    <section className="panel table-panel">
      <div className="panel-title"><div><p className="eyebrow dark">ОТПРАВКА</p><h3>Партии</h3></div><button className="action-button" onClick={load}>Обновить</button></div>
      <div className="order-tools">
        <label><span>Поиск по коду или складу</span><input value={queryDraft} onChange={(event) => updateSearch(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") setQuery(queryDraft.trim()); }} placeholder="Минимум 3 символа или Enter" /></label>
        <div className="status-pills"><span>{batches.length} всего</span><span>{batches.filter((batch) => batch.status === 0).length} открыто</span><span>{batches.filter((batch) => batch.status === 1).length} завершено</span></div>
      </div>
      {message && <p className="save-notice">{message}</p>}
      <div className="table-scroll"><table><thead><tr><th>Код</th><th>Название</th><th>Склад</th><th>Коробки</th><th>Вес</th><th>Статус</th><th></th></tr></thead><tbody>{visibleBatches.map((batch) => <tr key={batch.guid}><td><code>{batch.partial_code}</code></td><td><strong>{batch.partial_name}</strong></td><td>{batch.warehouse_name}</td><td>{batch.boxCount}</td><td>{formatWeight(batch.gross_weight)}</td><td>{batch.status === 1 ? <span className="status good">Завершена</span> : <span className="status">Открыта</span>}</td><td><button className="table-action" onClick={() => openBatch(batch)}>Открыть</button></td></tr>)}</tbody></table></div>
      {!visibleBatches.length && <p className="empty-state">Партии не найдены.</p>}
    </section>
    {active && <section className="panel editor-card">
      <div className="panel-title"><div><p className="eyebrow dark">ДЕТАЛИ ПАРТИИ</p><h3>{active.partial_code}</h3></div><button className="action-button" onClick={() => setActive(null)}>Закрыть</button></div>
      <div className="summary-strip"><div><small>Склад</small><strong>{active.warehouse_name}</strong></div><div><small>Коробки</small><strong>{boxes.length}</strong></div><div><small>Вес</small><strong>{formatWeight(active.gross_weight)}</strong></div><div><small>Статус</small><strong>{active.status === 1 ? "Завершена" : "Открыта"}</strong></div><div><small>Дата</small><strong>{formatDate(active.date_created)}</strong></div></div>
      {active.status === 0 && <button className="primary-button narrow" onClick={() => completeBatch(active)}>Завершить партию</button>}
      <div className="table-scroll"><table><thead><tr><th>Трек коробки</th><th>Размеры</th><th>Вес</th><th>Склад</th></tr></thead><tbody>{boxes.map((box) => <tr key={box.guid}><td><strong>{box.tracking_code}</strong></td><td>{box.length}×{box.width}×{box.height}</td><td>{formatWeight(box.gross_weight)}</td><td>{box.warehouse_name || active.warehouse_name}</td></tr>)}</tbody></table></div>
      {!boxes.length && <p className="empty-state">В партии пока нет коробок.</p>}
    </section>}
  </>;
}

function filterBoxes(boxes: BoxSummary[], query: string) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return boxes;
  return boxes.filter((box) =>
    box.tracking_code.toLowerCase().includes(normalized) ||
    box.warehouse.toLowerCase().includes(normalized) ||
    box.warehouse_name.toLowerCase().includes(normalized),
  );
}

function filterBatches(batches: BatchSummary[], query: string) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return batches;
  return batches.filter((batch) =>
    batch.partial_code.toLowerCase().includes(normalized) ||
    batch.partial_name.toLowerCase().includes(normalized) ||
    batch.warehouse_name.toLowerCase().includes(normalized),
  );
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
      <div className="panel-title"><div><p className="eyebrow dark">МАНИФЕСТ</p><h3>{active.manifest_code}</h3></div><div className="button-row"><button className="action-button" onClick={() => printManifest(active)}>Печать / PDF</button><button className="action-button" onClick={() => downloadManifestExcel(active)}>Excel</button><button className="action-button" onClick={() => setActive(null)}>Закрыть</button></div></div>
      <div className="summary-strip"><div><small>Партия</small><strong>{active.partial_code}</strong></div><div><small>Склад</small><strong>{active.warehouse_name}</strong></div><div><small>Коробки</small><strong>{active.box_count}</strong></div><div><small>Заказы</small><strong>{active.order_count}</strong></div><div><small>Общий вес</small><strong>{active.gross_weight.toFixed(3)} кг</strong></div></div>
      <div className="summary-strip currency-summary">{manifestCurrencyTotals(active).map(([currency, total]) => <div key={currency}><small>Стоимость {currency}</small><strong>{total.toFixed(2)} {currency}</strong></div>)}{!manifestCurrencyTotals(active).length && <div><small>Стоимость</small><strong>Нет данных</strong></div>}</div>
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
    {!client && <AdminDictionaries session={session} demo={demo} />}
  </article><article className="panel system-card"><p className="eyebrow dark">СИСТЕМА</p><h3>FastScan API</h3><dl><div><dt>Склад</dt><dd>{platform.warehouse || "BMG"}</dd></div><div><dt>Страна</dt><dd>{platform.country || "España"}</dd></div><div><dt>API</dt><dd>v0.4.0</dd></div><div><dt>Состояние</dt><dd><span className="signal-dot" /> Работает</dd></div></dl></article></section>;
}

function AdminDictionaries({ session, demo }: { session: Session | null; demo: boolean }) {
  const [compositions, setCompositions] = useState<DictionaryItem[]>([]);
  const [items, setItems] = useState<DictionaryItem[]>([]);
  const [selectedType, setSelectedType] = useState<DictionaryType>("WAREHOUSE");
  const [compositionDraft, setCompositionDraft] = useState<DictionaryDraft>({ code: "", name: "", sort_order: 0 });
  const [dictionaryDraft, setDictionaryDraft] = useState<DictionaryDraft>({ code: "", name: "", sort_order: 0 });
  const [compositionSearchDraft, setCompositionSearchDraft] = useState("");
  const [compositionSearch, setCompositionSearch] = useState("");
  const [dictionarySearchDraft, setDictionarySearchDraft] = useState("");
  const [dictionarySearch, setDictionarySearch] = useState("");
  const [message, setMessage] = useState("");

  async function loadCompositions() {
    if (!session || demo) return;
    setCompositions(await apiRequest<DictionaryItem[]>("/api/admin/order-compositions", {}, session));
  }

  async function loadDictionary(type = selectedType) {
    if (!session || demo) return;
    setItems(await apiRequest<DictionaryItem[]>(`/api/admin/dictionaries?type=${type}`, {}, session));
  }

  useEffect(() => {
    Promise.all([loadCompositions(), loadDictionary()]).catch((exception) =>
      setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить справочники"),
    );
  }, [session, demo]);

  useEffect(() => {
    setDictionaryDraft({ code: "", name: "", sort_order: 0 });
    setDictionarySearchDraft("");
    setDictionarySearch("");
    loadDictionary(selectedType).catch((exception) =>
      setMessage(exception instanceof Error ? exception.message : "Не удалось загрузить справочник"),
    );
  }, [selectedType]);

  function updateSearch(value: string, draft: (value: string) => void, apply: (value: string) => void) {
    draft(value);
    const normalized = value.trim();
    apply(normalized.length >= 3 ? normalized : "");
  }

  function filterDictionary(values: DictionaryItem[], search: string) {
    const normalized = search.trim().toLowerCase();
    if (!normalized) return values;
    return values.filter((item) =>
      item.code.toLowerCase().includes(normalized) ||
      item.name.toLowerCase().includes(normalized),
    );
  }

  async function saveComposition(event: FormEvent) {
    event.preventDefault();
    if (!session || demo) {
      setMessage("Демо: состав не сохранен на сервере");
      return;
    }
    setMessage("");
    try {
      await apiRequest(compositionDraft.guid ? `/api/admin/order-compositions/${compositionDraft.guid}` : "/api/admin/order-compositions", {
        method: compositionDraft.guid ? "PUT" : "POST",
        body: JSON.stringify(compositionDraft),
      }, session);
      setCompositionDraft({ code: "", name: "", sort_order: 0 });
      await loadCompositions();
      setMessage(compositionDraft.guid ? "Состав заказа обновлен" : "Состав заказа сохранен");
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось сохранить состав");
    }
  }

  async function saveDictionary(event: FormEvent) {
    event.preventDefault();
    if (!session || demo) {
      setMessage("Демо: значение не сохранено на сервере");
      return;
    }
    setMessage("");
    try {
      await apiRequest(dictionaryDraft.guid ? `/api/admin/dictionaries/${dictionaryDraft.guid}` : "/api/admin/dictionaries", {
        method: dictionaryDraft.guid ? "PUT" : "POST",
        body: JSON.stringify({ ...dictionaryDraft, type: selectedType }),
      }, session);
      setDictionaryDraft({ code: "", name: "", sort_order: 0 });
      await loadDictionary();
      setMessage(dictionaryDraft.guid ? "Значение справочника обновлено" : "Значение справочника сохранено");
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "Не удалось сохранить значение");
    }
  }

  async function toggleComposition(item: DictionaryItem) {
    if (!session || demo) return;
    const updated = await apiRequest<DictionaryItem>(`/api/admin/order-compositions/${item.guid}/enabled`, {
      method: "PUT",
      body: JSON.stringify({ enabled: !item.enabled }),
    }, session);
    setCompositions((current) => current.map((entry) => entry.guid === updated.guid ? updated : entry));
  }

  async function toggleDictionary(item: DictionaryItem) {
    if (!session || demo) return;
    const updated = await apiRequest<DictionaryItem>(`/api/admin/dictionaries/${item.guid}/enabled`, {
      method: "PUT",
      body: JSON.stringify({ enabled: !item.enabled }),
    }, session);
    setItems((current) => current.map((entry) => entry.guid === updated.guid ? updated : entry));
  }

  return <section className="admin-dictionaries">
    <div className="section-title"><div><p className="eyebrow dark">СПРАВОЧНИКИ</p><h4>Состав заказа</h4></div><button className="action-button" type="button" onClick={loadCompositions}>Обновить</button></div>
    <form className="dictionary-form" onSubmit={saveComposition}>
      <label><span>Код</span><input value={compositionDraft.code} onChange={(event) => setCompositionDraft({ ...compositionDraft, code: event.target.value.toUpperCase() })} /></label>
      <label><span>Название</span><input value={compositionDraft.name} onChange={(event) => setCompositionDraft({ ...compositionDraft, name: event.target.value })} /></label>
      <label><span>Порядок</span><input type="number" value={compositionDraft.sort_order} onChange={(event) => setCompositionDraft({ ...compositionDraft, sort_order: Number(event.target.value) })} /></label>
      <button className="primary-button" disabled={!compositionDraft.code.trim() || !compositionDraft.name.trim()}>{compositionDraft.guid ? "Сохранить" : "Добавить"}</button>
    </form>
    {compositionDraft.guid && <button className="demo-button compact" type="button" onClick={() => setCompositionDraft({ code: "", name: "", sort_order: 0 })}>Отменить редактирование</button>}
    <label className="dictionary-search"><span>Поиск по коду или названию</span><input value={compositionSearchDraft} onChange={(event) => updateSearch(event.target.value, setCompositionSearchDraft, setCompositionSearch)} onKeyDown={(event) => { if (event.key === "Enter") setCompositionSearch(compositionSearchDraft.trim()); }} placeholder="Минимум 3 символа или Enter" /></label>
    <DictionaryRows items={filterDictionary(compositions, compositionSearch)} toggle={toggleComposition} edit={(item) => setCompositionDraft({ guid: item.guid, code: item.code, name: item.name, sort_order: item.sort_order })} />

    <div className="section-title"><div><p className="eyebrow dark">НАСТРОЙКИ</p><h4>Системные значения</h4></div></div>
    <div className="dictionary-tabs">{dictionaryTypes.map(([type, label]) => <button key={type} type="button" className={selectedType === type ? "active" : ""} onClick={() => setSelectedType(type)}>{label}</button>)}</div>
    <form className="dictionary-form" onSubmit={saveDictionary}>
      <label><span>Код</span><input value={dictionaryDraft.code} onChange={(event) => setDictionaryDraft({ ...dictionaryDraft, code: event.target.value.toUpperCase() })} /></label>
      <label><span>Название</span><input value={dictionaryDraft.name} onChange={(event) => setDictionaryDraft({ ...dictionaryDraft, name: event.target.value })} /></label>
      <label><span>Порядок</span><input type="number" value={dictionaryDraft.sort_order} onChange={(event) => setDictionaryDraft({ ...dictionaryDraft, sort_order: Number(event.target.value) })} /></label>
      <button className="primary-button" disabled={!dictionaryDraft.code.trim() || !dictionaryDraft.name.trim()}>{dictionaryDraft.guid ? "Сохранить" : "Добавить"}</button>
    </form>
    {dictionaryDraft.guid && <button className="demo-button compact" type="button" onClick={() => setDictionaryDraft({ code: "", name: "", sort_order: 0 })}>Отменить редактирование</button>}
    <label className="dictionary-search"><span>Поиск по коду или названию</span><input value={dictionarySearchDraft} onChange={(event) => updateSearch(event.target.value, setDictionarySearchDraft, setDictionarySearch)} onKeyDown={(event) => { if (event.key === "Enter") setDictionarySearch(dictionarySearchDraft.trim()); }} placeholder="Минимум 3 символа или Enter" /></label>
    <DictionaryRows items={filterDictionary(items, dictionarySearch)} toggle={toggleDictionary} edit={(item) => setDictionaryDraft({ guid: item.guid, code: item.code, name: item.name, sort_order: item.sort_order })} />
    <p className="field-note">Используемые значения не удаляются: их можно отключить, чтобы скрыть из новых операций и сохранить историю.</p>
    {message && <p className="save-notice">{message}</p>}
  </section>;
}

function DictionaryRows({ items, toggle, edit }: { items: DictionaryItem[]; toggle: (item: DictionaryItem) => void; edit: (item: DictionaryItem) => void }) {
  return <div className="dictionary-list">
    {items.map((item) => <div className="dictionary-row" key={item.guid}>
      <code>{item.code}</code>
      <div><strong>{item.name}</strong><small>Порядок {item.sort_order} · используется {item.used_count}</small></div>
      <span className={item.enabled ? "status good" : "status blocked"}>{item.enabled ? "Активно" : "Отключено"}</span>
      <button className="table-action" type="button" onClick={() => edit(item)}>Изменить</button>
      <button className="table-action" type="button" onClick={() => toggle(item)}>{item.enabled ? "Отключить" : "Включить"}</button>
    </div>)}
    {!items.length && <p className="empty-state">Значения пока не добавлены.</p>}
  </div>;
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
