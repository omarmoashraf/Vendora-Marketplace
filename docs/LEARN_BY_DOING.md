# docs/LEARN_BY_DOING.md — Learn-by-Doing Development & Curriculum Guide

> The project is the curriculum. You do not study Java/Spring/PostgreSQL end-to-end first — you study exactly what the next feature demands, build it, break it, debug it, and move on.

This document is derived from `PRD.md`, `SRS.md`, `USE_CASES.md`, `DOMAIN_MODEL.md`, `ERD.md`, `ARCHITECTURE.md`, and `API_CONTRACT.md`. It does not introduce any feature, module, or technology that contradicts those documents. Module names below match `FOLDER_STRUCTURE.md` exactly.

---

## How To Use This Roadmap

Do **not**:
- Finish every concept perfectly before coding.
- Watch a full course on Spring Boot / JPA / Security before starting Feature 1.
- Memorize the JPA or Spring Security API surface.
- Study Redis, Kafka, Elasticsearch, or Kubernetes before the project actually needs them.
- Copy an implementation from a tutorial and paste it in.

Do:
```text
Learn enough
    → Build
    → Get stuck
    → Investigate
    → Fix
    → Understand
    → Continue
```

Use AI (Claude or otherwise) as a **mentor/debugger**, not as the implementer. When stuck, follow this progression before asking for a fix:
1. Read the error message carefully — what is it actually telling you?
2. Form a hypothesis about the cause.
3. Check official documentation (Spring, Hibernate, PostgreSQL docs) for the specific mechanism involved.
4. Try a fix based on your hypothesis.
5. If still stuck, ask AI for a **hint or explanation** — not the finished code.
6. Implement the fix yourself.
7. Write down, in your own words, why it works.

## The Core Learning Loop

Applied to every feature below:
```text
1. Choose the feature (in order below).
2. Read its Requirements references in the existing docs.
3. Understand the expected behavior (read Use Cases / API contract for that resource).
4. Try to design the solution yourself (endpoints, entities, service methods) before reading further.
5. Identify what you don't know from that design attempt.
6. Study ONLY the concepts in "Study Before Implementation" for this feature.
7. Implement the feature yourself.
8. Test it (see "Tests" checklist).
9. Try to break it (see "Things I Should Try to Break").
10. Debug and fix what broke.
11. Refactor for clarity once it works.
12. Answer the "Concepts Learned" prompts in your own notes.
13. Commit the feature (one feature ≈ one focused commit or small PR).
14. Move to the next feature.
```

## Development Order (Dependency-Aware)

```text
Stage 0: Project Setup
    ↓
Stage 1: Identity & Access (Registration → Login/JWT → Refresh/Logout → RBAC foundation)
    ↓
Stage 2: User Profile & Addresses
    ↓
Stage 3: Seller Onboarding (Application → Admin Review)
    ↓
Stage 4: Catalog (Categories → Products/Variants → Media)
    ↓
Stage 5: Inventory (concurrency-safe stock)
    ↓
Stage 6: Search (PostgreSQL FTS + filter/sort/pagination)
    ↓
Stage 7: Cart (multi-seller)
    ↓
Stage 8: Checkout & Order Splitting (+ Guest Checkout)
    ↓
Stage 9: Payments (COD → Online Intent/Webhook → Refunds)
    ↓
Stage 10: Shipping (Shipment tracking)
    ↓
Stage 11: Promotions (Coupons)
    ↓
Stage 12: Reviews (verified purchase)
    ↓
Stage 13: Wishlist
    ↓
Stage 14: Returns
    ↓
Stage 15: Finance (Commission → Seller Balance → Payouts)
    ↓
Stage 16: Notifications (in-app + async email)
    ↓
Stage 17: Admin Operations (moderation/suspension/policy config)
    ↓
Stage 18: Observability
    ↓
Stage 19: Deployment (Docker + CI/CD)
    ↓
Stage 20: Evolution Triggers (Redis, dedicated search, message broker — only if justified)
```

Each stage is chosen because later stages structurally depend on it: Checkout cannot exist without Cart, Inventory, and a placed-order concept; Payouts cannot exist without Payments and Finance/Commission; Returns depend on Orders and Payments existing first.

## Technology Introduction Rule

No technology below is introduced "because it's popular." Each is introduced at the stage where the project creates the specific problem it solves — matching `ARCHITECTURE.md` §9/§11/§15.

| Technology | Introduced at | Because |
|---|---|---|
| PostgreSQL + JPA/Hibernate | Stage 0 | System of record needed from the first entity. |
| Flyway migrations | Stage 0 | Constraints/indexes from `ERD.md` need versioned, reviewable schema changes. |
| Spring Security + JWT | Stage 1 | Authentication is the first real requirement (registration/login). |
| Bean Validation | Stage 1 | Input validation needed the moment any request body exists. |
| Optimistic locking (`@Version`) | Stage 4–5 (Catalog/Inventory) | Concurrent price/stock edits become possible once multiple actors can write the same row. |
| Atomic conditional SQL updates | Stage 5 (Inventory), Stage 11 (Coupons) | Oversell/over-redemption is only possible once concurrent writers exist. |
| PostgreSQL Full-Text Search (`tsvector`/GIN) | Stage 6 | Product search becomes a real requirement once a non-trivial catalog exists. |
| Payment provider SDK/abstraction | Stage 9 | Only needed once checkout/payment exists. |
| Idempotency keys | Stage 8–9 | Only meaningful once checkout/payment requests can be retried or duplicated. |
| Spring `@Async` / lightweight job mechanism | Stage 16 | Only needed once a slow side-effect (email) must not block a request. |
| Docker / CI/CD | Stage 19 | Deployment concerns are deliberately deferred until there's something worth deploying reliably. |
| Redis | **Not yet** | Introduce only if read-heavy catalog/category traffic or rate-limiting at multi-instance scale is actually observed (`ARCHITECTURE.md` §15). |
| Message broker (Kafka/RabbitMQ) | **Not yet** | Introduce only if in-process async notification/webhook volume becomes insufficient. |
| Elasticsearch/OpenSearch | **Not yet** | Introduce only if PostgreSQL FTS becomes a measured bottleneck. |
| Microservices / Kubernetes | **Not yet** | Introduce only if a concrete scaling or team-boundary problem is observed; the modular monolith is the default for the life of this roadmap. |

---

# Stage 0 — Project Setup

### Feature: PROJECT-SETUP — Repository & Application Bootstrap
**Goal**: Get a running Spring Boot application connected to PostgreSQL with a versioned schema and a place to put every future module, matching `FOLDER_STRUCTURE.md`.

**Requirements**: `ARCHITECTURE.md` §1, §6; `FOLDER_STRUCTURE.md` §1, §7.

**Prerequisites**: None — this is the first feature.

### Learning Gate: Project Setup
Before starting, understand:
- What a build tool (Maven) does and what `pom.xml` declares.
- What Spring Boot auto-configuration means at a high level.
- What a database migration tool (Flyway) is for.

**What I Need to Know Before Coding**
- Maven basics (dependencies, plugins) — MUST UNDERSTAND
- Spring Boot project structure & `@SpringBootApplication` — MUST UNDERSTAND
- Spring profiles (`application-local.yml` vs `application-prod.yml`) — SHOULD UNDERSTAND
- Flyway migration file naming/versioning — MUST UNDERSTAND
- Docker Compose for local PostgreSQL — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- Maven project structure
- Spring Boot starter dependencies (web, data-jpa, validation, security)
- Flyway naming convention (V1__description.sql)
- application.yml basics (datasource config)

Don't study yet:
- Spring Security internals
- JPA entity relationships
- Docker multi-stage builds (full Docker is Stage 19; a simple compose file for local Postgres is enough now)
```

**Implementation Task**: Initialize the Maven project with the module package skeleton from `FOLDER_STRUCTURE.md` §1–2 (empty packages are fine). Add a `docker-compose.yml` with only a PostgreSQL service for local development. Wire `application-local.yml` to that database. Add an empty Flyway migration `V1__init.sql` (even just a comment) to prove the pipeline runs. Confirm the app starts and connects.

**Engineering Problems to Encounter**: misconfigured datasource URL; Flyway failing on an empty/malformed migration; profile not being picked up.

**Things I Should Try to Break**
- Start the app with PostgreSQL not running — observe the failure.
- Introduce a bad Flyway checksum by editing an already-applied migration.

**Tests**: Application context loads (`@SpringBootTest` smoke test). No functional tests yet.

**Definition of Done**: `mvn spring-boot:run` starts the app against local PostgreSQL with Flyway reporting one applied migration and no errors.

**Concepts Learned** — write down: What does Spring Boot auto-configure for you? Why version schema changes instead of letting Hibernate auto-generate DDL?

---

# Stage 1 — Identity & Access
*(module: `identity`, `security`, foundational parts of `users`)*

### Learning Gate: Authentication & Authorization
Before starting Stage 1, understand at a "good enough" level:
- HTTP request/response basics (methods, status codes, headers).
- What authentication vs. authorization means conceptually.
- What a password hash is and why plaintext storage is unacceptable.
- What a JWT is structurally (header.payload.signature) — not full mastery of every claim type.

Do not require mastery — build the registration endpoint, get stuck on validation or hashing, then go deeper.

### Feature: AUTH-01 — User Registration
**Goal**: Let a new user create an account with hashed credentials. This is the entry point for every other actor (Customer/Seller/Admin all start as a registered User).

**Requirements**: `SRS.md` FR-AUTH-001; `API_CONTRACT.md` §12 Auth (`POST /auth/register`); `ERD.md` `USERS` table.

**Prerequisites**: PROJECT-SETUP complete.

**What I Need to Know Before Coding**
- REST controller basics (`@RestController`, `@PostMapping`) — MUST UNDERSTAND
- DTOs vs entities (never expose the entity directly) — MUST UNDERSTAND
- Bean Validation (`@Valid`, `@NotBlank`, `@Email`) — MUST UNDERSTAND
- Password hashing (bcrypt/argon2 via `PasswordEncoder`) — MUST UNDERSTAND
- PostgreSQL `UNIQUE` constraint on `email` — MUST UNDERSTAND
- Global exception handling (`@ControllerAdvice`) — SHOULD UNDERSTAND
- Dependency Injection basics (constructor injection) — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- HTTP request/response lifecycle
- Spring MVC controllers, request/response DTOs
- Bean Validation annotations
- PasswordEncoder / bcrypt
- PostgreSQL UNIQUE constraints and how a duplicate-key violation surfaces in Spring Data JPA

Don't study yet:
- JWT
- Redis
- OAuth2 / third-party login
- RBAC (that's the next feature)
```

**Learning Depth**: Bean Validation — MUST; PasswordEncoder — MUST; exception mapping — SHOULD; DI container internals (bean scopes, lifecycle callbacks) — NICE TO KNOW / LATER.

**Implementation Task**: Build `POST /auth/register` per `API_CONTRACT.md`. Validate input, hash the password, persist a `User` with a default `Customer` capability flag, enforce email uniqueness at the DB level, and return a clean response (never the password hash).

**Engineering Problems to Encounter**: duplicate email registration; weak/empty password slipping past client-side checks; leaking the password hash in a response by accident; mapping a DB constraint violation to a clean `400`/`409` instead of a raw stack trace.

**Things I Should Try to Break**
- Register the same email twice.
- Send a request with no password field at all.
- Send an extremely long email string.
- Inspect the JSON response and verify the hash never appears.

**Tests**
- Happy path: valid registration succeeds, `201`.
- Validation failure: missing/invalid email, missing password → `400` with field-level error detail.
- Duplicate email → `409` (or `400`, per your chosen convention — document your choice).
- Integration test hitting a real PostgreSQL (Testcontainers) verifying the row and that the stored value is a hash, not plaintext.

**Definition of Done**: Registration works end-to-end against a real DB; duplicate emails are rejected cleanly; password is never stored or returned in plaintext.

**Concepts Learned** — What problem does hashing solve that even encryption wouldn't? Why validate both at the DTO layer and the DB layer instead of relying on one? What would change if this needed to support 10,000 registrations/second?

---

### Feature: AUTH-02 — Login & JWT Issuance
**Goal**: Let a registered user authenticate and receive an access token usable on subsequent requests.

**Requirements**: `SRS.md` FR-AUTH-002; `API_CONTRACT.md` §12 Auth (`POST /auth/login`).

**Prerequisites**: AUTH-01 complete.

**What I Need to Know Before Coding**
- JWT structure and signing (HMAC secret or RSA key pair) — MUST UNDERSTAND
- Stateless authentication vs. session-based — MUST UNDERSTAND
- Spring Security filter chain basics (just enough to add a custom filter later) — SHOULD UNDERSTAND
- Token expiry (`exp` claim) — MUST UNDERSTAND
- Generic error responses to avoid leaking whether an email exists — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- JWT structure (header/payload/signature) and claims
- Signing a JWT with a secret (jjwt or Spring Security's JWT support)
- Password verification with PasswordEncoder.matches()
- Why login errors should be generic ("invalid credentials"), not "user not found" vs "wrong password"

Don't study yet:
- Refresh token rotation (next feature)
- RBAC / method security (feature after that)
- Rate limiting internals (note the requirement, implement a simple version later)
```

**Learning Depth**: JWT signing/verification — MUST; generic-error security reasoning — MUST; full Spring Security filter chain internals — SHOULD (enough to plug in a filter); OAuth2/SSO — NICE TO KNOW / LATER.

**Implementation Task**: Build `POST /auth/login` verifying credentials and issuing a signed JWT access token (short TTL) per `API_CONTRACT.md`'s response shape. Do not implement refresh tokens yet — that's the next feature. Add basic rate limiting on this endpoint (in-process counter is fine at this stage).

**Engineering Problems to Encounter**: leaking which part of the credential pair was wrong; token TTL too long/short; clock skew between issue and verify; forgetting to rate-limit and enabling brute force.

**Things I Should Try to Break**
- Login with a correct email but wrong password, and vice versa — response must look identical.
- Attempt many rapid logins to trigger the rate limiter.
- Tamper with a valid JWT's payload and confirm signature verification rejects it.

**Tests**
- Happy path: valid login returns a well-formed JWT.
- Wrong password / non-existent email → same generic `401`.
- Rate limit triggers after N attempts.
- Tampered/invalid-signature token is rejected wherever it's later used.

**Definition of Done**: A valid login returns a usable JWT; invalid attempts are indistinguishable and rate-limited.

**Concepts Learned** — Why is a stateless token appropriate here instead of server-side sessions? What does the signature actually protect against (and not protect against, e.g., it doesn't hide the payload)?

---

### Feature: AUTH-03 — Refresh Tokens & Logout
**Goal**: Let a user stay logged in beyond the short access-token TTL, and let them explicitly revoke a session.

**Requirements**: `SRS.md` FR-AUTH-003; `API_CONTRACT.md` §12 (`POST /auth/refresh`, `POST /auth/logout`); `ERD.md` `REFRESH_TOKENS`.

**Prerequisites**: AUTH-02 complete.

**What I Need to Know Before Coding**
- Refresh token storage (hashed, not raw, in DB) — MUST UNDERSTAND
- Token rotation on refresh (old token invalidated, new one issued) — MUST UNDERSTAND
- Revocation (`revoked` flag / deletion) — MUST UNDERSTAND
- Why refresh tokens are opaque/DB-checked rather than pure stateless JWTs — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- Refresh token rotation pattern
- Hashing tokens before storing them (same idea as password hashing, different purpose: leak protection)
- Revocation lookups

Don't study yet:
- Multi-device session management UI concerns (not required by SRS at MVP)
```

**Learning Depth**: Rotation-on-refresh — MUST; storing hashed tokens — MUST; theoretical alternatives (stateless refresh via longer JWT) — NICE TO KNOW / LATER.

**Implementation Task**: Implement `/auth/refresh` (validate + rotate) and `/auth/logout` (revoke). Persist refresh tokens hashed, matching `ERD.md`.

**Engineering Problems to Encounter**: reusing a rotated (already-exchanged) refresh token; race between two near-simultaneous refresh calls with the same token; forgetting to revoke on logout, leaving a usable token behind.

**Things I Should Try to Break**
- Call `/auth/refresh` twice with the same token in quick succession.
- Call `/auth/refresh` after logout with the same token.
- Use an expired refresh token.

**Tests**: Happy path rotation; reuse-of-rotated-token rejected; logout revokes the token; expired token rejected.

**Definition of Done**: A user can refresh indefinitely until logout or expiry; a rotated/revoked token is unusable afterward.

**Concepts Learned** — Why rotate refresh tokens instead of reusing the same one indefinitely? What attack does rotation-with-reuse-detection mitigate?

---

### Feature: AUTHZ-01 — RBAC & Resource Ownership Foundation
**Goal**: Establish role-based access (Customer/Seller/Admin) and the ownership-check pattern every later module reuses.

**Requirements**: `SRS.md` FR-AUTHZ-001..004; `ARCHITECTURE.md` §7.

**Prerequisites**: AUTH-02/03 complete.

**What I Need to Know Before Coding**
- Encoding roles/capabilities as JWT claims — MUST UNDERSTAND
- Method security (`@PreAuthorize`) vs. manual checks — SHOULD UNDERSTAND
- Why ownership checks belong in the service layer, not just route-level role checks — MUST UNDERSTAND
- A User simultaneously holding Customer + Seller capability — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Spring Security @PreAuthorize / method security
- Extracting the current authenticated principal in a service (CurrentUserProvider pattern)
- Designing an ownership-guard helper (e.g., "does this product belong to this seller?")

Don't study yet:
- Fine-grained ACL frameworks (Spring Security ACL) — not needed for this project's ownership model
```

**Learning Depth**: Role-based route protection — MUST; service-layer ownership checks — MUST; ACL frameworks — NICE TO KNOW / LATER (probably never needed here).

**Implementation Task**: Add role claims to the JWT, protect Admin-only and Seller-only endpoints at the controller level, and build a small reusable ownership-check pattern (e.g., an `OwnershipGuard`-style helper) that later modules (Catalog, Ordering, Finance) will reuse. Prove it with a placeholder protected endpoint if no real Seller/Admin endpoint exists yet.

**Engineering Problems to Encounter**: relying only on `@PreAuthorize("hasRole('SELLER')")` and forgetting the ownership check, letting one Seller edit another's data; stale role claims after a role change (until token refresh).

**Things I Should Try to Break**
- Call a Seller-only endpoint as a Customer.
- Call an endpoint for "my own resource" while authenticated as a different Seller with a different resource ID.
- Call an Admin-only endpoint with a valid but non-Admin token.

**Tests**: Role-protected endpoint rejects wrong role (`403`); ownership guard rejects cross-owner access (`403`) even with the correct role.

**Definition of Done**: Role checks and an ownership-check pattern exist and are demonstrably enforced by tests, ready to be reused by every future module.

**Concepts Learned** — Why is "correct role" not sufficient authorization by itself? What's the difference between authentication failure (`401`) and authorization failure (`403`)?

---

# Stage 2 — User Profile & Addresses
*(module: `users`)*

### Feature: USER-01 — Profile & Address Management
**Goal**: Let a Customer manage their profile and multiple shipping addresses, needed later for checkout.

**Requirements**: `SRS.md` FR-USER-001; `API_CONTRACT.md` §12 Users/Addresses; `ERD.md` `ADDRESSES`.

**Prerequisites**: AUTHZ-01 complete.

**What I Need to Know Before Coding**
- One-to-many JPA relationship (`User` → `Address`) — MUST UNDERSTAND
- Partial update patterns (`PATCH`) — SHOULD UNDERSTAND
- Ownership check reuse from AUTHZ-01 — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- @OneToMany / @ManyToOne mapping basics
- PATCH semantics vs PUT

Don't study yet:
- Optimistic locking (not yet needed for low-contention profile data)
```

**Learning Depth**: One-to-many mapping — MUST; PATCH semantics — SHOULD.

**Implementation Task**: `GET/PATCH /users/me`, and full CRUD on `/users/me/addresses`, enforcing that a user only ever sees/modifies their own addresses.

**Engineering Problems to Encounter**: a user passing another user's `addressId`; inconsistent `is_default` flag when multiple addresses are marked default.

**Things I Should Try to Break**
- Try to update/delete another user's address by guessing its ID.
- Mark two addresses as default and see what your logic does.

**Tests**: Happy path CRUD; ownership violation rejected; default-address invariant holds.

**Definition of Done**: A Customer can fully manage their profile and addresses, scoped strictly to themselves.

**Concepts Learned** — How does this reuse the ownership-check pattern from AUTHZ-01 instead of reinventing it?

---

# Stage 3 — Seller Onboarding
*(module: `sellers`)*

### Learning Gate: Seller Onboarding
Before starting, understand: simple state machines (Pending/Approved/Rejected) and why a state transition should be a single guarded operation rather than an unchecked field write.

### Feature: SELLER-01 — Seller Application Submission
**Goal**: Let a Customer apply to become a Seller.

**Requirements**: `SRS.md` FR-SELLER-001; `USE_CASES.md` UC-S-01; `ERD.md` `SELLER_APPLICATIONS`.

**Prerequisites**: USER-01 complete.

**What I Need to Know Before Coding**
- Simple state machine modeling as an enum + guarded transition method — MUST UNDERSTAND
- Preventing duplicate pending applications from the same user — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Enum-backed status fields
- Guard clauses for state transitions (e.g., reject "approve" on an already-decided application)

Don't study yet:
- Full domain-event infrastructure (a direct service call to Notifications is enough here)
```

**Learning Depth**: Guarded state transitions — MUST.

**Implementation Task**: `POST /seller-applications`, `GET /seller-applications/me`, defaulting to `PENDING`, rejecting a second pending application from the same user.

**Engineering Problems to Encounter**: double-submission race (two near-simultaneous applications from the same user).

**Things I Should Try to Break**: submit two applications back-to-back; check the second is rejected or reuses the pending one per your chosen rule (document the choice).

**Tests**: happy path; duplicate-pending rejected.

**Definition of Done**: A Customer can submit exactly one active application at a time and check its status.

**Concepts Learned** — Why guard a state transition in the service layer instead of trusting the client to only send valid transitions?

---

### Feature: SELLER-02 — Admin Review (Approve/Reject) & Seller Suspension
**Goal**: Let an Admin approve/reject applications and later suspend/reactivate a Seller independently of the underlying User.

**Requirements**: `SRS.md` FR-SELLER-002/003/004; `USE_CASES.md` UC-A-01; `ERD.md` `SELLER_PROFILES`.

**Prerequisites**: SELLER-01, AUTHZ-01 complete.

**What I Need to Know Before Coding**
- Creating a new aggregate (`SellerProfile`) as a side effect of approving another (`SellerApplication`) — MUST UNDERSTAND
- Modeling two independent status flags (User.status vs SellerProfile.status) — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Transactional side effects across two entities in one service method
- Designing independent status enums that don't collapse into one field

Don't study yet:
- Event-driven decoupling (a direct synchronous call between sellers and notifications is fine at this scale)
```

**Learning Depth**: Multi-entity transactional side effects — MUST.

**Implementation Task**: `POST /admin/seller-applications/{id}/approve` (creates `SellerProfile`, capability becomes usable), `.../reject`; `POST /admin/sellers/{id}/suspend|reactivate`. Notify the applicant (stub is fine until Stage 16).

**Engineering Problems to Encounter**: approving an already-decided application; suspending a Seller and forgetting historical Orders/Payouts must remain untouched (this becomes fully testable once those exist — write a TODO test placeholder now, complete it in Stage 15/17).

**Things I Should Try to Break**: approve the same application twice; suspend a seller and confirm existing product listing endpoints reflect the restriction while historical data queries (once they exist) are unaffected.

**Tests**: approve/reject happy paths; double-decision rejected; suspend blocks new seller actions.

**Definition of Done**: Approval grants seller capability exactly once; suspension is independent of user account status and doesn't touch historical data.

**Concepts Learned** — Why keep `User.status` and `SellerProfile.status` as two separate fields instead of one combined "account state" enum?

---

# Stage 4 — Catalog
*(module: `catalog`)*

### Feature: CATALOG-01 — Category Management (Hierarchical)
**Goal**: Support a category tree that products attach to, queryable including descendants.

**Requirements**: `SRS.md` FR-CAT-001..003; `ERD.md` `CATEGORIES` self-referencing FK.

**Prerequisites**: AUTHZ-01 (Admin role) complete.

**What I Need to Know Before Coding**
- Self-referencing FK / tree modeling in a relational DB — MUST UNDERSTAND
- Recursive CTE (`WITH RECURSIVE`) for descendant queries — MUST UNDERSTAND
- Basic index on `parent_id` — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- Adjacency list model for trees in SQL
- Recursive CTEs in PostgreSQL

Don't study yet:
- Materialized path / nested set alternatives (adjacency list + recursive CTE is sufficient at this scale, per ERD.md's stated design choice)
```

**Learning Depth**: Recursive CTE — MUST; alternative tree models — NICE TO KNOW / LATER.

**Implementation Task**: Admin CRUD on categories; `GET /categories` (tree); `GET /categories/{id}/products` including descendant categories via a recursive query.

**Engineering Problems to Encounter**: circular parent references; deep trees causing slow naive recursive queries without an index.

**Things I Should Try to Break**: try to set a category's parent to one of its own descendants; query a category with many descendants and check performance.

**Tests**: tree CRUD happy path; circular-parent rejected; descendant query returns correct set.

**Definition of Done**: Category tree is manageable by Admin and descendant-inclusive product queries work correctly.

**Concepts Learned** — Why is a recursive CTE preferable here to fetching the whole tree into the application and walking it in Java?

---

### Feature: CATALOG-02 — Product & Variant Management (Seller-Owned)
**Goal**: Let a Seller create/update their own Products and Variants, immediately published, with Admin moderation authority.

**Requirements**: `SRS.md` FR-PRODUCT-001..004; `USE_CASES.md` UC-S-02; `ERD.md` `PRODUCTS`, `PRODUCT_VARIANTS`, `PRODUCT_CATEGORIES`.

**Prerequisites**: CATALOG-01, SELLER-02, AUTHZ-01 complete.

**What I Need to Know Before Coding**
- One-to-many (`Product` → `Variant`) and many-to-many (`Product` ↔ `Category`) mapping — MUST UNDERSTAND
- Ownership-guard reuse (a Seller can't touch another Seller's product) — MUST UNDERSTAND
- Optimistic locking (`@Version`) on `Product`/`ProductVariant` — MUST UNDERSTAND
- SKU uniqueness constraint — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- @ManyToMany join table mapping
- @Version / optimistic locking basics and OptimisticLockException handling

Don't study yet:
- Inventory's atomic decrement strategy (that's the Inventory feature specifically, next stage — variant creation here just sets an initial stock value)
```

**Learning Depth**: Many-to-many mapping — MUST; optimistic locking — MUST (introduce it here, even though its real test comes with concurrent price edits); premature product-attribute "engine" — explicitly avoided per PRD.md §15/PRD non-goals.

**Implementation Task**: Seller endpoints to create/update/delete own Product (with categories) and add/update/delete Variants (sku, attributes, price, initial stock). Admin moderation endpoints (`hide`/`remove`) usable regardless of owner.

**Engineering Problems to Encounter**: a Seller attempting to edit another Seller's product; two concurrent edits to the same product's price (optimistic lock conflict); duplicate SKU.

**Things I Should Try to Break**: attempt cross-seller edits; simulate two near-simultaneous price updates and observe the `OptimisticLockException`; create two variants with the same SKU.

**Tests**: CRUD happy path; ownership violation rejected (`403`); duplicate SKU rejected; concurrent-edit conflict surfaces as a clean `409`, not a raw exception.

**Definition of Done**: Sellers fully manage their own catalog; concurrent edit conflicts are handled gracefully; Admin can moderate any product.

**Concepts Learned** — What does `@Version` actually protect against, and what does it *not* protect against (hint: it's not the same problem as inventory oversell — that needs the atomic conditional update in Stage 5)?

---

### Feature: CATALOG-03 — Product & Variant Media
**Goal**: Attach image metadata/URLs to products/variants without storing binaries in PostgreSQL.

**Requirements**: `SRS.md` FR-PRODUCT-005, FR-MEDIA-001/002; `ERD.md` `PRODUCT_MEDIA`.

**Prerequisites**: CATALOG-02 complete.

**What I Need to Know Before Coding**
- Why binaries don't belong in a relational DB row — MUST UNDERSTAND
- Storing a URL/key + validating type/size at the metadata layer — MUST UNDERSTAND
- Object storage as an external concern (mocked/stubbed at this stage if no real bucket is set up yet) — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- Metadata-vs-blob storage tradeoffs
- Basic file type/size validation

Don't study yet:
- A specific cloud provider's SDK in depth — build against a small interface you can stub, per ARCHITECTURE.md §6
```

**Learning Depth**: Metadata-only storage rationale — MUST; specific object-storage SDK mastery — NICE TO KNOW / LATER.

**Implementation Task**: `POST/DELETE` media endpoints storing `url`, `altText`, `sortOrder`, optional `variantId`, behind a small `MediaStorageClient` interface (a stub/local-disk implementation is acceptable until real object storage is wired up).

**Engineering Problems to Encounter**: accepting an unvalidated URL/type; ordering (`sortOrder`) collisions.

**Things I Should Try to Break**: submit a non-image URL/extension; submit media for a variant that doesn't belong to the product.

**Tests**: happy path; invalid file type/size rejected; cross-ownership rejected.

**Definition of Done**: Media metadata is fully manageable and cleanly separated from binary storage via an interface.

**Concepts Learned** — Why design against an interface (`MediaStorageClient`) now, even with a stub implementation, per `ARCHITECTURE.md`'s "provider-agnostic" decision?

---

# Stage 5 — Inventory
*(module: `inventory`)*

### Learning Gate: Concurrency
Before this stage, understand: what a race condition is conceptually, and the difference between optimistic locking (detect-after-the-fact) and an atomic conditional update (prevent-in-the-database). Don't aim for mastery of PostgreSQL's MVCC internals yet — you'll deepen this understanding *because* you'll hit a real bug here.

### Feature: INVENTORY-01 — Concurrency-Safe Stock Management
**Goal**: Guarantee stock never goes negative, even under concurrent purchase attempts on the last unit.

**Requirements**: `SRS.md` FR-INV-001..004, NFR-CONC-001; `ERD.md` §2 concurrency notes; `ARCHITECTURE.md` §8.

**Prerequisites**: CATALOG-02 complete.

**What I Need to Know Before Coding**
- Race conditions on a shared counter — MUST UNDERSTAND
- Atomic conditional SQL update (`UPDATE ... SET stock = stock - :qty WHERE stock >= :qty`) and reading affected-row-count — MUST UNDERSTAND
- Difference between this and optimistic locking (why `@Version` alone isn't the primary safeguard here) — MUST UNDERSTAND
- Transaction isolation basics (read committed default) — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- Race conditions with a concrete two-thread example
- @Modifying @Query in Spring Data JPA for a conditional UPDATE
- Checking affected row count to detect a failed atomic update

Don't study yet:
- Distributed locks (Redis-based) — not needed for a single-database modular monolith
- Pessimistic locking as the default strategy (reserve it as a fallback tool, not the default)
```

**Learning Depth**: Atomic conditional update — MUST; interpreting affected-row-count as success/failure signal — MUST; pessimistic `SELECT ... FOR UPDATE` as an alternative tool — SHOULD UNDERSTAND (you'll want it later for Payouts).

**Implementation Task**: Build a stock-decrement/restock service method using the atomic conditional update pattern. Write a concurrency test that fires many simultaneous decrement attempts against a variant with `stock_quantity = 1` and asserts exactly one succeeds.

**Engineering Problems to Encounter**: naive "read-then-write" stock logic (`if stock > 0 then stock--`) failing under concurrency; forgetting to check affected-row-count and silently "succeeding" a failed decrement; audit trail for stock changes.

**Things I Should Try to Break**
- The canonical scenario: two simulated concurrent requests both try to buy the last unit — verify exactly one wins.
- Attempt to decrement below zero directly.
- Restock and re-attempt after a previous failed decrement.

**Tests**: single-threaded happy path; concurrency test with N parallel threads against 1 unit of stock (assert exactly one success, N-1 clean `409`s); restock happy path.

**Definition of Done**: A concurrency test with real parallel threads (not just sequential calls) proves exactly one buyer wins the last unit.

**Concepts Learned** — Walk through, in your own words, exactly why the naive read-then-write approach fails and why the atomic conditional update doesn't. What would happen if you used `@Version`/optimistic locking alone here instead?

---

# Stage 6 — Search
*(module: `search`)*

### Feature: SEARCH-01 — Full-Text Search, Filtering, Sorting, Pagination
**Goal**: Let users search/filter/sort the catalog per `API_CONTRACT.md` §6–9.

**Requirements**: `SRS.md` FR-SEARCH-001..003; `ERD.md` GIN index note.

**Prerequisites**: CATALOG-02 complete (need real product data to search).

**What I Need to Know Before Coding**
- `tsvector`/`tsquery` and `ts_rank` in PostgreSQL — MUST UNDERSTAND
- GIN index creation for full-text search — MUST UNDERSTAND
- Combining full-text search with structured filters (category, price range) in one query — MUST UNDERSTAND
- Spring Data `Pageable`/`Sort` — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- PostgreSQL tsvector/tsquery basics and ts_rank
- GIN indexes
- Spring Data Pageable

Don't study yet:
- Elasticsearch/OpenSearch — explicitly deferred per PRD.md/ARCHITECTURE.md until PostgreSQL FTS is a measured bottleneck
```

**Learning Depth**: `tsvector`/GIN — MUST; combining ranked search with filters in one query — MUST; dedicated search-engine concepts — NICE TO KNOW / LATER.

**Implementation Task**: Add a generated/maintained `tsvector` column (or expression index) over product name+description, a GIN index on it, and a repository query combining `q` (ranked), `categoryId`, `minPrice`/`maxPrice`, `sellerId`, sort, and pagination per the API contract.

**Engineering Problems to Encounter**: search feeling correct but slow without the GIN index; sort-by-relevance conflicting with an explicit `sort` param; pagination + filtering returning inconsistent counts.

**Things I Should Try to Break**: run a broad query against a decent-sized seeded dataset without the index and observe the plan (`EXPLAIN ANALYZE`) before/after adding it; combine `q` with a `sort=price,asc` and confirm relevance ranking is correctly overridden.

**Tests**: happy path search; filter combinations; empty-result query; pagination boundaries (first/last page).

**Definition of Done**: Search returns ranked, filterable, sortable, paginated results, and you've seen the query plan improve with the GIN index in place.

**Concepts Learned** — What did `EXPLAIN ANALYZE` show before and after the index? At what point would this stop being "good enough" and justify a dedicated search engine?

---

# Stage 7 — Cart
*(module: `cart`)*

### Feature: CART-01 — Multi-Seller Cart
**Goal**: One Cart per Customer/guest session that can contain items from multiple Sellers.

**Requirements**: `SRS.md` FR-CART-001..003; `ERD.md` `CARTS`, `CART_ITEMS`.

**Prerequisites**: CATALOG-02, INVENTORY-01 complete.

**What I Need to Know Before Coding**
- Modeling a guest session (header/cookie token) alongside an authenticated cart — MUST UNDERSTAND
- Soft stock-check at add-time vs. hard check at checkout (why both exist) — MUST UNDERSTAND
- Idempotent "add or increment" logic for the same variant added twice — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- Session identification strategies for unauthenticated users (opaque session token)
- Why a cart-time stock check is advisory, not authoritative (Checkout re-validates)

Don't study yet:
- Full checkout/order-splitting logic (next stage)
```

**Learning Depth**: Guest session modeling — MUST; advisory vs. authoritative validation distinction — MUST.

**Implementation Task**: `GET/POST/PATCH/DELETE` on `/cart` and `/cart/items` supporting both an authenticated Customer and a guest session token, with items grouped implicitly by seller (derivable from each item's variant) for display.

**Engineering Problems to Encounter**: guest cart abandonment/cleanup; merging a guest cart into a Customer cart on login (note as an explicit decision — implement only if in scope, otherwise document as a known gap).

**Things I Should Try to Break**: add more quantity than currently in stock and confirm the soft check response; add the same variant twice and confirm it increments rather than duplicating a row.

**Tests**: happy path add/update/remove; soft stock-check rejection; guest vs. authenticated isolation (one guest can't see another guest's cart).

**Definition of Done**: A cart can hold items from multiple sellers, for both guests and Customers, with correct soft stock validation.

**Concepts Learned** — Why is cart-time stock validation deliberately "soft" instead of reserving stock immediately on add-to-cart?

---

# Stage 8 — Checkout & Orders
*(module: `ordering`)*

### Learning Gate: Transactions & Order Splitting
This is the most architecturally significant feature so far. Before starting, understand: what a database transaction boundary is, why "all or nothing" matters across multiple writes (inventory decrement + coupon redemption + order creation), and how the Domain Model's Customer Order → Seller Order split (`DOMAIN_MODEL.md` §6.2) needs to become real code.

### Feature: CHECKOUT-01 — Checkout & Order Splitting (Core Feature)
**Goal**: Turn a multi-seller Cart into one Customer Order containing one Seller Order per distinct Seller, atomically, with correct historical snapshots.

**Requirements**: `SRS.md` FR-CHECKOUT-001..004, FR-ORDER-001..003; `USE_CASES.md` UC-C-02; `DOMAIN_MODEL.md` §6.2; `ERD.md` `CUSTOMER_ORDERS`, `SELLER_ORDERS`, `ORDER_ITEMS`; `API_CONTRACT.md` `POST /orders/checkout`.

**Prerequisites**: CART-01, INVENTORY-01 complete. (COUPON validation can be stubbed as "always valid" here and completed for real in Stage 11 — document this explicitly if you defer it.)

**What I Need to Know Before Coding**
- `@Transactional` boundaries spanning multiple repository writes — MUST UNDERSTAND
- Grouping cart items by seller in the service layer — MUST UNDERSTAND
- Building an immutable snapshot (`OrderItem`) from current `Product`/`Variant` state at the moment of purchase — MUST UNDERSTAND
- Reusing the Stage 5 atomic stock-decrement inside this larger transaction — MUST UNDERSTAND
- Idempotency keys for the checkout endpoint — MUST UNDERSTAND
- Rolling back the *entire* checkout if any single item's stock decrement fails — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Spring @Transactional propagation basics (enough to know the whole checkout is one transaction)
- Grouping/partitioning a list by a key in Java (Collectors.groupingBy)
- Idempotency-Key pattern (store the key + result, short-circuit repeats)
- Designing an immutable snapshot entity (no setters that mutate post-creation fields)

Don't study yet:
- Real payment provider integration (Stage 9 — treat payment as "pending" here for online, or "COD marked pending-collection")
- Coupon redemption's own concurrency-safety internals (Stage 11 — stub it for now)
```

**Learning Depth**: Transactional consistency across multiple aggregates — MUST; snapshot immutability — MUST; idempotency key handling — MUST; payment provider specifics — explicitly deferred.

**Implementation Task**: Implement `POST /orders/checkout` for the registered-customer path: group cart items by seller, re-validate stock atomically per item (reusing INVENTORY-01), create one `CustomerOrder` + N `SellerOrder`s + their `OrderItem` snapshots, clear the cart, and return the response shape from `API_CONTRACT.md`. If any item fails stock validation, the entire checkout must roll back — no partial orders.

**Engineering Problems to Encounter**: partial success (e.g., 2 of 3 seller orders created before a failure) if the transaction boundary is wrong; snapshot fields accidentally referencing the live `Product` entity instead of copying values; double-checkout from a double-submitted request without idempotency protection; forgetting that `Cart` must be cleared only after a *successful* commit.

**Things I Should Try to Break**
- Submit a cart where one seller's item is out of stock — confirm the *entire* checkout fails, not just that item.
- Double-submit the same checkout request (same `Idempotency-Key`) rapidly — confirm only one order is created.
- Edit a Product's name/price immediately after checkout and confirm the existing Order Item snapshot is unaffected.

**Tests**: happy-path multi-seller checkout produces correct split; single-item stock failure rolls back the whole order; idempotency key prevents duplicate orders; snapshot immutability verified by mutating the product post-checkout and re-reading the order.

**Definition of Done**: A multi-seller cart checkout reliably produces a correctly split, snapshotted order, or fails atomically with nothing partially created; duplicate submissions are safe.

**Concepts Learned** — Why must inventory decrement and order/seller-order creation happen in the *same* transaction? What specifically would go wrong if they were two separate transactions?

---

### Feature: CHECKOUT-02 — Guest Checkout
**Goal**: Extend CHECKOUT-01 to support unauthenticated purchases with the required contact/shipping info.

**Requirements**: `SRS.md` FR-CHECKOUT-003; `USE_CASES.md` UC-CHECKOUT-GUEST; `ERD.md` nullable `user_id`, guest columns on `CUSTOMER_ORDERS`.

**Prerequisites**: CHECKOUT-01 complete.

**What I Need to Know Before Coding**
- Modeling an order that legitimately has no `user_id` — MUST UNDERSTAND
- Validating the required guest fields (name/email/phone/address/payment method) — MUST UNDERSTAND
- How a guest later retrieves their own order (guest token/order-lookup strategy) — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- Nullable foreign keys and what "no owning user" means for later authorization checks
- Guest order retrieval pattern (e.g., a signed lookup token returned at checkout)

Don't study yet:
- Linking a guest order to a later-created account (explicitly Post-MVP per PRD.md)
```

**Learning Depth**: Nullable-owner modeling — MUST; guest retrieval token design — SHOULD.

**Implementation Task**: Extend the checkout service to accept the guest payload branch from `API_CONTRACT.md`, validate all required guest fields, and issue a mechanism (e.g., a signed order-lookup token) so the guest can later call `GET /orders/{id}`.

**Engineering Problems to Encounter**: an authorization check written assuming `user_id` is always present, breaking on guest orders; missing one of the four required guest fields slipping through.

**Things I Should Try to Break**: submit guest checkout missing the phone number; try to `GET` a guest order without the lookup token; try to `GET` another guest's order by guessing its ID.

**Tests**: happy path guest checkout; missing-field validation; unauthorized access to another guest's order rejected.

**Definition of Done**: Guest checkout works end-to-end and produces a historically meaningful, independently retrievable order with no registered user.

**Concepts Learned** — What had to change in your authorization logic from CHECKOUT-01 to correctly support a null owner?

---

# Stage 9 — Payments
*(module: `payments`)*

### Learning Gate: Payments & Idempotency
Before this stage, understand: the difference between a payment *intent* and a payment *confirmation*, why webhooks exist (the client can't be trusted to report success), and what "idempotent" means for a handler that might receive the same event twice.

### Feature: PAYMENT-01 — Cash on Delivery
**Goal**: Support the simplest payment path with no external provider.

**Requirements**: `SRS.md` FR-PAY-001/002.

**Prerequisites**: CHECKOUT-01 complete.

**What I Need to Know Before Coding**
- Modeling `Payment` as its own entity even for COD (not just an order flag) — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Why Payment is a separate aggregate from CustomerOrder even for COD

Don't study yet:
- Any external payment SDK
```

**Learning Depth**: Payment-as-separate-aggregate — MUST.

**Implementation Task**: On COD checkout, create a `Payment` row with `method=COD`, `status=PENDING` (collected on delivery, not at order time).

**Engineering Problems to Encounter**: conflating "order confirmed" with "payment confirmed" for COD orders.

**Things I Should Try to Break**: attempt to mark a COD payment "confirmed" before delivery and see whether your state machine allows it (it shouldn't, per your Return/Delivery flow ordering).

**Tests**: COD checkout creates a `PENDING` payment correctly linked to the order.

**Definition of Done**: COD orders have a real Payment record with correct initial state.

**Concepts Learned** — Why keep Payment, SellerBalance, and Payout as separate concepts (per `DOMAIN_MODEL.md` invariant #7) even though COD looks simple enough to fake with one field?

---

### Feature: PAYMENT-02 — Online Payment Intent, Confirmation & Idempotent Webhook
**Goal**: Support a realistic online payment lifecycle: intent → provider confirmation via webhook, resilient to duplicate/out-of-order delivery.

**Requirements**: `SRS.md` FR-PAY-003/004/005; `ARCHITECTURE.md` §8 (duplicate webhooks); `API_CONTRACT.md` `POST /orders/{id}/payments/intent`, `POST /webhooks/payments`.

**Prerequisites**: PAYMENT-01 complete.

**What I Need to Know Before Coding**
- Payment intent/confirmation state machine — MUST UNDERSTAND
- Webhook signature/HMAC verification — MUST UNDERSTAND
- Idempotency via a stored, checked `providerEventId` — MUST UNDERSTAND
- Never trusting a client-side "success" callback alone — MUST UNDERSTAND
- Designing the payment provider integration behind a small interface (per `ARCHITECTURE.md` — no hard vendor coupling) — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- Payment intent lifecycle (created → requires action → succeeded/failed) at a conceptual level
- Webhook signature verification (HMAC)
- Idempotency key / processed-event-log pattern

Don't study yet:
- Building support for more than one payment provider (one abstraction, one real/mock implementation, is enough)
```

**Learning Depth**: Webhook idempotency — MUST; signature verification — MUST; multi-provider abstraction depth — NICE TO KNOW / LATER.

**Implementation Task**: `POST /orders/{id}/payments/intent` creates a `Payment` in `PENDING` with a provider reference. `POST /webhooks/payments` verifies the signature, checks whether `providerEventId` was already processed (no-op if so), and otherwise atomically updates the `Payment` to `CONFIRMED`/`FAILED` and triggers downstream effects (order confirmation).

**Engineering Problems to Encounter**: processing the same webhook event twice and double-crediting something; trusting an unsigned/unverified webhook payload; a failed payment leaving inventory permanently decremented with no path to release it.

**Things I Should Try to Break**: replay the exact same webhook payload twice — confirm the second is a no-op; send a webhook with a bad/missing signature; simulate a `payment.failed` event and confirm the order lands in a retryable/cancellable state, not a broken one.

**Tests**: happy-path intent→confirm; duplicate webhook is a no-op; invalid signature rejected; failed payment leaves order in a sane state.

**Definition of Done**: Webhook replay is provably safe (a test literally calls the webhook endpoint twice with the same event and asserts no double effect).

**Concepts Learned** — Why can't the client's "payment succeeded" redirect alone be trusted to confirm payment? What specifically makes your webhook handler idempotent — walk through the exact mechanism.

---

### Feature: PAYMENT-03 — Refunds
**Goal**: Model refunds as a distinct entity linked to a Payment (and later a Return Request).

**Requirements**: `SRS.md` FR-PAY-006; `ERD.md` `REFUNDS`.

**Prerequisites**: PAYMENT-02 complete. (Full return-triggered refund flow completes in Stage 14.)

**What I Need to Know Before Coding**
- Refund as its own entity, not a mutation of Payment's amount field — MUST UNDERSTAND
- Partial refund amounts — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Why Refund is modeled separately from Payment (preserves the original payment record intact)

Don't study yet:
- The full return-request approval workflow (Stage 14) — build the Refund entity/service method now, wire it to Returns later
```

**Learning Depth**: Refund-as-separate-entity — MUST.

**Implementation Task**: A `RefundService` capable of creating a `Refund` linked to a `Payment` for a given amount (admin-triggerable directly for now via `/admin/refunds/{id}/reprocess`-style testing hook, fully wired to Returns in Stage 14).

**Engineering Problems to Encounter**: allowing a refund total to exceed the original payment amount.

**Things I Should Try to Break**: attempt to refund more than the original payment amount; attempt two partial refunds that together exceed the total.

**Tests**: happy path partial/full refund; over-refund rejected.

**Definition of Done**: Refunds exist as auditable, amount-validated entities ready to be triggered by the Returns flow.

**Concepts Learned** — Why not just decrement `payment.amount` directly instead of creating a `Refund` row?

---

# Stage 10 — Shipping
*(module: `shipping`)*

### Feature: SHIPPING-01 — Shipment Tracking
**Goal**: Track fulfillment mode and status per Seller Order without assuming one universal shipping provider.

**Requirements**: `SRS.md` FR-SHIP-001..003; `USE_CASES.md` UC-S-04; `ERD.md` `SHIPMENTS`; `DOMAIN_MODEL.md` §6.4 state machine.

**Prerequisites**: CHECKOUT-01 complete.

**What I Need to Know Before Coding**
- Modeling a state machine as an enum with guarded transitions (reuse the pattern from SELLER-01) — MUST UNDERSTAND
- One-to-one relationship (`SellerOrder` ↔ `Shipment`) — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- @OneToOne mapping
- Reusing your earlier guarded-state-transition pattern

Don't study yet:
- Real carrier API integration (out of scope; tracking number is just a string field)
```

**Learning Depth**: Guarded state transitions (reused) — MUST; `@OneToOne` — MUST.

**Implementation Task**: `POST /seller/orders/{id}/ship` and `.../deliver`, updating `Shipment.status` per the state machine, rejecting invalid transitions (e.g., "deliver" before "ship").

**Engineering Problems to Encounter**: skipping a required state; a Seller marking another Seller's order shipped.

**Things I Should Try to Break**: call `deliver` before `ship`; call `ship` twice; attempt cross-seller-order access.

**Tests**: valid transition sequence; invalid transition rejected; ownership enforced.

**Definition of Done**: Shipment status accurately and safely tracks each Seller Order's fulfillment independent of the others in the same Customer Order.

**Concepts Learned** — How does this state machine differ from the Seller Application one, and how is it similar?

---

# Stage 11 — Promotions
*(module: `promotions`)*

### Feature: PROMO-01 — Coupons with Concurrency-Safe Redemption
**Goal**: Apply percentage/fixed discounts at checkout with expiry and usage-limit enforcement, safe under concurrent redemption.

**Requirements**: `SRS.md` FR-COUPON-001..003, NFR-CONC-002; `ERD.md` `COUPONS`, `COUPON_REDEMPTIONS`.

**Prerequisites**: CHECKOUT-01 complete (revisit the stubbed coupon check from CHECKOUT-01 and implement it for real now).

**What I Need to Know Before Coding**
- Reusing the atomic-conditional-update pattern from Stage 5, applied to `redeemed_count < usage_limit` — MUST UNDERSTAND
- Discount calculation with `BigDecimal` (percentage vs fixed) and rounding rules — MUST UNDERSTAND
- Expiry check as a simple timestamp comparison — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- BigDecimal arithmetic and RoundingMode
- Reapplying the Stage 5 atomic-update pattern to a different counter (usage count instead of stock)

Don't study yet:
- A general promotions/rules engine — explicitly out of scope per PRD.md §5
```

**Learning Depth**: `BigDecimal` correctness — MUST; atomic usage-count update — MUST (this is literally the same concurrency lesson from Stage 5, applied again — notice that on purpose).

**Implementation Task**: Admin coupon CRUD; wire real coupon validation into `CHECKOUT-01`'s checkout transaction: atomically increment `redeemed_count` only if under the limit and not expired, inside the same checkout transaction, rolling back the whole checkout if the coupon can't be applied.

**Engineering Problems to Encounter**: floating-point rounding errors if `double` is used instead of `BigDecimal`; a coupon usage-limit race identical in shape to the Stage 5 inventory race; applying an expired coupon that passed validation moments before expiry (race between check and use — solve with the same atomic-update discipline).

**Things I Should Try to Break**: fire many concurrent checkouts against a coupon with `usageLimit=1` and confirm exactly one succeeds; apply a coupon that expires mid-request.

**Tests**: happy path percentage/fixed discount math; expired coupon rejected; concurrency test proving usage limit holds under parallel redemption.

**Definition of Done**: Coupon math is exact (`BigDecimal`) and a concurrency test proves the usage limit can't be exceeded.

**Concepts Learned** — Compare this feature's concurrency fix to Stage 5's. What's identical about the underlying problem, and why did recognizing that pattern make this faster to build correctly?

---

# Stage 12 — Reviews
*(module: `reviews`)*

### Feature: REVIEW-01 — Verified-Purchase Reviews
**Goal**: Let a Customer review a product only if they have a delivered purchase of it.

**Requirements**: `SRS.md` FR-REVIEW-001..003; `USE_CASES.md` UC-C-05; `ERD.md` `REVIEWS` unique constraint.

**Prerequisites**: CHECKOUT-01, SHIPPING-01 complete (need a Delivered Order Item to exist).

**What I Need to Know Before Coding**
- Cross-module read (Reviews reads Ordering's data to verify purchase) via a service interface, not a repository reach-through — MUST UNDERSTAND
- Unique constraint design preventing duplicate reviews per purchase — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Designing a narrow cross-module query interface (e.g., ordering exposes `hasDeliveredPurchase(userId, productId)` rather than reviews querying order tables directly)

Don't study yet:
- Review moderation workflows beyond a simple admin delete (sentiment analysis, etc. are out of scope)
```

**Learning Depth**: Clean cross-module read boundary — MUST.

**Implementation Task**: `POST /products/{id}/reviews` validating the referenced `orderItemId` proves a delivered purchase by the caller of that exact product, enforcing one review per verified purchase; `GET /products/{id}/reviews`; admin delete.

**Engineering Problems to Encounter**: a Reviews service reaching directly into Ordering's repository (breaking the module boundary from `FOLDER_STRUCTURE.md` §5); allowing a review without a real purchase; allowing duplicate reviews for the same purchase.

**Things I Should Try to Break**: submit a review referencing an order item that isn't Delivered yet; submit a review referencing someone else's order item; submit the same review twice.

**Tests**: happy path; no-verified-purchase rejected (`422`); duplicate rejected; cross-user order item rejected.

**Definition of Done**: Reviews are strictly gated by verified purchase, implemented without violating module boundaries.

**Concepts Learned** — What narrow interface did you expose from `ordering` to `reviews`, and why not just let `reviews` query the `order_items` table directly?

---

# Stage 13 — Wishlist
*(module: `wishlist`)*

### Feature: WISHLIST-01 — Wishlist Management
**Goal**: Let a Customer save/remove/view products, strictly scoped to themselves.

**Requirements**: `SRS.md` FR-WISH-001/002; `USE_CASES.md` UC-C-01 (wishlist).

**Prerequisites**: CATALOG-02, AUTHZ-01 complete.

**What I Need to Know Before Coding**
- Reusing the ownership-check pattern one more time (this should now feel routine) — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Nothing new — this is deliberately a "consolidation" feature to practice the ownership pattern without new concepts

Don't study yet:
- Anything new — if you find yourself needing to study something for this feature, you may be over-engineering it
```

**Learning Depth**: Ownership pattern reuse — MUST.

**Implementation Task**: `GET/POST/DELETE` on `/wishlist` and `/wishlist/items`, one Wishlist per Customer.

**Engineering Problems to Encounter**: none new, by design — if you hit a genuinely new problem here, re-check whether it belongs in an earlier feature instead.

**Things I Should Try to Break**: try to view/modify another customer's wishlist by ID guessing.

**Tests**: happy path; cross-user access rejected.

**Definition of Done**: Wishlist works, built quickly by reusing existing patterns — a good checkpoint to notice how much faster this feature was than Stage 1's.

**Concepts Learned** — Notice explicitly: which parts of this took no new learning? That's a sign the earlier investment (ownership pattern, DTOs, validation) is paying off.

---

# Stage 14 — Returns
*(module: `returns`)*

### Feature: RETURN-01 — Return Requests, Approval & Refund/Inventory Effects
**Goal**: Support the Delivered → Return Requested → Approved/Rejected flow with correct downstream refund and inventory effects, under one marketplace-wide policy.

**Requirements**: `SRS.md` FR-RETURN-001..004; `USE_CASES.md` UC-C-04; `DOMAIN_MODEL.md` §6.4; `ERD.md` `RETURN_REQUESTS`.

**Prerequisites**: SHIPPING-01, PAYMENT-03, INVENTORY-01 complete.

**What I Need to Know Before Coding**
- Orchestrating a state transition that triggers side effects in two other modules (Payments for refund, Inventory for restock) — MUST UNDERSTAND
- Partial return/refund (subset of quantity/items) — MUST UNDERSTAND
- Centrally-configured policy (return window) as configuration, not hardcoded — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- Orchestration pattern: one service coordinating calls to other modules' service interfaces inside one transaction
- Partial-quantity refund calculation

Don't study yet:
- Per-seller custom return policies — explicitly out of scope (policy is marketplace-wide per PRD.md §14)
```

**Learning Depth**: Multi-module orchestration in one transaction — MUST; partial refund calculation — MUST.

**Implementation Task**: `POST /seller-orders/{id}/returns` (validate Delivered state + return window), Seller/Admin approve/reject endpoints; on approval, atomically create a `Refund` (via Payments' service interface) and restock the relevant variant quantity (via Inventory's service interface).

**Engineering Problems to Encounter**: approving a return outside the window; approving the same return twice; a partial refund miscalculating the per-unit amount; restocking without refunding (or vice versa) if the transaction boundary is wrong.

**Things I Should Try to Break**: request a return after the window closes; approve the same return request twice; request a partial return of 1 of 3 units and verify both the refund amount and restocked quantity are exactly proportional.

**Tests**: happy path full/partial return; outside-window rejected; double-approval rejected; refund+restock happen atomically together (test a simulated failure of one and confirm the other rolls back too).

**Definition of Done**: Approved returns reliably and atomically produce both the correct refund and the correct inventory restock, or neither.

**Concepts Learned** — Why must the refund and the restock happen in the same transaction? Sketch the failure mode if they didn't.

---

# Stage 15 — Finance
*(module: `finance`)*

### Learning Gate: Money & Ledgers
Before this stage, understand: why a running balance stored as a single mutable number is fragile compared to an append-only ledger of entries that a balance is *derived* from (or kept in sync with, guarded by locking). You don't need full double-entry bookkeeping theory — just this core idea.

### Feature: FINANCE-01 — Commission Calculation
**Goal**: Compute marketplace commission vs. seller revenue per Seller Order using configurable percentage and precise decimal math.

**Requirements**: `SRS.md` FR-COMM-001..003; `PRD.md` §9.

**Prerequisites**: CHECKOUT-01 complete.

**What I Need to Know Before Coding**
- Externalizing the commission percentage as configuration (`application.yml` value or a DB-backed config row), not a code literal — MUST UNDERSTAND
- `BigDecimal` multiplication/rounding for money — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Spring @ConfigurationProperties (or a simple config table) for externalized values
- BigDecimal.setScale / RoundingMode for currency math

Don't study yet:
- Per-seller or per-category commission tiers — explicitly a future evolution per PRD.md §17 assumptions
```

**Learning Depth**: Externalized configuration — MUST; `BigDecimal` rounding discipline — MUST.

**Implementation Task**: At Seller Order settlement (payment confirmed), compute `commission_amount` and `seller_revenue_amount` from the configured percentage and persist both on the `SellerOrder` per `ERD.md`.

**Engineering Problems to Encounter**: hardcoding "10%" somewhere despite the requirement; rounding commission and revenue such that they don't sum back to the original sale amount.

**Things I Should Try to Break**: change the configured percentage and confirm no code change was needed; pick a sale amount that doesn't divide evenly and verify commission + revenue reconstitute the original total exactly.

**Tests**: commission math correctness across several percentages and amounts; rounding reconciliation test (commission + revenue == subtotal).

**Definition of Done**: Commission is entirely configuration-driven and penny-accurate.

**Concepts Learned** — What rounding rule did you choose, and why does it matter that commission + revenue reconcile exactly to the sale amount?

---

### Feature: FINANCE-02 — Seller Balance Ledger
**Goal**: Track Seller earnings as auditable ledger entries, not a single opaque counter.

**Requirements**: `SRS.md` FR-BAL-001/002; `ERD.md` `SELLER_BALANCES`, `LEDGER_ENTRIES`.

**Prerequisites**: FINANCE-01, RETURN-01 complete.

**What I Need to Know Before Coding**
- Append-only ledger entries feeding a maintained balance — MUST UNDERSTAND
- Optimistic locking (`@Version`) on `SellerBalance` — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Ledger/journal entry pattern (each entry references its source: sale, refund, payout)
- Reapplying @Version here (same tool as Catalog, different row)

Don't study yet:
- Full double-entry accounting (debits/credits across two books) — a single-sided ledger with a typed entry is sufficient here
```

**Learning Depth**: Ledger entry design — MUST; `@Version` reuse — MUST.

**Implementation Task**: On payment confirmation, create a `SALE_CREDIT` ledger entry and update `SellerBalance.available_balance`. On refund, create a `REFUND_DEBIT` entry and decrement the balance accordingly (wire this into RETURN-01's refund step).

**Engineering Problems to Encounter**: updating the balance without a corresponding ledger entry (losing auditability); two near-simultaneous balance-affecting events causing a lost update without locking.

**Things I Should Try to Break**: trigger a sale credit and a refund debit concurrently for the same seller and confirm the final balance is correct via `@Version`-guarded retry, not silently wrong.

**Tests**: sale credit happy path; refund debit happy path; concurrency test on simultaneous ledger-affecting events.

**Definition of Done**: Every balance change has a corresponding ledger entry, and the balance survives concurrent updates correctly.

**Concepts Learned** — If the `available_balance` column were ever wrong, how would the ledger entries let you find out why?

---

### Feature: FINANCE-03 — Payout Lifecycle
**Goal**: Let a Seller request a payout up to their available balance, with an explicit Requested → Processing → Completed/Failed lifecycle.

**Requirements**: `SRS.md` FR-PAYOUT-001..003, NFR-CONC-004; `USE_CASES.md` UC-S-06, UC-A-08; `DOMAIN_MODEL.md` §6.5.

**Prerequisites**: FINANCE-02 complete.

**What I Need to Know Before Coding**
- Preventing a payout from exceeding balance under concurrent requests — pessimistic locking (`SELECT ... FOR UPDATE`) as the right tool here (contrast with Stage 5/11's atomic-update approach) — MUST UNDERSTAND
- Reversing a failed payout (crediting the balance back) as its own ledger entry — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Pessimistic locking (SELECT ... FOR UPDATE) and when it's preferable to an atomic conditional update or optimistic locking
- Designing the Failed-payout reversal as a new PAYOUT_DEBIT-reversing entry rather than deleting the original

Don't study yet:
- Real payout/bank-transfer provider integration — the Admin "complete/fail" endpoints simulate the external step at MVP
```

**Learning Depth**: Pessimistic locking — MUST (this is the moment to actually learn it, having deferred it since Stage 5); reversal-as-new-entry — MUST.

**Implementation Task**: `POST /seller/payouts` (lock the `SellerBalance` row, verify sufficient funds, create `Payout` in `REQUESTED`, debit the balance); Admin `.../complete` and `.../fail` endpoints finalizing the lifecycle, with `fail` crediting the balance back via a new ledger entry.

**Engineering Problems to Encounter**: two concurrent payout requests both reading a stale balance and both succeeding when only one should; forgetting to reverse the debit on failure, silently losing seller funds.

**Things I Should Try to Break**: fire two concurrent payout requests each individually valid but which together exceed the available balance — confirm only the affordable one(s) succeed; mark a payout Failed and confirm the balance is restored exactly.

**Tests**: happy path request→complete; happy path request→fail→balance restored; concurrency test on simultaneous over-budget payout requests.

**Definition of Done**: A concurrency test proves two simultaneous payout requests can never together exceed the available balance, and a failed payout always restores funds exactly.

**Concepts Learned** — Why was pessimistic locking the right tool here, when Stage 5 and Stage 11 used an atomic conditional update instead? What's different about this operation's shape?

---

# Stage 16 — Notifications
*(module: `notifications`)*

### Feature: NOTIFY-01 — In-App + Async Email Notifications
**Goal**: Deliver in-app and email notifications for key lifecycle events without blocking the triggering business transaction.

**Requirements**: `SRS.md` FR-NOTIF-001/002; `ARCHITECTURE.md` §11.

**Prerequisites**: At least CHECKOUT-01, SHIPPING-01, RETURN-01, SELLER-02, FINANCE-03 complete (these are the event sources).

**What I Need to Know Before Coding**
- Why email must not block the request thread — MUST UNDERSTAND
- `@Async` with a dedicated executor (not the default common pool) — MUST UNDERSTAND
- A simple reliability pattern (e.g., an outbox-style table) so a crash between "commit" and "send" doesn't silently drop a notification — SHOULD UNDERSTAND
- Retryability of a failed email send — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Spring @Async and configuring a dedicated TaskExecutor
- Outbox pattern basics (write the notification intent in the same transaction as the business event; a separate process/poller sends it)

Don't study yet:
- A message broker — this in-process/outbox approach is the deliberate MVP choice per ARCHITECTURE.md §11/§15
```

**Learning Depth**: Async execution off a dedicated executor — MUST; outbox-style reliability — SHOULD (implement a simple version; don't over-build it).

**Implementation Task**: A `NotificationService` called synchronously (fast: just persists a `Notification` row) from each triggering module at the point of the event; a separate async step (email) reads pending notifications and sends them, retrying on failure without re-triggering the original business transaction.

**Engineering Problems to Encounter**: putting the email send directly in the same transaction/thread as checkout, slowing it down or failing checkout if the mail server is down; losing a notification entirely if the app crashes between commit and send (this is exactly what the outbox-style table protects against).

**Things I Should Try to Break**: make the mail provider unavailable and confirm checkout/order actions still succeed and respond quickly; kill the app between persisting a notification and sending its email, restart, and confirm it still gets sent.

**Tests**: business transaction succeeds even if email sending fails; notification eventually sent (retry) after a transient failure; latency of the triggering endpoint is not affected by email latency.

**Definition of Done**: No business-critical endpoint's response time depends on email delivery, and no notification is silently lost on a crash.

**Concepts Learned** — Where exactly is the transaction boundary between "the business event happened" and "the email was sent," and why does that boundary matter?

---

# Stage 17 — Admin Operations
*(module: `admin`)*

### Feature: ADMIN-01 — Cross-Module Admin Endpoints
**Goal**: Consolidate admin-facing operations (moderation, suspension, category/coupon/policy management, payout oversight) behind a coherent set of endpoints that delegate to each owning module.

**Requirements**: `SRS.md` FR-ADMIN-001..004; `USE_CASES.md` Admin section; `API_CONTRACT.md` Admin Operations.

**Prerequisites**: Most other modules complete (this stage is largely wiring/consolidation, not new domain concepts).

**What I Need to Know Before Coding**
- Why `admin` should be a thin orchestration layer, not where business rules live (per `FOLDER_STRUCTURE.md` §3) — MUST UNDERSTAND

**Study Before Implementation**
```text
Study:
- Nothing conceptually new — this is a design-discipline checkpoint: resist the urge to put real logic in the admin module

Don't study yet:
- A full admin analytics/reporting subsystem — GET /admin/overview should stay a simple aggregation query, not a new reporting engine
```

**Learning Depth**: Orchestration-only module discipline — MUST.

**Implementation Task**: Ensure every Admin endpoint from `API_CONTRACT.md` exists and delegates to its owning module's service (no duplicated business logic in `admin`); build the simple `/admin/overview` aggregation.

**Engineering Problems to Encounter**: the temptation to reimplement a check (e.g., "is this application already decided?") inside the admin controller instead of calling the existing `sellers` service method.

**Things I Should Try to Break**: audit your own `admin` package for any method longer than a few lines of orchestration — if you find real business logic there, refactor it back into the owning module.

**Tests**: each admin endpoint's behavior matches its owning module's existing tests (no divergent logic).

**Definition of Done**: All Admin endpoints exist, and a code review confirms `admin` contains no duplicated business rules.

**Concepts Learned** — Where were you tempted to put logic directly in `admin`, and why did the owning module's service turn out to be the right place instead?

---

# Stage 18 — Observability

### Feature: OBS-01 — Logging, Metrics, Health Checks
**Goal**: Make the system's behavior inspectable in production per `ARCHITECTURE.md` §12.

**Requirements**: `SRS.md` NFR-OBS-001..003.

**Prerequisites**: Most functional modules complete (there's more to observe once more exists).

**What I Need to Know Before Coding**
- Structured (JSON) logging — MUST UNDERSTAND
- Correlation/request ID propagation across a request (MDC) — MUST UNDERSTAND
- Spring Boot Actuator health/metrics endpoints — MUST UNDERSTAND
- Micrometer custom business metrics (orders/min, payment success rate) — SHOULD UNDERSTAND

**Study Before Implementation**
```text
Study:
- SLF4J + a structured logging encoder
- MDC for correlation IDs
- Spring Boot Actuator basics (health, metrics endpoints)
- Micrometer counters/timers for custom metrics

Don't study yet:
- A full observability stack (Prometheus/Grafana/ELK) — expose the metrics/logs correctly first; wiring them into a dashboard is an infrastructure task, not a coding-concept one
```

**Learning Depth**: Structured logging + correlation ID — MUST; Actuator health/readiness — MUST; custom business metrics — SHOULD.

**Implementation Task**: Add a correlation-ID filter (generate or propagate an incoming header, put it in MDC, include it in every log line and error response); enable Actuator health/metrics; add a few custom Micrometer metrics for the business events called out in `ARCHITECTURE.md` §12.

**Engineering Problems to Encounter**: a log line without the correlation ID because MDC wasn't set before an async hop (tie this back to Stage 16's async notification path); a health check reporting healthy while the DB is actually unreachable.

**Things I Should Try to Break**: trace one request's correlation ID through your logs end-to-end, including into the async email path; stop the database and confirm the readiness check correctly reports unhealthy.

**Tests**: correlation ID present on every log line for a sample request; readiness check fails when DB is down.

**Definition of Done**: You can pick one request's correlation ID and find every related log line, and health checks accurately reflect real dependency status.

**Concepts Learned** — Why did the correlation ID need special handling to survive the jump into the async notification thread from Stage 16?

---

# Stage 19 — Deployment

### Feature: DEPLOY-01 — Docker & CI/CD
**Goal**: Reliably build and ship the application per `ARCHITECTURE.md` §14.

**Requirements**: `ARCHITECTURE.md` §14.

**Prerequisites**: A reasonably complete, tested application (most stages done).

**What I Need to Know Before Coding**
- Multi-stage Docker builds — MUST UNDERSTAND
- A basic CI pipeline (build, test, static checks on push) — MUST UNDERSTAND
- Environment-based configuration for Staging/Production — MUST UNDERSTAND
- Why no Kubernetes yet — SHOULD UNDERSTAND (be able to articulate it, not just accept it)

**Study Before Implementation**
```text
Study:
- Multi-stage Dockerfiles (build stage vs. slim runtime stage)
- A CI config (GitHub Actions or similar) running mvn test on push
- Externalizing config via environment variables for different environments

Don't study yet:
- Kubernetes, Helm charts, service mesh — explicitly deferred until a real scaling/ops need per ARCHITECTURE.md §14/§15
```

**Learning Depth**: Multi-stage Docker build — MUST; CI pipeline basics — MUST; Kubernetes — explicitly NOT NOW.

**Implementation Task**: Write a multi-stage `Dockerfile`; a CI workflow running the full test suite (including Testcontainers-based integration tests) on every push; environment-variable-driven config for a Staging deploy target.

**Engineering Problems to Encounter**: a bloated image from a single-stage build; CI failing because Testcontainers needs Docker-in-Docker support in the CI runner; secrets accidentally baked into the image.

**Things I Should Try to Break**: inspect the final image size before/after multi-stage build; deliberately commit a config error and confirm CI catches it before merge.

**Tests**: CI green run including integration tests; a built image runs and passes its own health check.

**Definition of Done**: A single command builds a deployable image, and CI blocks a broken change from merging.

**Concepts Learned** — Why is a single-container deployment the right choice right now, and what specific, measurable signal (referencing `ARCHITECTURE.md` §15) would tell you it's time to reconsider?

---

# Stage 20 — Evolution Triggers (Do Not Implement Preemptively)

This stage is intentionally a checklist of **conditions**, not features to build now. Revisit it periodically.

| Trigger observed | Then introduce |
|---|---|
| Catalog/category read latency degrades under real read load | Redis caching layer for category tree / hot product listings (`ARCHITECTURE.md` §9) |
| Rate limiting needs to work across more than one app instance | Redis-backed rate limiter (`ARCHITECTURE.md` §10) |
| PostgreSQL FTS query time/relevance becomes measurably insufficient at real data volume | Dedicated search engine (Elasticsearch/OpenSearch) (`ARCHITECTURE.md` §15) |
| In-process async/outbox notification handling can't keep up with event volume | Message broker (Kafka/RabbitMQ) for event fan-out (`ARCHITECTURE.md` §11/§15) |
| A genuine team or scaling boundary emerges around Finance or Search specifically | Extract that module into its own deployable service (`ARCHITECTURE.md` §15) |

Each row requires evidence (a metric from Stage 18's observability work), not intuition, before action.

---

# Final Consistency Note

This roadmap was checked against `PRD.md`, `SRS.md`, `USE_CASES.md`, `DOMAIN_MODEL.md`, `ERD.md`, `ARCHITECTURE.md`, and `API_CONTRACT.md` for: feature order and dependencies, module/domain boundaries (matches `FOLDER_STRUCTURE.md` module list exactly), terminology (Customer Order/Seller Order/Order Item, SellerProfile/SellerApplication, SellerBalance/LedgerEntry/Payout kept distinct throughout), authentication/authorization approach, inventory/coupon/payout concurrency strategy, and the deferred-technology list. No contradictions were found; where a feature depends on a not-yet-built piece (e.g., Coupon validation during CHECKOUT-01, Refund wiring during PAYMENT-03), this is called out explicitly as an intentional stub completed in a later stage, not a design inconsistency.
