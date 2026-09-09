# docs/API_CONTRACT.md — API Contract

## 1. API Conventions
JSON request/response bodies (`Content-Type: application/json`). Timestamps are ISO-8601 UTC. IDs are UUIDs (string). All monetary amounts are strings representing decimal values (e.g., `"19.99"`) to avoid float precision issues on the wire.

## 2. Base URL Convention
`https://api.marketplace.example/v1/...` — all resource paths below are relative to this base.

## 3. Authentication
`Authorization: Bearer <access_token>` (JWT). Guest endpoints omit this header; guest checkout instead identifies the cart via `X-Guest-Session` header or a guest session cookie.

## 4. Authorization
Endpoints marked **Role: Customer/Seller/Admin** require the corresponding capability claim in the JWT. Ownership-scoped endpoints (e.g., a Seller's own product) additionally verify the resource belongs to the caller.

## 5. Error Format
```json
{
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "Requested quantity exceeds available stock.",
    "details": [{ "field": "items[0].quantity", "issue": "max_available=2" }],
    "correlationId": "b3e1f7b2-..."
  }
}
```

## 6. Pagination
Query params: `page` (0-based), `size` (default 20, max 100). Response envelope:
```json
{ "content": [ /* items */ ], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
```

## 7. Filtering
Resource-specific query params, e.g. `?categoryId=...&minPrice=...&maxPrice=...&sellerId=...&minRating=...`.

## 8. Sorting
`?sort=price,asc` or `?sort=createdAt,desc`; multiple `sort` params allowed, applied in order.

## 9. Search
`?q=<query>` on list endpoints that support full-text search (e.g., `GET /products`), ranked by relevance unless overridden by `sort`.

## 10. Idempotency
Mutating financial/checkout endpoints accept an `Idempotency-Key` header; the server returns the original response for a repeated key within a bounded window instead of re-executing the operation.

## 11. HTTP Status Conventions
`200` success (read/update), `201` created, `204` no content (delete), `400` validation error, `401` unauthenticated, `403` unauthorized/ownership violation, `404` not found, `409` conflict (e.g., insufficient stock, coupon limit reached), `422` business rule violation, `429` rate limited, `500` unexpected error.

---

## 12. Resource Endpoints

### Auth
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/auth/register` | Register a new User | None | — |
| POST | `/auth/login` | Login, issue access+refresh tokens | None | — |
| POST | `/auth/refresh` | Exchange refresh token for new access token | None (refresh token in body) | — |
| POST | `/auth/logout` | Revoke refresh token | Bearer | Any |

**POST /auth/login**
Request:
```json
{ "email": "user@example.com", "password": "secret" }
```
Response `200`:
```json
{ "accessToken": "...", "refreshToken": "...", "expiresIn": 900 }
```
Business rules: rate-limited; generic error on bad credentials (no user-existence leak).

### Users
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/users/me` | Get own profile | Bearer | Any |
| PATCH | `/users/me` | Update own profile | Bearer | Any |

### Addresses
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/users/me/addresses` | List own addresses | Bearer | Customer |
| POST | `/users/me/addresses` | Add address | Bearer | Customer |
| PATCH | `/users/me/addresses/{id}` | Update own address | Bearer | Customer |
| DELETE | `/users/me/addresses/{id}` | Remove own address | Bearer | Customer |

### Seller Applications
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/seller-applications` | Submit application | Bearer | Customer |
| GET | `/seller-applications/me` | View own application status | Bearer | Customer |
| GET | `/admin/seller-applications?status=PENDING` | List applications | Bearer | Admin |
| POST | `/admin/seller-applications/{id}/approve` | Approve application | Bearer | Admin |
| POST | `/admin/seller-applications/{id}/reject` | Reject application | Bearer | Admin |

**POST /seller-applications**
```json
{ "businessName": "Acme Gadgets", "notes": "Selling electronics accessories" }
```
Response `201`: application object with `status: "PENDING"`.

### Sellers
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/sellers/{id}` | Public seller profile | None | — |
| POST | `/admin/sellers/{id}/suspend` | Suspend seller | Bearer | Admin |
| POST | `/admin/sellers/{id}/reactivate` | Reactivate seller | Bearer | Admin |

### Products
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/products` | Browse/search/filter/sort products | None | — |
| GET | `/products/{id}` | Product detail (with variants, media, rating summary) | None | — |
| POST | `/seller/products` | Create product | Bearer | Seller |
| PATCH | `/seller/products/{id}` | Update own product | Bearer | Seller (owner) |
| DELETE | `/seller/products/{id}` | Soft-remove own product | Bearer | Seller (owner) |
| POST | `/admin/products/{id}/hide` | Moderate: hide | Bearer | Admin |
| POST | `/admin/products/{id}/remove` | Moderate: remove | Bearer | Admin |

**POST /seller/products**
```json
{
  "name": "Wireless Mouse",
  "description": "Ergonomic wireless mouse",
  "categoryIds": ["cat-uuid-1"]
}
```
Response `201`: product object, `status: "PUBLISHED"` (no admin approval required).

### Product Variants
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/seller/products/{productId}/variants` | Add variant | Bearer | Seller (owner) |
| PATCH | `/seller/variants/{id}` | Update price/stock/attributes | Bearer | Seller (owner) |
| DELETE | `/seller/variants/{id}` | Remove variant | Bearer | Seller (owner) |

**POST /seller/products/{productId}/variants**
```json
{ "sku": "WM-BLK", "attributes": { "color": "Black" }, "price": "24.99", "stockQuantity": 100 }
```
Response `201`: variant object.

### Categories
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/categories` | List category tree | None | — |
| GET | `/categories/{id}/products` | Products in category (incl. descendants) | None | — |
| POST | `/admin/categories` | Create category | Bearer | Admin |
| PATCH | `/admin/categories/{id}` | Update category | Bearer | Admin |
| DELETE | `/admin/categories/{id}` | Delete category | Bearer | Admin |

### Media
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/seller/products/{productId}/media` | Attach media reference | Bearer | Seller (owner) |
| DELETE | `/seller/media/{id}` | Remove media reference | Bearer | Seller (owner) |

**POST /seller/products/{productId}/media**
```json
{ "url": "https://cdn.example.com/img/abc.jpg", "altText": "Front view", "sortOrder": 0, "variantId": null }
```

### Cart
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/cart` | View current cart (guest or customer) | Optional | — |
| POST | `/cart/items` | Add item to cart | Optional | — |
| PATCH | `/cart/items/{id}` | Update quantity | Optional | — |
| DELETE | `/cart/items/{id}` | Remove item | Optional | — |

**POST /cart/items**
```json
{ "variantId": "variant-uuid", "quantity": 2 }
```
Response `409` if requested quantity exceeds current stock.

### Wishlist
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/wishlist` | View own wishlist | Bearer | Customer |
| POST | `/wishlist/items` | Add product | Bearer | Customer |
| DELETE | `/wishlist/items/{productId}` | Remove product | Bearer | Customer |

### Orders (Checkout)
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/orders/checkout` | Create Customer Order from Cart | Optional (guest or Customer) | — |
| GET | `/orders` | List own orders | Bearer | Customer |
| GET | `/orders/{id}` | Order detail (incl. seller orders) | Bearer or guest token | — |
| POST | `/orders/{id}/seller-orders/{sellerOrderId}/cancel` | Cancel eligible seller order | Bearer or guest token | Customer/Guest owner |

**POST /orders/checkout** (registered customer)
Headers: `Idempotency-Key: <uuid>`
```json
{
  "shippingAddressId": "addr-uuid",
  "paymentMethod": "ONLINE",
  "couponCode": "SAVE10"
}
```
**POST /orders/checkout** (guest)
```json
{
  "guest": { "name": "Jane Doe", "email": "jane@example.com", "phone": "+1..." },
  "shippingAddress": { "line1": "...", "city": "...", "region": "...", "postalCode": "...", "country": "EG" },
  "paymentMethod": "COD"
}
```
Response `201`:
```json
{
  "customerOrderId": "order-uuid",
  "status": "PENDING_PAYMENT",
  "sellerOrders": [
    { "sellerOrderId": "so-uuid-1", "sellerId": "seller-uuid-1", "items": [ { "productName": "Wireless Mouse", "variantDescriptor": "Black", "unitPrice": "24.99", "quantity": 2 } ], "subtotal": "49.98" }
  ],
  "totalAmount": "49.98"
}
```
Important business rules: stock and coupon validated atomically at commit; `409 INSUFFICIENT_STOCK` or `409 COUPON_LIMIT_REACHED` on conflict; repeated calls with the same `Idempotency-Key` return the original result.

### Seller Orders
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/seller/orders` | List seller's own seller-orders | Bearer | Seller |
| GET | `/seller/orders/{id}` | Seller order detail | Bearer | Seller (owner) |
| POST | `/seller/orders/{id}/ship` | Mark shipped (with tracking info) | Bearer | Seller (owner) |
| POST | `/seller/orders/{id}/deliver` | Mark delivered | Bearer | Seller (owner) |

### Payments
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/orders/{id}/payments/intent` | Create online payment intent | Bearer/guest token | Customer/Guest owner |
| POST | `/webhooks/payments` | Provider webhook (payment confirmed/failed) | Provider signature | — |
| GET | `/orders/{id}/payments` | View payment(s) for order | Bearer/guest token | Owner |

**POST /webhooks/payments**
```json
{ "providerEventId": "evt_123", "type": "payment.succeeded", "providerReference": "pi_123", "amount": "49.98" }
```
Response `200` always (even for duplicates); duplicate `providerEventId` is a no-op. Business rule: signature/HMAC verification required before processing.

### Refunds
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/orders/{id}/refunds` | View refunds for an order | Bearer/guest token | Owner |
| POST | `/admin/refunds/{id}/reprocess` | Retry a failed refund | Bearer | Admin |

### Returns
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/seller-orders/{id}/returns` | Request return | Bearer/guest token | Owner |
| GET | `/seller/returns` | List returns for seller's orders | Bearer | Seller |
| POST | `/seller/returns/{id}/approve` | Approve return | Bearer | Seller (owner) or Admin |
| POST | `/seller/returns/{id}/reject` | Reject return | Bearer | Seller (owner) or Admin |

**POST /seller-orders/{id}/returns**
```json
{ "items": [ { "orderItemId": "oi-uuid", "quantity": 1 } ], "reason": "Item arrived damaged" }
```

### Shipping
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/seller-orders/{id}/shipment` | View shipment status | Bearer/guest token | Owner |

### Coupons
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/coupons/{code}/validate` | Check coupon validity (pre-checkout UX) | Optional | — |
| POST | `/admin/coupons` | Create coupon | Bearer | Admin |
| PATCH | `/admin/coupons/{id}` | Update/deactivate coupon | Bearer | Admin |

**POST /admin/coupons**
```json
{ "code": "SAVE10", "discountType": "PERCENTAGE", "discountValue": "10", "expiresAt": "2026-12-31T23:59:59Z", "usageLimit": 500 }
```

### Reviews
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/products/{id}/reviews` | Submit review | Bearer | Customer (verified purchase) |
| GET | `/products/{id}/reviews` | List product reviews | None | — |
| DELETE | `/admin/reviews/{id}` | Moderate: remove review | Bearer | Admin |

**POST /products/{id}/reviews**
```json
{ "orderItemId": "oi-uuid", "rating": 5, "comment": "Great product!" }
```
Response `422 NO_VERIFIED_PURCHASE` if the referenced `orderItemId` doesn't prove a delivered purchase of this product by the caller.

### Notifications
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/notifications` | List own notifications | Bearer | Any |
| POST | `/notifications/{id}/read` | Mark read | Bearer | Any (owner) |

### Seller Balance
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/seller/balance` | View current balance + recent ledger entries | Bearer | Seller |

### Payouts
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/seller/payouts` | Request payout | Bearer | Seller |
| GET | `/seller/payouts` | List own payout history | Bearer | Seller |
| POST | `/admin/payouts/{id}/complete` | Mark payout completed | Bearer | Admin |
| POST | `/admin/payouts/{id}/fail` | Mark payout failed (credits balance back) | Bearer | Admin |

**POST /seller/payouts**
```json
{ "amount": "150.00" }
```
Response `409 INSUFFICIENT_BALANCE` if amount exceeds available balance.

### Admin Operations
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/admin/users/{id}/suspend` | Suspend user | Bearer | Admin |
| POST | `/admin/users/{id}/reactivate` | Reactivate user | Bearer | Admin |
| GET | `/admin/return-policy` | View current return policy | Bearer | Admin |
| PATCH | `/admin/return-policy` | Update return policy (window/eligibility) | Bearer | Admin |
| GET | `/admin/overview` | Aggregated operational metrics | Bearer | Admin |
