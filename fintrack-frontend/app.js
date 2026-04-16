const API_BASES = ["http://localhost:8081/api", "http://localhost:8080/api"];
const TX_PAGE_SIZE = 15;
const DASH_TX_PAGE_SIZE = 8;
const CATEGORY_COLORS = ["#c8f000", "#00e5ff", "#ff4d00", "#e8e4dc", "#7bd389", "#ff8fab"];

const APP_STATE = {
  token: null,
  user: null,
  apiBase: null,
  activePage: "dashboard",
  dataLoaded: false,
  analytics: {},
  intelligence: {},
  insights: {},
  transactions: [],
  accounts: [],
  budgets: [],
  goals: [],
  recurring: [],
  hmmByDate: {},
  charts: {},
  txPage: 0,
  selectedUploadAccount: "",
  dashboardFilters: { search: "", category: "", type: "", state: "", page: 1 },
};

const PAGE_STATE = {
  budgetsLoaded: false,
};

function asNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
}

function formatAmount(value) {
  return Math.round(asNumber(value)).toLocaleString("en-IN");
}

function formatCurrency(value) {
  return `â‚¹${formatAmount(value)}`;
}

function formatDateLabel(value) {
  if (!value) return "â€”";
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

function formatShortDate(value) {
  if (!value) return "â€”";
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString("en-IN", { day: "numeric", month: "short" });
}

function formatMonthLabel(value) {
  if (!value) return "â€”";
  const date = new Date(`${value}-01T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString("en-IN", { month: "short", year: "2-digit" });
}

function formatCompactCurrency(value) {
  const amount = asNumber(value);
  if (amount >= 100000) {
    return `â‚¹${(amount / 100000).toFixed(2)}L`;
  }
  if (amount >= 1000) {
    return `â‚¹${(amount / 1000).toFixed(1)}K`;
  }
  return formatCurrency(amount);
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => (
    {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[char]
  ));
}

function normalizeType(value) {
  if (!value) return "";
  const upper = String(value).toUpperCase();
  if (upper === "CREDIT") return "INCOME";
  if (upper === "DEBIT") return "EXPENSE";
  return upper;
}

function normalizeHmmState(value) {
  if (!value) return "NORMAL";
  const upper = String(value).toUpperCase();
  if (upper.includes("LOW")) return "LOW";
  if (upper.includes("HIGH")) return "HIGH";
  return "NORMAL";
}

function getStateColor(state) {
  if (state === "LOW") return "var(--acid)";
  if (state === "HIGH") return "var(--ember)";
  return "#ffd166";
}

function getStateBadge(state) {
  const normalized = normalizeHmmState(state);
  return `<span style="display:inline-flex;align-items:center;gap:6px;font-family:var(--font-mono);font-size:9px;letter-spacing:0.08em;text-transform:uppercase;color:${getStateColor(normalized)}">${normalized}</span>`;
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element) {
    element.textContent = value;
  }
}

function revealElements(container = document) {
  const elements = Array.from(container.querySelectorAll?.(".reveal") || []);
  if (!elements.length) return;

  elements.forEach((element) => {
    element.style.opacity = "1";
    element.style.transform = "translateY(0)";
    element.style.transition = "opacity 0.35s ease, transform 0.35s ease";
  });
}

function showToast(message, type = "info") {
  const toast = document.createElement("div");
  toast.style.cssText = `position:fixed;bottom:24px;right:24px;z-index:10000;background:var(--card);border:1px solid var(--line);color:var(--offwhite);padding:16px 20px;font-family:var(--font-mono);font-size:11px;letter-spacing:0.08em;border-left:3px solid ${type === "error" ? "var(--ember)" : type === "success" ? "var(--acid)" : "var(--ice)"};max-width:360px`;
  toast.textContent = message;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 3200);
}

function showFailure(message, options = {}) {
  const { alertUser = true } = options;
  console.error(message);
  showToast(message, "error");
  if (alertUser) {
    window.alert(message);
  }
}

function getApiBases() {
  if (!APP_STATE.apiBase) return [...API_BASES];
  return [APP_STATE.apiBase, ...API_BASES.filter((base) => base !== APP_STATE.apiBase)];
}

function loadAuthState() {
  APP_STATE.token = localStorage.getItem("token");
  try {
    APP_STATE.user = JSON.parse(localStorage.getItem("fintrack_user") || "null");
  } catch {
    APP_STATE.user = null;
  }
}

function saveAuthState(token, user) {
  APP_STATE.token = token;
  APP_STATE.user = user || null;
  localStorage.setItem("token", token);
  localStorage.setItem("fintrack_user", JSON.stringify(user || null));
  console.log("TOKEN STORED");
}

function clearAuth() {
  APP_STATE.token = null;
  APP_STATE.user = null;
  localStorage.removeItem("token");
  localStorage.removeItem("fintrack_user");
}

function getUserDisplayName() {
  const user = APP_STATE.user || {};
  return user.fullName || user.name || user.username || user.email || "User";
}

function getUserInitial() {
  const label = getUserDisplayName().trim();
  return label ? label.charAt(0).toUpperCase() : "U";
}

function renderUserChip() {
  setText("user-name", getUserDisplayName());
  setText("user-initial", getUserInitial());
}

function setButtonBusy(button, busy, idleLabel, busyLabel) {
  if (!button) return;
  button.disabled = busy;
  button.textContent = busy ? busyLabel : idleLabel;
  button.style.opacity = busy ? "0.7" : "1";
  button.style.cursor = busy ? "wait" : "pointer";
}

function normalizeAuthUser(user, fallback = {}) {
  if (user && typeof user === "object") {
    return {
      ...user,
      fullName: user.fullName || user.name || fallback.fullName || null,
      email: user.email || fallback.email || null,
    };
  }

  return {
    fullName: fallback.fullName || null,
    email: fallback.email || null,
  };
}

function goToLoginPage(message = "") {
  document.body.style.cursor = "auto";
  document.body.innerHTML = `
    <div style="min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0a0a08;padding:24px">
      <div style="width:100%;max-width:440px;border:1px solid rgba(255,255,255,0.08);background:#131310;padding:36px;color:#e8e4dc;font-family:'Barlow',sans-serif">
        <div style="font-family:'Barlow Condensed',sans-serif;font-size:32px;font-weight:900;letter-spacing:0.14em;text-transform:uppercase;margin-bottom:12px">FIN<span style="color:#c8f000">TRACK</span></div>
        <div style="font-family:'IBM Plex Mono',monospace;font-size:10px;letter-spacing:0.18em;color:#5a5a52;text-transform:uppercase;margin-bottom:20px">Login</div>
        ${message ? `<div style="margin-bottom:16px;padding:12px;border:1px solid rgba(255,77,0,0.3);background:rgba(255,77,0,0.08);font-family:'IBM Plex Mono',monospace;font-size:10px;color:#ffb4a2">${escapeHtml(message)}</div>` : ""}
        <form id="login-form" style="display:flex;flex-direction:column;gap:14px">
          <input id="login-email" type="email" autocomplete="email" placeholder="Email" required style="background:#0f0f0c;border:1px solid rgba(255,255,255,0.08);color:#e8e4dc;padding:12px 14px;font-family:'IBM Plex Mono',monospace;font-size:12px;outline:none" />
          <input id="login-password" type="password" autocomplete="current-password" placeholder="Password" required style="background:#0f0f0c;border:1px solid rgba(255,255,255,0.08);color:#e8e4dc;padding:12px 14px;font-family:'IBM Plex Mono',monospace;font-size:12px;outline:none" />
          <button id="login-submit" type="submit" style="background:#c8f000;color:#000;border:none;padding:12px 14px;font-family:'Barlow Condensed',sans-serif;font-weight:700;letter-spacing:0.14em;text-transform:uppercase;cursor:pointer">Login</button>
        </form>
        <div style="margin-top:12px;font-family:'IBM Plex Mono',monospace;font-size:10px;color:#5a5a52;line-height:1.6">Demo account: <span style="color:#e8e4dc">test@fintrack.com</span> / <span style="color:#e8e4dc">password123</span></div>
        <form id="register-form" style="display:flex;flex-direction:column;gap:14px;margin-top:24px;padding-top:20px;border-top:1px solid rgba(255,255,255,0.08)">
          <input id="register-name" type="text" autocomplete="name" placeholder="Full name" required style="background:#0f0f0c;border:1px solid rgba(255,255,255,0.08);color:#e8e4dc;padding:12px 14px;font-family:'IBM Plex Mono',monospace;font-size:12px;outline:none" />
          <input id="register-email" type="email" autocomplete="email" placeholder="Email" required style="background:#0f0f0c;border:1px solid rgba(255,255,255,0.08);color:#e8e4dc;padding:12px 14px;font-family:'IBM Plex Mono',monospace;font-size:12px;outline:none" />
          <input id="register-password" type="password" autocomplete="new-password" placeholder="Password (min 8 chars)" required style="background:#0f0f0c;border:1px solid rgba(255,255,255,0.08);color:#e8e4dc;padding:12px 14px;font-family:'IBM Plex Mono',monospace;font-size:12px;outline:none" />
          <button id="register-submit" type="submit" style="background:transparent;color:#00e5ff;border:1px solid rgba(0,229,255,0.28);padding:12px 14px;font-family:'Barlow Condensed',sans-serif;font-weight:700;letter-spacing:0.14em;text-transform:uppercase;cursor:pointer">Register</button>
        </form>
      </div>
    </div>`;

  document.getElementById("login-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    await handleLogin(document.getElementById("login-email").value, document.getElementById("login-password").value);
  });

  document.getElementById("register-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    await handleRegister(
      document.getElementById("register-name").value,
      document.getElementById("register-email").value,
      document.getElementById("register-password").value,
    );
  });
}

function handleUnauthorized() {
  clearAuth();
  goToLoginPage("Session expired. Please log in again.");
}

async function apiRequest(endpoint, options = {}) {
  const { method = "GET", body = null, headers = {}, silent = false, throwOnError = false } = options;
  const path = endpoint.startsWith("/") ? endpoint : `/${endpoint}`;
  const bases = getApiBases();
  let lastError = null;

  for (let index = 0; index < bases.length; index += 1) {
    const base = bases[index];
    const url = `${base}${path}`;
    const requestHeaders = { ...headers };
    if (!(body instanceof FormData) && !requestHeaders["Content-Type"]) {
      requestHeaders["Content-Type"] = "application/json";
    }
    if (APP_STATE.token) {
      requestHeaders.Authorization = `Bearer ${APP_STATE.token}`;
    }

    const requestOptions = { method, headers: requestHeaders };
    if (body !== null) {
      requestOptions.body = body instanceof FormData ? body : JSON.stringify(body);
    }

    try {
      console.log("[API REQUEST]", method, url, body instanceof FormData ? "[FormData]" : body || "");
      const response = await fetch(url, requestOptions);
      const contentType = (response.headers.get("content-type") || "").toLowerCase();
      const payload = contentType.includes("application/json")
        ? await response.json().catch(() => null)
        : await response.text().catch(() => null);
      console.log("[API RESPONSE]", response.status, url, payload);

      if (response.status === 401 || response.status === 403) {
        handleUnauthorized();
        return null;
      }

      if (response.ok) {
        APP_STATE.apiBase = base;
        return payload;
      }

      const errorMessage = payload?.message || payload?.error || `HTTP ${response.status}`;
      lastError = new Error(errorMessage);
      if (response.status === 404 && index < bases.length - 1) {
        continue;
      }
      throw lastError;
    } catch (error) {
      lastError = error;
      console.error("[API RESPONSE]", url, error);
      const isNetworkError = error instanceof TypeError;
      if (isNetworkError && index < bases.length - 1) {
        continue;
      }
      break;
    }
  }

  if (throwOnError && lastError) {
    throw lastError;
  }
  if (!silent) {
    showFailure(lastError?.message || `Request failed for ${path}`);
  }
  return null;
}
function normalizeTransaction(transaction) {
  const type = normalizeType(transaction.type || (asNumber(transaction.amount) >= 0 ? "INCOME" : "EXPENSE"));
  const date = transaction.transactionDate || transaction.date || "";
  const state = normalizeHmmState(
    transaction.state
    || transaction.spendingState
    || transaction.hmmState
    || transaction.hmm_state,
  );
  return {
    id: transaction.id,
    description: transaction.description || transaction.entity || transaction.merchant || transaction.name || "Transaction",
    subText: transaction.notes || transaction.merchant || "",
    category: transaction.categoryName || transaction.category || "Uncategorized",
    accountName: transaction.accountName || transaction.account || "â€”",
    date,
    time: transaction.transactionTime || transaction.time || "",
    amount: asNumber(transaction.amount),
    type,
    isRecurring: Boolean(transaction.isRecurring || transaction.is_recurring),
    hmmState: state,
  };
}

function buildHmmLookup(intelligence) {
  const lookup = {};
  const timeline = intelligence?.hidden_state_timeline;
  if (Array.isArray(timeline)) {
    timeline.forEach((item) => {
      if (item?.date) {
        lookup[item.date] = normalizeHmmState(item.hidden_state);
      }
    });
  }
  return lookup;
}

function destroyChart(name) {
  if (APP_STATE.charts[name]) {
    APP_STATE.charts[name].destroy();
    APP_STATE.charts[name] = null;
  }
}

function getMonthlyTrendData() {
  if (Array.isArray(APP_STATE.analytics?.monthlyTrend) && APP_STATE.analytics.monthlyTrend.length) {
    return APP_STATE.analytics.monthlyTrend;
  }
  if (Array.isArray(APP_STATE.analytics?.monthlyData) && APP_STATE.analytics.monthlyData.length) {
    return APP_STATE.analytics.monthlyData;
  }
  return [];
}

function buildPolylinePath(points) {
  if (!points.length) return "";
  return points.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`).join(" ");
}

function renderTrendSvg(svgId, items, series) {
  const svg = document.getElementById(svgId);
  if (!svg) return;

  const rows = Array.isArray(items) ? items : [];
  if (!rows.length) {
    svg.innerHTML = `<text x="50%" y="50%" text-anchor="middle" fill="rgba(255,255,255,0.28)" font-size="10" font-family="var(--font-mono)">No trend data available</text>`;
    return;
  }

  const width = svg.clientWidth || 760;
  const height = svg.clientHeight || 240;
  const margin = { top: 16, right: 20, bottom: 28, left: 36 };
  const plotWidth = Math.max(width - margin.left - margin.right, 20);
  const plotHeight = Math.max(height - margin.top - margin.bottom, 20);
  const maxValue = Math.max(
    ...rows.flatMap((item) => series.map((entry) => asNumber(item[entry.key]))),
    1,
  );
  const xForIndex = (index) => (
    rows.length === 1
      ? margin.left + (plotWidth / 2)
      : margin.left + ((plotWidth / (rows.length - 1)) * index)
  );
  const yForValue = (value) => margin.top + plotHeight - ((asNumber(value) / maxValue) * plotHeight);

  const gridLines = Array.from({ length: 4 }, (_, index) => {
    const y = margin.top + ((plotHeight / 3) * index);
    return `<line x1="${margin.left}" y1="${y}" x2="${width - margin.right}" y2="${y}" stroke="rgba(255,255,255,0.06)" stroke-width="1" />`;
  }).join("");

  const xLabels = rows.map((item, index) => {
    const x = xForIndex(index);
    return `<text x="${x}" y="${height - 8}" text-anchor="middle" fill="rgba(255,255,255,0.38)" font-size="8" font-family="var(--font-mono)">${escapeHtml(formatMonthLabel(item.month || item.period))}</text>`;
  }).join("");

  const paths = series.map((entry) => {
    const points = rows.map((item, index) => ({
      x: xForIndex(index),
      y: yForValue(item[entry.key]),
      value: asNumber(item[entry.key]),
    }));
    const path = buildPolylinePath(points);
    const pointDots = points.map((point) => `<circle cx="${point.x}" cy="${point.y}" r="2.5" fill="${entry.color}" />`).join("");
    return `<path d="${path}" fill="none" stroke="${entry.color}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />${pointDots}`;
  }).join("");

  svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
  svg.innerHTML = `${gridLines}${paths}${xLabels}`;
}

function renderDonutSvg(svgId, items, centerId, legendId) {
  const svg = document.getElementById(svgId);
  const center = centerId ? document.getElementById(centerId) : null;
  const legend = legendId ? document.getElementById(legendId) : null;
  if (!svg) return;

  const normalizedItems = (Array.isArray(items) ? items : []).filter((item) => asNumber(item.value) > 0);
  const total = normalizedItems.reduce((sum, item) => sum + asNumber(item.value), 0);

  if (!normalizedItems.length || total <= 0) {
    svg.innerHTML = `<circle cx="80" cy="80" r="54" fill="none" stroke="rgba(255,255,255,0.08)" stroke-width="18" />`;
    if (center) center.textContent = "â€”";
    if (legend) legend.innerHTML = '<div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-transform:uppercase">No category data available</div>';
    return;
  }

  const radius = svgId === "donut" ? 46 : 54;
  const strokeWidth = svgId === "donut" ? 16 : 18;
  const centerPoint = svgId === "donut" ? 65 : 80;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;

  svg.innerHTML = normalizedItems.map((item) => {
    const segment = (asNumber(item.value) / total) * circumference;
    const circle = `<circle cx="${centerPoint}" cy="${centerPoint}" r="${radius}" fill="none" stroke="${item.color}" stroke-width="${strokeWidth}" stroke-dasharray="${segment} ${Math.max(circumference - segment, 0)}" stroke-dashoffset="${-offset}" transform="rotate(-90 ${centerPoint} ${centerPoint})" stroke-linecap="butt" />`;
    offset += segment;
    return circle;
  }).join("");

  if (center) {
    center.textContent = formatCompactCurrency(total);
  }
  if (legend) {
    legend.innerHTML = normalizedItems.map((item) => `
      <div style="display:flex;align-items:center;justify-content:space-between;gap:12px">
        <div style="display:flex;align-items:center;gap:10px;font-family:var(--font-mono);font-size:9px;color:var(--offwhite);text-transform:uppercase;letter-spacing:0.08em">
          <span style="width:8px;height:8px;border-radius:50%;display:inline-block;background:${item.color}"></span>
          <span>${escapeHtml(item.label)}</span>
        </div>
        <div style="font-family:var(--font-display);font-size:15px;color:${item.color}">${formatCurrency(item.value)}</div>
      </div>`).join("");
  }
}

function getCategorySeries() {
  if (Array.isArray(APP_STATE.analytics?.topCategories) && APP_STATE.analytics.topCategories.length) {
    return APP_STATE.analytics.topCategories.map((item, index) => ({
      label: item.category || `Category ${index + 1}`,
      value: asNumber(item.amount),
      color: item.color || CATEGORY_COLORS[index % CATEGORY_COLORS.length],
    }));
  }

  if (Array.isArray(APP_STATE.intelligence?.category_distribution) && APP_STATE.intelligence.category_distribution.length) {
    return APP_STATE.intelligence.category_distribution.map((item, index) => ({
      label: item.category || `Category ${index + 1}`,
      value: asNumber(item.amount),
      color: CATEGORY_COLORS[index % CATEGORY_COLORS.length],
    }));
  }

  return [];
}

function getAnomalyItems() {
  if (Array.isArray(APP_STATE.insights?.anomalies) && APP_STATE.insights.anomalies.length) {
    return APP_STATE.insights.anomalies.map((item) => ({
      title: item.type || "Anomaly",
      message: item.description || item.message || "Anomaly detected",
      amount: item.amount,
    }));
  }

  if (Array.isArray(APP_STATE.intelligence?.alerts) && APP_STATE.intelligence.alerts.length) {
    return APP_STATE.intelligence.alerts.map((item) => ({
      title: item.title || item.level || "Alert",
      message: item.detail || item.title || "Alert detected",
      amount: null,
    }));
  }

  return [];
}

function updateTransactionFilterLabels() {
  const transactionTypeFilter = document.getElementById("tx-filter-type");
  if (transactionTypeFilter) {
    [...transactionTypeFilter.options].forEach((option) => {
      if (option.value === "CREDIT") option.value = "INCOME";
      if (option.value === "DEBIT") option.value = "EXPENSE";
    });
  }

  const header = document.querySelector("#page-transactions .tx-full-header");
  if (header?.children?.[3]) {
    header.children[3].textContent = "HMM State";
  }
}

function updateNavigation(page) {
  APP_STATE.activePage = page;
  document.getElementById("content")?.scrollTo({ top: 0, behavior: "auto" });
  document.querySelectorAll(".page").forEach((element) => element.classList.remove("active"));
  document.getElementById(`page-${page}`)?.classList.add("active");
  document.querySelectorAll("[data-page]").forEach((element) => {
    element.classList.toggle("active", element.classList.contains("nav-icon") && element.dataset.page === page);
    element.classList.toggle("on", element.classList.contains("tn") && element.dataset.page === page);
  });
  setText("tb-section-label", page.charAt(0).toUpperCase() + page.slice(1));
}

function wireCursor() {
  const cursor = document.getElementById("cursor");
  const outer = document.getElementById("cursor-outer");
  if (!cursor || !outer) return;

  document.addEventListener("mousemove", (event) => {
    cursor.style.transform = `translate(${event.clientX - 4}px, ${event.clientY - 4}px)`;
    outer.style.transform = `translate(${event.clientX - 16}px, ${event.clientY - 16}px)`;
  });
}

function populateCategoryFilters() {
  const categories = [...new Set(APP_STATE.transactions.map((transaction) => transaction.category).filter(Boolean))].sort();
  const filters = [document.getElementById("tx-filter-cat"), document.getElementById("dash-catFilter")].filter(Boolean);
  filters.forEach((select) => {
    const currentValue = select.value;
    select.innerHTML = '<option value="">All Categories</option>';
    categories.forEach((category) => {
      const option = document.createElement("option");
      option.value = category;
      option.textContent = category;
      select.appendChild(option);
    });
    select.value = categories.includes(currentValue) ? currentValue : "";
  });
}

function renderTransactionTable() {
  const list = document.getElementById("tx-full-list");
  if (!list) return;

  const search = (document.getElementById("tx-search")?.value || "").trim().toLowerCase();
  const typeFilter = normalizeType(document.getElementById("tx-filter-type")?.value || "");
  const categoryFilter = document.getElementById("tx-filter-cat")?.value || "";

  const filtered = APP_STATE.transactions.filter((transaction) => {
    const matchesSearch = !search || `${transaction.description} ${transaction.category}`.toLowerCase().includes(search);
    const matchesType = !typeFilter || transaction.type === typeFilter;
    const matchesCategory = !categoryFilter || transaction.category === categoryFilter;
    return matchesSearch && matchesType && matchesCategory;
  });

  const totalPages = Math.max(1, Math.ceil(filtered.length / TX_PAGE_SIZE));
  if (APP_STATE.txPage >= totalPages) {
    APP_STATE.txPage = 0;
  }

  const pageItems = filtered.slice(APP_STATE.txPage * TX_PAGE_SIZE, (APP_STATE.txPage + 1) * TX_PAGE_SIZE);
  if (!pageItems.length) {
    list.innerHTML = '<div class="page-loading">No transactions found</div>';
  } else {
    list.innerHTML = pageItems.map((transaction, index) => `
      <div class="tx-full-row">
        <span class="tx-row-num">${APP_STATE.txPage * TX_PAGE_SIZE + index + 1}</span>
        <div>
          <div class="tx-full-name">${escapeHtml(transaction.description)}</div>
          <div class="tx-full-sub">${escapeHtml(transaction.subText || transaction.accountName)}</div>
        </div>
        <span class="tx-full-cat">${escapeHtml(transaction.category)}</span>
        <span class="tx-full-acc">${getStateBadge(transaction.hmmState)}</span>
        <span class="tx-full-date">${formatShortDate(transaction.date)}</span>
        <span class="tx-full-amount ${transaction.type === "INCOME" ? "up" : "dn"}">${transaction.type === "INCOME" ? "+" : "-"}${formatCurrency(transaction.amount)}</span>
      </div>`).join("");
  }

  setText("tx-page-info", `PAGE ${APP_STATE.txPage + 1} / ${totalPages}`);
  const previous = document.getElementById("tx-prev");
  const next = document.getElementById("tx-next");
  if (previous) previous.disabled = APP_STATE.txPage === 0;
  if (next) next.disabled = APP_STATE.txPage >= totalPages - 1;
}

function renderDashboardSummary() {
  const analytics = APP_STATE.analytics || {};
  const totalIncome = asNumber(analytics.totalIncome);
  const totalExpenses = asNumber(analytics.totalExpenses || analytics.totalExpense);
  const netSavings = asNumber(analytics.netSavings || totalIncome - totalExpenses);
  const savingsRate = analytics.savingsRate != null
    ? `${Number(analytics.savingsRate).toFixed(1)}%`
    : `${(totalIncome > 0 ? (netSavings / totalIncome) * 100 : 0).toFixed(1)}%`;
  const totalBalance = asNumber(analytics.totalBalance || analytics.accountBalance);

  setText("dash-totalIncome", formatCurrency(totalIncome));
  setText("dash-totalExpenses", formatCurrency(totalExpenses));
  setText("dash-netSavings", formatCurrency(netSavings));
  setText("dash-savingsRate", savingsRate);
  setText("dash-totalBalance", formatCurrency(totalBalance));
}

function renderDashboardInsights() {
  const categories = getCategorySeries().sort((left, right) => right.value - left.value);
  if (categories[0]) {
    setText("dash-topCategory", categories[0].label);
    const total = categories.reduce((sum, item) => sum + item.value, 0);
    const percentage = total > 0 ? ((categories[0].value / total) * 100).toFixed(1) : "0.0";
    setText("dash-topCategoryPercent", `${percentage}% of spending`);
  }

  const merchants = new Map();
  APP_STATE.transactions.forEach((transaction) => {
    merchants.set(transaction.description, (merchants.get(transaction.description) || 0) + 1);
  });
  const topMerchant = [...merchants.entries()].sort((left, right) => right[1] - left[1])[0];
  if (topMerchant) {
    setText("dash-topMerchant", topMerchant[0]);
    setText("dash-topMerchantCount", `${topMerchant[1]} transactions`);
  }

  const unusualAmount = APP_STATE.transactions
    .filter((transaction) => transaction.type === "EXPENSE")
    .reduce((max, transaction) => Math.max(max, transaction.amount), 0);
  setText("dash-unusualAmount", formatCurrency(unusualAmount));
  setText("dash-recurringCount", String(APP_STATE.transactions.filter((transaction) => transaction.isRecurring).length));
}
function renderDashboardAnomalies() {
  const container = document.getElementById("dash-anomalies");
  if (!container) return;

  const anomalies = getAnomalyItems();
  if (!anomalies.length) {
    container.innerHTML = '<div class="alert-box"><span style="color:var(--muted);font-family:var(--font-mono);font-size:10px;text-transform:uppercase">No anomalies detected</span></div>';
    return;
  }

  container.innerHTML = anomalies.map((item) => `
    <div class="alert-box alert-warning">
      <div style="font-family:var(--font-mono);font-size:8px;letter-spacing:0.12em;color:var(--muted);text-transform:uppercase">${escapeHtml(item.title)}</div>
      <div style="margin-top:6px;color:var(--offwhite)">${escapeHtml(item.message)}${item.amount ? ` â€¢ ${formatCurrency(item.amount)}` : ""}</div>
    </div>`).join("");
}

function renderDashboardCharts() {
  if (typeof Chart === "undefined") return;

  const categoryCanvas = document.getElementById("dash-categoryChart");
  const trendCanvas = document.getElementById("dash-trendChart");
  const hmmCanvas = document.getElementById("dash-hmmChart");
  const categories = getCategorySeries();
  const monthlyTrend = Array.isArray(APP_STATE.analytics?.monthlyTrend)
    ? APP_STATE.analytics.monthlyTrend
    : Array.isArray(APP_STATE.analytics?.monthlyData)
      ? APP_STATE.analytics.monthlyData
      : [];
  const stateCounts = { LOW: 0, NORMAL: 0, HIGH: 0 };
  Object.values(APP_STATE.hmmByDate).forEach((state) => {
    stateCounts[normalizeHmmState(state)] += 1;
  });
  if (!Object.keys(APP_STATE.hmmByDate).length) {
    APP_STATE.transactions.forEach((transaction) => {
      stateCounts[normalizeHmmState(transaction.hmmState)] += 1;
    });
  }

  destroyChart("category");
  destroyChart("trend");
  destroyChart("hmm");

  if (categoryCanvas) {
    APP_STATE.charts.category = new Chart(categoryCanvas, {
      type: "doughnut",
      data: {
        labels: categories.map((item) => item.label),
        datasets: [{
          data: categories.map((item) => item.value),
          backgroundColor: categories.map((item, index) => item.color || CATEGORY_COLORS[index % CATEGORY_COLORS.length]),
          borderColor: "#0a0a08",
          borderWidth: 2,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { labels: { color: "#5a5a52" } } },
      },
    });
  }

  if (trendCanvas) {
    APP_STATE.charts.trend = new Chart(trendCanvas, {
      type: "line",
      data: {
        labels: monthlyTrend.map((item) => formatMonthLabel(item.month)),
        datasets: [
          {
            label: "Income",
            data: monthlyTrend.map((item) => asNumber(item.income)),
            borderColor: "#c8f000",
            backgroundColor: "rgba(200,240,0,0.12)",
            tension: 0.35,
            borderWidth: 2,
          },
          {
            label: "Expenses",
            data: monthlyTrend.map((item) => asNumber(item.expenses)),
            borderColor: "#ff4d00",
            backgroundColor: "rgba(255,77,0,0.10)",
            tension: 0.35,
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { labels: { color: "#5a5a52" } } },
        scales: {
          x: { ticks: { color: "#5a5a52" }, grid: { color: "rgba(255,255,255,0.04)" } },
          y: { ticks: { color: "#5a5a52" }, grid: { color: "rgba(255,255,255,0.04)" } },
        },
      },
    });
  }

  if (hmmCanvas) {
    APP_STATE.charts.hmm = new Chart(hmmCanvas, {
      type: "bar",
      data: {
        labels: ["LOW", "NORMAL", "HIGH"],
        datasets: [{
          data: [stateCounts.LOW, stateCounts.NORMAL, stateCounts.HIGH],
          backgroundColor: ["#36d399", "#ffd166", "#ff4d00"],
          borderColor: "#0a0a08",
          borderWidth: 1,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { ticks: { color: "#5a5a52" }, grid: { color: "rgba(255,255,255,0.04)" } },
          y: { ticks: { color: "#5a5a52" }, grid: { color: "rgba(255,255,255,0.04)" } },
        },
      },
    });
  }

  const totalStates = stateCounts.LOW + stateCounts.NORMAL + stateCounts.HIGH;
  setText("dash-stateLow", String(stateCounts.LOW));
  setText("dash-stateNormal", String(stateCounts.NORMAL));
  setText("dash-stateHigh", String(stateCounts.HIGH));
  setText("dash-stateLowPct", `${totalStates ? Math.round((stateCounts.LOW / totalStates) * 100) : 0}% of days`);
  setText("dash-stateNormalPct", `${totalStates ? Math.round((stateCounts.NORMAL / totalStates) * 100) : 0}% of days`);
  setText("dash-stateHighPct", `${totalStates ? Math.round((stateCounts.HIGH / totalStates) * 100) : 0}% of days`);
}

function renderDashboardTransactionTable() {
  const tableBody = document.getElementById("dash-txTable");
  const pagination = document.getElementById("dash-pagination");
  if (!tableBody) return;

  const { search, category, type, state } = APP_STATE.dashboardFilters;
  const filtered = APP_STATE.transactions.filter((transaction) => {
    const matchesSearch = !search || `${transaction.description} ${transaction.category}`.toLowerCase().includes(search.toLowerCase());
    const matchesCategory = !category || transaction.category === category;
    const matchesType = !type || transaction.type === type;
    const matchesState = !state || normalizeHmmState(transaction.hmmState) === normalizeHmmState(state);
    return matchesSearch && matchesCategory && matchesType && matchesState;
  });

  const totalPages = Math.max(1, Math.ceil(filtered.length / DASH_TX_PAGE_SIZE));
  if (APP_STATE.dashboardFilters.page > totalPages) {
    APP_STATE.dashboardFilters.page = 1;
  }

  const start = (APP_STATE.dashboardFilters.page - 1) * DASH_TX_PAGE_SIZE;
  const items = filtered.slice(start, start + DASH_TX_PAGE_SIZE);
  if (!items.length) {
    tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--muted);padding:32px 0;font-family:var(--font-mono);font-size:10px;letter-spacing:0.15em;text-transform:uppercase">No transactions found</td></tr>';
  } else {
    tableBody.innerHTML = items.map((transaction) => `
      <tr>
        <td style="font-family:var(--font-mono);font-size:9px;color:var(--muted);padding:14px 0;border-bottom:1px solid var(--line2)">${formatDateLabel(transaction.date)}</td>
        <td style="padding:14px 0;border-bottom:1px solid var(--line2)"><div style="font-family:var(--font-display);font-weight:700;letter-spacing:0.04em;text-transform:uppercase;color:var(--offwhite);font-size:14px">${escapeHtml(transaction.description)}</div><div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);letter-spacing:0.1em;margin-top:3px">${escapeHtml(transaction.accountName)}</div></td>
        <td style="font-family:var(--font-mono);font-size:9px;color:var(--muted);padding:14px 0;border-bottom:1px solid var(--line2)">${transaction.type === "INCOME" ? "Credit" : "Debit"}</td>
        <td style="font-family:var(--font-display);font-size:16px;font-weight:700;padding:14px 0;border-bottom:1px solid var(--line2);color:${transaction.type === "INCOME" ? "var(--acid)" : "var(--ember)"}">${transaction.type === "INCOME" ? "+" : "-"}${formatCurrency(transaction.amount)}</td>
        <td style="font-family:var(--font-mono);font-size:9px;color:var(--muted);padding:14px 0;border-bottom:1px solid var(--line2);text-transform:uppercase;letter-spacing:0.08em">${escapeHtml(transaction.category)}</td>
        <td style="padding:14px 0;border-bottom:1px solid var(--line2)">${getStateBadge(transaction.hmmState)}</td>
      </tr>`).join("");
  }

  if (pagination) {
    pagination.innerHTML = Array.from({ length: totalPages }, (_, index) => index + 1).map((pageNumber) => `
      <button style="font-family:var(--font-mono);padding:6px 12px;border:1px solid var(--line);background:${pageNumber === APP_STATE.dashboardFilters.page ? "rgba(200,240,0,0.10)" : "transparent"};color:${pageNumber === APP_STATE.dashboardFilters.page ? "var(--acid)" : "var(--muted)"};cursor:pointer;font-size:10px" data-dash-page="${pageNumber}">${pageNumber}</button>`).join("");
    pagination.querySelectorAll("[data-dash-page]").forEach((button) => {
      button.addEventListener("click", () => {
        APP_STATE.dashboardFilters.page = Number(button.dataset.dashPage);
        renderDashboardTransactionTable();
      });
    });
  }
}

function renderOverviewPage() {
  const page = document.getElementById("page-overview");
  if (!page) return;

  const analytics = APP_STATE.analytics || {};
  const monthlyTrend = getMonthlyTrendData();
  const totalIncome = asNumber(analytics.totalIncome);
  const totalExpenses = asNumber(analytics.totalExpenses || analytics.totalExpense);
  const netSavings = asNumber(analytics.netSavings || totalIncome - totalExpenses);
  const totalBalance = asNumber(analytics.totalBalance || analytics.accountBalance);
  const savingsRate = analytics.savingsRate != null
    ? Number(analytics.savingsRate).toFixed(1)
    : (totalIncome > 0 ? ((netSavings / totalIncome) * 100) : 0).toFixed(1);
  const heroParts = totalBalance.toFixed(2).split(".");
  const heroDelta = page.querySelector(".badge-delta");
  const heroLabel = page.querySelector(".badge-label");
  const heroCounters = page.querySelectorAll(".mc");
  const latestMonth = monthlyTrend[monthlyTrend.length - 1];
  const previousMonth = monthlyTrend[monthlyTrend.length - 2];
  const latestNet = asNumber(latestMonth?.net);
  const previousNet = asNumber(previousMonth?.net);
  const growth = previousNet > 0 ? (((latestNet - previousNet) / previousNet) * 100) : 0;

  setText("hero-int", formatAmount(heroParts[0]));
  setText("hero-dec", heroParts[1]);
  if (heroDelta) heroDelta.textContent = `${growth >= 0 ? "+" : ""}${growth.toFixed(1)}% THIS MONTH`;
  if (heroLabel) heroLabel.textContent = `Savings rate ${savingsRate}% with balance ${formatCompactCurrency(totalBalance)}`;
  if (heroCounters[0]) heroCounters[0].textContent = formatAmount(totalIncome);
  if (heroCounters[1]) heroCounters[1].textContent = formatAmount(totalExpenses);
  if (heroCounters[2]) heroCounters[2].textContent = formatAmount(netSavings);
  const heroDeltas = page.querySelectorAll(".hs-delta");
  if (heroDeltas[0]) heroDeltas[0].textContent = `${monthlyTrend.length ? formatMonthLabel(latestMonth?.month) : "Current month"} income`;
  if (heroDeltas[1]) heroDeltas[1].textContent = `${monthlyTrend.length ? formatMonthLabel(latestMonth?.month) : "Current month"} spend`;
  if (heroDeltas[2]) heroDeltas[2].textContent = `${savingsRate}% savings rate`;

  const tickerItems = [
    ...APP_STATE.accounts.map((account) => ({
      label: account.name,
      value: formatCurrency(account.balance),
      status: asNumber(account.balance) >= 0 ? "up" : "dn",
      delta: account.type,
    })),
    ...getCategorySeries().slice(0, 3).map((category) => ({
      label: category.label,
      value: formatCurrency(category.value),
      status: "dn",
      delta: "spend",
    })),
  ];
  const tickerTrack = document.getElementById("ticker-track");
  if (tickerTrack && tickerItems.length) {
    const doubled = [...tickerItems, ...tickerItems];
    tickerTrack.innerHTML = doubled.map((item) => `
      <div class="tick">
        <span class="tick-name">${escapeHtml(item.label)}</span>
        <span class="tick-val ${item.status}">${escapeHtml(item.value)}</span>
        <span class="tick-val ${item.status}">${escapeHtml(item.delta)}</span>
      </div>`).join("");
  }

  renderTrendSvg("chartSvg", monthlyTrend, [
    { key: "income", color: "#c8f000" },
    { key: "expenses", color: "#ff4d00" },
    { key: "net", color: "#e8e4dc" },
  ]);

  renderDonutSvg("donut", APP_STATE.accounts.map((account, index) => ({
    label: account.name,
    value: asNumber(account.balance),
    color: account.color || CATEGORY_COLORS[index % CATEGORY_COLORS.length],
  })), "overview-donut-total", "donut-legend");

  const accountsList = document.getElementById("accounts-list");
  if (accountsList) {
    accountsList.innerHTML = APP_STATE.accounts.length
      ? APP_STATE.accounts.map((account, index) => {
        const color = account.color || CATEGORY_COLORS[index % CATEGORY_COLORS.length];
        return `<div class="account-row"><div class="acc-left"><div class="acc-dot" style="background:${color};box-shadow:0 0 6px ${color}"></div><div><div class="acc-name">${escapeHtml(account.name)}</div><div class="acc-type">${escapeHtml(account.institution || account.type)}</div></div></div><div class="acc-val" style="color:${color}">${formatCurrency(account.balance)}</div></div>`;
      }).join("")
      : '<div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-transform:uppercase">No accounts available</div>';
  }

  const txList = document.getElementById("overview-tx-list");
  if (txList) {
    const items = APP_STATE.transactions.slice(0, 5);
    txList.innerHTML = items.length
      ? items.map((transaction) => `
        <div class="tx-row clickable">
          <div><div class="tx-name">${escapeHtml(transaction.description)}</div><div class="tx-cat">${escapeHtml(transaction.category)}</div></div>
          <div class="tx-date">${escapeHtml(formatShortDate(transaction.date))}</div>
          <div class="tx-amount ${transaction.type === "INCOME" ? "up" : "dn"}">${transaction.type === "INCOME" ? "+" : "-"}${formatCurrency(transaction.amount)}</div>
        </div>`).join("")
      : '<div class="page-loading">No transactions yet</div>';
  }

  const budgetList = document.getElementById("overview-budget-list");
  if (budgetList) {
    const budgetItems = APP_STATE.budgets.length
      ? APP_STATE.budgets.slice(0, 4).map((budget) => {
        const spent = asNumber(budget.spent || budget.usedAmount);
        const limit = Math.max(asNumber(budget.limit || budget.amount || budget.totalAmount), 1);
        const percent = Math.min(Math.round((spent / limit) * 100), 100);
        const color = percent >= 80 ? "var(--ember)" : percent >= 50 ? "var(--ice)" : "var(--acid)";
        return {
          name: budget.name || budget.categoryName || "Budget",
          spent,
          limit,
          percent,
          color,
          status: percent >= 80 ? `${percent}% used â€” ${formatCurrency(limit - spent)} remaining` : `${percent}% used â€” On track`,
        };
      })
      : getCategorySeries().slice(0, 4).map((category, index, array) => {
        const maxValue = Math.max(...array.map((item) => item.value), 1);
        const percent = Math.max(12, Math.round((category.value / maxValue) * 100));
        const color = CATEGORY_COLORS[index % CATEGORY_COLORS.length];
        return {
          name: category.label,
          spent: category.value,
          limit: category.value * 1.25,
          percent,
          color,
          status: `${percent}% of top category spend`,
        };
      });

    budgetList.innerHTML = budgetItems.map((item) => `
      <div class="bud-row">
        <div class="bud-meta"><span class="bud-name">${escapeHtml(item.name)}</span><span class="bud-nums">${formatCurrency(item.spent)} / ${formatCurrency(item.limit)}</span></div>
        <div class="bud-track"><div class="bud-fill" style="width:${item.percent}%;background:${item.color}"></div></div>
        <div class="bud-status" style="color:${item.color}">${escapeHtml(item.status)}</div>
      </div>`).join("");
  }

  const goalsList = document.getElementById("overview-goals-list");
  if (goalsList) {
    const goals = APP_STATE.goals.slice(0, 3);
    goalsList.innerHTML = goals.length
      ? goals.map((goal, index) => {
        const color = goal.color || CATEGORY_COLORS[index % CATEGORY_COLORS.length];
        const percent = Math.max(0, Math.min(100, Math.round(asNumber(goal.percentComplete))));
        return `<div class="goal-row clickable"><div class="goal-index">${String(index + 1).padStart(2, "0")}</div><div class="goal-body"><div class="goal-name">${escapeHtml(goal.name)}</div><div class="goal-bar-track"><div class="goal-bar-fill" style="width:${percent}%;background:${color}"></div></div></div><div class="goal-right"><div class="goal-pct" style="color:${color}">${percent}%</div><div class="goal-eta">${goal.monthsToGoal != null ? `~${goal.monthsToGoal} months left` : "In progress"}</div></div></div>`;
      }).join("")
      : '<div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-transform:uppercase">No savings goals yet</div>';
  }

  const insightsList = document.getElementById("overview-insights-list");
  if (insightsList) {
    const insights = [
      ...(Array.isArray(APP_STATE.intelligence?.insights) ? APP_STATE.intelligence.insights : []),
      ...(Array.isArray(APP_STATE.insights?.recommendations) ? APP_STATE.insights.recommendations.map((item) => item.message || item.title).filter(Boolean) : []),
    ].slice(0, 4);
    insightsList.innerHTML = insights.length
      ? insights.map((message, index) => {
        const tags = ["WIN", "INFO", "ALERT", "NOTE"];
        const colors = ["var(--acid)", "var(--ice)", "var(--ember)", "var(--offwhite)"];
        return `<div class="insight-row clickable"><div class="insight-tag" style="color:${colors[index % colors.length]}">${tags[index % tags.length]}</div><div class="insight-text">${escapeHtml(message)}</div></div>`;
      }).join("")
      : '<div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-transform:uppercase">No insights available</div>';
  }

  revealElements(page);
}

function getRecurringItems() {
  if (APP_STATE.recurring.length) {
    return APP_STATE.recurring;
  }
  return APP_STATE.transactions.filter((transaction) => transaction.isRecurring).map((transaction) => ({
    id: transaction.id,
    description: transaction.description,
    amount: transaction.amount,
    categoryName: transaction.category,
    frequency: "Detected",
    nextDueDate: transaction.date,
    type: transaction.type,
  }));
}

function renderAnalyticsPage() {
  const kpiContainer = document.getElementById("analytics-kpis");
  const recurringList = document.getElementById("recurring-list");
  const monthlyTrend = getMonthlyTrendData();
  const categories = getCategorySeries();
  const totalIncome = asNumber(APP_STATE.analytics?.totalIncome);
  const totalExpenses = asNumber(APP_STATE.analytics?.totalExpenses || APP_STATE.analytics?.totalExpense);
  const netSavings = asNumber(APP_STATE.analytics?.netSavings || totalIncome - totalExpenses);
  const savingsRate = APP_STATE.analytics?.savingsRate != null
    ? `${Number(APP_STATE.analytics.savingsRate).toFixed(1)}%`
    : `${(totalIncome > 0 ? ((netSavings / totalIncome) * 100) : 0).toFixed(1)}%`;

  if (kpiContainer) {
    kpiContainer.innerHTML = `
      <div style="display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:18px">
        <div class="stat-card"><div class="stat-label">Total Income</div><div class="stat-value" style="color:var(--acid)">${formatCurrency(totalIncome)}</div><div class="stat-delta up">Current month</div></div>
        <div class="stat-card"><div class="stat-label">Total Expenses</div><div class="stat-value" style="color:var(--ember)">${formatCurrency(totalExpenses)}</div><div class="stat-delta dn">Current month</div></div>
        <div class="stat-card"><div class="stat-label">Net Savings</div><div class="stat-value">${formatCurrency(netSavings)}</div><div class="stat-delta up">After spending</div></div>
        <div class="stat-card"><div class="stat-label">Savings Rate</div><div class="stat-value up">${savingsRate}</div><div class="stat-delta" style="color:var(--muted)">Income retained</div></div>
      </div>`;
  }

  renderTrendSvg("analyticsChartSvg", monthlyTrend, [
    { key: "income", color: "#c8f000" },
    { key: "expenses", color: "#ff4d00" },
    { key: "net", color: "#00e5ff" },
  ]);
  renderDonutSvg("analyticsDonut", categories, "analytics-donut-center", "analytics-cat-legend");

  if (recurringList) {
    const items = getRecurringItems();
    recurringList.innerHTML = items.length
      ? items.map((item, index) => `
        <div style="display:grid;grid-template-columns:1.3fr 0.8fr 0.7fr 0.8fr;gap:16px;padding:14px 0;border-bottom:1px solid var(--line2);align-items:center">
          <div><div style="font-family:var(--font-display);font-size:16px;font-weight:700;text-transform:uppercase">${escapeHtml(item.description)}</div><div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);letter-spacing:0.1em;text-transform:uppercase">${escapeHtml(item.categoryName || item.category || "Recurring")}</div></div>
          <div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-transform:uppercase">${escapeHtml(item.frequency || "Detected")}</div>
          <div style="font-family:var(--font-mono);font-size:10px;color:var(--muted)">${escapeHtml(formatDateLabel(item.nextDueDate || item.date))}</div>
          <div style="font-family:var(--font-display);font-size:18px;font-weight:700;color:${normalizeType(item.type) === "INCOME" ? "var(--acid)" : "var(--ember)"}">${formatCurrency(item.amount)}</div>
        </div>`).join("")
      : '<div class="page-loading">No recurring transactions found</div>';
  }
}

function renderGoalCreatePanel() {
  const defaultAccountId = APP_STATE.accounts[0]?.id || "";
  return `
    <div class="full-panel" style="margin-top:0;margin-bottom:28px;padding:40px;background:linear-gradient(135deg, rgba(200,240,0,0.04) 0%, rgba(200,240,0,0.02) 100%);border:1px solid rgba(200,240,0,0.1)">
      <div class="panel-eyebrow" style="margin-bottom:16px">Goal Planner</div>
      <div class="panel-title" style="margin-bottom:32px;font-size:28px">Create Savings Goal</div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-bottom:24px">
        <div style="padding:20px;background:rgba(255,255,255,0.02);border:1px solid var(--line2);border-radius:8px">
          <div style="font-family:var(--font-mono);font-size:12px;color:var(--acid);text-transform:uppercase;margin-bottom:12px;font-weight:700;letter-spacing:0.1em">Goal Name</div>
          <input id="goal-name-input" class="ft-input" type="text" placeholder="Emergency Fund" style="width:100%;padding:16px;font-size:18px;background:rgba(0,0,0,0.3);border:1px solid var(--line2);border-radius:6px;color:var(--offwhite);font-family:var(--font-display)">
        </div>
        <div style="padding:20px;background:rgba(255,255,255,0.02);border:1px solid var(--line2);border-radius:8px">
          <div style="font-family:var(--font-mono);font-size:12px;color:var(--acid);text-transform:uppercase;margin-bottom:12px;font-weight:700;letter-spacing:0.1em">Target Amount (₹)</div>
          <input id="goal-target-input" class="ft-input" type="number" placeholder="50,000" style="width:100%;padding:16px;font-size:18px;background:rgba(0,0,0,0.3);border:1px solid var(--line2);border-radius:6px;color:var(--offwhite);font-family:var(--font-display)">
        </div>
        <div style="padding:20px;background:rgba(255,255,255,0.02);border:1px solid var(--line2);border-radius:8px">
          <div style="font-family:var(--font-mono);font-size:12px;color:var(--acid);text-transform:uppercase;margin-bottom:12px;font-weight:700;letter-spacing:0.1em">Monthly Contribution (₹)</div>
          <input id="goal-monthly-input" class="ft-input" type="number" placeholder="5,000" style="width:100%;padding:16px;font-size:18px;background:rgba(0,0,0,0.3);border:1px solid var(--line2);border-radius:6px;color:var(--offwhite);font-family:var(--font-display)">
        </div>
        <div style="padding:20px;background:rgba(255,255,255,0.02);border:1px solid var(--line2);border-radius:8px">
          <div style="font-family:var(--font-mono);font-size:12px;color:var(--acid);text-transform:uppercase;margin-bottom:12px;font-weight:700;letter-spacing:0.1em">Target Date</div>
          <input id="goal-date-input" class="ft-input" type="date" style="width:100%;padding:16px;font-size:18px;background:rgba(0,0,0,0.3);border:1px solid var(--line2);border-radius:6px;color:var(--offwhite);font-family:var(--font-display)">
        </div>
      </div>
      <div>
        <input id="goal-account-input" type="hidden" value="${escapeHtml(defaultAccountId)}">
        <button id="goal-create-btn" class="pg-btn" type="button" style="width:100%;padding:18px;font-size:16px;font-weight:700;letter-spacing:0.1em;background:var(--acid);color:var(--dark);border-radius:8px;border:none;cursor:pointer;transition:all 0.2s">CREATE GOAL</button>
      </div>
      <div id="goal-success-feedback" style="margin-top:16px;padding:14px;background:rgba(200,240,0,0.15);border:1px solid var(--acid);border-radius:6px;color:var(--acid);font-size:14px;display:none;text-align:center;font-weight:600">✓ Goal created successfully!</div>
    </div>`;
}

function renderBudgetCreatePanel() {
  const defaultAccountId = APP_STATE.accounts[0]?.id || "";
  return `
    <div class="full-panel" style="margin-top:0;margin-bottom:28px;padding:40px;background:linear-gradient(135deg, rgba(255,107,107,0.04) 0%, rgba(255,107,107,0.02) 100%);border:1px solid rgba(255,107,107,0.1)">
      <div class="panel-eyebrow" style="margin-bottom:16px">Budget Planner</div>
      <div class="panel-title" style="margin-bottom:32px;font-size:28px">Create Spending Budget</div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-bottom:24px">
        <div style="padding:20px;background:rgba(255,255,255,0.02);border:1px solid var(--line2);border-radius:8px">
          <div style="font-family:var(--font-mono);font-size:12px;color:var(--ember);text-transform:uppercase;margin-bottom:12px;font-weight:700;letter-spacing:0.1em">Budget Category</div>
          <input id="budget-name-input" class="ft-input" type="text" placeholder="Groceries" style="width:100%;padding:16px;font-size:18px;background:rgba(0,0,0,0.3);border:1px solid var(--line2);border-radius:6px;color:var(--offwhite);font-family:var(--font-display)">
        </div>
        <div style="padding:20px;background:rgba(255,255,255,0.02);border:1px solid var(--line2);border-radius:8px">
          <div style="font-family:var(--font-mono);font-size:12px;color:var(--ember);text-transform:uppercase;margin-bottom:12px;font-weight:700;letter-spacing:0.1em">Monthly Limit (₹)</div>
          <input id="budget-limit-input" class="ft-input" type="number" placeholder="10,000" style="width:100%;padding:16px;font-size:18px;background:rgba(0,0,0,0.3);border:1px solid var(--line2);border-radius:6px;color:var(--offwhite);font-family:var(--font-display)">
        </div>
        <div style="padding:20px;background:rgba(255,255,255,0.02);border:1px solid var(--line2);border-radius:8px">
          <div style="font-family:var(--font-mono);font-size:12px;color:var(--ember);text-transform:uppercase;margin-bottom:12px;font-weight:700;letter-spacing:0.1em">Alert Threshold (%)</div>
          <input id="budget-alert-input" class="ft-input" type="number" placeholder="80" min="0" max="100" style="width:100%;padding:16px;font-size:18px;background:rgba(0,0,0,0.3);border:1px solid var(--line2);border-radius:6px;color:var(--offwhite);font-family:var(--font-display)">
        </div>
        <div style="padding:20px;background:rgba(255,255,255,0.02);border:1px solid var(--line2);border-radius:8px">
          <div style="font-family:var(--font-mono);font-size:12px;color:var(--ember);text-transform:uppercase;margin-bottom:12px;font-weight:700;letter-spacing:0.1em">Start Date</div>
          <input id="budget-date-input" class="ft-input" type="date" style="width:100%;padding:16px;font-size:18px;background:rgba(0,0,0,0.3);border:1px solid var(--line2);border-radius:6px;color:var(--offwhite);font-family:var(--font-display)">
        </div>
      </div>
      <div>
        <input id="budget-account-input" type="hidden" value="${escapeHtml(defaultAccountId)}">
        <button id="budget-create-btn" class="pg-btn" type="button" style="width:100%;padding:18px;font-size:16px;font-weight:700;letter-spacing:0.1em;background:var(--ember);color:var(--offwhite);border-radius:8px;border:none;cursor:pointer;transition:all 0.2s">CREATE BUDGET</button>
      </div>
      <div id="budget-success-feedback" style="margin-top:16px;padding:14px;background:rgba(255,107,107,0.15);border:1px solid var(--ember);border-radius:6px;color:var(--ember);font-size:14px;display:none;text-align:center;font-weight:600">✓ Budget created successfully!</div>
    </div>`;
}

function bindGoalsControls() {
  document.getElementById("goal-create-btn")?.addEventListener("click", createGoal);
  document.getElementById("budget-create-btn")?.addEventListener("click", createBudget);
  document.querySelectorAll("[data-goal-contribute]").forEach((button) => {
    button.addEventListener("click", async () => {
      await contributeToGoal(button.dataset.goalContribute);
    });
  });
}

function renderGoalsPage() {
  const container = document.getElementById("goals-full-list");
  if (!container) return;

  const totalSaved = APP_STATE.goals.reduce((sum, goal) => sum + asNumber(goal.currentAmount), 0);
  setText("goals-total-chip", `Total Saved: ${formatCurrency(totalSaved)}`);

  const goalsMarkup = APP_STATE.goals.length
    ? APP_STATE.goals.map((goal, index) => {
      const color = goal.color || CATEGORY_COLORS[index % CATEGORY_COLORS.length];
      const percent = Math.max(0, Math.min(100, Math.round(asNumber(goal.percentComplete))));
      return `
        <div class="full-panel" style="margin-top:0">
          <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:20px;margin-bottom:16px">
            <div>
              <div style="font-family:var(--font-display);font-size:24px;font-weight:700;text-transform:uppercase;color:${color}">${escapeHtml(goal.name)}</div>
              <div style="font-family:var(--font-mono);font-size:9px;color:var(--muted);letter-spacing:0.08em;text-transform:uppercase;margin-top:6px">${escapeHtml(goal.description || "Savings target")}</div>
            </div>
            <div style="font-family:var(--font-display);font-size:26px;font-weight:900;color:${color}">${percent}%</div>
          </div>
          <div style="height:8px;background:var(--line2);margin-bottom:12px"><div style="width:${percent}%;height:100%;background:${color}"></div></div>
          <div style="display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:18px">
            <div><div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase">Saved</div><div style="font-family:var(--font-display);font-size:20px">${formatCurrency(goal.currentAmount)}</div></div>
            <div><div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase">Target</div><div style="font-family:var(--font-display);font-size:20px">${formatCurrency(goal.targetAmount)}</div></div>
            <div><div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase">Remaining</div><div style="font-family:var(--font-display);font-size:20px">${formatCurrency(goal.remaining)}</div></div>
            <div><div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase">Timeline</div><div style="font-family:var(--font-display);font-size:20px">${goal.monthsToGoal != null ? `${goal.monthsToGoal} mo` : "Open"}</div></div>
          </div>
          <div style="display:flex;gap:12px;align-items:end">
            <div style="flex:1"><div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase;margin-bottom:6px">Add Contribution</div><input id="goal-contribution-${goal.id}" class="ft-input" type="number" placeholder="1000" style="width:100%"></div>
            <button class="pg-btn" data-goal-contribute="${goal.id}" type="button">ADD FUNDS</button>
          </div>
        </div>`;
    }).join("")
    : '<div class="full-panel" style="margin-top:0"><div class="page-loading">No savings goals yet. Create one above to start tracking progress.</div></div>';

  container.innerHTML = `${renderGoalCreatePanel()}${renderBudgetCreatePanel()}${goalsMarkup}`;
  bindGoalsControls();
}

// ============= FUZZY LOGIC FUNCTIONS =============

async function apiRequestML(endpoint, options = {}) {
  const url = `http://localhost:8001${endpoint}`;
  options.headers = options.headers || {};
  options.headers["Content-Type"] = "application/json";

  try {
    const response = await fetch(url, {
      method: options.method || "GET",
      headers: options.headers,
      body: options.body,
    });

    if (!response.ok) {
      console.warn(`[ML API] ${endpoint} returned ${response.status}`);
      return null;
    }

    return await response.json();
  } catch (err) {
    if (!options.silent) {
      console.error(`[ML API ERROR] ${endpoint}:`, err);
    }
    return null;
  }
}

async function loadFuzzyData() {
  try {
    // Load membership function data for financial risk FIS
    const mfResponse = await apiRequestML("/ml/fuzzy/membership-functions", { method: "GET", silent: true });
    APP_STATE.fuzzyMembershipData = mfResponse || null;

    // Load budget alerts if budgets exist
    if (APP_STATE.budgets && APP_STATE.budgets.length > 0) {
      const budgetAlerts = [];
      for (const budget of APP_STATE.budgets) {
        if (!budget.limit) continue;
        const spent = budget.spent || 0;
        const utilization = (spent / budget.limit) * 100;
        const daysRemaining = budget.period === "MONTHLY" ? 20 : budget.period === "YEARLY" ? 200 : 30;
        
        const alertResponse = await apiRequestML("/ml/fuzzy/budget-alert", {
          method: "POST",
          body: JSON.stringify({
            budget_utilization_pct: utilization,
            days_remaining: daysRemaining
          }),
          silent: true
        });
        if (alertResponse) {
          budgetAlerts.push({ ...alertResponse, budgetName: budget.name });
        }
      }
      APP_STATE.fuzzyBudgetAlerts = budgetAlerts;
    }

    // Load savings advisor data
    if (APP_STATE.analytics) {
      const savingsRate = APP_STATE.analytics.savingsRate || 0;
      const expenseVolatility = APP_STATE.analytics.expenseVolatility || 20;
      const incomeStability = APP_STATE.analytics.incomeStability || 75;
      const monthlyIncome = APP_STATE.analytics.monthlyIncome || 50000;

      const savingsResponse = await apiRequestML("/ml/fuzzy/savings-advisor", {
        method: "POST",
        body: JSON.stringify({
          savings_rate: savingsRate,
          expense_volatility_pct: expenseVolatility,
          income_stability: incomeStability,
          current_monthly_income: monthlyIncome
        }),
        silent: true
      });
      APP_STATE.fuzzySavingsAdvisor = savingsResponse || null;
    }

    // Load anomaly severity data
    if (APP_STATE.transactions && APP_STATE.transactions.length > 0) {
      const anomalyResponse = await apiRequestML("/ml/fuzzy/anomaly-severity", {
        method: "POST",
        body: JSON.stringify({
          transactions: APP_STATE.transactions.slice(0, 20).map(tx => ({
            amount: tx.amount,
            category: tx.category,
            date: tx.date
          }))
        }),
        silent: true
      });
      APP_STATE.fuzzyAnomalySeverity = anomalyResponse || null;
    }
  } catch (err) {
    console.error("[FUZZY DATA LOAD ERROR]", err);
  }
}

function drawMembershipFunction(canvasId, mfData, label) {
  const canvas = document.getElementById(canvasId);
  if (!canvas || !mfData || !mfData.points || mfData.points.length === 0) return;

  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  canvas.width = canvas.offsetWidth;
  canvas.height = 100;
  const padding = 10;
  const width = canvas.width - 2 * padding;
  const height = canvas.height - 2 * padding;

  // Clear canvas
  ctx.fillStyle = "rgba(0,0,0,0.3)";
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // Draw grid
  ctx.strokeStyle = "rgba(255,255,255,0.05)";
  ctx.lineWidth = 1;
  for (let i = 0; i <= 10; i++) {
    const x = padding + (width / 10) * i;
    ctx.beginPath();
    ctx.moveTo(x, padding);
    ctx.lineTo(x, canvas.height - padding);
    ctx.stroke();
  }

  // Draw axes
  ctx.strokeStyle = "rgba(255,255,255,0.2)";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(padding, canvas.height - padding);
  ctx.lineTo(canvas.width - padding, canvas.height - padding);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(padding, padding);
  ctx.lineTo(padding, canvas.height - padding);
  ctx.stroke();

  // Find min/max values
  const values = mfData.points.map(p => p.y);
  const maxVal = Math.max(...values, 1);
  const minVal = 0;
  const minX = Math.min(...mfData.points.map(p => p.x));
  const maxX = Math.max(...mfData.points.map(p => p.x));

  // Draw membership functions
  const colors = ["rgba(200, 240, 0, 0.8)", "rgba(255, 77, 0, 0.8)", "rgba(0, 229, 255, 0.8)", "rgba(255, 143, 171, 0.8)"];
  if (mfData.mfs && Array.isArray(mfData.mfs)) {
    mfData.mfs.forEach((mf, idx) => {
      ctx.strokeStyle = colors[idx % colors.length];
      ctx.lineWidth = 2;
      ctx.beginPath();

      for (let pi = 0; pi < mf.points.length; pi++) {
        const p = mf.points[pi];
        const x = padding + ((p.x - minX) / (maxX - minX || 1)) * width;
        const y = canvas.height - padding - (p.y / (maxVal || 1)) * height;

        if (pi === 0) {
          ctx.moveTo(x, y);
        } else {
          ctx.lineTo(x, y);
        }
      }
      ctx.stroke();
    });
  }
}

function renderFuzzyFinancialRisk() {
  if (!APP_STATE.fuzzyMembershipData) return;

  const data = APP_STATE.fuzzyMembershipData;
  if (!data.financial_risk_fis) return;

  const fis = data.financial_risk_fis;
  document.getElementById("fz-income-val").textContent = (fis.income_stability || 0).toFixed(0);
  document.getElementById("fz-expense-val").textContent = (fis.expense_level || 0).toFixed(0);
  document.getElementById("fz-savings-val").textContent = (fis.savings_rate || 0).toFixed(0);
  document.getElementById("fz-risk-val").textContent = (fis.financial_risk_output || 0).toFixed(1);
  document.getElementById("fz-risk-label").textContent = fis.risk_label || "—";
  document.getElementById("fz-recommendation").textContent = fis.recommendation || "—";

  // Draw membership functions
  if (data.mf_data) {
    drawMembershipFunction("mf-income", data.mf_data[0], "Income");
    drawMembershipFunction("mf-expense", data.mf_data[1], "Expense");
    drawMembershipFunction("mf-savings", data.mf_data[2], "Savings");
    drawMembershipFunction("mf-risk-out", data.mf_data[3], "Risk Output");
  }
}

function renderFuzzyBudgetWarnings() {
  const container = document.getElementById("fuzzy-budget-list");
  if (!container) return;

  if (!APP_STATE.fuzzyBudgetAlerts || APP_STATE.fuzzyBudgetAlerts.length === 0) {
    container.innerHTML =
      '<div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-align:center;padding:24px 0">No budgets configured yet. Add budgets in the Budgets tab to see fuzzy alerts.</div>';
    return;
  }

  container.innerHTML = APP_STATE.fuzzyBudgetAlerts.map((alert) => {
    const alertColor =
      alert.alert_level === "CRITICAL"
        ? "var(--ember)"
        : alert.alert_level === "WARNING"
          ? "#ff9500"
          : alert.alert_level === "CAUTION"
            ? "#ffb700"
            : "var(--acid)";

    return `
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;padding:16px;background:rgba(255,255,255,0.02);border:1px solid var(--line);border-radius:4px;margin-bottom:12px">
        <div>
          <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase;margin-bottom:4px">${escapeHtml(alert.budgetName)}</div>
          <div style="font-family:var(--font-display);font-size:16px;font-weight:900;color:${alertColor}">${escapeHtml(alert.alert_level)}</div>
        </div>
        <div>
          <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase;margin-bottom:4px">Alert Score</div>
          <div style="font-family:var(--font-display);font-size:16px;font-weight:900;color:${alertColor}">${(alert.alert_score || 0).toFixed(1)}%</div>
        </div>
        <div style="grid-column:1/-1">
          <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted)">${escapeHtml(alert.explanation || "")}</div>
        </div>
      </div>
    `;
  }).join("");
}

function renderFuzzySavingsAdvisor() {
  const container = document.getElementById("fuzzy-savings-content");
  if (!container) return;

  if (!APP_STATE.fuzzySavingsAdvisor) {
    container.innerHTML =
      '<div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-align:center;padding:24px 0">No savings data available.</div>';
    return;
  }

  const advisor = APP_STATE.fuzzySavingsAdvisor;
  container.innerHTML = `
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:20px">
      <div>
        <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase;margin-bottom:6px">Recommended Target</div>
        <div style="font-family:var(--font-display);font-size:18px;font-weight:900;color:var(--acid)">${escapeHtml(advisor.target_label || "—")}</div>
        <div style="font-family:var(--font-mono);font-size:9px;color:var(--muted)">${(advisor.target_score || 0).toFixed(1)}% confidence</div>
      </div>
      <div>
        <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase;margin-bottom:6px">Monthly Range</div>
        <div style="font-family:var(--font-display);font-size:14px;font-weight:700;color:var(--ice)">₹${formatAmount(advisor.target_amt_low || 0)} – ₹${formatAmount(advisor.target_amt_high || 0)}</div>
      </div>
      <div style="grid-column:1/-1">
        <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);margin-bottom:6px;text-transform:uppercase">Advice</div>
        <div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);line-height:1.5">${escapeHtml(advisor.advice || "")}</div>
      </div>
    </div>
  `;
}

function renderFuzzyAnomalySeverity() {
  const container = document.getElementById("fuzzy-anomaly-list");
  if (!container) return;

  if (!APP_STATE.fuzzyAnomalySeverity || APP_STATE.fuzzyAnomalySeverity.results.length === 0) {
    container.innerHTML =
      '<div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-align:center;padding:24px 0">No transactions analyzed yet.</div>';
    return;
  }

  const results = APP_STATE.fuzzyAnomalySeverity.results.filter(r => r.severity_score > 10);
  if (results.length === 0) {
    container.innerHTML =
      '<div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-align:center;padding:24px 0">All transactions appear normal.</div>';
    return;
  }

  container.innerHTML = results
    .sort((a, b) => b.severity_score - a.severity_score)
    .slice(0, 10)
    .map((result) => {
      const severityColor =
        result.severity_label === "SEVERE"
          ? "var(--ember)"
          : result.severity_label === "MODERATE"
            ? "#ff9500"
            : "var(--acid)";

      return `
        <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:12px;padding:12px;background:rgba(255,255,255,0.02);border-left:3px solid ${severityColor};margin-bottom:8px">
          <div>
            <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase">Transaction</div>
            <div style="font-family:var(--font-display);font-size:13px;font-weight:700">${escapeHtml(result.category || "Unknown")}</div>
          </div>
          <div>
            <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase">Severity</div>
            <div style="font-family:var(--font-display);font-size:13px;font-weight:700;color:${severityColor}">${escapeHtml(result.severity_label)}</div>
          </div>
          <div>
            <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);text-transform:uppercase">Score</div>
            <div style="font-family:var(--font-display);font-size:13px;font-weight:700">${(result.severity_score || 0).toFixed(1)}</div>
          </div>
        </div>
      `;
    })
    .join("");
}

function renderDashboard() {
  renderDashboardSummary();
  renderDashboardInsights();
  renderDashboardAnomalies();
  renderDashboardCharts();
  renderFuzzyFinancialRisk();
  renderFuzzyBudgetWarnings();
  renderFuzzySavingsAdvisor();
  renderFuzzyAnomalySeverity();
  renderDashboardTransactionTable();
  revealElements(document.getElementById("page-dashboard"));
  void loadFuzzyInsights();
}

function bindUploadZone() {
  const dropZone = document.getElementById("pdf-drop-zone");
  if (!dropZone || dropZone.dataset.bound === "true") return;
  dropZone.dataset.bound = "true";
  dropZone.addEventListener("click", () => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".pdf";
    input.addEventListener("change", (event) => handlePdfUpload(event.target.files?.[0]));
    input.click();
  });
  dropZone.addEventListener("dragover", (event) => {
    event.preventDefault();
    dropZone.style.background = "rgba(200,240,0,0.08)";
  });
  dropZone.addEventListener("dragleave", () => {
    dropZone.style.background = "transparent";
  });
  dropZone.addEventListener("drop", (event) => {
    event.preventDefault();
    dropZone.style.background = "transparent";
    handlePdfUpload(event.dataTransfer?.files?.[0]);
  });

  document.getElementById("upload-account-select")?.addEventListener("change", (event) => {
    APP_STATE.selectedUploadAccount = event.target.value;
    console.log("[UPLOAD ACCOUNT]", APP_STATE.selectedUploadAccount);
  });
}

function renderAccountsList() {
  const container = document.getElementById("account-list");
  if (!container) return;

  if (!APP_STATE.accounts.length) {
    container.innerHTML = '<div style="font-family:var(--font-mono);font-size:9px;color:var(--muted);letter-spacing:0.08em;text-transform:uppercase">No accounts yet</div>';
    return;
  }

  container.innerHTML = APP_STATE.accounts.map((account) => `
    <div style="display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border:1px solid var(--line);background:rgba(255,255,255,0.02)">
      <div>
        <div style="font-family:var(--font-display);font-size:14px;font-weight:700;letter-spacing:0.05em;text-transform:uppercase">${escapeHtml(account.name)}</div>
        <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted);letter-spacing:0.08em;text-transform:uppercase">${escapeHtml(account.type)}${account.institution ? ` â€¢ ${escapeHtml(account.institution)}` : ""}</div>
      </div>
      <div style="font-family:var(--font-display);font-size:18px;font-weight:700;color:var(--acid)">${formatCurrency(account.balance)}</div>
    </div>`).join("");
}
async function loadAccounts() {
  const accounts = await apiRequest("/accounts", { silent: true });
  APP_STATE.accounts = Array.isArray(accounts) ? accounts : accounts?.content || [];
  const accountSelect = document.getElementById("upload-account-select");
  if (!accountSelect) {
    renderAccountsList();
    return;
  }

  const currentValue = APP_STATE.selectedUploadAccount;
  accountSelect.innerHTML = '<option value="">-- Choose Account --</option>';
  APP_STATE.accounts.forEach((account) => {
    const option = document.createElement("option");
    option.value = account.id;
    option.textContent = `${account.name} (${account.type})`;
    accountSelect.appendChild(option);
  });
  if (APP_STATE.accounts.some((account) => String(account.id) === String(currentValue))) {
    accountSelect.value = currentValue;
    APP_STATE.selectedUploadAccount = String(currentValue);
  } else if (APP_STATE.accounts[0]) {
    APP_STATE.selectedUploadAccount = String(APP_STATE.accounts[0].id);
    accountSelect.value = APP_STATE.selectedUploadAccount;
  } else {
    APP_STATE.selectedUploadAccount = "";
  }
  renderAccountsList();
}

async function createAccount() {
  const name = document.getElementById("account-name")?.value?.trim();
  const type = document.getElementById("account-type")?.value || "SAVINGS";
  const balance = document.getElementById("account-balance")?.value;
  const institution = document.getElementById("account-institution")?.value?.trim();

  if (!name) {
    showFailure("Please enter an account name.");
    return;
  }
  if (!type) {
    showFailure("Please choose an account type.");
    return;
  }

  let initialBalance = 0;
  if (balance != null && String(balance).trim() !== "") {
    initialBalance = Number(balance);
    if (!Number.isFinite(initialBalance)) {
      showFailure("Initial balance must be a valid number.");
      return;
    }
  }

  let response = null;
  try {
    response = await apiRequest("/accounts", {
      method: "POST",
      throwOnError: true,
      body: {
        name,
        type,
        initialBalance,
        institution: institution || null,
        currency: "INR",
        color: "#c8f000",
      },
    });
  } catch (error) {
    showFailure(error?.message || "Account creation failed.");
    return;
  }

  if (!response?.id) {
    showFailure("Account creation failed.");
    return;
  }

  showToast("Account created successfully.", "success");
  document.getElementById("account-name").value = "";
  document.getElementById("account-balance").value = "";
  document.getElementById("account-institution").value = "";
  await loadAccounts();
  APP_STATE.selectedUploadAccount = String(response.id);
  const accountSelect = document.getElementById("upload-account-select");
  if (accountSelect) accountSelect.value = String(response.id);
}

async function handlePdfUpload(file) {
  if (!file || !file.name?.toLowerCase().endsWith(".pdf")) {
    showFailure("Please choose a PDF file.");
    return;
  }
  if (!APP_STATE.selectedUploadAccount) {
    showFailure("Please select an account before uploading.");
    return;
  }

  const formData = new FormData();
  formData.append("file", file);
  formData.append("accountId", APP_STATE.selectedUploadAccount);
  formData.append("source", "Browser Upload");

  let response = await apiRequest("/upload", { method: "POST", body: formData, silent: true });
  if (!response) {
    response = await apiRequest("/statements/upload", { method: "POST", body: formData, silent: true });
  }

  if (!response) {
    showFailure("Upload failed. The statement endpoint did not return a usable response.");
    return;
  }

  console.log("UPLOAD SUCCESS", response);
  showToast("Upload successful. Refreshing dashboard and transactions.", "success");
  await loadDashboardPage(true);
  await loadTransactionsPage();
  updateNavigation("dashboard");
}

async function loadBudgetsPage() {
  const container = document.getElementById("bud-full-list");
  if (!container || PAGE_STATE.budgetsLoaded) return;

  const budgets = APP_STATE.budgets.length ? APP_STATE.budgets : await apiRequest("/budgets", { silent: true });
  PAGE_STATE.budgetsLoaded = true;
  const items = Array.isArray(budgets) ? budgets : budgets?.content || [];
  if (!items.length) {
    container.innerHTML = '<div class="page-loading">No budgets found</div>';
    return;
  }

  container.innerHTML = items.map((budget) => {
    const spent = asNumber(budget.spent || budget.usedAmount);
    const limit = Math.max(asNumber(budget.limit || budget.amount || budget.totalAmount), 1);
    const percent = Math.min(Math.round((spent / limit) * 100), 100);
    const color = percent >= 80 ? "var(--ember)" : percent >= 50 ? "var(--ice)" : "var(--acid)";
    return `
      <div class="full-panel" style="margin-top:0;margin-bottom:12px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <span style="font-family:var(--font-display);font-size:16px;font-weight:700;color:${color}">${escapeHtml(budget.name || budget.categoryName || "Budget")}</span>
          <span style="font-family:var(--font-mono);font-size:10px;color:var(--muted)">${formatCurrency(spent)} / ${formatCurrency(limit)}</span>
        </div>
        <div style="height:4px;background:var(--line2);margin-bottom:8px"><div style="height:100%;width:${percent}%;background:${color}"></div></div>
        <div style="font-family:var(--font-mono);font-size:9px;color:var(--muted)">${percent}% used</div>
      </div>`;
  }).join("");
}

async function createGoal() {
  const name = document.getElementById("goal-name-input")?.value?.trim();
  const targetAmount = document.getElementById("goal-target-input")?.value;
  const monthlyContribution = document.getElementById("goal-monthly-input")?.value;
  const targetDate = document.getElementById("goal-date-input")?.value;
  const accountId = document.getElementById("goal-account-input")?.value || APP_STATE.accounts[0]?.id;

  if (!name || !targetAmount) {
    showFailure("Please enter a goal name and target amount.");
    return;
  }

  const response = await apiRequest("/savings-goals", {
    method: "POST",
    body: {
      name,
      description: `${name} goal`,
      targetAmount: Number(targetAmount),
      monthlyContribution: monthlyContribution ? Number(monthlyContribution) : null,
      targetDate: targetDate || null,
      accountId: accountId ? Number(accountId) : null,
      icon: "target",
      color: "#c8f000",
    },
  });

  if (!response?.id) {
    showFailure("Goal creation failed.");
    return;
  }

  showToast("Goal created successfully.", "success");
  
  // Show success feedback
  const feedback = document.getElementById("goal-success-feedback");
  if (feedback) {
    feedback.style.display = "block";
    setTimeout(() => {
      feedback.style.display = "none";
    }, 3000);
  }
  
  // Clear form
  document.getElementById("goal-name-input").value = "";
  document.getElementById("goal-target-input").value = "";
  document.getElementById("goal-monthly-input").value = "";
  document.getElementById("goal-date-input").value = "";
  
  await loadGoalsPage(true);
  renderOverviewPage();
}

async function createBudget() {
  const name = document.getElementById("budget-name-input")?.value?.trim();
  const limit = document.getElementById("budget-limit-input")?.value;
  const alertThreshold = document.getElementById("budget-alert-input")?.value || "80";
  const startDate = document.getElementById("budget-date-input")?.value;
  const accountId = document.getElementById("budget-account-input")?.value || APP_STATE.accounts[0]?.id;

  if (!name || !limit) {
    showFailure("Please enter a budget category and monthly limit.");
    return;
  }

  const response = await apiRequest("/budgets", {
    method: "POST",
    body: {
      name,
      categoryName: name,
      limit: Number(limit),
      amount: Number(limit),
      totalAmount: Number(limit),
      alertThreshold: Number(alertThreshold),
      startDate: startDate || null,
      accountId: accountId ? Number(accountId) : null,
      type: "MONTHLY",
      color: "#FF6B6B",
    },
  });

  if (!response?.id) {
    showFailure("Budget creation failed.");
    return;
  }

  showToast("Budget created successfully.", "success");
  
  // Show success feedback
  const feedback = document.getElementById("budget-success-feedback");
  if (feedback) {
    feedback.style.display = "block";
    setTimeout(() => {
      feedback.style.display = "none";
    }, 3000);
  }
  
  // Clear form
  document.getElementById("budget-name-input").value = "";
  document.getElementById("budget-limit-input").value = "";
  document.getElementById("budget-alert-input").value = "80";
  document.getElementById("budget-date-input").value = "";
  
  await loadBudgetsPage();
}

async function contributeToGoal(goalId) {
  const input = document.getElementById(`goal-contribution-${goalId}`);
  const amount = input?.value;
  if (!amount || Number(amount) <= 0) {
    showFailure("Enter a valid contribution amount.");
    return;
  }

  const response = await apiRequest(`/savings-goals/${goalId}/contribute`, {
    method: "POST",
    body: {
      amount: Number(amount),
      notes: "Contribution from FinTrack web app",
    },
  });

  if (!response?.id) {
    showFailure("Contribution could not be added.");
    return;
  }

  showToast("Contribution added.", "success");
  await loadGoalsPage(true);
  renderOverviewPage();
}

async function refreshAllData() {
  const [transactionsResponse, analyticsResponse, intelligenceResponse, insightsResponse, budgetsResponse, goalsResponse, recurringResponse] = await Promise.all([
    apiRequest("/transactions?page=0&size=200"),
    apiRequest("/analytics/dashboard"),
    apiRequest("/intelligence/analyze", { method: "POST", silent: true }),
    apiRequest("/insights", { silent: true }),
    apiRequest("/budgets", { silent: true }),
    apiRequest("/savings-goals", { silent: true }),
    apiRequest("/recurring-transactions", { silent: true }),
  ]);

  APP_STATE.analytics = analyticsResponse || {};
  APP_STATE.intelligence = intelligenceResponse || {};
  APP_STATE.insights = insightsResponse || {};
  APP_STATE.budgets = Array.isArray(budgetsResponse) ? budgetsResponse : budgetsResponse?.content || [];
  APP_STATE.goals = Array.isArray(goalsResponse) ? goalsResponse : goalsResponse?.content || [];
  APP_STATE.recurring = Array.isArray(recurringResponse) ? recurringResponse : recurringResponse?.content || [];
  APP_STATE.hmmByDate = buildHmmLookup(APP_STATE.intelligence);
  APP_STATE.transactions = (Array.isArray(transactionsResponse) ? transactionsResponse : transactionsResponse?.content || [])
    .map(normalizeTransaction)
    .sort((left, right) => String(right.date).localeCompare(String(left.date)));

  APP_STATE.dataLoaded = true;
  PAGE_STATE.budgetsLoaded = false;
  _fzState.loaded = false;
  populateCategoryFilters();
  await loadAccounts();
  await loadFuzzyData();
  renderTransactionTable();
  renderOverviewPage();
  renderAnalyticsPage();
  renderGoalsPage();
  renderDashboard();
}

async function loadDashboardPage(force = false) {
  if (force || !APP_STATE.dataLoaded) {
    await refreshAllData();
  } else {
    renderDashboard();
  }
}

async function loadOverviewPage(force = false) {
  if (force || !APP_STATE.dataLoaded) {
    await refreshAllData();
  } else {
    renderOverviewPage();
  }
}

async function loadTransactionsPage(force = false) {
  bindUploadZone();
  if (force || !APP_STATE.dataLoaded) {
    await refreshAllData();
  } else {
    await loadAccounts();
    renderTransactionTable();
  }
}

async function loadAnalyticsPage(force = false) {
  if (force || !APP_STATE.dataLoaded) {
    await refreshAllData();
  } else {
    renderAnalyticsPage();
  }
}

async function loadGoalsPage(force = false) {
  if (force || !APP_STATE.dataLoaded) {
    await refreshAllData();
  } else {
    renderGoalsPage();
  }
}

function bindTransactionControls() {
  document.getElementById("tx-prev")?.addEventListener("click", () => {
    APP_STATE.txPage = Math.max(0, APP_STATE.txPage - 1);
    renderTransactionTable();
  });
  document.getElementById("tx-next")?.addEventListener("click", () => {
    APP_STATE.txPage += 1;
    renderTransactionTable();
  });
  document.getElementById("tx-search")?.addEventListener("input", () => {
    APP_STATE.txPage = 0;
    renderTransactionTable();
  });
  document.getElementById("tx-filter-type")?.addEventListener("change", () => {
    APP_STATE.txPage = 0;
    renderTransactionTable();
  });
  document.getElementById("tx-filter-cat")?.addEventListener("change", () => {
    APP_STATE.txPage = 0;
    renderTransactionTable();
  });
}

function bindDashboardControls() {
  document.getElementById("dash-search")?.addEventListener("input", (event) => {
    APP_STATE.dashboardFilters.search = event.target.value;
    APP_STATE.dashboardFilters.page = 1;
    renderDashboardTransactionTable();
  });
  document.getElementById("dash-catFilter")?.addEventListener("change", (event) => {
    APP_STATE.dashboardFilters.category = event.target.value;
    APP_STATE.dashboardFilters.page = 1;
    renderDashboardTransactionTable();
  });
  document.getElementById("dash-typeFilter")?.addEventListener("change", (event) => {
    APP_STATE.dashboardFilters.type = normalizeType(event.target.value);
    APP_STATE.dashboardFilters.page = 1;
    renderDashboardTransactionTable();
  });
  document.getElementById("dash-stateFilter")?.addEventListener("change", (event) => {
    APP_STATE.dashboardFilters.state = event.target.value;
    APP_STATE.dashboardFilters.page = 1;
    renderDashboardTransactionTable();
  });
}

function bindNavigation() {
  document.querySelectorAll(".tn[data-page], .nav-icon[data-page]").forEach((element) => {
    element.addEventListener("click", async () => {
      const page = element.dataset.page;
      updateNavigation(page);
      if (page === "overview") await loadOverviewPage();
      if (page === "dashboard") await loadDashboardPage();
      if (page === "transactions") await loadTransactionsPage();
      if (page === "budgets") await loadBudgetsPage();
      if (page === "analytics") await loadAnalyticsPage();
      if (page === "goals") await loadGoalsPage();
    });
  });
}

function bindAccountControls() {
  document.getElementById("create-account-btn")?.addEventListener("click", createAccount);
  bindUploadZone();
}

function bindTopbarControls() {
  if (document.getElementById("logout-btn")?.dataset.bound === "true") return;
  const logoutButton = document.getElementById("logout-btn");
  if (logoutButton) {
    logoutButton.dataset.bound = "true";
    logoutButton.addEventListener("click", async () => {
      await logout();
    });
  }
  renderUserChip();
}

async function handleLogin(email, password) {
  const submitButton = document.getElementById("login-submit");
  const normalizedEmail = String(email || "").trim().toLowerCase();
  const normalizedPassword = String(password || "");

  if (!normalizedEmail || !normalizedPassword) {
    showFailure("Enter your email and password.");
    return;
  }

  setButtonBusy(submitButton, true, "Login", "Signing In...");
  try {
    const response = await apiRequest("/auth/login", {
      method: "POST",
      body: { email: normalizedEmail, password: normalizedPassword },
    });
    if (!response?.accessToken) {
      showFailure("Login failed. Please verify your credentials.");
      return;
    }

    console.log("LOGIN SUCCESS");
    saveAuthState(response.accessToken, normalizeAuthUser(response.user, { email: normalizedEmail }));
    showToast("Login successful.", "success");
    location.reload();
  } finally {
    setButtonBusy(submitButton, false, "Login", "Signing In...");
  }
}

async function handleRegister(fullName, email, password) {
  const submitButton = document.getElementById("register-submit");
  const normalizedName = String(fullName || "").trim();
  const normalizedEmail = String(email || "").trim().toLowerCase();
  const normalizedPassword = String(password || "").trim();

  if (!normalizedName || !normalizedEmail || !normalizedPassword) {
    showFailure("Complete all registration fields.");
    return;
  }
  if (normalizedPassword.length < 8) {
    showFailure("Password must be at least 8 characters.");
    return;
  }

  setButtonBusy(submitButton, true, "Register", "Creating...");
  try {
    const response = await apiRequest("/auth/register", {
      method: "POST",
      body: { fullName: normalizedName, email: normalizedEmail, password: normalizedPassword, currency: "INR" },
    });
    if (!response?.accessToken) {
      showFailure("Registration failed.");
      return;
    }

    console.log("LOGIN SUCCESS");
    saveAuthState(response.accessToken, normalizeAuthUser(response.user, { fullName: normalizedName, email: normalizedEmail }));
    showToast("Registration successful.", "success");
    location.reload();
  } finally {
    setButtonBusy(submitButton, false, "Register", "Creating...");
  }
}

async function logout() {
  const logoutButton = document.getElementById("logout-btn");
  setButtonBusy(logoutButton, true, "Logout", "Signing Out...");
  await apiRequest("/auth/logout", { method: "POST", silent: true });
  clearAuth();
  location.reload();
}

window.logout = logout;
window.handlePdfUpload = handlePdfUpload;

window.addEventListener("load", async () => {
  console.log("APP INIT");
  updateTransactionFilterLabels();
  wireCursor();
  bindNavigation();
  bindTransactionControls();
  bindDashboardControls();
  bindAccountControls();
  loadAuthState();

  if (!APP_STATE.token) {
    goToLoginPage();
    return;
  }

  bindTopbarControls();
  bindUploadZone();
  updateNavigation("dashboard");
  try {
    await loadDashboardPage(true);
    await loadTransactionsPage();
  } catch (error) {
    console.error(error);
    showFailure("The frontend could not load dashboard data from the backend.");
  }
});

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// FUZZY AI ENGINE MODULE
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

const _fzState = {
  mfData: null,
  charts: {},
  loaded: false,
};

function fzAlertColor(level) {
  return ({
    safe: "var(--acid)",
    caution: "#ffb800",
    warning: "#ff6600",
    critical: "var(--ember)",
  })[String(level || "").toLowerCase()] || "var(--muted)";
}

function fzSeverityColor(score) {
  if (score >= 70) return "var(--ember)";
  if (score >= 40) return "#ffb800";
  return "var(--acid)";
}

async function fuzzyApiRequest(path, method = "GET", body = null) {
  try {
    const headers = { "Content-Type": "application/json" };
    if (APP_STATE.token) headers.Authorization = `Bearer ${APP_STATE.token}`;
    const options = { method, headers };
    if (body) options.body = JSON.stringify(body);

    for (const base of getApiBases()) {
      try {
        const response = await fetch(`${base}/ml/fuzzy${path}`, options);
        if (response.ok) {
          return await response.json();
        }
      } catch (_error) {
      }
    }
  } catch (error) {
    console.warn("[FUZZY] API error:", error?.message ?? error);
  }
  return null;
}

function buildLocalMFData() {
  const universe = Array.from({ length: 101 }, (_, index) => index);
  function trimf([a, b, c]) {
    return universe.map((x) => {
      if (x <= a || x >= c) return 0;
      if (x <= b) return (x - a) / (b - a);
      return (c - x) / (c - b);
    });
  }

  return {
    universe,
    variables: {
      income_stability: { low: trimf([0, 0, 45]), medium: trimf([30, 50, 70]), high: trimf([55, 100, 100]) },
      expense_level: { low: trimf([0, 0, 35]), medium: trimf([25, 50, 75]), high: trimf([60, 100, 100]) },
      savings_rate: { low: trimf([0, 0, 30]), medium: trimf([20, 45, 70]), high: trimf([55, 100, 100]) },
      financial_risk: { low: trimf([0, 0, 40]), medium: trimf([25, 50, 75]), high: trimf([60, 100, 100]) },
    },
  };
}

function renderMFChart(canvasId, varName, inputValue, mfData, mainColor) {
  const canvas = document.getElementById(canvasId);
  if (!canvas || !mfData?.variables) return;

  const variableData = mfData.variables[varName];
  if (!variableData) return;

  const labels = mfData.universe;
  const palette = ["#c8f000", "#00e5ff", "#ff4d00", "#b06fff"];
  const markerIndex = Math.round(Math.max(0, Math.min(100, Number(inputValue ?? 0))));

  if (_fzState.charts[canvasId]) {
    _fzState.charts[canvasId].destroy();
  }

  const datasets = Object.entries(variableData).map(([label, values], index) => ({
    label: label.replace(/_/g, " "),
    data: values,
    borderColor: palette[index % palette.length],
    backgroundColor: `${palette[index % palette.length]}22`,
    borderWidth: 2,
    fill: true,
    pointRadius: 0,
    tension: 0.1,
  }));

  if (inputValue !== undefined) {
    const markerData = new Array(labels.length).fill(null);
    markerData[markerIndex] = 1;
    datasets.push({
      label: "Input",
      data: markerData,
      borderColor: mainColor || "rgba(255,255,255,0.5)",
      backgroundColor: "transparent",
      borderWidth: 1.5,
      borderDash: [4, 4],
      pointRadius: (ctx) => ctx.dataIndex === markerIndex ? 4 : 0,
      pointBackgroundColor: "#ffffff",
      tension: 0,
      spanGaps: false,
    });
  }

  _fzState.charts[canvasId] = new Chart(canvas, {
    type: "line",
    data: { labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: { mode: "index", intersect: false },
      plugins: {
        legend: {
          labels: { color: "#5a5a52", font: { family: "IBM Plex Mono", size: 8 }, boxWidth: 10, padding: 8 },
        },
        tooltip: { enabled: false },
      },
      scales: {
        x: {
          ticks: {
            color: "#5a5a52",
            font: { family: "IBM Plex Mono", size: 8 },
            maxTicksLimit: 6,
            callback: (_value, index) => index % 20 === 0 ? index : "",
          },
          grid: { color: "rgba(255,255,255,0.04)" },
        },
        y: {
          min: 0,
          max: 1.05,
          ticks: {
            color: "#5a5a52",
            font: { family: "IBM Plex Mono", size: 8 },
            stepSize: 0.5,
          },
          grid: { color: "rgba(255,255,255,0.04)" },
        },
      },
    },
  });
}

function renderAllMFCharts(mfData, riskAssessment) {
  if (!riskAssessment) return;
  renderMFChart("mf-income", "income_stability", riskAssessment.income_stability, mfData, "var(--acid)");
  renderMFChart("mf-expense", "expense_level", riskAssessment.expense_level, mfData, "var(--ember)");
  renderMFChart("mf-savings", "savings_rate", Math.max(0, riskAssessment.savings_rate), mfData, "var(--ice)");
  renderMFChart("mf-risk-out", "financial_risk", riskAssessment.risk_score, mfData, "#b06fff");
}

function _revealFuzzySections() {
  ["fuzzy-risk-section", "fuzzy-budget-section", "fuzzy-savings-section", "fuzzy-anomaly-section"].forEach((id) => {
    const element = document.getElementById(id);
    if (!element) return;
    if (window.gsap) {
      gsap.to(element, { opacity: 1, y: 0, duration: 0.45, ease: "power3.out" });
      return;
    }
    element.style.opacity = "1";
    element.style.transform = "translateY(0)";
  });
}

function _deriveExpenseVolatility(transactions) {
  const expenses = (transactions || [])
    .filter((transaction) => {
      const type = String(transaction.type || transaction.transaction_type || "").toUpperCase();
      return type === "EXPENSE" || type === "DEBIT";
    })
    .map((transaction) => Math.abs(asNumber(transaction.amount)))
    .filter((amount) => amount > 0);

  if (expenses.length < 2) return 30;

  const mean = expenses.reduce((sum, amount) => sum + amount, 0) / expenses.length;
  const std = Math.sqrt(expenses.reduce((sum, amount) => sum + ((amount - mean) ** 2), 0) / expenses.length);
  return Math.min(100, mean > 0 ? (std / mean) * 100 : 0);
}

function _buildRiskFallback() {
  const totalIncome = asNumber(APP_STATE.analytics?.totalIncome);
  const totalExpenses = asNumber(APP_STATE.analytics?.totalExpenses || APP_STATE.analytics?.totalExpense);
  const savingsRate = totalIncome > 0 ? Math.max(0, ((totalIncome - totalExpenses) / totalIncome) * 100) : 0;
  const expenseLevel = totalIncome > 0 ? Math.min(100, (totalExpenses / totalIncome) * 100) : 0;
  const volatility = _deriveExpenseVolatility(APP_STATE.transactions);
  const riskScore = Math.min(100, Math.max(0, (expenseLevel * 0.45) + ((100 - savingsRate) * 0.35) + (volatility * 0.2)));

  return {
    income_stability: Math.max(0, 100 - volatility),
    expense_level: expenseLevel,
    savings_rate: savingsRate,
    debt_pressure: volatility,
    risk_score: riskScore,
    financial_risk: riskScore >= 70 ? "high" : riskScore >= 40 ? "medium" : "low",
    recommendation: riskScore >= 70 ? "reduce_spending" : riskScore >= 40 ? "save_more" : "safe_to_invest",
  };
}

async function renderFuzzyRiskPanel() {
  if (!document.getElementById("fuzzy-risk-section")) return;

  let riskAssessment = APP_STATE.intelligence?.risk_assessment;
  if (!riskAssessment) {
    const fallback = _buildRiskFallback();
    const recalculated = await fuzzyApiRequest("/risk", "POST", {
      income_stability: fallback.income_stability,
      expense_level: fallback.expense_level,
      savings_rate: fallback.savings_rate,
      debt_pressure: fallback.debt_pressure,
    });

    riskAssessment = {
      ...fallback,
      risk_score: recalculated?.financial_risk ?? fallback.risk_score,
      financial_risk: recalculated?.risk_label ?? fallback.financial_risk,
      recommendation: recalculated?.recommendation_label ?? fallback.recommendation,
    };
  }

  const riskColor = riskAssessment.risk_score >= 65 ? "var(--ember)" : riskAssessment.risk_score >= 35 ? "#ffb800" : "var(--acid)";
  const riskLabelColor = String(riskAssessment.financial_risk || "").toLowerCase() === "high"
    ? "var(--ember)"
    : String(riskAssessment.financial_risk || "").toLowerCase() === "medium"
      ? "var(--ice)"
      : "var(--acid)";

  setText("fz-income-val", Number(riskAssessment.income_stability || 0).toFixed(1));
  setText("fz-expense-val", Number(riskAssessment.expense_level || 0).toFixed(1));
  setText("fz-savings-val", Number(riskAssessment.savings_rate || 0).toFixed(1));
  setText("fz-risk-val", Number(riskAssessment.risk_score || 0).toFixed(1));
  setText("fz-risk-label", String(riskAssessment.financial_risk || "-").replace(/_/g, " "));
  setText("fz-recommendation", String(riskAssessment.recommendation || "-").replace(/_/g, " "));

  const riskValueEl = document.getElementById("fz-risk-val");
  if (riskValueEl) riskValueEl.style.color = riskColor;
  const riskLabelEl = document.getElementById("fz-risk-label");
  if (riskLabelEl) riskLabelEl.style.color = riskLabelColor;

  renderAllMFCharts(_fzState.mfData, riskAssessment);
}

async function loadFuzzyBudgets() {
  const listEl = document.getElementById("fuzzy-budget-list");
  if (!listEl) return;

  const budgets = Array.isArray(APP_STATE.budgets) ? APP_STATE.budgets : [];
  if (!budgets.length) {
    listEl.innerHTML = '<div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-align:center;padding:24px 0">No budgets configured yet. Add budgets in the Budgets tab to see fuzzy alerts.</div>';
    return;
  }

  const items = budgets.map((budget) => ({
    name: budget.name ?? budget.category ?? "Budget",
    spent: asNumber(budget.spent ?? budget.usedAmount),
    limit: Math.max(asNumber(budget.limit ?? budget.totalAmount ?? budget.amount), 1),
  }));

  const result = await fuzzyApiRequest("/budget-alert", "POST", { budgets: items });
  const alerts = result?.alerts ?? items.map((item) => {
    const pct = Math.min((item.spent / item.limit) * 100, 100);
    const level = pct >= 85 ? "critical" : pct >= 65 ? "warning" : pct >= 40 ? "caution" : "safe";
    return { name: item.name, spent: item.spent, limit: item.limit, pct_used: pct.toFixed(1), alert_level: level, score: pct };
  });

  listEl.innerHTML = alerts.map((alert) => {
    const color = fzAlertColor(alert.alert_level);
    const pct = parseFloat(alert.pct_used ?? 0);
    return `
      <div class="alert-box" style="border-left-color:${color};margin-bottom:10px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
          <div>
            <span style="font-family:var(--font-display);font-size:14px;font-weight:700;color:${color}">${escapeHtml(alert.name)}</span>
            <span style="font-family:var(--font-mono);font-size:9px;letter-spacing:0.12em;text-transform:uppercase;color:${color};margin-left:10px">${escapeHtml(String(alert.alert_level).toUpperCase())}</span>
          </div>
          <div style="text-align:right">
            <div style="font-family:var(--font-mono);font-size:9px;color:var(--muted)">Fuzzy Alert Score</div>
            <div style="font-family:var(--font-display);font-size:20px;font-weight:900;color:${color}">${Number(alert.score ?? pct).toFixed(1)}<span style="font-size:11px;color:var(--muted)">/100</span></div>
          </div>
        </div>
        <div style="height:3px;background:#2a2a26;margin-bottom:8px">
          <div style="height:100%;width:${Math.min(pct, 100)}%;background:${color};transition:width 0.4s cubic-bezier(0.22,1,0.36,1)"></div>
        </div>
        <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted)">${formatCurrency(alert.spent)} / ${formatCurrency(alert.limit)} · ${pct.toFixed(1)}% used</div>
      </div>`;
  }).join("");
}

async function loadFuzzySavings() {
  const contentEl = document.getElementById("fuzzy-savings-content");
  if (!contentEl) return;

  const income = asNumber(APP_STATE.analytics?.totalIncome);
  const expenses = asNumber(APP_STATE.analytics?.totalExpenses || APP_STATE.analytics?.totalExpense);
  const savings = income - expenses;
  const result = await fuzzyApiRequest("/savings-advisor", "POST", {
    monthly_income: income,
    monthly_expenses: expenses,
    current_savings: savings,
    savings_goal: null,
  });

  const strategy = result?.strategy ?? (savings / Math.max(income, 1) > 0.3 ? "Aggressive" : savings / Math.max(income, 1) > 0.15 ? "Moderate" : "Conservative");
  const range = result?.target_range ?? [Math.round(Math.max(savings, 0) * 1.05), Math.round(Math.max(savings, 0) * 1.4)];
  const advice = result?.advice ?? "Increase savings gradually and aim for a healthier monthly buffer.";
  const score = result?.score ?? Math.min(100, Math.max(15, Math.round((Math.max(savings, 0) / Math.max(income, 1)) * 100)));
  const strategyColor = strategy === "Aggressive" ? "var(--acid)" : strategy === "Moderate" ? "var(--ice)" : "var(--ember)";
  const savingsRate = income > 0 ? (savings / income) * 100 : 0;
  const expenseVolatility = _deriveExpenseVolatility(APP_STATE.transactions);
  const incomeStability = APP_STATE.intelligence?.risk_assessment?.income_stability ?? Math.max(0, 100 - expenseVolatility);

  contentEl.innerHTML = `
    <div style="display:grid;grid-template-columns:1fr 1fr 1fr 1fr;gap:20px;margin-bottom:24px">
      <div>
        <div style="font-family:var(--font-mono);font-size:8px;letter-spacing:0.2em;color:var(--muted);text-transform:uppercase;margin-bottom:6px">Current Savings Rate</div>
        <div style="font-family:var(--font-display);font-size:28px;font-weight:900;color:${savingsRate > 30 ? "var(--acid)" : "var(--ember)"}">${savingsRate.toFixed(1)}%</div>
      </div>
      <div>
        <div style="font-family:var(--font-mono);font-size:8px;letter-spacing:0.2em;color:var(--muted);text-transform:uppercase;margin-bottom:6px">Fuzzy Target Strategy</div>
        <div style="font-family:var(--font-display);font-size:28px;font-weight:900;color:${strategyColor};text-transform:uppercase">${escapeHtml(strategy)}</div>
      </div>
      <div>
        <div style="font-family:var(--font-mono);font-size:8px;letter-spacing:0.2em;color:var(--muted);text-transform:uppercase;margin-bottom:6px">Target Monthly Range</div>
        <div style="font-family:var(--font-display);font-size:22px;font-weight:900;color:${strategyColor}">${formatCurrency(range[0])} - ${formatCurrency(range[1])}</div>
      </div>
      <div>
        <div style="font-family:var(--font-mono);font-size:8px;letter-spacing:0.2em;color:var(--muted);text-transform:uppercase;margin-bottom:6px">Advisor Score</div>
        <div style="font-family:var(--font-display);font-size:28px;font-weight:900;color:${strategyColor}">${Number(score).toFixed(1)}</div>
      </div>
    </div>
    <div style="border-top:1px solid var(--line);padding-top:16px">
      <div style="font-family:var(--font-mono);font-size:8px;letter-spacing:0.15em;color:var(--muted);text-transform:uppercase;margin-bottom:8px">FIS Advisory</div>
      <div style="font-family:var(--font-mono);font-size:11px;color:var(--offwhite);line-height:1.7">${escapeHtml(advice)}</div>
      <div style="display:flex;gap:20px;margin-top:16px;flex-wrap:wrap">
        <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted)">Expense Volatility: <span style="color:var(--offwhite)">${expenseVolatility.toFixed(1)}/100</span></div>
        <div style="font-family:var(--font-mono);font-size:8px;color:var(--muted)">Income Stability: <span style="color:var(--offwhite)">${Number(incomeStability).toFixed(1)}/100</span></div>
      </div>
    </div>`;
}

async function loadFuzzyAnomalies() {
  const listEl = document.getElementById("fuzzy-anomaly-list");
  if (!listEl) return;

  const debits = (APP_STATE.transactions || []).filter((transaction) => {
    const type = String(transaction.type || transaction.transaction_type || "").toUpperCase();
    return type === "EXPENSE" || type === "DEBIT";
  }).slice(0, 30);

  if (!debits.length) {
    listEl.innerHTML = '<div style="font-family:var(--font-mono);font-size:10px;color:var(--muted);text-align:center;padding:16px 0">Upload transactions to see fuzzy anomaly severity scoring.</div>';
    return;
  }

  const txPayload = debits.map((transaction, index) => ({
    id: transaction.id ?? index + 1,
    description: transaction.description ?? transaction.merchant ?? transaction.merchant_person ?? "Transaction",
    amount: Math.abs(asNumber(transaction.amount)),
  }));

  const result = await fuzzyApiRequest("/anomaly-severity", "POST", { transactions: txPayload });
  const rows = Array.isArray(result) && result.length > 0
    ? result
    : (() => {
        const mean = txPayload.reduce((sum, transaction) => sum + transaction.amount, 0) / txPayload.length;
        const std = Math.sqrt(txPayload.reduce((sum, transaction) => sum + ((transaction.amount - mean) ** 2), 0) / txPayload.length) || 1;
        return txPayload
          .map((transaction) => {
            const severity = Math.min(Math.abs(transaction.amount - mean) / std * 25, 100);
            return {
              transaction_id: transaction.id,
              description: transaction.description,
              amount: transaction.amount,
              severity: Number(severity.toFixed(1)),
              label: severity >= 70 ? "Severe" : severity >= 40 ? "Moderate" : "Mild",
            };
          })
          .sort((left, right) => right.severity - left.severity)
          .slice(0, 10);
      })();

  listEl.innerHTML = `
    <div style="display:grid;grid-template-columns:1fr 110px 100px 110px;gap:8px;padding:0 0 10px;border-bottom:1px solid var(--line);font-family:var(--font-mono);font-size:8px;letter-spacing:0.2em;color:var(--muted);text-transform:uppercase">
      <span>Merchant</span><span>Amount</span><span>Severity</span><span>Label</span>
    </div>
    ${rows.map((row) => {
      const color = fzSeverityColor(Number(row.severity ?? 0));
      return `
        <div style="display:grid;grid-template-columns:1fr 110px 100px 110px;gap:8px;padding:12px 0;border-bottom:1px solid var(--line);align-items:center">
          <div style="font-family:var(--font-display);font-size:13px;font-weight:700;color:var(--offwhite)">${escapeHtml(row.description)}</div>
          <div style="font-family:var(--font-display);font-size:14px;font-weight:700;color:${color}">${formatCurrency(row.amount)}</div>
          <div style="font-family:var(--font-display);font-size:14px;font-weight:700;color:${color}">${Number(row.severity ?? 0).toFixed(1)}</div>
          <div style="font-family:var(--font-mono);font-size:8px;letter-spacing:0.12em;text-transform:uppercase;color:${color}">${escapeHtml(row.label)}</div>
        </div>`;
    }).join("")}`;
}

async function loadFuzzyInsights(force = false) {
  if (!document.getElementById("fuzzy-risk-section")) return;
  if (_fzState.loaded && !force) {
    _revealFuzzySections();
    return;
  }

  if (!_fzState.mfData || force) {
    const mfResult = await fuzzyApiRequest("/membership-functions");
    _fzState.mfData = mfResult?.variables ? mfResult : buildLocalMFData();
  }

  await Promise.all([
    renderFuzzyRiskPanel(),
    loadFuzzyBudgets(),
    loadFuzzySavings(),
    loadFuzzyAnomalies(),
  ]);

  _fzState.loaded = true;
  _revealFuzzySections();
}

window.loadFuzzyInsights = loadFuzzyInsights;
window.loadFuzzyPage = loadFuzzyInsights;
