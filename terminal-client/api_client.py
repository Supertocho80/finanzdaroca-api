"""
Cliente HTTP para la API Spring Boot (sesión JSESSIONID).

Mapeo REST → métodos de FinanzAPI.
"""

from __future__ import annotations

from typing import Any

import requests


class APIError(Exception):
    """Error de red o respuesta HTTP no exitosa (fuera del rango 2xx)."""

    def __init__(self, message: str, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


class FinanzAPI:
    """Motor de conexión con cookie de sesión Spring Security."""

    def __init__(self, base_url: str = "http://localhost:8080") -> None:
        self.base_url = base_url.rstrip("/")
        self._session = requests.Session()
        self._session.headers.update(
            {
                "Accept": "application/json",
                "Content-Type": "application/json",
            }
        )

    @property
    def session(self) -> requests.Session:
        return self._session

    # --- Autenticación ---

    def login(self, username: str, password: str) -> dict[str, Any]:
        return self._request(
            "POST",
            "/api/login",
            json={"username": username, "password": password},
            timeout=15,
        )

    def logout(self) -> None:
        try:
            self._session.post(f"{self.base_url}/api/logout", timeout=10)
        except requests.RequestException:
            pass
        self._session.cookies.clear()

    # --- Usuarios (/api/usuarios) ---

    def get_usuarios(self) -> list[dict[str, Any]]:
        return self._as_list(self._request("GET", "/api/usuarios", timeout=20))

    def crear_usuario(self, payload_dict: dict[str, Any]) -> dict[str, Any]:
        return self._as_dict(
            self._request("POST", "/api/usuarios", json=payload_dict, timeout=25)
        )

    def actualizar_usuario(
        self, usuario_id: int, payload_dict: dict[str, Any]
    ) -> dict[str, Any]:
        return self._as_dict(
            self._request(
                "PUT",
                f"/api/usuarios/{usuario_id}",
                json=payload_dict,
                timeout=25,
            )
        )

    def eliminar_usuario(self, usuario_id: int) -> None:
        self._request("DELETE", f"/api/usuarios/{usuario_id}", timeout=20)

    # --- Clientes (/api/clientes) ---

    def get_clientes(self) -> list[dict[str, Any]]:
        return self._as_list(self._request("GET", "/api/clientes", timeout=20))

    def crear_cliente(self, payload_dict: dict[str, Any]) -> dict[str, Any]:
        return self._as_dict(
            self._request("POST", "/api/clientes", json=payload_dict, timeout=25)
        )

    def actualizar_cliente(
        self, cliente_id: int, payload_dict: dict[str, Any]
    ) -> dict[str, Any]:
        return self._as_dict(
            self._request(
                "PUT",
                f"/api/clientes/{cliente_id}",
                json=payload_dict,
                timeout=25,
            )
        )

    def eliminar_cliente(self, cliente_id: int) -> None:
        self._request("DELETE", f"/api/clientes/{cliente_id}", timeout=20)

    def get_cartera(self, cliente_id: int, divisa: str = "EUR") -> dict[str, Any]:
        data = self._request(
            "GET",
            f"/api/clientes/{cliente_id}/cartera",
            params={"divisa": divisa},
            timeout=25,
        )
        if isinstance(data, dict):
            return data
        raise APIError("Respuesta de cartera inválida")

    # --- Activos financieros (/api/activos-financieros) ---

    def get_activos(self) -> list[dict[str, Any]]:
        return self._as_list(
            self._request("GET", "/api/activos-financieros", timeout=20)
        )

    def crear_activo(self, payload_dict: dict[str, Any]) -> dict[str, Any]:
        return self._as_dict(
            self._request(
                "POST",
                "/api/activos-financieros",
                json=payload_dict,
                timeout=25,
            )
        )

    def actualizar_activo(
        self, activo_id: int, payload_dict: dict[str, Any]
    ) -> dict[str, Any]:
        return self._as_dict(
            self._request(
                "PUT",
                f"/api/activos-financieros/{activo_id}",
                json=payload_dict,
                timeout=25,
            )
        )

    def eliminar_activo(self, activo_id: int) -> None:
        self._request("DELETE", f"/api/activos-financieros/{activo_id}", timeout=20)

    # --- Transacciones (/api/transacciones) ---

    def get_transacciones(self, cliente_id: int) -> list[dict[str, Any]]:
        return self._as_list(
            self._request(
                "GET",
                f"/api/transacciones/cliente/{cliente_id}",
                timeout=25,
            )
        )

    def crear_transaccion(self, payload_dict: dict[str, Any]) -> dict[str, Any]:
        return self._as_dict(
            self._request(
                "POST",
                "/api/transacciones",
                json=payload_dict,
                timeout=25,
            )
        )

    def actualizar_transaccion(
        self, transaccion_id: int, payload_dict: dict[str, Any]
    ) -> dict[str, Any]:
        return self._as_dict(
            self._request(
                "PUT",
                f"/api/transacciones/{transaccion_id}",
                json=payload_dict,
                timeout=25,
            )
        )

    def eliminar_transaccion(self, transaccion_id: int) -> None:
        self._request("DELETE", f"/api/transacciones/{transaccion_id}", timeout=20)

    # --- Transporte ---

    def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        url = f"{self.base_url}{path}"
        try:
            response = self._session.request(method, url, **kwargs)
        except requests.RequestException as exc:
            raise APIError(f"No se pudo conectar con el backend: {exc}") from exc

        if not (200 <= response.status_code < 300):
            raise APIError(self._extract_error(response), response.status_code)

        if not response.content:
            return {}

        try:
            return response.json()
        except ValueError as exc:
            raise APIError("Respuesta JSON inválida del servidor") from exc

    @staticmethod
    def _extract_error(response: requests.Response) -> str:
        try:
            body = response.json()
            if isinstance(body, dict):
                if body.get("error"):
                    return str(body["error"])
                if body.get("mensaje"):
                    return str(body["mensaje"])
        except ValueError:
            pass
        return f"Error HTTP {response.status_code}"

    @staticmethod
    def _as_list(data: Any) -> list[dict[str, Any]]:
        if isinstance(data, list):
            return data
        return []

    @staticmethod
    def _as_dict(data: Any) -> dict[str, Any]:
        if isinstance(data, dict):
            return data
        return {}
