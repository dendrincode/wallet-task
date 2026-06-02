# Wallet Service — Execution Scenario

Σενάριο πλήρους εκτέλεσης που καλύπτει δημιουργία wallet, idempotency, pessimistic locking, freeze/close lifecycle και error handling.

**Χρήστης:** Alice  
**userId:** `550e8400-e29b-41d4-a716-446655440001`

---

## Βήμα 1 — Πρώτο Deposit (Αυτόματη Δημιουργία Wallet)

Η Alice κάνει deposit €100. Το wallet **δεν υπάρχει ακόμα**.

```http
POST /wallets/550e8400-e29b-41d4-a716-446655440001/deposit
Content-Type: application/json

{
  "amount": 100.00,
  "idempotencyKey": "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa",
  "description": "Primary wallet"
}
```

**Τι γίνεται εσωτερικά:**
1. Status check → `findById()` → δεν βρίσκει wallet → παραλείπεται
2. Idempotency check → `findByIdempotencyKey()` → δεν βρίσκει → νέα συναλλαγή
3. Δημιουργείται νέο `Wallet` με `balance=0`, `status=ACTIVE`, `currency=EUR`, `description="Primary wallet"`
4. `balance = 0 + 100 = 100`, `totalDeposited = 100`
5. Αποθηκεύεται `Transaction(type=DEPOSIT, amount=100, balanceAfter=100, idempotencyKey=aaa...)`

```json
{ "status": "success", "newBalance": 100.00, "message": "Deposit completed" }
```

---

## Βήμα 2 — Retry του ίδιου Deposit (Idempotency)

Το δίκτυο "κόπηκε" και ο client ξαναστέλνει **ακριβώς το ίδιο request** με το ίδιο idempotency key.

```http
POST /wallets/550e8400-e29b-41d4-a716-446655440001/deposit
Content-Type: application/json

{
  "amount": 100.00,
  "idempotencyKey": "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"
}
```

**Τι γίνεται εσωτερικά:**
1. Status check → wallet υπάρχει, `status=ACTIVE` → συνεχίζει
2. Idempotency check → βρίσκει υπάρχουσα `Transaction` με αυτό το key → επιστρέφει αμέσως το αποθηκευμένο `balanceAfter=100`
3. **Κανένα write στη βάση**

```json
{ "status": "success", "newBalance": 100.00, "message": "Deposit already processed" }
```

> Το υπόλοιπο παρέμεινε 100, όχι 200.

---

## Βήμα 3 — Trade Επιτυχές

Η Alice κάνει trade €30.

```http
POST /wallets/550e8400-e29b-41d4-a716-446655440001/trade
Content-Type: application/json

{
  "amount": 30.00,
  "idempotencyKey": "bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb"
}
```

**Τι γίνεται εσωτερικά:**
1. Status check → `ACTIVE` → συνεχίζει
2. Idempotency check → νέο key → συνεχίζει
3. `findByUserIdForUpdate()` → αποκτά **PESSIMISTIC_WRITE lock** στη γραμμή του wallet
4. `newBalance = 100 - 30 = 70` → ≥ 0 → επιτρέπεται
5. `balance=70`, `totalTraded=30`
6. Αποθηκεύεται Transaction, αποδεσμεύεται το lock

```json
{ "status": "success", "newBalance": 70.00, "message": "Trade completed" }
```

---

## Βήμα 4 — Trade Αποτυχημένο (Ανεπαρκές Υπόλοιπο)

Προσπαθεί να κάνει trade €200 ενώ έχει μόνο €70.

```http
POST /wallets/550e8400-e29b-41d4-a716-446655440001/trade
Content-Type: application/json

{
  "amount": 200.00,
  "idempotencyKey": "cccccccc-cccc-4ccc-cccc-cccccccccccc"
}
```

**Τι γίνεται εσωτερικά:**
1. Status check → `ACTIVE` → συνεχίζει
2. Pessimistic lock αποκτάται
3. `newBalance = 70 - 200 = -130` → **< 0 → απόρριψη**
4. Κανένα write, το lock αποδεσμεύεται

```json
{
  "status": "error",
  "newBalance": 70.00,
  "message": "Insufficient balance for trade. Required: 200.00, Available: 70.00"
}
```

---

## Βήμα 5 — Freeze του Wallet

Ο διαχειριστής παγώνει το wallet λόγω ύποπτης δραστηριότητας.

```http
POST /wallets/550e8400-e29b-41d4-a716-446655440001/freeze
```

**Τι γίνεται εσωτερικά:**
1. `findById()` → βρίσκει wallet με `status=ACTIVE`
2. Δεν είναι `CLOSED` ή `FROZEN` → επιτρέπεται
3. `wallet.status = FROZEN`, αποθηκεύεται

```json
{ "userId": "550e8400-e29b-41d4-a716-446655440001", "balance": 70.00, "status": "FROZEN" }
```

---

## Βήμα 6 — Απόπειρα Συναλλαγής σε Frozen Wallet

Η Alice προσπαθεί να κάνει deposit ενώ το wallet είναι frozen.

```http
POST /wallets/550e8400-e29b-41d4-a716-446655440001/deposit
Content-Type: application/json

{
  "amount": 50.00,
  "idempotencyKey": "dddddddd-dddd-4ddd-dddd-dddddddddddd"
}
```

**Τι γίνεται εσωτερικά:**
1. Status check → `findById()` → βρίσκει wallet με `status=FROZEN`
2. Πετάει `WalletNotActiveException` **αμέσως** — δεν φτάνει καν στον idempotency check
3. `GlobalExceptionHandler` → HTTP 409 Conflict

```json
{
  "error": "Wallet Not Active",
  "message": "Wallet is FROZEN and cannot accept transactions",
  "errorCode": "WALLET_NOT_ACTIVE",
  "status": 409
}
```

---

## Βήμα 7 — Κλείσιμο του Wallet

```http
POST /wallets/550e8400-e29b-41d4-a716-446655440001/close
```

**Τι γίνεται εσωτερικά:**
1. `findById()` → `status=FROZEN` → δεν είναι `CLOSED` → επιτρέπεται
2. Η μετάβαση `FROZEN → CLOSED` είναι έγκυρη
3. `wallet.status = CLOSED`, αποθηκεύεται

```json
{ "userId": "550e8400-e29b-41d4-a716-446655440001", "balance": 70.00, "status": "CLOSED" }
```

---

## Βήμα 8 — Προβολή Ιστορικού

```http
GET /wallets/550e8400-e29b-41d4-a716-446655440001
```

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "balance": 70.00,
  "status": "CLOSED",
  "totalDeposited": 100.00,
  "totalTraded": 30.00,
  "description": "Primary wallet",
  "transactions": [
    { "type": "DEPOSIT", "amount": 100.00, "balanceAfter": 100.00 },
    { "type": "TRADE",   "amount": 30.00,  "balanceAfter": 70.00  }
  ]
}
```

> Το idempotent retry (Βήμα 2) και το αποτυχημένο trade (Βήμα 4) **δεν εμφανίζονται** στο ιστορικό — μόνο οι πραγματικές αλλαγές υπολοίπου καταγράφονται ως Transactions.

---

## Σύνοψη

| Βήμα | Λειτουργία | Αποτέλεσμα |
|---|---|---|
| 1 | Deposit €100 (νέο wallet) | Δημιουργία + balance 100 |
| 2 | Retry ίδιου deposit | Idempotency → balance παραμένει 100 |
| 3 | Trade €30 | Pessimistic lock → balance 70 |
| 4 | Trade €200 | Insufficient balance → απόρριψη |
| 5 | Freeze | status → FROZEN |
| 6 | Deposit σε frozen | 409 Conflict αμέσως |
| 7 | Close | status → CLOSED (από FROZEN) |
| 8 | GET history | Μόνο 2 πραγματικές transactions |
