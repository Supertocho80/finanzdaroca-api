# 🏦 FinanzDaroca · Sistema Patrimonial Full-Stack

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen.svg)
![Python](https://img.shields.io/badge/Python-3.11+-3776AB.svg?logo=python&logoColor=white)
![Textual](https://img.shields.io/badge/Textual-8.x-FFD43B.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)
![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED.svg)
![Security](https://img.shields.io/badge/Security-Spring%20Security-success.svg)

Sistema integral de gestión patrimonial e inversiones para asesores financieros independientes. Desarrollado en el marco del programa **Kit Digital (NextGenerationEU)**, combina un **motor contable y normativo en Java** con un **cliente de terminal profesional en Python** que emula el flujo de trabajo de una **Terminal Bloomberg**: consulta de NAV, ejecución de operaciones, CRM de clientes y gobierno de mercado, todo gobernado por sesión y roles.

---

## 🚀 Arquitectura y Stack Tecnológico

El proyecto sigue una **arquitectura multicapa** con separación clara entre presentación, API, dominio y persistencia.

### Backend (Java / Spring Boot)

| Capa | Tecnología |
|------|------------|
| **Runtime** | Java 21, Spring Boot 4.0.6 |
| **API** | REST (`spring-boot-starter-webmvc`) |
| **Persistencia** | Spring Data JPA / Hibernate, PostgreSQL 15 |
| **Seguridad** | Spring Security — autenticación stateful por cookie `JSESSIONID`, contraseñas BCrypt, RBAC y control de acceso por cliente (row-level) |
| **Caché** | Spring Cache (`@EnableCaching`) — caché en memoria de cotizaciones Yahoo Finance |
| **Integración** | `HttpClient` nativo → Yahoo Finance Chart API |
| **Datos iniciales** | `schema.sql` + `data.sql` (idempotentes al arranque) |
| **Configuración** | Variables en `.env` importadas vía `spring.config.import` |
| **Testing** | JUnit 5, Mockito, AssertJ |

### Frontend (Python / Textual TUI)

| Componente | Descripción |
|------------|-------------|
| **Cliente** | `terminal-client/` — aplicación [Textual](https://textual.textualize.io/) (TUI) |
| **Estilo** | Terminal tipo **Bloomberg** (paleta oscura, tablas densas, atajos de teclado) |
| **Comunicación** | `api_client.py` — sesión HTTP con `requests`, cookie `JSESSIONID` tras login |
| **Adaptabilidad** | Pestañas y acciones según rol devuelto por la API (`ADMIN` vs `ASESOR`) |

```
┌─────────────────────┐     JSESSIONID      ┌──────────────────────────┐
│  Textual TUI        │ ◄─────────────────► │  Spring Boot REST API    │
│  (terminal-client)  │     JSON / REST     │  (sistema-financiero)    │
└─────────────────────┘                     └────────────┬─────────────┘
                                                         │
                                              ┌──────────▼──────────┐
                                              │  PostgreSQL (Docker) │
                                              └─────────────────────┘
```

---

## ✨ Características Principales (Business Logic)

1. **Motor NAV (Net Asset Value) en tiempo real**  
   Consolidación de patrimonio: efectivo multi-divisa (EUR, USD, GBP, GBp, etc.), valoración de activos vivos, tipos de cambio vía Yahoo y desglose de **billeteras de liquidez** (`posicionesEfectivo`) en la TUI.

2. **Multi-clase de activos y operaciones**  
   - **Acciones:** `COMPRA` / `VENTA`  
   - **Ingresos patrimoniales:** `DIVIDENDO`, `ALQUILER` (inmuebles u otros activos vinculados en catálogo)  
   - **Tesorería:** `DEPOSITO` / `RETIRO` multi-divisa  

3. **Cuenta remunerada automática**  
   Interés compuesto prorrateado (2 % anual) sobre liquidez no invertida.

4. **Cumplimiento MiFID II (alertas)**  
   Detección de desalineación entre perfil `CONSERVADOR` y activos volátiles no exentos.

5. **Caché de cotizaciones (anti rate-limit)**  
   `@Cacheable("cotizaciones")` en `YahooFinanceChartClient` reduce llamadas repetidas a Yahoo Finance y mitiga bloqueos por IP al calcular NAV o sincronizar precios.

6. **Sincronización programada de mercado**  
   Tarea `@Scheduled` actualiza precios de activos en base de datos desde Yahoo Finance.

7. **Seguridad por sesión y por fila (RGPD / RBAC)**  
   - **ADMIN:** acceso global (clientes, usuarios, activos, transacciones).  
   - **ASESOR:** solo clientes asignados; la TUI **oculta o deshabilita** componentes (p. ej. pestaña *Usuarios & Staff*, borrado de activos) según el rol autenticado.

8. **Trading Desk con historial editable**  
   Alta, edición y baja de transacciones desde la TUI (corrección de errores operativos con recálculo automático del NAV).

---

## ⚙️ Requisitos Previos

| Herramienta | Versión recomendada |
|-------------|---------------------|
| **Docker** + Docker Compose | Reciente |
| **JDK** | 21+ |
| **Maven** | Incluido (`./mvnw`) |
| **Python** | 3.11+ |
| **jq** (opcional) | Para formatear respuestas `curl` |

---

## 📦 Instalación y Arranque

### Paso 0 · Variables de entorno (obligatorio)

Antes de levantar Docker o Spring, crea el archivo **`.env` en la raíz del repositorio**:

```bash
# .env (raíz del proyecto — NO subir a Git)
DB_PASSWORD=secret123
```

- Está listado en `.gitignore`.
- **Docker Compose** lo usa vía `env_file` e interpolación `${DB_PASSWORD:-secret123}`.
- **Spring Boot** lo carga con `spring.config.import=optional:file:.env[.properties]` y `spring.datasource.password=${DB_PASSWORD:secret123}`.

> **Error `password authentication failed`:** el volumen `.postgres-data` puede haberse creado con otra contraseña.  
> - **Sin borrar datos:**  
>   `docker exec finanzas-db psql -U admin_daroca -d finanz_daroca -c "ALTER USER admin_daroca WITH PASSWORD 'secret123';"`  
> - **Base limpia:**  
>   `docker compose down -v && docker compose up -d`

### Paso 1 · Base de datos (Docker)

```bash
docker compose up -d
```

PostgreSQL queda expuesto en `localhost:5432` (BD: `finanz_daroca`, usuario: `admin_daroca`).

Para reiniciar desde cero (borra datos locales):

```bash
docker compose down -v
docker compose up -d
```

### Paso 2 · Backend (Spring Boot)

Desde la raíz del proyecto:

```bash
./mvnw clean spring-boot:run
```

API disponible en **`http://localhost:8080`**.

### Paso 3 · Frontend TUI (Python / Textual)

En otra terminal:

```bash
cd terminal-client
python -m venv venv
source venv/bin/activate          # Linux / macOS
# venv\Scripts\activate           # Windows (PowerShell / CMD)
pip install -r requirements.txt
python app.py
```

La TUI se conecta por defecto a `http://localhost:8080`. Inicia sesión con uno de los usuarios de prueba (tabla inferior).

#### Pestañas principales de la TUI

| Pestaña | Función |
|---------|---------|
| **NAV & Portfolio** | Informe patrimonial, posiciones de activos y billeteras de efectivo |
| **Trading Desk** | Historial de operaciones + alta/edición/baja de transacciones |
| **CRM Clientes** | Gestión de clientes y asignación de asesor |
| **Market Activos** | Catálogo de tickers y precios |
| **Usuarios & Staff** | Solo **ADMIN** — gestión de usuarios del sistema |

Atajos útiles: **`q`** salir · **`r`** recargar pestaña activa.

---

## ⚖️ Alcance del Sistema: API vs Cliente TUI

FinanzDaroca adopta un enfoque **API-First**: el backend **Spring Boot** es el **motor completo y la fuente de verdad** del dominio. Concentra el 100 % de la lógica de negocio — evaluación **MiFID II**, cálculo de **NAV** multi-divisa, libro mayor de transacciones, seguridad por roles y el **CRON** de sincronización con **Yahoo Finance** (con caché anti rate-limit).

El cliente **Textual TUI** (`terminal-client/`) es un **consumidor operativo opcional** que emula una terminal Bloomberg: cubre el flujo diario del asesor o administrador (alta de operaciones, consulta de cartera, CRM, catálogo de activos) y expone aproximadamente el **80 % de los endpoints REST** de forma interactiva. Cualquier capacidad no disponible en la terminal permanece accesible vía **API pura** (`curl`, scripts o futuros frontends web/móviles).

### Tabla comparativa de capacidades

| Capacidad / Recurso | Backend API (Spring Boot) | TUI (Cliente Python) |
|---------------------|---------------------------|----------------------|
| Autenticación (`JSESSIONID`) | ✅ Soportado | ✅ Soportado |
| Gestión de Usuarios (Staff) | ✅ CRUD completo (solo ADMIN) | ✅ Alta y baja (solo ADMIN) |
| Gestión de Clientes (CRM) | ✅ CRUD completo + filtros por asesor | ✅ Alta, edición y baja |
| Catálogo de activos | ✅ CRUD completo | ✅ Alta y baja (edición vía API) |
| Transacciones y libro mayor | ✅ Global (ADMIN) e individual (ASESOR) | ✅ Historial individual por cliente |
| Cálculo NAV y cartera | ✅ Multi-divisa dinámica (`?divisa=`) | ✅ Fijado a divisa base (EUR) |
| Intereses y devengos | ✅ Desglose de intereses generados | ❌ Agrupado en saldo total |
| Sincronización Yahoo Finance | ✅ Automática asíncrona (`@Scheduled`) | ✅ Transparente al cliente |

### Limitaciones operativas de la TUI

Las operaciones siguientes están **reservadas a la API REST** (integraciones externas, automatización o `curl`). La TUI no las sustituye; el contrato oficial del sistema es siempre la API:

- **Listado global de transacciones** del sistema (`GET /api/transacciones`) — solo **ADMIN**; la terminal solo muestra el historial por cliente.
- **Alteración manual de la fecha** de una transacción en alta o edición — la TUI registra con la fecha del servidor (actual por defecto); la API acepta `fecha` en el cuerpo del `PUT`/`POST`.
- **Edición (`PUT`) de activos financieros** existentes (ticker, nombre, precio de mercado, moneda) — solo **ADMIN** en backend; en Market la TUI permite crear y eliminar, no modificar.
- **Cambio de divisa base** para consolidar y visualizar el NAV (`?divisa=USD`, `GBP`, etc.) — soportado dinámicamente por la API; la TUI consolida y muestra el informe en **EUR**.

> **Integradores:** cualquier nuevo canal (web, móvil, reporting regulatorio) debe implementarse contra los endpoints documentados en esta guía, sin duplicar reglas de negocio fuera del backend.

---

## 📖 Guía de Uso

### Usuarios de prueba

Contraseña común para todos: **`secret123`**

| Usuario | Rol | Alcance típico |
|---------|-----|----------------|
| `admin_jefe` | ADMIN | Todos los clientes, usuarios y activos |
| `asesor_aleix` | ASESOR | Solo clientes asignados a Aleix |
| `asesor_maria` | ASESOR | Solo clientes asignados a María |

### Interacción recomendada: Terminal TUI

1. Arranca backend + base de datos.  
2. Ejecuta `python app.py` en `terminal-client/`.  
3. Inicia sesión → explora NAV, registra operaciones en *Trading Desk*, gestiona clientes en *CRM*.  
4. El rol determina qué pestañas y botones están disponibles (p. ej. *Staff* solo para administradores).

### API REST alternativa (cURL)

La API sigue siendo el contrato oficial del sistema. CSRF está desactivado para facilitar integraciones; la autenticación es por **cookie de sesión** tras `POST /api/login`.

<details>
<summary>Ver ejemplos de API REST pura con cURL</summary>

#### Login (obtener `JSESSIONID`)

```bash
curl -i -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username": "asesor_aleix", "password": "secret123"}' \
  -c cookies.txt
```

#### Depósito de capital

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/transacciones \
  -H "Content-Type: application/json" \
  -d '{
    "cliente": {"id": 1},
    "tipoOperacion": "DEPOSITO",
    "precioEjecucion": 5000.0,
    "moneda": "EUR"
  }'
```

#### Compra de acciones (ej. Apple, activo id 2)

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/transacciones \
  -H "Content-Type: application/json" \
  -d '{
    "cliente": {"id": 1},
    "activoFinanciero": {"id": 2},
    "tipoOperacion": "COMPRA",
    "cantidad": 10,
    "precioEjecucion": 180.0
  }'
```

#### Dividendo o alquiler (ingreso sobre activo)

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/transacciones \
  -H "Content-Type: application/json" \
  -d '{
    "cliente": {"id": 1},
    "activoFinanciero": {"id": 5},
    "tipoOperacion": "DIVIDENDO",
    "cantidad": 1,
    "precioEjecucion": 250.0
  }'
```

#### Cartera consolidada (NAV)

```bash
curl -b cookies.txt -X GET "http://localhost:8080/api/clientes/1/cartera?divisa=EUR" | jq
```

#### Historial de transacciones por cliente

```bash
curl -b cookies.txt -X GET "http://localhost:8080/api/transacciones/cliente/1" | jq
```

#### Actualizar / eliminar transacción

```bash
# PUT — corregir precio o tipo
curl -b cookies.txt -X PUT http://localhost:8080/api/transacciones/42 \
  -H "Content-Type: application/json" \
  -d '{
    "cliente": {"id": 1},
    "activoFinanciero": {"id": 2},
    "tipoOperacion": "COMPRA",
    "cantidad": 10,
    "precioEjecucion": 185.0
  }'

# DELETE
curl -b cookies.txt -X DELETE http://localhost:8080/api/transacciones/42
```

</details>

---

## 🧪 Tests

La lógica financiera central (`ClienteService`) incluye pruebas unitarias con mocks (sin base de datos real):

```bash
./mvnw test
```

---

## 🛡️ Auditoría y Conformidad

Este sistema aborda las carencias habituales de gestión patrimonial en hojas de cálculo aisladas:

| Área | Enfoque |
|------|---------|
| **Confidencialidad** | PostgreSQL centralizado, acceso autenticado y segregación por asesor |
| **Integridad** | Ledger de transacciones como fuente de verdad del NAV |
| **Trazabilidad** | Operaciones tipadas (`COMPRA`, `VENTA`, `DEPOSITO`, `RETIRO`, `DIVIDENDO`, `ALQUILER`) con control de acceso previo a cada mutación |
| **Normativa** | Alertas MiFID II en cartera conservadora |

> **Nota:** Las transacciones pueden corregirse vía API/TUI bajo políticas de acceso (ADMIN / ASESOR del cliente). Para entornos regulados de producción se recomienda complementar con auditoría inmutable (historial de cambios, asientos de reversión).

---

## 📁 Estructura del Repositorio

```
sistema-financiero/
├── .env                    # Secretos locales (no versionado)
├── docker-compose.yml      # PostgreSQL
├── src/main/java/          # Backend Spring Boot
├── src/main/resources/     # schema.sql, data.sql, application.properties
├── terminal-client/        # TUI Textual (app.py, api_client.py, styles.tcss)
└── pom.xml
```

---

**FinanzDaroca** · Motor patrimonial, terminal profesional y API REST unificados para la gestión diaria del asesor independiente.
