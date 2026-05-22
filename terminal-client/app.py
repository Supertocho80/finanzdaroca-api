#!/usr/bin/env python3
"""
Bloomberg Terminal · Backoffice administrativo (Textual TUI).

CRUD completo sobre API Spring Boot con sesión JSESSIONID.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from api_client import APIError, FinanzAPI
from textual import on, work
from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Center, Grid, Horizontal, Vertical
from textual.screen import Screen
from textual.widgets import (
    Button,
    DataTable,
    Footer,
    Header,
    Input,
    Label,
    Select,
    Static,
    TabbedContent,
    TabPane,
)

DIVISA_DEFAULT = "EUR"

PERFILES_RIESGO = (
    ("CONSERVADOR", "CONSERVADOR"),
    ("MODERADO", "MODERADO"),
    ("AGRESIVO", "AGRESIVO"),
)
ROLES_USUARIO = (("ADMIN", "ADMIN"), ("ASESOR", "ASESOR"))
TIPOS_OPERACION = (
    ("COMPRA", "COMPRA"),
    ("VENTA", "VENTA"),
    ("DEPOSITO", "DEPOSITO"),
    ("RETIRO", "RETIRO"),
    ("DIVIDENDO", "DIVIDENDO"),
    ("ALQUILER", "ALQUILER"),
)
TIPOS_SIN_ACTIVO = frozenset({"DEPOSITO", "RETIRO"})

PLACEHOLDER_CLIENTES = [("Cargando clientes…", "")]
PLACEHOLDER_ACTIVOS = [("Cargando activos…", "")]
PLACEHOLDER_ASESORES = [("Cargando asesores…", "")]


# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------


def _money(value: float, moneda: str = DIVISA_DEFAULT) -> str:
    return f"{value:,.2f} {moneda}"


def _select_value(select: Select) -> Any:
    if select.value is Select.NULL:
        return None
    return select.value


def _select_int(select: Select) -> int | None:
    raw = _select_value(select)
    if raw is None or raw == "":
        return None
    return int(raw)


def _rol_str(rol: Any) -> str:
    if isinstance(rol, str):
        return rol.upper()
    return str(getattr(rol, "name", rol) or "").upper()


def _asesor_id(cliente: dict[str, Any]) -> int | None:
    asesor = cliente.get("asesor")
    if isinstance(asesor, dict) and asesor.get("id") is not None:
        return int(asesor["id"])
    return None


def _cliente_select_options(clientes: list[dict[str, Any]]) -> list[tuple[str, str]]:
    return [
        (f"{c.get('nombre', '—')} - {c.get('perfilRiesgo', '—')}", str(c["id"]))
        for c in clientes
        if c.get("id") is not None
    ]


def _activo_select_options(activos: list[dict[str, Any]]) -> list[tuple[str, str]]:
    return [
        (f"{a.get('ticker', '—')} - {a.get('nombre', '—')}", str(a["id"]))
        for a in activos
        if a.get("id") is not None
    ]


# ---------------------------------------------------------------------------
# Login
# ---------------------------------------------------------------------------


class LoginScreen(Screen):

    def compose(self) -> ComposeResult:
        with Center(id="login-center"):
            with Vertical(id="login-panel"):
                yield Label("◆ FINANZ DAROCA TERMINAL", classes="brand")
                yield Label("Backoffice · Sesión JSESSIONID", classes="subtitle")
                yield Input(placeholder="Usuario", id="username")
                yield Input(placeholder="Contraseña", password=True, id="password")
                yield Button("Conectar", id="btn-login", variant="primary")

    def on_mount(self) -> None:
        self.query_one("#username", Input).focus()

    @on(Button.Pressed, "#btn-login")
    def submit_login(self) -> None:
        try:
            user = self.query_one("#username", Input).value.strip()
            pwd = self.query_one("#password", Input).value
            if not user or not pwd:
                self.notify("Usuario y contraseña obligatorios", severity="warning")
                return
            self._do_login(user, pwd)
        except Exception as exc:
            self.notify(f"Error: {exc}", severity="error", timeout=8)

    @on(Input.Submitted)
    def login_on_enter(self) -> None:
        try:
            self.submit_login()
        except Exception as exc:
            self.notify(f"Error: {exc}", severity="error", timeout=8)

    @work(thread=True)
    def _do_login(self, username: str, password: str) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            user_data = api.login(username, password)
            self.app.call_from_thread(self._login_ok, user_data)
        except APIError as exc:
            self.app.call_from_thread(self._login_fail, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._login_fail, str(exc))

    def _login_ok(self, user_data: dict[str, Any]) -> None:
        app = self.app  # type: ignore[attr-defined]
        app.current_user_rol = _rol_str(user_data.get("rol"))
        app.current_user_id = user_data.get("id")
        app.current_username = str(user_data.get("username", ""))
        rol_label = app.current_user_rol or "—"
        self.notify(f"Conexión establecida · Rol {rol_label}", severity="information")
        self.app.push_screen(MainScreen())

    def _login_fail(self, message: str) -> None:
        self.notify(message, severity="error", timeout=8)


# ---------------------------------------------------------------------------
# Pantalla principal
# ---------------------------------------------------------------------------


class MainScreen(Screen):
    BINDINGS = [
        Binding("q", "salir", "Salir", show=True),
        Binding("r", "recargar_pestana", "Recargar", show=True),
    ]

    def __init__(self) -> None:
        super().__init__()
        self.cliente_edit_id: int | None = None
        self.usuario_edit_id: int | None = None
        self.transaccion_edit_id: int | None = None
        self._clientes_cache: list[dict[str, Any]] = []
        self._usuarios_cache: list[dict[str, Any]] = []
        self._transacciones_cache: list[dict[str, Any]] = []

    def _is_admin(self) -> bool:
        return getattr(self.app, "current_user_rol", "") == "ADMIN"

    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)
        with TabbedContent(id="main-tabs"):
            with TabPane("NAV & Portfolio", id="tab-nav"):
                yield from self._compose_nav_tab()
            with TabPane("Trading Desk", id="tab-trading"):
                yield from self._compose_trading_tab()
            with TabPane("CRM Clientes", id="tab-crm"):
                yield from self._compose_crm_tab()
            with TabPane("Market Activos", id="tab-market"):
                yield from self._compose_market_tab()
            if self._is_admin():
                with TabPane("Usuarios & Staff", id="tab-staff"):
                    yield from self._compose_usuarios_tab()
        yield Footer()

    # --- Composición de pestañas ---

    def _compose_nav_tab(self) -> ComposeResult:
        with Horizontal(classes="tab-toolbar"):
            yield Label("Cliente:")
            yield Select(
                PLACEHOLDER_CLIENTES,
                id="nav-cliente-select",
                prompt="Buscar por nombre",
                allow_blank=False,
            )
        yield Static("Seleccione un cliente para ver el informe NAV…", id="nav-summary", classes="nav-panel")
        with Horizontal(id="nav-tables-panel"):
            with Vertical(classes="nav-table-container"):
                yield Label("📊 Posiciones de Activos", classes="nav-section-title")
                yield DataTable(id="positions-table", zebra_stripes=True)
            with Vertical(classes="nav-table-container"):
                yield Label("💵 Billeteras de Efectivo", classes="nav-section-title")
                yield DataTable(id="cash-table", zebra_stripes=True)

    def _compose_trading_tab(self) -> ComposeResult:
        with Horizontal(classes="tab-toolbar"):
            yield Label("Cliente:")
            yield Select(
                PLACEHOLDER_CLIENTES,
                id="trade-cliente-select",
                prompt="Cliente",
                allow_blank=False,
            )
        with Horizontal(id="trading-layout"):
            with Vertical(classes="split-left"):
                yield Label("Historial de Operaciones", classes="form-title")
                yield DataTable(id="trade-history-table", zebra_stripes=True)
                yield Button(
                    "Eliminar Operación",
                    id="btn-delete-transaccion",
                    variant="error",
                )
            with Vertical(classes="split-right form-panel"):
                yield Label("Operación", id="trade-form-title", classes="form-title")
                yield Label("Tipo operación")
                yield Select(TIPOS_OPERACION, id="trade-tipo", value="COMPRA")
                yield Label("Activo (dividendo/alquiler/compra/venta)")
                yield Select(
                    PLACEHOLDER_ACTIVOS,
                    id="trade-activo-select",
                    prompt="Activo",
                    allow_blank=True,
                )
                yield Label("Moneda (depósito/retiro)")
                yield Input(value="EUR", id="trade-moneda")
                yield Label("Cantidad")
                yield Input(value="1", id="trade-cantidad", type="integer")
                yield Label("Precio ejecución")
                yield Input(value="0", id="trade-precio", type="number")
                with Horizontal(classes="form-actions"):
                    yield Button(
                        "Guardar Operación",
                        id="btn-save-transaccion",
                        variant="success",
                    )
                    yield Button("Limpiar/Nuevo", id="btn-clear-transaccion")
        yield Static("", id="trade-status")

    def _compose_crm_tab(self) -> ComposeResult:
        with Horizontal(id="crm-layout"):
            with Vertical(classes="split-left"):
                yield DataTable(id="crm-table", zebra_stripes=True)
                if self._is_admin():
                    yield Button("Eliminar Seleccionado", id="btn-delete-cliente", variant="error")
            with Vertical(classes="split-right form-panel"):
                yield Label("Gestión de Cliente", id="crm-form-title", classes="form-title")
                yield Label("Nombre")
                yield Input(placeholder="Nombre completo", id="crm-nombre")
                yield Label("Email")
                yield Input(placeholder="email@ejemplo.com", id="crm-email")
                yield Label("Perfil de Riesgo")
                yield Select(PERFILES_RIESGO, id="crm-perfil", value="CONSERVADOR")
                yield Label("Asesor")
                yield Select(
                    PLACEHOLDER_ASESORES,
                    id="crm-asesor-select",
                    prompt="Asesor",
                    allow_blank=False,
                )
                with Horizontal(classes="form-actions"):
                    yield Button("Guardar", id="btn-save-cliente", variant="success")
                    yield Button("Nuevo / Limpiar", id="btn-clear-cliente")

    def _compose_market_tab(self) -> ComposeResult:
        with Horizontal(id="market-layout"):
            with Vertical(classes="split-left"):
                with Horizontal(classes="tab-toolbar"):
                    yield Button("Refrescar", id="btn-refresh-activos", variant="primary")
                    if self._is_admin():
                        yield Button("Eliminar Seleccionado", id="btn-delete-activo", variant="error")
                yield DataTable(id="market-table", zebra_stripes=True)
            with Vertical(classes="split-right form-panel"):
                yield Label("Nuevo Activo", classes="form-title")
                yield Label("Ticker")
                yield Input(placeholder="AAPL", id="market-ticker")
                yield Label("Nombre")
                yield Input(placeholder="Apple Inc.", id="market-nombre")
                yield Label("Precio mercado")
                yield Input(value="0", id="market-precio", type="number")
                yield Label("Moneda")
                yield Input(value="EUR", id="market-moneda")
                yield Button("Crear Activo", id="btn-create-activo", variant="success")

    def _compose_usuarios_tab(self) -> ComposeResult:
        with Horizontal(id="usuarios-layout"):
            with Vertical(classes="split-left"):
                yield DataTable(id="usuarios-table", zebra_stripes=True)
                yield Button("Eliminar Seleccionado", id="btn-delete-usuario", variant="error")
            with Vertical(classes="split-right form-panel"):
                yield Label("Gestión de Usuario", id="usuarios-form-title", classes="form-title")
                yield Label("Username")
                yield Input(placeholder="usuario", id="usuarios-username")
                yield Label("Password")
                yield Input(
                    placeholder="Vacío = no cambiar (edición)",
                    password=True,
                    id="usuarios-password",
                )
                yield Label("Rol")
                yield Select(ROLES_USUARIO, id="usuarios-rol", value="ASESOR")
                with Horizontal(classes="form-actions"):
                    yield Button("Guardar Usuario", id="btn-save-usuario", variant="success")
                    yield Button("Nuevo / Limpiar", id="btn-clear-usuario")

    # --- Ciclo de vida ---

    def on_mount(self) -> None:
        try:
            self._init_tables()
            self.refrescar_datos_maestros(cargar_nav=True)
        except Exception as exc:
            self._notify_fatal_ui(exc)

    def _init_tables(self) -> None:
        specs: list[tuple[str, list[str]]] = [
            (
                "#positions-table",
                ["Ticker", "Cantidad", "Precio Medio", "Precio Actual", "Rentab. %", "MiFID"],
            ),
            ("#cash-table", ["Divisa", "Saldo Original", "Contravalor (EUR)"]),
            ("#crm-table", ["ID", "Nombre", "Email", "Perfil Riesgo"]),
            (
                "#trade-history-table",
                ["ID", "Tipo", "Activo/Moneda", "Cantidad", "Precio", "Fecha"],
            ),
            ("#market-table", ["ID", "Ticker", "Nombre", "Precio Mercado", "Moneda"]),
        ]
        if self._is_admin():
            specs.append(("#usuarios-table", ["ID", "Username", "Rol"]))
        for table_id, columns in specs:
            table = self.query_one(table_id, DataTable)
            table.clear(columns=True)
            table.add_columns(*columns)
            table.cursor_type = "row"

    # --- Notificaciones y utilidades UI ---

    def _notify_ok(self, message: str) -> None:
        self.notify(message, severity="information", timeout=6)

    def _notify_err(self, message: str) -> None:
        self.notify(message, severity="error", timeout=8)

    def _notify_fatal_ui(self, error: Exception) -> None:
        self.notify(f"Error: {error}", severity="error", timeout=8)

    def _notify_sync_fail(self, message: str) -> None:
        self.notify(
            f"Error al sincronizar con el servidor: {message}",
            severity="error",
            timeout=8,
        )

    def _ui_safe(self, action: Callable[[], None]) -> None:
        """Ejecuta lógica de UI capturando cualquier excepción sin tumbar la TUI."""
        try:
            action()
        except Exception as exc:
            self._notify_fatal_ui(exc)

    def _has_widget(self, selector: str) -> bool:
        try:
            return bool(self.query(selector))
        except Exception:
            return False

    def _safe_set_input(self, selector: str, value: str) -> None:
        if not self._has_widget(selector):
            return
        try:
            self.query_one(selector, Input).value = value
        except Exception:
            pass

    def _safe_set_select(self, selector: str, value: str) -> None:
        if not self._has_widget(selector):
            return
        try:
            self.query_one(selector, Select).value = value
        except Exception:
            pass

    def _safe_update_label(self, selector: str, text: str) -> None:
        if not self._has_widget(selector):
            return
        try:
            self.query_one(selector, Label).update(text)
        except Exception:
            pass

    @staticmethod
    def _table_selected_id(table: DataTable) -> int | None:
        if table.row_count == 0 or table.cursor_row is None:
            return None
        try:
            row = table.get_row_at(table.cursor_row)
        except Exception:
            return None
        if not row:
            return None
        try:
            return int(str(row[0]))
        except (TypeError, ValueError):
            return None

    def _apply_select_options(
        self, select: Select, options: list[tuple[str, str]], *, blank: bool = False
    ) -> None:
        if options:
            select.set_options(options)
            select.value = options[0][1]
        elif blank:
            select.set_options([("Sin opciones", "")])
            select.value = Select.NULL
        else:
            select.set_options([("Sin opciones", "")])

    # --- Sincronización global (tablas + Select NAV/Trading) ---

    @work(thread=True)
    def refrescar_datos_maestros(self, cargar_nav: bool = False, notify: bool = False) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        is_admin = getattr(self.app, "current_user_rol", "") == "ADMIN"
        try:
            clientes = api.get_clientes()
            activos = api.get_activos()
            usuarios = api.get_usuarios() if is_admin else []
            self.app.call_from_thread(
                self._on_datos_maestros,
                clientes,
                activos,
                usuarios,
                cargar_nav,
                notify,
                is_admin,
            )
        except APIError as exc:
            self.app.call_from_thread(self._notify_sync_fail, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._notify_sync_fail, str(exc))

    def _on_datos_maestros(
        self,
        clientes: list[dict[str, Any]],
        activos: list[dict[str, Any]],
        usuarios: list[dict[str, Any]],
        cargar_nav: bool,
        notify: bool,
        is_admin: bool,
    ) -> None:
        try:
            self._clientes_cache = list(clientes)
            self._usuarios_cache = list(usuarios)

            cliente_opts = _cliente_select_options(clientes)
            activo_opts = _activo_select_options(activos)

            for sid in ("#nav-cliente-select", "#trade-cliente-select"):
                if self._has_widget(sid):
                    self._apply_select_options(self.query_one(sid, Select), cliente_opts)

            if self._has_widget("#trade-activo-select"):
                activo_sel = self.query_one("#trade-activo-select", Select)
                self._apply_select_options(activo_sel, activo_opts, blank=True)

            if self._has_widget("#crm-asesor-select"):
                asesor_sel = self.query_one("#crm-asesor-select", Select)
                if is_admin:
                    asesores = [u for u in usuarios if _rol_str(u.get("rol")) == "ASESOR"] or usuarios
                    asesor_opts = [
                        (f"{u.get('username', '—')} - {_rol_str(u.get('rol'))}", str(u["id"]))
                        for u in asesores
                        if u.get("id") is not None
                    ]
                    if asesor_opts:
                        asesor_sel.set_options(asesor_opts)
                        if self.cliente_edit_id is None:
                            asesor_sel.value = asesor_opts[0][1]
                    else:
                        asesor_sel.set_options(PLACEHOLDER_ASESORES)
                else:
                    uid = getattr(self.app, "current_user_id", None)
                    uname = getattr(self.app, "current_username", "—")
                    if uid is not None:
                        asesor_sel.set_options([(f"{uname} - ASESOR", str(uid))])
                        asesor_sel.value = str(uid)
                    else:
                        asesor_sel.set_options(PLACEHOLDER_ASESORES)

            self._pintar_tabla_clientes(clientes)
            self._pintar_tabla_activos(activos)
            if is_admin:
                self._pintar_tabla_usuarios(usuarios)

            if notify:
                extra = f", {len(usuarios)} usuarios" if is_admin else ""
                self._notify_ok(
                    f"Datos sincronizados · {len(clientes)} clientes, "
                    f"{len(activos)} activos{extra}"
                )
            if cargar_nav and cliente_opts:
                self.cargar_cartera()
        except Exception as exc:
            self._notify_fatal_ui(exc)

    def _pintar_tabla_clientes(self, clientes: list[dict[str, Any]]) -> None:
        table = self.query_one("#crm-table", DataTable)
        table.clear()
        for c in clientes:
            table.add_row(
                str(c.get("id", "")),
                str(c.get("nombre", "—")),
                str(c.get("email", "—")),
                str(c.get("perfilRiesgo", "—")),
            )

    def _pintar_tabla_activos(self, activos: list[dict[str, Any]]) -> None:
        activos = list(activos)
        activos.sort(key=lambda x: x.get("id", 0))
        table = self.query_one("#market-table", DataTable)
        table.clear()
        for a in activos:
            table.add_row(
                str(a.get("id", "")),
                str(a.get("ticker", "—")),
                str(a.get("nombre", "—")),
                f"{float(a.get('precioMercado', 0)):,.2f}",
                str(a.get("moneda") or "—"),
            )

    def _pintar_tabla_usuarios(self, usuarios: list[dict[str, Any]]) -> None:
        if not self._is_admin():
            return
        table = self.query_one("#usuarios-table", DataTable)
        table.clear()
        for u in usuarios:
            table.add_row(
                str(u.get("id", "")),
                str(u.get("username", "—")),
                _rol_str(u.get("rol")) or "—",
            )

    # --- Formularios: limpiar ---

    def _limpiar_formulario_cliente(self) -> None:
        self.cliente_edit_id = None
        self._safe_set_input("#crm-nombre", "")
        self._safe_set_input("#crm-email", "")
        self._safe_set_select("#crm-perfil", "CONSERVADOR")
        self._safe_update_label("#crm-form-title", "Gestión de Cliente")

    def _limpiar_formulario_usuario(self) -> None:
        if not self._is_admin():
            self.usuario_edit_id = None
            return
        self.usuario_edit_id = None
        self._safe_set_input("#usuarios-username", "")
        self._safe_set_input("#usuarios-password", "")
        self._safe_set_select("#usuarios-rol", "ASESOR")
        self._safe_update_label("#usuarios-form-title", "Gestión de Usuario")

    def _limpiar_formulario_activo(self) -> None:
        self._safe_set_input("#market-ticker", "")
        self._safe_set_input("#market-nombre", "")
        self._safe_set_input("#market-precio", "0")
        self._safe_set_input("#market-moneda", DIVISA_DEFAULT)

    def _limpiar_formulario_transaccion(self) -> None:
        self.transaccion_edit_id = None
        self._safe_set_select("#trade-tipo", "COMPRA")
        self._safe_set_input("#trade-moneda", DIVISA_DEFAULT)
        self._safe_set_input("#trade-cantidad", "1")
        self._safe_set_input("#trade-precio", "0")
        self._safe_update_label("#trade-form-title", "Operación")

    def _finalizar_mutacion(self, mensaje: str) -> None:
        """Tras crear/editar/eliminar: notificar, limpiar forms y resincronizar todo."""
        self._notify_ok(mensaje)
        self._limpiar_formulario_cliente()
        self._limpiar_formulario_usuario()
        self._limpiar_formulario_activo()
        self.refrescar_datos_maestros(cargar_nav=True)

    # --- Acciones globales ---

    def action_salir(self) -> None:
        def _salir() -> None:
            app = self.app  # type: ignore[attr-defined]
            app.api.logout()
            app.current_user_rol = ""
            app.current_user_id = None
            app.current_username = ""
            self.app.pop_screen()
            self.notify("Sesión cerrada", severity="information")

        self._ui_safe(_salir)

    def action_recargar_pestana(self) -> None:
        def _recargar() -> None:
            tabs = self.query_one("#main-tabs", TabbedContent)
            active = tabs.active or "tab-nav"
            if active == "tab-nav":
                self.cargar_cartera()
            elif active == "tab-trading":
                self.refrescar_datos_maestros(notify=True)
                self.cargar_historial_transacciones()
            elif active in ("tab-crm",) or (active == "tab-staff" and self._is_admin()):
                self.refrescar_datos_maestros(notify=True)
            elif active == "tab-market":
                self.refrescar_datos_maestros(notify=True)

        self._ui_safe(_recargar)

    # --- NAV: carga automática al elegir cliente por nombre ---

    @on(Select.Changed, "#nav-cliente-select")
    def on_nav_cliente_changed(self) -> None:
        self._ui_safe(self.cargar_cartera)

    @work(thread=True)
    def cargar_cartera(self) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            cliente_id = _select_int(self.query_one("#nav-cliente-select", Select))
            if cliente_id is None:
                raise ValueError("Seleccione un cliente en el desplegable")
            data = api.get_cartera(cliente_id, DIVISA_DEFAULT)
            self.app.call_from_thread(self._render_cartera, data)
        except (APIError, ValueError) as exc:
            self.app.call_from_thread(self._notify_err, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._notify_sync_fail, str(exc))

    def _render_cartera(self, data: dict[str, Any]) -> None:
        try:
            self._render_cartera_inner(data)
        except Exception as exc:
            self._notify_fatal_ui(exc)

    def _render_cartera_inner(self, data: dict[str, Any]) -> None:
        moneda = str(data.get("monedaDestino", DIVISA_DEFAULT))
        beneficio = float(data.get("beneficioGlobalNeto", 0))
        rent = float(data.get("rentabilidadGlobalPorcentaje", 0))
        up, down = "#39ff14", "#ff3b3b"
        b_col = up if beneficio >= 0 else down
        r_col = up if rent >= 0 else down

        summary = (
            f"[bold #58a6ff]{data.get('nombreCliente', '—')}[/]  ·  "
            f"{data.get('perfilRiesgo', '—')}  ·  #{data.get('clienteId', '')}\n\n"
            f"[bold]PATRIMONIO NETO (NAV)[/]\n"
            f"[bold #39ff14]{_money(float(data.get('patrimonioNetoTotal', 0)), moneda)}[/]\n\n"
            f"Efectivo: [bold]{_money(float(data.get('saldoEfectivoTotal', 0)), moneda)}[/]\n"
            f"Capital depositado: [bold]{_money(float(data.get('capitalTotalDepositado', 0)), moneda)}[/]\n"
            f"Valoración activos: [bold]{_money(float(data.get('valoracionTotalActivos', 0)), moneda)}[/]\n\n"
            f"Beneficio global: [{b_col}]{_money(beneficio, moneda)}[/]\n"
            f"Rentabilidad: [{r_col}]{rent:.2f}%[/]\n"
            f"Comisión éxito: {_money(float(data.get('comisionExito', 0)), moneda)}"
        )
        self.query_one("#nav-summary", Static).update(summary)

        positions_table = self.query_one("#positions-table", DataTable)
        positions_table.clear()
        for pos in data.get("posicionesActivos") or []:
            moneda_orig = pos.get("monedaOriginal") or moneda
            positions_table.add_row(
                str(pos.get("ticker", "—")),
                str(pos.get("cantidad", 0)),
                _money(float(pos.get("precioMedioCompra", 0)), moneda_orig),
                _money(float(pos.get("precioMercadoActual", 0)), moneda_orig),
                f"{float(pos.get('rentabilidadPorcentaje', 0)):.2f}%",
                "⚠️" if pos.get("alertaMifid") else "",
            )

        cash_table = self.query_one("#cash-table", DataTable)
        cash_table.clear()
        for efectivo in data.get("posicionesEfectivo") or []:
            divisa = str(efectivo.get("moneda") or DIVISA_DEFAULT)
            saldo_orig = float(efectivo.get("saldoTotalOriginal", 0) or 0)
            saldo_dest = float(efectivo.get("saldoEnMonedaDestino", 0) or 0)
            cash_table.add_row(
                divisa,
                _money(saldo_orig, divisa),
                _money(saldo_dest, moneda),
            )

    # --- Edición interactiva: CRM ---

    @on(DataTable.RowSelected, "#crm-table")
    def on_crm_row_selected(self, event: DataTable.RowSelected) -> None:
        def _fill() -> None:
            row = event.data_table.get_row_at(event.cursor_row)
            if not row:
                return
            try:
                cliente_id = int(str(row[0]))
            except ValueError:
                return
            cliente = next((c for c in self._clientes_cache if c.get("id") == cliente_id), None)
            if not cliente:
                return

            self.cliente_edit_id = cliente_id
            self._safe_set_input("#crm-nombre", str(cliente.get("nombre", "")))
            self._safe_set_input("#crm-email", str(cliente.get("email", "")))
            self._safe_set_select("#crm-perfil", str(cliente.get("perfilRiesgo", "CONSERVADOR")))
            aid = _asesor_id(cliente)
            if aid is not None:
                self._safe_set_select("#crm-asesor-select", str(aid))
            self._safe_update_label("#crm-form-title", f"Editar Cliente #{cliente_id}")

        self._ui_safe(_fill)

    @on(Button.Pressed, "#btn-save-cliente")
    def on_save_cliente(self) -> None:
        self._ui_safe(self._guardar_cliente)

    @on(Button.Pressed, "#btn-clear-cliente")
    def on_clear_cliente(self) -> None:
        self._ui_safe(self._limpiar_formulario_cliente)

    @on(Button.Pressed, "#btn-delete-cliente")
    def on_delete_cliente(self) -> None:
        def _delete() -> None:
            cid = self._table_selected_id(self.query_one("#crm-table", DataTable))
            if cid is None:
                self.notify("Seleccione un cliente en la tabla", severity="warning")
                return
            self._eliminar_cliente(cid)

        self._ui_safe(_delete)

    @work(thread=True)
    def _guardar_cliente(self) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            nombre = self.query_one("#crm-nombre", Input).value.strip()
            email = self.query_one("#crm-email", Input).value.strip()
            perfil = str(self.query_one("#crm-perfil", Select).value)
            asesor_id = _select_int(self.query_one("#crm-asesor-select", Select))
            if not nombre or not email:
                raise ValueError("Nombre y email son obligatorios")
            if asesor_id is None:
                raise ValueError("Seleccione un asesor")

            payload = {
                "nombre": nombre,
                "email": email,
                "perfilRiesgo": perfil,
                "asesor": {"id": asesor_id},
            }
            if self.cliente_edit_id is not None:
                r = api.actualizar_cliente(self.cliente_edit_id, payload)
                msg = f"Cliente actualizado · #{r.get('id', self.cliente_edit_id)}"
            else:
                r = api.crear_cliente(payload)
                msg = f"Cliente creado · #{r.get('id', 'OK')} {r.get('nombre', nombre)}"
            self.app.call_from_thread(self._finalizar_mutacion, msg)
        except (APIError, ValueError) as exc:
            self.app.call_from_thread(self._notify_err, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._notify_err, str(exc))

    @work(thread=True)
    def _eliminar_cliente(self, cliente_id: int) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            api.eliminar_cliente(cliente_id)
            self.app.call_from_thread(
                self._finalizar_mutacion, f"Cliente #{cliente_id} eliminado"
            )
        except (APIError, ValueError) as exc:
            self.app.call_from_thread(self._notify_err, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._notify_err, str(exc))

    # --- Edición interactiva: Usuarios ---

    @on(DataTable.RowSelected, "#usuarios-table")
    def on_usuarios_row_selected(self, event: DataTable.RowSelected) -> None:
        def _fill() -> None:
            row = event.data_table.get_row_at(event.cursor_row)
            if not row:
                return
            try:
                usuario_id = int(str(row[0]))
            except ValueError:
                return
            usuario = next((u for u in self._usuarios_cache if u.get("id") == usuario_id), None)
            if not usuario:
                return

            self.usuario_edit_id = usuario_id
            self._safe_set_input("#usuarios-username", str(usuario.get("username", "")))
            self._safe_set_input("#usuarios-password", "")
            self._safe_set_select("#usuarios-rol", _rol_str(usuario.get("rol")) or "ASESOR")
            self._safe_update_label("#usuarios-form-title", f"Editar Usuario #{usuario_id}")

        self._ui_safe(_fill)

    @on(Button.Pressed, "#btn-save-usuario")
    def on_save_usuario(self) -> None:
        self._ui_safe(self._guardar_usuario)

    @on(Button.Pressed, "#btn-clear-usuario")
    def on_clear_usuario(self) -> None:
        self._ui_safe(self._limpiar_formulario_usuario)

    @on(Button.Pressed, "#btn-delete-usuario")
    def on_delete_usuario(self) -> None:
        def _delete() -> None:
            uid = self._table_selected_id(self.query_one("#usuarios-table", DataTable))
            if uid is None:
                self.notify("Seleccione un usuario en la tabla", severity="warning")
                return
            self._eliminar_usuario(uid)

        self._ui_safe(_delete)

    @work(thread=True)
    def _guardar_usuario(self) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            username = self.query_one("#usuarios-username", Input).value.strip()
            password = self.query_one("#usuarios-password", Input).value
            rol = str(self.query_one("#usuarios-rol", Select).value)
            if not username:
                raise ValueError("Username obligatorio")

            payload: dict[str, Any] = {"username": username, "rol": rol}

            if self.usuario_edit_id is not None:
                if password.strip():
                    payload["password"] = password
                r = api.actualizar_usuario(self.usuario_edit_id, payload)
                msg = f"Usuario actualizado · {r.get('username', username)}"
            else:
                if not password.strip():
                    raise ValueError("Password obligatoria al crear usuario")
                payload["password"] = password
                r = api.crear_usuario(payload)
                msg = f"Usuario creado · {r.get('username', username)}"
            self.app.call_from_thread(self._finalizar_mutacion, msg)
        except (APIError, ValueError) as exc:
            self.app.call_from_thread(self._notify_err, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._notify_err, str(exc))

    @work(thread=True)
    def _eliminar_usuario(self, usuario_id: int) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            api.eliminar_usuario(usuario_id)
            self.app.call_from_thread(
                self._finalizar_mutacion, f"Usuario #{usuario_id} eliminado"
            )
        except APIError as exc:
            self.app.call_from_thread(self._notify_err, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._notify_err, str(exc))

    # --- Market activos ---

    @on(Button.Pressed, "#btn-refresh-activos")
    def on_refresh_activos(self) -> None:
        self._ui_safe(lambda: self.refrescar_datos_maestros(notify=True))

    @on(Button.Pressed, "#btn-create-activo")
    def on_create_activo(self) -> None:
        self._ui_safe(self._crear_activo)

    @on(Button.Pressed, "#btn-delete-activo")
    def on_delete_activo(self) -> None:
        def _delete() -> None:
            aid = self._table_selected_id(self.query_one("#market-table", DataTable))
            if aid is None:
                self.notify("Seleccione un activo en la tabla", severity="warning")
                return
            self._eliminar_activo(aid)

        self._ui_safe(_delete)

    @work(thread=True)
    def _crear_activo(self) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            ticker = self.query_one("#market-ticker", Input).value.strip().upper()
            nombre = self.query_one("#market-nombre", Input).value.strip()
            precio = float(self.query_one("#market-precio", Input).value.strip() or "0")
            moneda = (
                self.query_one("#market-moneda", Input).value.strip().upper()
                or DIVISA_DEFAULT
            )
            if not ticker or not nombre:
                raise ValueError("Ticker y nombre son obligatorios")
            payload = {
                "ticker": ticker,
                "nombre": nombre,
                "precioMercado": precio,
                "moneda": moneda,
            }
            created = api.crear_activo(payload)
            msg = f"Activo creado · {created.get('ticker', ticker)} (#{created.get('id', 'OK')})"
            self.app.call_from_thread(self._finalizar_mutacion, msg)
        except (APIError, ValueError) as exc:
            self.app.call_from_thread(self._notify_err, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._notify_err, str(exc))

    @work(thread=True)
    def _eliminar_activo(self, activo_id: int) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            api.eliminar_activo(activo_id)
            self.app.call_from_thread(
                self._finalizar_mutacion, f"Activo #{activo_id} eliminado"
            )
        except APIError as exc:
            self.app.call_from_thread(self._notify_err, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._notify_err, str(exc))

    # --- Trading Desk ---

    @on(Select.Changed, "#trade-cliente-select")
    def on_trade_cliente_changed(self) -> None:
        self._ui_safe(self.cargar_historial_transacciones)

    @on(DataTable.RowSelected, "#trade-history-table")
    def on_trade_history_row_selected(self, event: DataTable.RowSelected) -> None:
        def _fill() -> None:
            tid = self._table_selected_id(self.query_one("#trade-history-table", DataTable))
            if tid is None:
                return
            trans = next(
                (t for t in self._transacciones_cache if t.get("id") == tid),
                None,
            )
            if trans is None:
                return
            self.transaccion_edit_id = tid
            tipo = str(trans.get("tipoOperacion", "COMPRA"))
            self._safe_set_select("#trade-tipo", tipo)
            self._safe_set_input(
                "#trade-cantidad", str(trans.get("cantidad", 1) or 1)
            )
            self._safe_set_input(
                "#trade-precio", str(trans.get("precioEjecucion", 0) or 0)
            )
            if tipo in TIPOS_SIN_ACTIVO:
                self._safe_set_input(
                    "#trade-moneda",
                    str(trans.get("moneda", DIVISA_DEFAULT) or DIVISA_DEFAULT),
                )
            else:
                activo = trans.get("activoFinanciero") or {}
                if activo.get("id") is not None:
                    self._safe_set_select("#trade-activo-select", str(activo["id"]))
            self._safe_update_label("#trade-form-title", f"Editar Operación #{tid}")

        self._ui_safe(_fill)

    @on(Button.Pressed, "#btn-clear-transaccion")
    def on_clear_transaccion(self) -> None:
        self._ui_safe(self._limpiar_formulario_transaccion)

    @on(Button.Pressed, "#btn-save-transaccion")
    def on_save_transaccion(self) -> None:
        self._ui_safe(self._guardar_transaccion)

    @on(Button.Pressed, "#btn-delete-transaccion")
    def on_delete_transaccion(self) -> None:
        def _delete() -> None:
            tid = self._table_selected_id(
                self.query_one("#trade-history-table", DataTable)
            )
            if tid is None:
                self.notify(
                    "Seleccione una operación en el historial",
                    severity="warning",
                )
                return
            self._eliminar_transaccion(tid)

        self._ui_safe(_delete)

    @work(thread=True)
    def cargar_historial_transacciones(self) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            if not self._has_widget("#trade-history-table"):
                return
            cliente_id = _select_int(self.query_one("#trade-cliente-select", Select))
            if cliente_id is None:
                self.app.call_from_thread(self._pintar_historial_transacciones, [])
                return
            transacciones = api.get_transacciones(cliente_id)
            self.app.call_from_thread(self._pintar_historial_transacciones, transacciones)
        except APIError as exc:
            self.app.call_from_thread(self._trade_fail, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._trade_fail, str(exc))

    def _pintar_historial_transacciones(self, transacciones: list[dict[str, Any]]) -> None:
        try:
            self._transacciones_cache = list(transacciones)
            table = self.query_one("#trade-history-table", DataTable)
            table.clear()
            for t in transacciones:
                tipo = str(t.get("tipoOperacion", "—"))
                activo = t.get("activoFinanciero") or {}
                if tipo in TIPOS_SIN_ACTIVO:
                    activo_moneda = str(t.get("moneda", "—") or "—")
                else:
                    activo_moneda = str(activo.get("ticker") or activo.get("nombre") or "—")
                fecha_raw = t.get("fecha")
                fecha = str(fecha_raw)[:16].replace("T", " ") if fecha_raw else "—"
                table.add_row(
                    str(t.get("id", "")),
                    tipo,
                    activo_moneda,
                    str(t.get("cantidad", "")),
                    str(t.get("precioEjecucion", "")),
                    fecha,
                )
        except Exception as exc:
            self._notify_fatal_ui(exc)

    @work(thread=True)
    def _guardar_transaccion(self) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            payload = self._build_trade_payload()
            if self.transaccion_edit_id is not None:
                result = api.actualizar_transaccion(self.transaccion_edit_id, payload)
                msg = f"Operación actualizada · #{result.get('id', self.transaccion_edit_id)}"
            else:
                result = api.crear_transaccion(payload)
                msg = f"Operación registrada · #{result.get('id', 'OK')}"
            self.app.call_from_thread(self._trade_ok, msg)
            self.app.call_from_thread(self.cargar_historial_transacciones)
            self.app.call_from_thread(self.cargar_cartera)
        except (APIError, ValueError) as exc:
            self.app.call_from_thread(self._trade_fail, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._trade_fail, str(exc))

    @work(thread=True)
    def _eliminar_transaccion(self, transaccion_id: int) -> None:
        api: FinanzAPI = self.app.api  # type: ignore[attr-defined]
        try:
            api.eliminar_transaccion(transaccion_id)
            self.app.call_from_thread(
                self._trade_ok, f"Operación #{transaccion_id} eliminada"
            )
            if self.transaccion_edit_id == transaccion_id:
                self.app.call_from_thread(self._limpiar_formulario_transaccion)
            self.app.call_from_thread(self.cargar_historial_transacciones)
            self.app.call_from_thread(self.cargar_cartera)
        except APIError as exc:
            self.app.call_from_thread(self._trade_fail, str(exc))
        except Exception as exc:
            self.app.call_from_thread(self._trade_fail, str(exc))

    def _build_trade_payload(self) -> dict[str, Any]:
        tipo = str(self.query_one("#trade-tipo", Select).value)
        cliente_id = _select_int(self.query_one("#trade-cliente-select", Select))
        if cliente_id is None:
            raise ValueError("Seleccione un cliente para la operación")

        payload: dict[str, Any] = {
            "cliente": {"id": cliente_id},
            "tipoOperacion": tipo,
            "precioEjecucion": float(self.query_one("#trade-precio", Input).value.strip() or "0"),
            "cantidad": int(self.query_one("#trade-cantidad", Input).value.strip() or "1"),
        }

        if tipo in TIPOS_SIN_ACTIVO:
            payload["moneda"] = (
                self.query_one("#trade-moneda", Input).value.strip().upper() or DIVISA_DEFAULT
            )
            payload["activoFinanciero"] = None
        else:
            activo_id = _select_int(self.query_one("#trade-activo-select", Select))
            if activo_id is None:
                raise ValueError(
                    "Seleccione un activo (COMPRA, VENTA, DIVIDENDO o ALQUILER)"
                )
            payload["activoFinanciero"] = {"id": activo_id}

        return payload

    def _trade_ok(self, message: str) -> None:
        self.query_one("#trade-status", Static).update(f"[#39ff14]{message}[/]")
        self._notify_ok(message)

    def _trade_fail(self, message: str) -> None:
        self.query_one("#trade-status", Static).update(f"[#ff3b3b]{message}[/]")
        self._notify_err(message)


# ---------------------------------------------------------------------------
# Aplicación
# ---------------------------------------------------------------------------


class FinanzDarocaApp(App):
    TITLE = "FINANZ DAROCA · Bloomberg TUI"
    SUB_TITLE = "Backoffice administrativo"
    CSS_PATH = "styles.tcss"

    BINDINGS = [Binding("ctrl+c", "quit", "Cerrar", show=False)]

    def __init__(self, base_url: str = "http://localhost:8080") -> None:
        super().__init__()
        self.api = FinanzAPI(base_url)
        self.current_user_rol: str = ""
        self.current_user_id: int | None = None
        self.current_username: str = ""

    def on_mount(self) -> None:
        self.push_screen(LoginScreen())


if __name__ == "__main__":
    FinanzDarocaApp().run()
