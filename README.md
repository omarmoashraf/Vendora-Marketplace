# Vendora

> A modular monolith multi-vendor e-commerce backend built with **Java 21**, **Spring Boot**, and **PostgreSQL**.

Vendora is a general-purpose, multi-vendor marketplace platform where independent sellers list and fulfill products, customers enjoy a unified multi-seller cart and checkout experience, and platform administrators manage operations, moderation, and financial balances.

---

## Table of Contents

- [Key Features](#key-features)
- [Architecture & Tech Stack](#architecture--tech-stack)
- [Project Structure](#project-structure)
- [API Overview](#api-overview)
- [Documentation Index](#documentation-index)
- [Getting Started](#getting-started)
- [Core Engineering Guarantees](#core-engineering-guarantees)
- [Contributing & License](#contributing--license)

---

## Key Features

- **Multi-Tenant Catalog:** Independent sellers manage products, hierarchical categories, variants (SKU, attributes, price, stock), and media metadata.
- **Unified Cart & Checkout:** Customers purchase items from multiple sellers in a single transaction; the system automatically decomposes the checkout into discrete per-seller fulfillment orders.
- **Guest & Registered Purchasing:** Supports both authenticated users and guest checkout flows with guest session tracking.
- **Overselling Prevention:** Atomic, race-free conditional stock decrements at checkout.
- **Flexible Payments & Webhooks:** Supports Cash on Delivery (COD) and online payments with idempotent webhook ingestion.
- **Seller Financials & Payouts:** Automated platform commission deduction, immutable ledger-tracked seller balances, and payout request lifecycles.
- **Promotions & Coupons:** Fixed-amount and percentage coupons with atomic redemption counters to prevent concurrent over-allocation.
- **Verified Reviews & Wishlists:** Product reviews strictly gated by verified delivered purchases; customer wishlists.
- **Returns & Refunds:** Centralized marketplace return policy workflow with seller and admin approvals.
- **PostgreSQL Full-Text Search:** Weighted `tsvector`/GIN indexing for fast, relevant product discovery with faceted filtering and sorting.

---

## Architecture & Tech Stack

Vendora is implemented as a **Modular Monolith** designed with clean domain boundaries. Modules interact strictly via public service interfaces, avoiding direct cross-module repository access and enabling future service extraction if necessary.

```
Client (Web / Mobile / Admin)
        │  HTTPS / REST / JSON
        ▼
Spring Boot Application (Modular Monolith)
 ├── Identity & Auth (JWT, Refresh Tokens)
 ├── Users & Sellers (RBAC, Onboarding, Profiles)
 ├── Catalog, Inventory & Search (PostgreSQL FTS)
 ├── Cart, Ordering & Fulfillment (Split Orders)
 ├── Payments, Promotions & Finance (Ledger, Payouts)
 └── Reviews, Returns & Notifications
        │
        ▼
PostgreSQL (Single System of Record with Flyway Migrations)
```

### Technology Matrix

| Layer | Technology |
|---|---|
| **Runtime & Language** | Java 21 |
| **Framework** | Spring Boot 4.x (WebMVC, Validation, Data JPA) |
| **Database & Migrations**| PostgreSQL 15+, Flyway |
| **Security** | Spring Security, stateless JWT (short-lived access + rotated refresh tokens), BCrypt |
| **Persistence & ORM** | Hibernate / Spring Data JPA with optimistic locking (`@Version`) |
| **Build Tool** | Apache Maven (`mvnw` wrapper provided) |

---

## Project Structure

The project follows a business-first module layout rather than global technical layering:

```text
Vendora/
├── docs/                        # Comprehensive design specifications
│   ├── API_CONTRACT.md          # Complete REST API specifications & schemas
│   ├── ARCHITECTURE.md          # Architecture, C4 diagrams, concurrency & security
│   ├── DOMAIN_MODEL.md          # Domain entities, value objects, and aggregates
│   ├── ERD.md                   # Database schema, foreign keys, and indexes
│   ├── FOLDER_STRUCTURE.md      # Codebase package layout & boundary rules
│   ├── LEARN_BY_DOING.md        # Step-by-step engineering tutorial & exercises
│   ├── PRD.md                   # Product requirements document
│   ├── SRS.md                   # Software requirements specification (FRs/NFRs)
│   └── USE_CASES.md             # Detailed actor flows & edge cases
├── src/
│   ├── main/
│   │   ├── java/com/omar/vendora/
│   │   │   ├── common/          # Cross-cutting utilities, base exceptions, pagination
│   │   │   ├── config/          # Spring beans, security filter chain, OpenAPI
│   │   │   ├── identity/        # Auth mechanics, JWT handling, credentials
│   │   │   ├── users/           # User profiles, addresses, account status
│   │   │   ├── sellers/         # Seller applications, profiles, suspension
│   │   │   ├── catalog/         # Products, variants, categories, media
│   │   │   ├── inventory/       # Stock tracking and concurrency-safe decrements
│   │   │   ├── search/          # Full-text search and filtering queries
│   │   │   ├── cart/            # Customer & guest cart sessions
│   │   │   ├── ordering/        # Checkout orchestration, order splitting, snapshots
│   │   │   ├── payments/        # Payment intents, webhook verification, refunds
│   │   │   ├── shipping/        # Fulfillment modes and tracking
│   │   │   ├── promotions/      # Coupon codes and usage counters
│   │   │   ├── reviews/         # Verified-purchase reviews
│   │   │   ├── wishlist/        # Customer saved items
│   │   │   ├── returns/         # Return requests and policy workflows
│   │   │   ├── finance/         # Seller balance ledger, commission, payouts
│   │   │   ├── notifications/   # In-app notifications and async email dispatch
│   │   │   └── admin/           # Administrative orchestration endpoints
│   │   └── resources/
│   │       ├── application.yaml # Core application configuration
│   │       └── db/migration/    # Flyway versioned SQL scripts
│   └── test/                    # Unit, integration, and concurrency tests
├── pom.xml                      # Maven build descriptor
└── README.md                    # Project overview and quickstart
```

---

## API Overview

The complete API specification is defined in [docs/API_CONTRACT.md](docs/API_CONTRACT.md).

### Conventions
- **Base URL:** `/v1/...`
- **Format:** JSON (`Content-Type: application/json`)
- **Authentication:** `Authorization: Bearer <access_token>`
- **Identifiers:** UUID strings
- **Monetary Values:** Fixed-point string decimals (e.g., `"24.99"`) to eliminate wire precision issues
- **Timestamps:** ISO-8601 UTC strings (`YYYY-MM-DDTHH:mm:ssZ`)
- **Pagination Envelope:** `{ "content": [...], "page": 0, "size": 20, "totalElements": N, "totalPages": M }`

### Endpoint Summary

| Domain | Method & Base Path | Description | Access |
|---|---|---|---|
| **Auth** | `POST /auth/register`, `/login`, `/refresh`, `/logout` | Registration, token issuance and revocation | Public / Bearer |
| **Users & Addresses** | `GET/PATCH /users/me`, `/users/me/addresses` | Profile and multi-address management | Authenticated |
| **Seller Applications** | `POST /seller-applications`, `GET /seller-applications/me` | Customer seller onboarding application | Customer |
| **Catalog & Products** | `GET /products`, `GET /products/{id}`, `GET /categories` | Public browsing, search, and hierarchy | Public |
| **Seller Catalog** | `POST/PATCH/DELETE /seller/products`, `/seller/variants` | Manage owned products, variants, and media | Seller (Owner) |
| **Cart** | `GET /cart`, `POST/PATCH/DELETE /cart/items` | Multi-seller cart management | Guest / Customer |
| **Wishlist** | `GET/POST/DELETE /wishlist` | Saved product list | Customer |
| **Orders & Checkout** | `POST /orders/checkout`, `GET /orders`, `GET /orders/{id}` | Atomic checkout, order split, cancel order | Guest / Customer |
| **Seller Orders** | `GET /seller/orders`, `POST /seller/orders/{id}/ship` | Fulfill and track assigned order portions | Seller (Owner) |
| **Payments** | `POST /orders/{id}/payments/intent`, `POST /webhooks/payments` | Payment intent creation, provider webhook | Customer / Webhook |
| **Returns & Refunds** | `POST /seller-orders/{id}/returns`, `GET /seller/returns` | Initiate and review return requests | Customer / Seller |
| **Promotions** | `GET /coupons/{code}/validate`, `POST /admin/coupons` | Validate code, admin coupon CRUD | Public / Admin |
| **Reviews** | `POST /products/{id}/reviews`, `GET /products/{id}/reviews` | Submit verified review, list reviews | Verified Customer |
| **Seller Balance** | `GET /seller/balance`, `POST /seller/payouts` | View balance ledger, request payouts | Seller |
| **Admin Operations** | `/admin/*` | Approve sellers, moderate listings, payouts | Admin |

For request/response schemas, error structures, and query parameters, see [docs/API_CONTRACT.md](docs/API_CONTRACT.md).

---

## Documentation Index

Detailed specifications and architectural guides are located in the `docs/` folder:

| Document | Focus |
|---|---|
| [API Contract](docs/API_CONTRACT.md) | Authoritative REST endpoint specifications, headers, DTOs, and HTTP status codes. |
| [Architecture](docs/ARCHITECTURE.md) | C4 diagrams, request lifecycle, modular boundaries, security, and scalability roadmap. |
| [Software Requirements (SRS)](docs/SRS.md) | Complete functional (`FR-*`) and non-functional (`NFR-*`) requirements. |
| [Product Requirements (PRD)](docs/PRD.md) | Product vision, business model, user personas, MVP and post-MVP scope. |
| [Domain Model](docs/DOMAIN_MODEL.md) | Domain aggregates, entities, value objects, and business invariants. |
| [Entity-Relationship Diagram (ERD)](docs/ERD.md) | Database tables, column types, foreign key constraints, and performance indexes. |
| [Folder Structure](docs/FOLDER_STRUCTURE.md) | Detailed package structure and inter-module dependency guidelines. |
| [Use Cases](docs/USE_CASES.md) | Comprehensive step-by-step user and system interaction flows. |
| [Learn by Doing](docs/LEARN_BY_DOING.md) | Hands-on engineering roadmap and implementation challenge guide. |

---

## Getting Started

### Prerequisites

- **Java:** JDK 21+
- **Database:** PostgreSQL 15+
- **Build Tool:** Maven 3.9+ (or use the bundled `./mvnw`)

### Local Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/omar-ashraf/Vendora.git
   cd Vendora
   ```

2. **Configure PostgreSQL:**
   Ensure PostgreSQL is running and create the target database:
   ```sql
   CREATE DATABASE vendora;
   ```

3. **Configure Environment:**
   Adjust `src/main/resources/application.yaml` or set environment variables:
   ```bash
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/vendora
   export SPRING_DATASOURCE_USERNAME=postgres
   export SPRING_DATASOURCE_PASSWORD=postgres
   ```

4. **Build and Run:**
   Execute the Spring Boot Maven wrapper:
   ```bash
   ./mvnw clean spring-boot:run
   ```
   Flyway will automatically execute all database migrations on startup.

5. **Run Tests:**
   ```bash
   ./mvnw test
   ```

---

## Core Engineering Guarantees

- **Concurrency-Safe Stock Updates:** Prevents overselling using atomic conditional SQL updates (`WHERE stock_quantity >= :qty`) within transactional boundaries.
- **Order Item Snapshotting:** Order items retain purchase-time product name, variant attributes, and unit price, preserving historical financial integrity even if products are edited or deleted later.
- **Idempotent Operations:** Mutating financial endpoints accept `Idempotency-Key` headers, and webhook endpoints de-duplicate events using unique provider transaction identifiers.
- **Audit-Safe Balance Ledger:** Seller balances are backed by discrete ledger entries (credit, debit, commission deduction) to ensure transparent financial reconciliation.
