# docs/PRD.md — Product Requirements Document

## 1. Product Overview
A general-purpose, multi-vendor marketplace backend where independent Sellers list and sell Products to Customers, with the platform (Marketplace) acting as an intermediary that takes a commission, orchestrates checkout/fulfillment across multiple Sellers in a single Customer order, and provides Admin oversight. The system is delivered backend-first (Java + Spring Boot + PostgreSQL); Customer, Seller, and Admin frontends are future consumers of the APIs.

## 2. Product Vision
Provide a lightweight, trustworthy foundation for a real multi-vendor marketplace that could grow into a production product, while intentionally exposing realistic backend engineering problems (inventory concurrency, payments, multi-party order splitting, payouts, search, security) so the system can double as a hands-on backend engineering lab.

## 3. Problem Statement
Independent sellers need a shared storefront and fulfillment/payment infrastructure without building their own e-commerce stack. Customers want a single, coherent shopping and checkout experience even when items come from different sellers. The platform needs to broker payments, commission, and payouts fairly and transparently while remaining simple enough for a small team (or solo developer) to build and operate.

## 4. Goals
- Enable multiple independent Sellers to list and manage Products under Admin oversight.
- Provide a unified Customer shopping experience (single cart, single checkout) that transparently splits into per-Seller fulfillment.
- Guarantee correct, race-free inventory and payment handling.
- Support both guest and registered-user purchasing.
- Provide a fair, transparent commission and payout model for Sellers.
- Keep the system's scope lean enough to implement and maintain as a solo/small-team modular monolith.

## 5. Non-Goals
- Not a clone of Amazon's full feature set (no recommendations engine, no advanced logistics network, no multi-warehouse optimization).
- Not building a customer-facing or admin-facing UI in this phase.
- Not building a specialized vertical marketplace (electronics-only, fashion-only, etc.) — the domain model stays generic.
- Not adopting microservices, Kafka, Elasticsearch, or Kubernetes at this stage — these are explicitly deferred (see Post-MVP/Evolution).
- Not building a fully independent per-seller return policy engine.

## 6. Target Users
- **Individual/small-business Sellers** who want to reach customers without building their own storefront.
- **Customers** (guest or registered) who want to buy from multiple sellers in one transaction.
- **Marketplace Admins/Operators** who moderate content, manage sellers, and oversee finances.

## 7. Actors
| Actor | Description |
|---|---|
| Guest | Unauthenticated visitor; can browse, search, and check out without an account. |
| Customer | Registered user who shops, reviews, manages wishlist/orders. |
| Seller | A Customer whose seller application was approved; manages products, inventory, orders, balance, payouts. |
| Admin | Marketplace operator; moderates users/sellers/products, manages categories/coupons/return policy, oversees payouts. |
| System (background) | Executes async jobs: emails, webhook processing, notification fan-out. |

## 8. Core Product Capabilities
1. Seller onboarding with Admin approval.
2. Product & variant catalog with hierarchical categories and media.
3. Inventory tracking with overselling prevention.
4. Multi-seller cart and unified checkout that splits into per-seller orders.
5. Guest and registered checkout.
6. Cash-on-delivery and online payments.
7. Seller- or marketplace-handled shipping.
8. Reviews gated by verified purchase.
9. Wishlist.
10. Coupons (percentage/fixed, expiry, usage limits).
11. Returns/refunds with a unified marketplace policy.
12. Commission-based revenue model with Seller balance and payout lifecycle.
13. In-app + email notifications.
14. Admin and Seller management APIs (no dedicated UI yet).
15. PostgreSQL full-text search with filtering/sorting/pagination.

## 9. Marketplace Business Model
The marketplace charges a configurable percentage commission on each Seller Order's sale value. On successful payment, the sale amount is conceptually split into Marketplace Commission and Seller Revenue; Seller Revenue accrues to the Seller's balance, from which the Seller can request payouts. Commission is configuration-driven (not hardcoded), and all money values use precise decimal arithmetic.

## 10. Major User Journeys
- **Guest purchase:** browse → search → add to cart → guest checkout (COD or online) → order confirmation.
- **Registered purchase:** login → build multi-seller cart → apply coupon → checkout → payment → track seller orders → request return if needed → review purchased items.
- **Seller onboarding & selling:** register as Customer → apply as Seller → Admin approves → create products/variants → manage inventory/price → fulfill seller orders → track balance → request payout.
- **Admin operations:** review seller applications → moderate products → manage categories/coupons → handle escalated returns → oversee payouts → suspend/reactivate accounts as needed.

## 11. Functional Scope
Covers: identity & auth, seller lifecycle, catalog (products/variants/categories/media), inventory, cart, checkout/orders (multi-seller split), payments (COD + online), shipping representation, coupons, reviews, wishlist, returns/refunds, commission, seller balance/payouts, notifications, admin moderation, search.

## 12. MVP Scope
- Auth (registration/login/JWT + refresh), RBAC (Customer/Seller/Admin).
- Seller application → Admin approval flow.
- Product/variant/category CRUD (Seller-owned), product media metadata.
- Inventory with atomic, race-safe stock decrement.
- Cart (multi-seller), guest + registered checkout.
- Order split into Seller Orders with historical item snapshot.
- COD payment; online payment with basic intent/confirmation/webhook flow.
- Basic shipping representation (marketplace-fulfilled or seller-fulfilled) with status tracking.
- Coupons (percentage/fixed, expiry, usage limits) applied safely at checkout.
- Reviews gated by verified purchase.
- Wishlist.
- Returns/refunds against a single marketplace-wide policy.
- Commission calculation, Seller balance ledger, payout request lifecycle.
- In-app + async email notifications for key events.
- Admin APIs for moderation, category/coupon management, user/seller suspension.
- PostgreSQL full-text search with filters/sort/pagination.

## 13. Post-MVP / Future Scope
- Seller/Admin/Customer frontends.
- Advanced promotions engine (bundles, tiered discounts, seller-specific promos).
- Per-seller custom return policies.
- Recommendation systems, personalization.
- Multi-currency, multi-region tax handling.
- Advanced seller analytics dashboards.

## 14. Important Business Rules
- A Seller application must be Admin-approved before selling capability is granted.
- A single Customer Order always decomposes into one or more Seller Orders.
- Products are editable after sale, but Order Items snapshot purchase-time name/variant/price/quantity.
- Reviews require a verified purchase of the specific product.
- Inventory must never go negative; concurrent purchase attempts on the last unit must resolve to exactly one winner.
- Commission percentage is configurable, not hardcoded.
- Payment, Seller Balance, and Payout are distinct domain concepts and are never merged.
- Suspending a User/Seller does not delete or alter historical Orders/Payments/Payouts/Reviews.
- Return/refund policy is marketplace-wide; Sellers cannot define independent policies.

## 15. Success Criteria
- A Customer can complete a multi-seller purchase (guest or registered) with correct per-seller order splitting and no overselling under concurrent load.
- A Seller can onboard, list products with variants, fulfill orders, and successfully request a payout reflecting correct commission deduction.
- An Admin can moderate sellers/products and manage categories/coupons without needing a dedicated frontend.
- The system remains a single deployable modular monolith with clear internal module boundaries.

## 16. Key Risks / Constraints
- Concurrency correctness for inventory, coupon usage, and duplicate payment webhooks is a core technical risk.
- Payment provider abstraction must avoid tight coupling to one vendor.
- Scope creep toward "full Amazon clone" is a constant risk and must be actively resisted.
- Solo/small-team maintainability constrains technology choices (no premature microservices/Kafka/Elasticsearch).

## 17. Assumptions
- Single marketplace currency at MVP (currency itself is stored, but multi-currency conversion is out of scope).
- Commission is a flat, globally configured percentage at MVP (per-seller/per-category commission tiers are a future evolution).
- Return window and eligibility rules are simple and centrally configured (e.g., N days post-delivery).
- Object storage for media is provider-agnostic (e.g., S3-compatible) and referenced by URL/key only.
- Online payment integration targets one representative provider abstraction (e.g., Stripe-like) behind a generic interface.
