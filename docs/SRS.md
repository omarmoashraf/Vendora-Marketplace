# docs/SRS.md — Software Requirements Specification

## 1. Introduction
This SRS translates the PRD into precise, testable software requirements for the marketplace backend (Java + Spring Boot + PostgreSQL, modular monolith). It is the authoritative reference for functional and non-functional behavior; the Domain Model, ERD, Architecture, and API Contract must remain consistent with it.

## 2. System Scope
The system exposes REST APIs for Guest, Customer, Seller, and Admin actors covering identity, catalog, cart/checkout, orders, payments, shipping, coupons, reviews, wishlist, returns, commission/balance/payouts, notifications, search, and admin moderation. No frontend is in scope.

## 3. Actors
Guest, Customer, Seller, Admin, System/Background Jobs (see PRD §7).

## 4. Functional Requirements (summary index)
Detailed per-domain requirements are in sections 8–28. Requirement IDs use the pattern `FR-<DOMAIN>-<NNN>`.

## 5. Non-Functional Requirements
- **NFR-PERF-001**: Product listing/search endpoints must return within acceptable latency under paginated queries (target: p95 < 300ms at MVP data scale).
- **NFR-SCALE-001**: The system must run as a single deployable modular monolith with internal module boundaries suitable for future extraction.
- **NFR-AVAIL-001**: Core read paths (browse/search) must degrade gracefully if non-critical subsystems (e.g., email) fail.
- **NFR-MAINT-001**: Modules must not have circular dependencies; cross-module access goes through defined service interfaces.
- **NFR-DATA-001**: All monetary values use fixed-point decimal types (e.g., `BigDecimal` / `NUMERIC`), never floating point.

## 6. Authentication Requirements
- **FR-AUTH-001**: Users register with email + password; passwords are hashed with a strong adaptive algorithm (e.g., bcrypt/argon2).
- **FR-AUTH-002**: Login issues a short-lived JWT access token and a longer-lived refresh token.
- **FR-AUTH-003**: Refresh tokens can be rotated and revoked; a revoked/expired refresh token cannot issue new access tokens.
- **FR-AUTH-004**: Guest checkout requires no authentication but must capture name, email, phone, shipping address, and payment method.
- **FR-AUTH-005**: Login and registration endpoints must be rate-limited.

## 7. Authorization / RBAC Requirements
- **FR-AUTHZ-001**: Roles are Customer, Seller, Admin; a User may simultaneously hold Customer and Seller capability.
- **FR-AUTHZ-002**: A Seller may only modify resources (products, variants, inventory, seller orders) they own.
- **FR-AUTHZ-003**: Admin-only endpoints (moderation, category/coupon management, suspension, payout oversight) must reject non-Admin callers.
- **FR-AUTHZ-004**: Resource ownership checks must occur at the service layer, not only via routing.

## 8. User Requirements
- **FR-USER-001**: Customers can view/update their profile and manage multiple shipping addresses.
- **FR-USER-002**: Admins can suspend/reactivate Users without deleting historical data (Orders, Payments, Reviews).

## 9. Seller Requirements
- **FR-SELLER-001**: Any authenticated User can submit a Seller Application (status: Pending).
- **FR-SELLER-002**: Admin can Approve or Reject a Seller Application; only Approved grants Seller capability.
- **FR-SELLER-003**: Admin can suspend/reactivate a Seller independently of the underlying User account.
- **FR-SELLER-004**: A suspended Seller cannot create/update products or fulfill new orders, but historical data remains intact.

## 10. Product/Catalog Requirements
- **FR-PRODUCT-001**: A Seller can create, update, and (soft-)delete their own Products; edits do not require Admin approval.
- **FR-PRODUCT-002**: A Product belongs to exactly one Seller and one or more Categories.
- **FR-PRODUCT-003**: Admin can hide/remove/moderate any Product regardless of owning Seller.
- **FR-PRODUCT-004**: A Product may have one or more Variants; each Variant has its own price and stock.
- **FR-PRODUCT-005**: Product and Variant support associated media (images) stored as metadata/URL references, not binary blobs in PostgreSQL.

## 11. Category Requirements
- **FR-CAT-001**: Categories form a hierarchical tree (parent/child).
- **FR-CAT-002**: Products can be queried/filtered by category, including descendants of a given category.
- **FR-CAT-003**: Only Admin can create/modify/delete Categories.

## 12. Inventory Requirements
- **FR-INV-001**: Every sellable Variant (or Product, if variant-less) has a tracked stock quantity.
- **FR-INV-002**: Stock decrement on purchase must be atomic; concurrent purchases must never oversell.
- **FR-INV-003**: Stock adjustments must be auditable (who/when/why changed).
- **FR-INV-004**: Attempting to purchase more than available stock must fail cleanly with a clear error.

## 13. Cart Requirements
- **FR-CART-001**: A Customer (or guest session) has exactly one active Cart.
- **FR-CART-002**: A Cart may contain items from multiple Sellers.
- **FR-CART-003**: Cart item quantity is validated against current stock at add/update time (soft check; hard check occurs at checkout).

## 14. Checkout Requirements
- **FR-CHECKOUT-001**: Checkout accepts one Cart and produces exactly one Customer Order, decomposed into one Seller Order per distinct Seller present in the cart.
- **FR-CHECKOUT-002**: Checkout re-validates stock and price atomically at commit time (not solely relying on cart-time snapshot).
- **FR-CHECKOUT-003**: Checkout supports both authenticated Customers and Guests; Guest checkout requires contact + shipping + payment info.
- **FR-CHECKOUT-004**: A Coupon, if applied, is validated (expiry, usage limit, eligibility) atomically at checkout to prevent race-based overuse.

## 15. Order Requirements
- **FR-ORDER-001**: A Customer Order is the top-level object visible to the Customer; it aggregates one or more Seller Orders.
- **FR-ORDER-002**: Each Seller Order has independent fulfillment, shipping, cancellation, and return state.
- **FR-ORDER-003**: Order Items store a purchase-time snapshot (product name, variant descriptor, unit price, quantity) independent of current Product state.
- **FR-ORDER-004**: Orders remain queryable and coherent even when the originating User account is later suspended or (for guests) never existed.
- **FR-ORDER-005**: A Customer may cancel an eligible Order/Seller Order only while it is in a cancellable state (e.g., prior to shipment).

## 16. Payment Requirements
- **FR-PAY-001**: Supported payment methods: Cash on Delivery, Online Payment.
- **FR-PAY-002**: COD orders do not require a payment gateway and are marked as pending collection.
- **FR-PAY-003**: Online payments follow an intent → confirmation lifecycle; final state is derived from provider webhook, not solely from the client callback.
- **FR-PAY-004**: Payment webhook processing must be idempotent (duplicate webhook deliveries must not double-apply effects).
- **FR-PAY-005**: Failed payments must leave the Order in a state that allows retry or cancellation, without corrupting inventory reservations.
- **FR-PAY-006**: Refunds are modeled as a distinct entity linked to a Payment and (optionally) a Return Request.

## 17. Shipping Requirements
- **FR-SHIP-001**: Each Seller Order has a Shipment concept indicating whether fulfillment is Marketplace-handled or Seller-handled.
- **FR-SHIP-002**: Shipment status transitions (e.g., Pending → Shipped → Delivered) are tracked per Seller Order.
- **FR-SHIP-003**: The system does not assume a single universal shipping provider.

## 18. Coupon Requirements
- **FR-COUPON-001**: Coupons support percentage or fixed-amount discounts, an expiration date, and a usage limit (global and/or per-user).
- **FR-COUPON-002**: Coupon redemption count must be incremented atomically to prevent exceeding the usage limit under concurrent redemption.
- **FR-COUPON-003**: Only Admin manages Coupons.

## 19. Review Requirements
- **FR-REVIEW-001**: A Customer can review a Product only if they have a Delivered Order Item for that Product (verified purchase).
- **FR-REVIEW-002**: A Customer may not submit more than one review per purchased Product (duplicate prevention), though re-purchase may allow an updated review per business rule.
- **FR-REVIEW-003**: Admin can moderate (hide/remove) reviews.

## 20. Wishlist Requirements
- **FR-WISH-001**: A Customer can add/remove/view Products in their own Wishlist.
- **FR-WISH-002**: A Customer cannot view or modify another Customer's Wishlist.

## 21. Return/Refund Requirements
- **FR-RETURN-001**: A Customer may request a Return on a Delivered Seller Order Item within the marketplace-wide return window.
- **FR-RETURN-002**: Return Requests move Pending Review → Approved/Rejected; Approved returns trigger refund and inventory-restock processing per policy.
- **FR-RETURN-003**: Partial returns/refunds (subset of items/quantity) are supported.
- **FR-RETURN-004**: The return policy is centrally defined; Sellers cannot override it per-listing.

## 22. Commission Requirements
- **FR-COMM-001**: Commission is calculated as a configurable percentage of each Seller Order's sale value.
- **FR-COMM-002**: The commission percentage is stored as configuration, not hardcoded in business logic.
- **FR-COMM-003**: Commission calculation must use precise decimal arithmetic with defined rounding rules.

## 23. Seller Balance Requirements
- **FR-BAL-001**: A Seller has a running Balance derived from completed sales minus commission and minus completed payouts.
- **FR-BAL-002**: Balance-affecting events (sale settled, refund issued, payout completed) must be recorded as discrete ledger entries, not just a mutated running total, to preserve auditability.

## 24. Payout Requirements
- **FR-PAYOUT-001**: A Seller can request a Payout up to their available Balance.
- **FR-PAYOUT-002**: Payout has an explicit lifecycle: Requested → Processing → Completed/Failed.
- **FR-PAYOUT-003**: A Failed payout returns the requested amount to the available Balance.

## 25. Notification Requirements
- **FR-NOTIF-001**: The system sends in-app and email notifications for key lifecycle events (order confirmed/shipped/delivered, return approved/rejected, seller application decision, payout completed/failed).
- **FR-NOTIF-002**: Email delivery is performed asynchronously and must not block the triggering business transaction.

## 26. Admin Requirements
- **FR-ADMIN-001**: Admin can review/approve/reject Seller Applications.
- **FR-ADMIN-002**: Admin can suspend/reactivate Users and Sellers independently.
- **FR-ADMIN-003**: Admin can moderate Products, manage Categories, manage Coupons, and manage the marketplace Return Policy.
- **FR-ADMIN-004**: Admin actions that affect account state must not delete historical financial/order/review data.

## 27. Search Requirements
- **FR-SEARCH-001**: Product search uses PostgreSQL Full-Text Search with ranking.
- **FR-SEARCH-002**: Search results support filtering (category, price range, seller, rating) and sorting (relevance, price, newest).
- **FR-SEARCH-003**: All list/search endpoints are paginated.

## 28. Media/File Requirements
- **FR-MEDIA-001**: Product/Variant media metadata (URL, alt text, ordering) is stored in PostgreSQL; binary content is stored in object storage.
- **FR-MEDIA-002**: File uploads are validated for type/size before accepted references are created.

## 29. Data Integrity Requirements
- **NFR-DATA-002**: Foreign keys enforce referential integrity between Orders, Order Items, Products, and Sellers.
- **NFR-DATA-003**: Historical Order Item snapshots must never change when the referenced Product/Variant is later edited or deleted.

## 30. Concurrency Requirements
- **NFR-CONC-001**: Inventory decrement at checkout must use an atomic, race-safe strategy (optimistic or pessimistic locking / atomic SQL update).
- **NFR-CONC-002**: Coupon usage-limit enforcement must be race-safe under concurrent redemption.
- **NFR-CONC-003**: Payment webhook handlers must be idempotent under duplicate/out-of-order delivery.
- **NFR-CONC-004**: Payout processing must not allow double-spending of the same Seller Balance.

## 31. Security Requirements
- **NFR-SEC-001**: Passwords hashed with a modern adaptive hash function; never stored/logged in plaintext.
- **NFR-SEC-002**: JWT access tokens are short-lived; refresh tokens are revocable and rotated.
- **NFR-SEC-003**: All mutating endpoints validate input server-side regardless of client-side validation.
- **NFR-SEC-004**: Parameterized queries/ORM usage prevent SQL injection.
- **NFR-SEC-005**: File uploads are restricted by type/size and stored outside the web root, referenced via signed/controlled URLs.
- **NFR-SEC-006**: Sensitive mutation endpoints (login, password reset, payout requests) are rate-limited.

## 32. Observability Requirements
- **NFR-OBS-001**: Structured logs include a correlation/request ID traceable across a request's lifecycle.
- **NFR-OBS-002**: Health check endpoints expose liveness/readiness.
- **NFR-OBS-003**: Key business metrics (orders/min, payment success rate, payout failures) are exposed for monitoring.

## 33. Performance Requirements
- **NFR-PERF-002**: Category/product listing queries must be indexed to avoid full table scans at expected data volumes.

## 34. Availability/Reliability Requirements
- **NFR-AVAIL-002**: Background job failures (e.g., email send) must be retryable without corrupting the originating business transaction.

## 35. Audit/History Requirements
- **NFR-AUDIT-001**: Seller Application decisions, Admin suspension actions, and Payout state changes are recorded with actor and timestamp.

## 36. Assumptions and Constraints
- Single currency, single active return policy version at a time.
- One payment provider abstraction implemented at MVP; interface allows future providers.
- Redis, message brokers, and dedicated search engines are explicitly out of MVP scope (see PRD §13, Architecture §15).
