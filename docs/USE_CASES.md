# docs/USE_CASES.md — Use Cases

Use cases are grouped by actor. Detailed flows are provided for the most important/complex use cases; simpler ones are listed briefly.

---

## Guest

### UC-G-01: Browse Products (brief)
Browse catalog by category, paginated.

### UC-G-02: Search Products (brief)
Full-text search with filters/sorting/pagination.

### UC-G-03: View Product (brief)
View product detail including variants, media, rating summary.

### UC-G-04: Add Product to Cart (brief)
Add a variant to a session-based Cart without an account.

### UC-CHECKOUT-GUEST: Checkout as Guest
- **Actor**: Guest
- **Goal**: Complete a purchase without creating an account.
- **Preconditions**: Cart contains ≥1 item with sufficient stock.
- **Trigger**: Guest submits checkout with contact/shipping/payment info.
- **Main Flow**:
  1. Guest provides name, email, phone, shipping address, and payment method.
  2. System re-validates stock and price for each cart item.
  3. System creates one Customer Order (unauthenticated, keyed by guest contact info) split into per-Seller Orders.
  4. System reserves/decrements inventory atomically.
  5. Payment is processed (COD marks pending-collection; online payment creates a Payment Intent).
  6. Order confirmation notification is sent to guest email.
- **Alternative Flows**: Guest later registers and can optionally link past guest orders by verified email (post-MVP).
- **Exception Flows**: Insufficient stock on any item → checkout fails, item(s) flagged; payment failure → Order left in a failed/pending-payment state, inventory not permanently consumed.
- **Postconditions**: Order exists and is historically meaningful independent of any registered user.
- **Business Rules**: Guest checkout requires all four contact/shipping/payment fields; no account is created implicitly.

---

## Customer

Brief use cases: Register, Login, Manage Profile, Manage Addresses, Manage Wishlist, View Notifications, Track Order.

### UC-C-01: Manage Cart (brief)
Add/update/remove items across multiple sellers in one Cart.

### UC-C-02: Checkout (Registered)
- **Actor**: Customer
- **Goal**: Purchase items in Cart.
- **Preconditions**: Authenticated; Cart non-empty.
- **Trigger**: Customer submits checkout.
- **Main Flow**:
  1. Customer selects/confirms shipping address and payment method.
  2. Customer optionally applies a Coupon; system validates atomically (expiry, usage limit, eligibility).
  3. System re-validates stock/price, computes totals (subtotal, discount, shipping, total) per Seller Order.
  4. System creates Customer Order + Seller Orders, decrements inventory atomically.
  5. Payment processed; on success, Order moves to Confirmed; notifications sent.
- **Alternative Flows**: Coupon invalid/expired → checkout proceeds without discount after Customer confirmation, or is rejected per current business rule (assumption: rejected with clear error).
- **Exception Flows**: Stock changed since cart-add → affected item(s) reported, Customer must adjust quantities; payment declined → Order marked Payment Failed, retry allowed.
- **Postconditions**: Coupon usage count incremented only on successful checkout.
- **Business Rules**: One Coupon per Order (assumption); Coupon usage increment and inventory decrement occur in the same atomic checkout transaction boundary.

### UC-C-03: Cancel Eligible Order
- **Actor**: Customer
- **Goal**: Cancel a Seller Order before it ships.
- **Preconditions**: Seller Order state is Confirmed/Processing (not yet Shipped).
- **Main Flow**: Customer requests cancellation → system validates state → inventory restocked → payment reversed/refunded if already captured → notification sent.
- **Exception Flows**: Seller Order already Shipped → cancellation rejected; Customer directed to Return flow instead.
- **Postconditions**: Seller Order state = Cancelled; other Seller Orders in the same Customer Order are unaffected.

### UC-C-04: Request Return
- **Actor**: Customer
- **Goal**: Return delivered item(s).
- **Preconditions**: Seller Order Item(s) in Delivered state, within return window.
- **Main Flow**: Customer selects item(s)/quantity → submits Return Request (Pending Review) → Seller/Admin reviews → Approved/Rejected → if Approved, Refund created and inventory restocked per policy → notification sent.
- **Business Rules**: Return window and eligibility are governed by the single marketplace-wide policy.

### UC-C-05: Review Purchased Product
- **Actor**: Customer
- **Goal**: Leave a rating/review for a purchased product.
- **Preconditions**: Customer has a Delivered Order Item for the Product (verified purchase); no existing review for that purchase.
- **Main Flow**: Customer submits rating + text → system validates verified purchase and duplicate rule → review published.
- **Exception Flows**: No verified purchase found → rejected with error.

### UC-C-06: Apply to Become Seller (brief)
Submit Seller Application; status becomes Pending until Admin decision.

---

## Seller

### UC-S-01: Submit Seller Application (brief)
Authenticated Customer submits application (business info); status Pending.

### UC-S-02: Manage Products & Variants
- **Actor**: Seller
- **Goal**: Create/update own Products and Variants.
- **Preconditions**: Seller account Approved and not Suspended.
- **Main Flow**: Seller creates Product (name, description, category, media) → adds Variants (attributes, price, stock) → publishes immediately (no Admin approval required).
- **Exception Flows**: Attempt to modify a Product owned by another Seller → rejected (403).
- **Business Rules**: Publication does not require Admin pre-approval; Admin may moderate post-publication.

### UC-S-03: Manage Inventory / Update Prices (brief)
Seller adjusts stock and price per Variant; changes are audited.

### UC-S-04: View & Fulfill Seller Orders
- **Actor**: Seller
- **Goal**: Process orders containing their products.
- **Main Flow**: Seller views own Seller Orders → updates Shipment status (Processing → Shipped → Delivered) → system notifies Customer at each transition.
- **Exception Flows**: Attempt to fulfill another Seller's order → rejected.

### UC-S-05: Manage Returns (brief)
Seller reviews Return Requests for their Seller Orders within the unified policy; recommends Approve/Reject (final Admin escalation possible).

### UC-S-06: View Balance & Request Payout
- **Actor**: Seller
- **Goal**: Withdraw available earnings.
- **Preconditions**: Available Balance > 0.
- **Main Flow**: Seller requests Payout up to available Balance → Payout created (Requested) → async processing → Completed (Balance reduced permanently) or Failed (amount returned to Balance) → notification sent.
- **Exception Flows**: Requested amount exceeds available Balance → rejected.

### UC-S-07: View Payout History (brief)
List past Payouts with status and timestamps.

---

## Admin

### UC-A-01: Review & Decide Seller Applications
- **Actor**: Admin
- **Goal**: Approve or reject a pending Seller Application.
- **Main Flow**: Admin views Pending applications → Approves (User gains Seller capability) or Rejects (with reason) → notification sent to applicant.

### UC-A-02: Suspend/Reactivate Users & Sellers
- **Actor**: Admin
- **Goal**: Restrict a User's or Seller's platform capability without destroying history.
- **Main Flow**: Admin suspends User or Seller independently → affected account loses relevant capability (login restricted for User suspension; selling restricted for Seller suspension) → historical Orders/Payments/Payouts/Reviews remain unchanged and queryable.

### UC-A-03: Moderate Products (brief)
Admin hides/removes a Product regardless of owning Seller, with reason logged.

### UC-A-04: Manage Categories (brief)
Admin creates/edits/deletes hierarchical Categories.

### UC-A-05: Manage Coupons (brief)
Admin creates/edits/deactivates Coupons.

### UC-A-06: Manage Marketplace Return Policy (brief)
Admin configures the single marketplace-wide return window/eligibility rules.

### UC-A-07: Review Operational Information (brief)
Admin views aggregated operational data (orders, payments, payouts) for oversight.

### UC-A-08: Manage Payout Operations
- **Actor**: Admin
- **Goal**: Oversee Payout processing (e.g., mark Processing → Completed/Failed if manual step is required, or review failures).
- **Main Flow**: Admin views Payout queue → confirms/executes processing outcome → System updates Seller Balance accordingly → notification sent to Seller.
