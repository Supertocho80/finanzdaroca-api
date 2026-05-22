#!/usr/bin/env python3
"""Cliente TUI para el Sistema Financiero (Spring Boot + sesión por cookie)."""

from __future__ import annotations

from api_client import CarteraData, FinancieroApiClient
from textual import on, work
from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Container, Grid
from textual.screen import Screen
from textual.widgets import Button, DataTable, Footer, Header, Input, Label, Static

CLIENTE_ID = 1
DIVISA = "EUR"


def _fmt_money(value: float, moneda: str = "EUR") -> str:
    return f"{value:,.2f} {moneda}"


class LoginScreen(Screen):
    """Formulario de autenticación contra POST /api/login."""

    CSS = """
    LoginScreen {
        align: center middle;
    }
    """

    def compose(self) -> ComposeResult:
        with Container(id="login-form"):
            yield Label("◆ SISTEMA FINANCIERO DAROCA", classes="login-title")
            yield Label("Acceso seguro (sesión JSESSIONID)", classes="neutral")
            yield Input(placeholder="Usuario", id="username")
            yield Input(placeholder="Contraseña", password=True, id="password")
            yield Button("Conectar", variant="primary", id="connect")

    def on_mount(self) -> None:
        self.query_one("#username", Input).focus()

    @on(Button.Pressed, "#connect")
    def conectar(self) -> None:
        username = self.query_one("#username", Input).value.strip()
        password = self.query_one("#password", Input).value
        if not username or not password:
            self.notify("Introduzca usuario y contraseña", severity="warning")
            return
        self._ejecutar_login(username, password)

    @on(Input.Submitted)
    def enviar_con_enter(self) -> None:
        self.conectar()

    @work(thread=True)
    def _ejecutar_login(self, username: str, password: str) -> None:
        api: FinancieroApiClient = self.app.api  # type: ignore[attr-defined]
        ok, mensaje = api.login(username, password)
        self.app.call_from_thread(self._finalizar_login, ok, mensaje)

    def _finalizar_login(self, ok: bool, mensaje: str) -> None:
        if ok:
            self.notify("Sesión iniciada correctamente", severity="information")
            self.app.push_screen("dashboard")
        else:
            self.notify(mensaje or "Error de autenticación", severity="error", timeout=6)


class DashboardScreen(Screen):
    """Panel NAV + tabla de posiciones."""

    BINDINGS = [
        Binding("q", "salir", "Salir", show=True),
        Binding("r", "recargar", "Recargar", show=True),
    ]

    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)
        with Grid(id="dashboard-grid"):
            with Container(classes="panel"):
                yield Label("RESUMEN NAV", classes="panel-title")
                yield Static("Cargando cartera…", id="nav-content")
            with Container(classes="panel"):
                yield Label("POSICIONES", classes="panel-title")
                yield DataTable(id="positions-table", zebra_stripes=True)
        yield Footer()

    def on_mount(self) -> None:
        tabla = self.query_one("#positions-table", DataTable)
        tabla.add_columns(
            "Ticker",
            "Cantidad",
            "Precio Medio",
            "Precio Actual",
            "Valoración",
            "MiFID",
        )
        tabla.cursor_type = "row"
        self.recargar_cartera()

    def action_recargar(self) -> None:
        self.recargar_cartera()

    def action_salir(self) -> None:
        api: FinancieroApiClient = self.app.api  # type: ignore[attr-defined]
        api.logout()
        self.app.pop_screen()
        self.notify("Sesión cerrada", severity="information")

    @work(thread=True)
    def recargar_cartera(self) -> None:
        api: FinancieroApiClient = self.app.api  # type: ignore[attr-defined]
        cartera, error = api.obtener_cartera(CLIENTE_ID, DIVISA)
        self.app.call_from_thread(self._mostrar_cartera, cartera, error)

    def _mostrar_cartera(self, cartera: CarteraData | None, error: str) -> None:
        if error:
            self.notify(error, severity="error", timeout=8)
            self.query_one("#nav-content", Static).update(
                f"[red]No se pudo cargar la cartera.[/]\n{error}"
            )
            return

        if cartera is None:
            return

        beneficio_cls = "profit" if cartera.beneficio_global_neto >= 0 else "loss"
        rent_cls = "profit" if cartera.rentabilidad_global_porcentaje >= 0 else "loss"
        moneda = cartera.moneda_destino

        nav_text = (
            f"[bold]{cartera.nombre_cliente}[/]  ·  {cartera.perfil_riesgo}\n"
            f"Cliente #{cartera.cliente_id}  ·  Divisa: {moneda}\n\n"
            f"Capital depositado:\n"
            f"[bold]{_fmt_money(cartera.capital_total_depositado, moneda)}[/]\n\n"
            f"Efectivo disponible:\n"
            f"[bold]{_fmt_money(cartera.saldo_efectivo_total, moneda)}[/]\n\n"
            f"Valoración activos:\n"
            f"[bold]{_fmt_money(cartera.valoracion_total_activos, moneda)}[/]\n\n"
            f"Patrimonio neto (NAV):\n"
            f"[bold $accent]{_fmt_money(cartera.patrimonio_neto_total, moneda)}[/]\n\n"
            f"Beneficio global: [{beneficio_cls}]{_fmt_money(cartera.beneficio_global_neto, moneda)}[/]\n"
            f"Rentabilidad: [{rent_cls}]{cartera.rentabilidad_global_porcentaje:.2f}%[/]\n"
            f"Comisión éxito: {_fmt_money(cartera.comision_exito, moneda)}"
        )
        self.query_one("#nav-content", Static).update(nav_text)

        tabla = self.query_one("#positions-table", DataTable)
        tabla.clear()
        for pos in cartera.posiciones_activos:
            mifid = "⚠️" if pos.get("alertaMifid") else ""
            moneda_orig = pos.get("monedaOriginal") or moneda
            tabla.add_row(
                str(pos.get("ticker", "—")),
                str(pos.get("cantidad", 0)),
                _fmt_money(float(pos.get("precioMedioCompra", 0)), moneda_orig),
                _fmt_money(float(pos.get("precioMercadoActual", 0)), moneda_orig),
                _fmt_money(float(pos.get("valoracionTotal", 0)), moneda),
                mifid,
            )

        if not cartera.posiciones_activos:
            self.notify("Sin posiciones abiertas en cartera", severity="warning")


class FinancieroApp(App):
    """Aplicación principal Textual."""

    TITLE = "Sistema Financiero · Terminal"
    CSS_PATH = "app.tcss"

    SCREENS = {
        "login": LoginScreen,
        "dashboard": DashboardScreen,
    }

    BINDINGS = [
        Binding("ctrl+c", "quit", "Cerrar app", show=False),
    ]

    def __init__(self, base_url: str = "http://localhost:8080") -> None:
        super().__init__()
        self.api = FinancieroApiClient(base_url)

    def on_mount(self) -> None:
        self.push_screen("login")


if __name__ == "__main__":
    FinancieroApp().run()
