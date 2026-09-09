# docs/FOLDER_STRUCTURE.md — Repository & Module Structure

Reflects the modules defined in `ARCHITECTURE.md` §4. Organized by business module, not by global technical layer, so module boundaries stay real in code, not just on a diagram.

## 1. Repository Tree

```text
Vendora/
├── docs/
│   ├── PRD.md
│   ├── SRS.md
│   ├── USE_CASES.md
│   ├── DOMAIN_MODEL.md
│   ├── ERD.md
│   ├── ARCHITECTURE.md
│   ├── API_CONTRACT.md
│   ├── LEARN_BY_DOING.md
│   └── FOLDER_STRUCTURE.md
├── src/
│   ├── main/
│   │   ├── java/com/omar/vendora/
│   │   │   ├── VendoraApplication.java
│   │   │   ├── common/
│   │   │   ├── config/
│   │   │   ├── security/
│   │   │   ├── identity/
│   │   │   ├── users/
│   │   │   ├── sellers/
│   │   │   ├── catalog/
│   │   │   ├── inventory/
│   │   │   ├── search/
│   │   │   ├── cart/
│   │   │   ├── ordering/
│   │   │   ├── payments/
│   │   │   ├── shipping/
│   │   │   ├── promotions/
│   │   │   ├── reviews/
│   │   │   ├── wishlist/
│   │   │   ├── returns/
│   │   │   ├── finance/
│   │   │   ├── notifications/
│   │   │   └── admin/
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/          (Flyway SQL migrations)
│   └── test/
│       └── java/com/omar/vendora/
│           └── (mirrors main package structure)
├── .gitignore
├── docker-compose.yaml                 (PostgreSQL local dev container)
├── .env.example                        (Environment variables template)
├── pom.xml
└── README.md
```

> Note: `identity` handles authN mechanics (tokens, credentials); `users` handles the User/Address domain and profile. They are separate because "how you prove who you are" and "who you are" are different concerns, even though they're small enough at MVP to feel similar. This split mirrors `ARCHITECTURE.md`'s "Identity & Access" and "User Management" boxes.

## 2. Module Package Layout (representative example: `catalog`)

```text
catalog/
├── controller/
│   ├── ProductController.java          (public + seller-facing read endpoints, split by role if it grows)
│   ├── SellerProductController.java
│   └── CategoryController.java
├── service/
│   ├── ProductService.java
│   ├── CategoryService.java
│   └── ProductOwnershipGuard.java       (ownership check helper)
├── domain/
│   ├── Product.java
│   ├── ProductVariant.java
│   ├── ProductMedia.java
│   ├── Category.java
│   └── ProductStatus.java               (enum)
├── repository/
│   ├── ProductRepository.java
│   ├── ProductVariantRepository.java
│   └── CategoryRepository.java
├── dto/
│   ├── ProductRequest.java
│   ├── ProductResponse.java
│   ├── VariantRequest.java
│   └── CategoryResponse.java
└── mapper/
    └── ProductMapper.java
```

Every module under `src/main/java/com/omar/vendora/` follows this same shape (`controller/`, `service/`, `domain/`, `repository/`, `dto/`, `mapper/`), with sub-packages only added when a module genuinely needs them (e.g., `inventory` needs a `locking/` or `concurrency/`-flavored service split once optimistic/atomic-update logic grows; most modules won't need that at MVP).

Not every module needs every package on day one — e.g., `wishlist` may not need a `mapper/` package if its DTOs are trivial. Add packages when they earn their place, not preemptively.

## 3. Module Responsibilities

| Module | Owns | Responsible For | Does NOT Own | Interacts With |
|---|---|---|---|---|
| `identity` | Credentials, JWT issuance, refresh tokens | Login, registration mechanics, token lifecycle | User profile data, roles business meaning | `users` |
| `users` | User entity, Address, account status | Profile, addresses, suspension state | Seller-specific data | `identity`, `sellers` |
| `sellers` | SellerApplication, SellerProfile | Application review, approval/rejection, seller suspension | Products themselves | `users`, `catalog`, `notifications` |
| `catalog` | Product, ProductVariant, ProductMedia, Category | Product/variant CRUD, category tree, ownership enforcement | Stock quantity semantics beyond the field itself, search ranking | `sellers`, `inventory`, `search` |
| `inventory` | Stock quantity mutation logic | Atomic stock decrement/restock, concurrency-safe updates | Product metadata | `catalog`, `ordering` |
| `search` | Query/ranking logic over catalog read models | Full-text search, filtering, sorting | Product write operations | `catalog` |
| `cart` | Cart, CartItem | Cart mutation for guest/customer sessions | Stock truth (only soft-checks) | `catalog`, `inventory` |
| `ordering` | CustomerOrder, SellerOrder, OrderItem | Checkout orchestration, order splitting, snapshotting | Payment processing, shipping mechanics | `cart`, `inventory`, `payments`, `promotions`, `shipping`, `finance` |
| `payments` | Payment, Refund | Payment intent/confirmation, webhook processing | Order splitting logic | `ordering`, `returns` |
| `shipping` | Shipment | Fulfillment mode, shipment status | Payment/refund logic | `ordering` |
| `promotions` | Coupon, CouponRedemption | Discount calculation, usage-limit enforcement | Order totals beyond discount amount | `ordering` |
| `reviews` | Review | Verified-purchase validation, review CRUD | Order/purchase truth (only reads it) | `ordering`, `catalog` |
| `wishlist` | Wishlist, WishlistItem | Customer-owned saved products | Product data itself | `catalog` |
| `returns` | ReturnRequest, ReturnRequestItem | Return lifecycle, item-level return logic, policy enforcement | Refund execution (delegates to `payments`) | `ordering`, `payments`, `inventory` |
| `finance` | SellerBalance, LedgerEntry, Payout | Commission calc, balance ledger, payout lifecycle | Payment capture itself | `payments`, `ordering`, `sellers` |
| `notifications` | Notification | In-app storage, async email dispatch, outbox delivery | Business logic that triggers events | all modules (consumer of domain events) |
| `admin` | — (thin orchestration layer) | Cross-module admin endpoints (moderation, suspension, policy config) | Domain logic itself (delegates to owning module's service) | all modules |
| `common` | Shared value objects, base exception types, pagination envelope | Cross-cutting utilities with no business logic | Anything module-specific | all modules (one-directional: everyone depends on `common`, `common` depends on nothing) |
| `config` | Spring configuration beans | Security config, web config, async executor config, OpenAPI config | Business logic | all modules |
| `security` | JWT filter, `SecurityContext` helpers, method-security annotations | Enforcing authentication at the web layer | Authorization business rules (ownership checks live in each module's service) | `identity`, all controllers |

## 4. Package Responsibilities (within a module)

- **controller/** — REST endpoints only: request mapping, delegating to service, mapping service results/exceptions to HTTP responses. No business logic.
- **service/** — Application/business logic, transaction boundaries (`@Transactional`), orchestration across repositories and (via interfaces) other modules.
- **domain/** — JPA entities and enums representing the module's aggregate(s); may hold small invariant-checking methods (e.g., `variant.decreaseStock(qty)` guarding against negative stock at the object level, even though the DB-level atomic update is the primary safeguard).
- **repository/** — Spring Data JPA repositories; only data access, no business rules.
- **dto/** — Request/response shapes exposed via the API; never expose JPA entities directly to controllers' external contracts.
- **mapper/** — Entity ↔ DTO conversion (MapStruct or manual), kept out of services to keep services focused on logic.
- **configuration** (`config/`) — `@Configuration` classes: security filter chain, async executor, OpenAPI/Swagger, CORS.
- **exception handling** — A shared `common/exception/` package with a `@ControllerAdvice` global handler mapping domain exceptions (e.g., `InsufficientStockException`, `OwnershipViolationException`) to the standard error envelope from `API_CONTRACT.md` §5.
- **security** (`security/`) — JWT parsing/validation filter, `@PreAuthorize` role expressions, and a small `CurrentUserProvider` used by services for ownership checks.
- **infrastructure** — Cross-cutting technical concerns that aren't a business module: e.g., a future `infrastructure/storage/` package for the object-storage client, or `infrastructure/payment/` for the payment provider client adapter (kept separate from the `payments` module's domain logic so the provider SDK is swappable).

## 5. Dependency Rules

```
Controller
    ↓
Service (this module)
    ↓
Domain (this module)
    ↓
Repository (this module)

Service (this module) → Service interface (other module)   [allowed]
Service (this module) → Repository (other module)          [NOT allowed]
Service (this module) → Domain entity (other module)       [avoid; pass IDs/DTOs across module boundaries where practical]
```

Rules:
1. A module's controller only calls its own module's service.
2. A module's service may call another module's **service interface** (e.g., `ordering` calls `inventory.reserveStock(...)`), never another module's repository directly.
3. Cross-module calls prefer passing IDs and small DTOs rather than sharing JPA entity instances, to keep modules decoupled enough for later extraction (see `ARCHITECTURE.md` §15).
4. `common` has no dependency on any other module; every module may depend on `common`.
5. `admin` depends on other modules' services but no module depends on `admin`.
6. `notifications` is called by other modules (fire domain event / call `notificationService.notify(...)`), but `notifications` never calls back into business modules.

This mirrors the module graph in `ARCHITECTURE.md` §4 exactly — if a dependency isn't in that diagram, it shouldn't exist in code.

## 6. Testing Structure

```text
src/test/java/com/omar/vendora/
├── catalog/
│   ├── ProductServiceTest.java              (unit)
│   └── ProductControllerIT.java             (integration, @SpringBootTest + Testcontainers)
├── inventory/
│   └── StockUpdateConcurrencyTest.java       (concurrency-focused integration test)
├── ordering/
│   ├── CheckoutServiceTest.java
│   └── CheckoutFlowIT.java
├── payments/
│   └── PaymentWebhookIdempotencyTest.java
└── ...
```

Rules:
- Unit tests sit next to the module they test, named `<Class>Test.java`, mocking collaborators (including other modules' service interfaces).
- Integration tests are named `*IT.java`, use a real PostgreSQL instance via Testcontainers, and exercise a full request→DB round trip.
- Concurrency-specific tests (inventory decrement, coupon redemption, payout processing) get their own explicitly named test classes because they test a property (race-safety), not just a single input/output pair.
- No `src/test/java` mirrors go deeper than the module's own package; cross-module test helpers (e.g., a JWT test-token builder) live in `common/testsupport/` (test scope only).

## 7. Resources

```text
src/main/resources/
├── application.yaml           (shared defaults: pagination, JWT TTLs, commission config key)
├── application-dev.yml        (local/dev Postgres connection with fallback defaults)
├── application-prod.yml       (prod datasource via env vars, strict settings)
└── db/migration/
    ├── V1__init.sql
    ├── V2__init_sellers.sql
    ├── V3__init_catalog.sql
    ├── V4__init_inventory_indexes.sql
    ├── V5__init_cart.sql
    ├── V6__init_orders.sql
    ├── V7__init_payments.sql
    └── ...
```
Flyway (or an equivalent SQL migration tool) is introduced from the very first feature (User Registration) rather than relying on Hibernate auto-DDL, because migrations are how the ERD's constraints/indexes actually get created and versioned — this isn't a "later" concern.

No `templates/` (no server-rendered views) and no `static/` directory are created — this is a pure REST backend; adding them would be premature until a genuine need (e.g., serving an email template) arises, at which point a narrow `resources/mail-templates/` directory is the right scope, not a general `static/`.

## 8. Growth Strategy

```
Initial modular structure (this document)
    ↓
More features land inside existing module boundaries
    ↓
A module's internal packages subdivide further only when a single package becomes hard to navigate
    (e.g., inventory/ gains a locking/ sub-package once concurrency logic outgrows one class)
    ↓
Shared infrastructure (object storage client, payment provider adapter) is extracted into
    infrastructure/ only once more than one module needs it
    ↓
If — and only if — a real scaling or organizational boundary problem is observed
    (per ARCHITECTURE.md §15's triggers: search load, notification volume, catalog read load,
    finance isolation needs), the corresponding module is extracted into its own deployable service,
    keeping its existing package internals largely intact since the module boundary was already real in code.
```

No microservice folder layout (per-service `pom.xml`, separate repos, service mesh config) is introduced now. The single-module boundary discipline above is precisely what makes that extraction cheap later, without paying the operational cost today.
