# 🏦 FinanzDaroca Dashboard - Core Bancario & Portfolio Manager

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)
![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED.svg)
![Security](https://img.shields.io/badge/Security-Spring%20Security%206-success.svg)

Sistema integral de gestión patrimonial e inversiones diseñado para asesores financieros independientes. Desarrollado como solución tecnológica enmarcada en el programa **Kit Digital (NextGenerationEU)**, este backend proporciona un motor contable robusto, control normativo y trazabilidad absoluta de operaciones.

## 🚀 Arquitectura y Stack Tecnológico

El proyecto sigue los principios de **Arquitectura Multicapa (Clean Architecture)** , separando estrictamente los controladores REST, la lógica de negocio y el acceso a datos.

- **Core:** Java 21 + Spring Boot 3.
- **Base de Datos:** PostgreSQL contenedorizado mediante Docker [garantizando entornos reproducibles](cite: 86, 87).
- **Persistencia:** Spring Data JPA [Hibernate](cite: 99).
- **Seguridad:** Spring Security 6 [Autenticación Stateful basada en Cookies + Cifrado BCrypt](cite: 126, 127).
- **Integración Externa:** `HttpClient` nativo de Java para sincronización con **Yahoo Finance API**.
- **Testing:** JUnit 5, Mockito y AssertJ.

## ✨ Características Principales (Business Logic)

1. **Motor NAV (Valor Liquidativo Neto) en Tiempo Real:** Cálculo consolidado del patrimonio combinando cuentas de efectivo multi-divisa (EUR, USD, GBP, GBp) y valoración de activos vivos.
2. **Cuenta Remunerada Automática:** El sistema aplica un cálculo de interés compuesto prorrateado por días (2% anual) sobre la liquidez sin invertir.
3. **Cumplimiento Normativo (MiFID II):** Evaluación algorítmica automatizada que dispara alertas si un cliente con perfil `CONSERVADOR` invierte en activos volátiles no exentos.
4. **Sincronización Asíncrona (CRON):** Proceso en segundo plano (`@Scheduled`) que actualiza las cotizaciones de la base de datos consumiendo los feeds de Yahoo Finance para evitar latencias en el cliente.
5. **Seguridad a Nivel de Fila (Row-Level Security):** Estricto cumplimiento del RGPD. Un asesor solo puede auditar y transaccionar sobre las carteras de los clientes que tiene asignados explícitamente [RBAC](cite: 129).

---

## ⚙️ Requisitos Previos e Instalación

- [Docker](https://www.docker.com/) y Docker Compose.
- JDK 21+.
- Maven.

### 1. Despliegue de la Base de Datos

El proyecto cuenta con scripts de inicialización idempotentes (`schema.sql` y `data.sql`). Para levantar la infraestructura limpia:

```bash
docker compose down -v
docker compose up -d

```

Copia `.env` en la raíz (o créalo) con `DB_PASSWORD=secret123`. Spring lo carga vía `spring.config.import=optional:file:.env[.properties]`.

> **Si ves `password authentication failed`:** el volumen `.postgres-data` se creó con otra contraseña. Opción A (sin borrar datos):  
> `docker exec finanzas-db psql -U admin_daroca -d finanz_daroca -c "ALTER USER admin_daroca WITH PASSWORD 'secret123';"`  
> Opción B (BD limpia): `docker compose down -v && docker compose up -d`

### 2. Arranque del Servidor Backend

```bash
./mvnw clean spring-boot:run

```

El servidor se expondrá en `http://localhost:8080`.

---

## 📖 Guía de Uso Rápido (API REST)

Para garantizar la seguridad, la API requiere autenticación mediante sesión (Cookies). A continuación, se muestra el flujo de trabajo básico usando `curl`.

### Paso 1: Iniciar Sesión (Obtener JSESSIONID)

El sistema precarga usuarios de prueba (contraseña común: `secret123`):

| Usuario | Rol |
|---------|-----|
| `admin_jefe` | ADMIN |
| `asesor_aleix` | ASESOR |
| `asesor_maria` | ASESOR |

```bash
curl -i -X POST http://localhost:8080/api/login \
-H "Content-Type: application/json" \
-d '{"username": "asesor_aleix", "password": "secret123"}' \
-c cookies.txt

```

### Paso 2: Realizar un Depósito (Aportación de capital)

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

### Paso 3: Comprar Acciones (Ej. Apple - Activo ID: 2)

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

### Paso 4: Obtener el Informe de Cartera Consolidado (NAV)

```bash
curl -b cookies.txt -X GET "http://localhost:8080/api/clientes/1/cartera?divisa=EUR" | jq

```

---

## 🧪 Ejecución de Tests Unitarios

La lógica core financiera (`ClienteService`) está estrictamente testeada aislando la base de datos e inyectando mocks. Para verificar la integridad matemática y normativa:

```bash
./mvnw test

```

## 🛡️ Auditoría y Conformidad

Este sistema resuelve las vulnerabilidades identificadas en la auditoría inicial de la empresa:

- **Confidencialidad:** Base de datos relacional robusta en lugar de hojas de Excel locales.

- **Inmutabilidad:** La tabla de transacciones carece de métodos `DELETE` o `UPDATE` por diseño, asegurando el rastro contable requerido por los reguladores.
