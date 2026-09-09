# docs/API_CONTRACT.md — API Contract

## 1. API Conventions
JSON request/response bodies (`Content-Type: application/json`). Timestamps are ISO-8601 UTC. IDs are UUIDs (string). All monetary amounts are strings representing decimal values (e.g., `"19.99"`) to avoid float precision issues on the wire.

## 2. Base URL Convention
`https://api.marketplace.example/v1/...` — all resource paths below are relative to this base.

## 3. Authentication
`Authorization: Bearer <access_token>` (JWT). Guest endpoints omit this header; guest cart interactions identify the cart via `X-Guest-Session` header or cookie. After guest checkout, unauthenticated retrieval of orders and related resources uses the signed `X-Guest-Lookup-Token` header.

## 4. Authorization
Endpoints marked **Role: Customer/Seller/Admin** require the corresponding capability claim in the JWT. Ownership-scoped endpoints (e.g., a Seller's own product) additionally verify the resource belongs to the caller.

## 5. Error Format
```json
{
  \"error\": {
    \"code\": \"INSUFFICIENT_STOCK\",
    \"message\": \"Requested quantity exceeds available stock.\",
    \"details\": [{ \"field\": \"items[0].quantity\", \"issue\": \"max_available=2\" }],
    \"correlationId\": \"b3e1f7b2-...\"
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
| POST | `/auth/password-reset/request` | Request password reset token / link | None | — |
| POST | `/auth/password-reset/confirm` | Reset password using verified token | None | — |

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

**POST /auth/password-reset/request**
Request:
```json
{ "email": "user@example.com" }
```
Response `200`:
```json
{ "message": "If the email is registered, a password reset link has been dispatched." }
```

**POST /auth/password-reset/confirm**
Request:
```json
{ "token": "reset-token-xyz", "newPassword": "NewSecurePassword123!" }
```
Response `200`:
```json
{ "message": "Password successfully reset. Please log in with your new credentials." }
```

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
| GET | `/orders/{id}` | Order detail (incl. seller orders) | Bearer or `X-Guest-Lookup-Token` | — |
| POST | `/orders/{id}/seller-orders/{sellerOrderId}/cancel` | Cancel eligible seller order | Bearer or `X-Guest-Lookup-Token` | Customer/Guest owner |

**POST /orders/checkout** (registered customer)
Headers: `Idempotency-Key: <uuid>`
```json
{
  "shippingAddressId": "addr-uuid",
  "paymentMethod": "ONLINE",
  "couponCode": "SAVE10"
}
```
Response `201`:
```json
{
  "customerOrderId": "order-uuid",
  "status": "PENDING_PAYMENT",
  "sellerOrders": [
    {
      "sellerOrderId": "so-uuid-1",
      "sellerId": "seller-uuid-1",
      "items": [
        {
          "orderItemId": "oi-uuid-1",
          "productName": "Wireless Mouse",
          "variantDescriptor": "Black",
          "unitPrice": "24.99",
          "quantity": 2
        }
      ],
      "subtotal": "49.98"
    }
  ],
  "totalAmount": "49.98"
}
```

**POST /orders/checkout** (guest)
Headers: `Idempotency-Key: <uuid>`, `X-Guest-Session: <session-token>`
```json
{
  "guest": { "name": "Jane Doe", "email": "jane@example.com", "phone": "+1..." },
  "shippingAddress": { "line1": "123 Main St", "city": "Metropolis", "region": "NY", "postalCode": "10001", "country": "US" },
  "paymentMethod": "COD"
}
```
Response `201`:
```json
{
  "customerOrderId": "order-uuid",
  "status": "PLACED",
  "guestLookupToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "sellerOrders": [
    {
      "sellerOrderId": "so-uuid-1",
      "sellerId": "seller-uuid-1",
      "items": [
        {
          "orderItemId": "oi-uuid-1",
          "productName": "Wireless Mouse",
          "variantDescriptor": "Black",
          "unitPrice": "24.99",
          "quantity": 2
        }
      ],
      "subtotal": "49.98"
    }
  ],
  "totalAmount": "49.98"
}
```

**POST /orders/{id}/seller-orders/{sellerOrderId}/cancel**
Request:
```json
{ "reason": "Item no longer needed" }
```
Response `200`:
```json
{
  "sellerOrderId": "so-uuid-1",
  "cancellationStatus": "CANCELLED",
  "reason": "Item no longer needed",
  "cancelledAt": "2026-09-09T22:00:00Z"
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

**POST /seller/orders/{id}/ship**
Request:
```json
{
  "trackingNumber": "TRK-987654321",
  "carrier": "DHL"
}
```
Response `200`:
```json
{
  "sellerOrderId": "so-uuid-1",
  "fulfillmentStatus": "SHIPPED",
  "trackingNumber": "TRK-987654321",
  "shippedAt": "2026-09-09T22:00:00Z"
}
```

**POST /seller/orders/{id}/deliver**
Request:
```json
{
  "notes": "Delivered and signed by recipient"
}
```
Response `200`:
```json
{
  "sellerOrderId": "so-uuid-1",
  "fulfillmentStatus": "DELIVERED",
  "deliveredAt": "2026-09-09T22:00:00Z"
}
```

### Payments
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/orders/{id}/payments/intent` | Create online payment intent | Bearer or `X-Guest-Lookup-Token` | Customer/Guest owner |
| POST | `/webhooks/payments` | Provider webhook (payment confirmed/failed) | Provider signature | — |
| GET | `/orders/{id}/payments` | View payment(s) for order | Bearer or `X-Guest-Lookup-Token` | Owner |

**POST /webhooks/payments**
```json
{ "providerEventId": "evt_123", "type": "payment.succeeded", "providerReference": "pi_123", "amount": "49.98" }
```
Response `200` always (even for duplicates); duplicate `providerEventId` is a no-op. Business rule: signature/HMAC verification required before processing.

### Refunds
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/orders/{id}/refunds` | View refunds for an order | Bearer or `X-Guest-Lookup-Token` | Owner |
| POST | `/admin/refunds/{id}/reprocess` | Retry a failed refund | Bearer | Admin |

### Returns
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| POST | `/seller-orders/{id}/returns` | Request return | Bearer or `X-Guest-Lookup-Token` | Owner |
| GET | `/seller/returns` | List returns for seller's orders | Bearer | Seller |
| POST | `/seller/returns/{id}/approve` | Approve return | Bearer | Seller (owner) or Admin |
| POST | `/seller/returns/{id}/reject` | Reject return | Bearer | Seller (owner) or Admin |

**POST /seller-orders/{id}/returns**
```json
{
  "items": [
    { "orderItemId": "oi-uuid-1", "quantity": 1 }
  ],
  "reason": "Item arrived damaged"
}
```
Response `201`:
```json
{
  "returnRequestId": "ret-uuid-1",
  "sellerOrderId": "so-uuid-1",
  "status": "PENDING",
  "items": [
    { "orderItemId": "oi-uuid-1", "quantity": 1 }
  ],
  "reason": "Item arrived damaged",
  "requestedAt": "2026-09-09T22:00:00Z"
}
```

**POST /seller/returns/{id}/approve**
Request:
```json
{ "notes": "Items received in original box and inspected" }
```
Response `200`:
```json
{
  "returnRequestId": "ret-uuid-1",
  "status": "APPROVED",
  "refundId": "ref-uuid-1",
  "refundAmount": "24.99",
  "decidedAt": "2026-09-09T22:00:00Z"
}
```

**POST /seller/returns/{id}/reject**
Request:
```json
{ "rejectionReason": "Item was opened and damaged after delivery" }
```
Response `200`:
```json
{
  "returnRequestId": "ret-uuid-1",
  "status": "REJECTED",
  "rejectionReason": "Item was opened and damaged after delivery",
  "decidedAt": "2026-09-09T22:00:00Z"
}
```

### Shipping
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/seller-orders/{id}/shipment` | View shipment status | Bearer or `X-Guest-Lookup-Token` | Owner |

### Coupons
| Method | Path | Purpose | Auth | Role |
|---|---|---|---|---|
| GET | `/coupons/{code}/validate` | Check coupon validity (pre-checkout UX) | Optional | — |
| POST | `/admin/coupons` | Create coupon | Bearer | Admin |
| PATCH | `/admin/coupons/{id}` | Update/deactivate coupon | Bearer | Admin |

**GET /coupons/{code}/validate**
Response `200`:
```json
{
  "valid": true,
  "code": "SAVE10",
  "discountType": "PERCENTAGE",
  "discountValue": "10.00",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

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
| GET | `/admin/users` | List platform users with filters | Bearer | Admin |
| POST | `/admin/users/{id}/suspend` | Suspend user | Bearer | Admin |
| POST | `/admin/users/{id}/reactivate` | Reactivate user | Bearer | Admin |
| GET | `/admin/products` | List all products across sellers with moderation status filter | Bearer | Admin |
| GET | `/admin/payouts` | List all payout requests (filterable by status) | Bearer | Admin |
| GET | `/admin/return-policy` | View current return policy | Bearer | Admin |
| PATCH | `/admin/return-policy` | Update return policy (window/eligibility) | Bearer | Admin |
| GET | `/admin/overview` | Aggregated operational metrics | Bearer | Admin |

**GET /admin/return-policy**
Response `200`:
```json
{
  \"returnWindowDays\": 14,
  \"policyDescription\": \"Returns accepted within 14 calendar days of confirmed delivery.\"
}
```

**PATCH /admin/return-policy**
Request:
```json
{
  \"returnWindowDays\": 30,
  \"policyDescription\": \"Updated return window to 30 days for holiday season.\"
}
```
Response `200`:
```json
{
  \"returnWindowDays\": 30,
  \"policyDescription\": \"Updated return window to 30 days for holiday season.\",
  \"updatedAt\": \"2026-09-09T22:00:00Z\"
}
```
