# EZBuild Store

An online PC component store with:

- a filterable shop
- a manual PC builder with live compatibility checks
- an AI assistant that plans complete builds within a budget
- checkout with cash on delivery, SSLCommerz or Stripe
- an admin back office

---

## Contents

1. [Features](#features)
2. [Requirements](#requirements)
3. [Getting started](#getting-started)
4. [Configuration](#configuration)
5. [Demo accounts](#demo-accounts)
6. [Payments in test mode](#payments-in-test-mode)
7. [How the AI assistant works](#how-the-ai-assistant-works)
8. [Project structure](#project-structure)
9. [REST API](#rest-api)
10. [Testing](#testing)
11. [Catalogue data and images](#catalogue-data-and-images)
12. [Running with Docker](#running-with-docker)
13. [Deploying to production](#deploying-to-production)
14. [Troubleshooting](#troubleshooting)

---

## Features

**For customers**
- **Shop:**
  - filters for category, brand, price range, stock and discounts
  - spec filters such as socket, chipset, VRAM, GPU chip, RAM type, wattage and panel type
  - sorting, live search suggestions and shareable filter URLs
- **Product pages** with full specifications, reviews and ratings, a compare tool (up to 3 products) and a wishlist.
- **PC builder:** pick parts one by one with live compatibility checks on:
  - socket and memory type
  - form factor and GPU length
  - power supply headroom (based on real peak power draw)
  - cooler capacity
  - CPU/GPU balance

  Builds can be saved and shared via `/b/{code}`.
- **AI build assistant:** a floating chat on every page plus a full `/assistant` page.
  - Ask in English, Bangla or Banglish.
  - It returns up to three compatible builds (Value, Balanced and Performance) and explains them.
  - It handles follow-ups like "better CPU", "cheaper", "Intel instead" or "2TB storage".
  - It answers comparison questions ("is the 7600 better than the 12400F?").
- **Checkout:**
  - delivery zones: ৳60 inside Dhaka, ৳100 outside
  - payment by cash on delivery (+৳50), SSLCommerz (bKash, Nagad, cards) or Stripe (international cards)
  - totals are always calculated on the server
- **Accounts:**
  - sign up and sign in, with remember-me and password reset
  - saved addresses
  - order history, invoices and order tracking

**For staff and admins**
- Dashboard with sales figures.
- Product management:
  - image upload
  - discount percentages per product, or in bulk by selection, category or brand, with optional start and end dates
- Order workflow with refunds.
- Member management and support tickets.

---

## Requirements

| Tool | Version | Notes |
|---|---|---|
| Java | 21 | |
| Maven | 3.9+ | IntelliJ IDEA's bundled Maven also works |
| MongoDB | 7+ | A replica set is recommended (MongoDB Atlas free tier, or `docker compose up -d mongo`) |
| Python | 3.10+ | Optional, only for the image download tool |

---

## Getting started

1. **Create your local settings file.** Run this in the `ezbuild` folder, next to `pom.xml`:
   ```bash
   cp .env.example .env                 # macOS / Linux / Git Bash
   Copy-Item .env.example .env          # Windows PowerShell
   ```
   `.env` is git-ignored. Never commit it.

2. **Point it at MongoDB.** Edit `.env`:
   ```properties
   MONGODB_URI=mongodb+srv://USER:PASSWORD@cluster0.xxxxx.mongodb.net/ezbuild?retryWrites=true&w=majority
   SEED=true
   ```
   - URL-encode special characters in the password, for example `@` becomes `%40`.
   - In Atlas, allow your IP under **Network Access**.
   - With a standalone (non-replica-set) MongoDB, also set `MONGO_TRANSACTIONS=false`.

3. **Run it:**
   ```bash
   mvn spring-boot:run
   ```
   Or run `EzBuildApplication` from IntelliJ.

   The startup log shows which `.env` keys were loaded, for example `[EZBuild] Loaded .env from ... -> [MONGODB_URI, ...]`.

4. **Open** http://localhost:1010.

   With `SEED=true`, the first start loads about 90 products, 5 demo accounts and a few demo orders. Later starts keep product specs in sync with the seed file but never change prices, stock or discounts.

---

## Configuration

All settings live in `src/main/resources/application.properties` and come from environment variables. For local development, put them in `.env`. Real environment variables always override `.env`.

| Variable | Default | Purpose |
|---|---|---|
| `MONGODB_URI` | local replica set | MongoDB connection string |
| `MONGO_TRANSACTIONS` | `true` | Set `false` for a standalone MongoDB without a replica set |
| `SEED` | `true` locally, `false` in prod | Load and refresh the demo catalogue and accounts |
| `SEED_ADMIN_PASSWORD` | `Admin@123` | Password for the seeded `admin` account |
| `PORT` | `1010` | HTTP port |
| `BASE_URL` | `http://localhost:1010` | Public URL, used in payment callbacks and emails |
| `JWT_SECRET`, `REMEMBER_ME_KEY` | dev-only values | **Set long random values in any shared environment** |
| `GEMINI_API_KEY` | – | Google AI Studio key for the assistant |
| `GEMINI_MODEL` | `gemini-flash-latest` | Gemini model name |
| `AI_PROVIDER` | `google-genai` | Set `none` to run without an AI key |
| `AI_WEB_SEARCH` | `true` | Lets the assistant use Google Search for comparisons |
| `ADVISOR_MODE` | `local` | `remote` sends the AI calls to the `ezbuild-ai` service instead |
| `ADVISOR_URL`, `ADVISOR_KEY` | localhost:1020 / dev key | Address and shared key for `ezbuild-ai` |
| `SSLC_STORE_ID`, `SSLC_STORE_PASS` | – | SSLCommerz sandbox or live credentials |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | – | Stripe keys |
| `BDT_PER_USD` | `122` | Exchange rate used for Stripe charges |
| `MAIL_HOST`, `MAIL_USER`, `MAIL_PASSWORD` | – | SMTP. When unset, emails are written to the log |

Payment methods without keys still appear at checkout, greyed out with a note saying which keys are missing.

---

## Demo accounts

These are created by the seeder for local use (also listed in `creds.txt`).

| Username | Password | Can do |
|---|---|---|
| `admin` | `Admin@123` | Everything, including refunds and member management |
| `staff` | `Staff@123` | Products, discounts, orders and reports (no refunds, no members) |
| `rahim`, `nusrat`, `tanvir` | `User@1234` | Customer |

---

## Payments in test mode

- **Cash on delivery:** always available and adds ৳50.
- **Demo sandbox:** an offline approve/decline page for trying the full flow without any keys.
- **SSLCommerz sandbox:**
  - Register at https://developer.sslcommerz.com/registration/ and set `SSLC_STORE_ID` and `SSLC_STORE_PASS`.
  - Test card `4111 1111 1111 1111`, CVV `111`, OTP `111111`.
  - Instant payment notifications need a public URL (Cloudflare Tunnel or ngrok). Without one, the success redirect and the background reconciliation job still confirm payments.
- **Stripe test mode:**
  - Set `STRIPE_SECRET_KEY`.
  - For webhooks, run `stripe listen --forward-to localhost:1010/api/payments/stripe/webhook` and copy the signing secret into `STRIPE_WEBHOOK_SECRET`.
  - Test card `4242 4242 4242 4242`.

Payments are only marked as paid after the store checks with the provider. Browser redirects are never trusted on their own.

---

## How the AI assistant works

```
customer message
  → requirement extraction (Gemini, with a rule-based fallback for English/Bangla/Banglish)
  → named parts matched to catalogue products
  → build planner (deterministic): tries every affordable CPU + GPU pair, completes each build with
    compatible parts, upgrades within budget, scores it for the use case
  → compatibility engine: socket, memory, form factor, GPU length, PSU headroom, cooler, bottlenecks
  → Gemini writes the explanation from catalogue facts (and optional Google Search notes)
```

Things to know:
- **The model never chooses parts or prices.** It only fills a typed requirements record and writes the explanation. Build cards are rendered from planner data.
- **Follow-ups keep context.** "Better CPU" after a suggestion keeps the budget and use case, and each tier's new CPU must beat the previous one. "Doesn't matter if it's Intel or AMD" removes brand restrictions.
- **Power and balance are checked.** Power supplies are sized from each CPU's peak turbo power plus the GPU's board power, with 35% headroom. CPU/GPU pairs that would bottleneck badly are rejected.
- **No AI key?** Set `AI_PROVIDER=none`. The assistant falls back to rule-based understanding with the same planner and template replies.
- **Prompts** live in `src/main/resources/prompts/` (`extract.st` and `explain.st`).

---

## Project structure

```
src/main/java/ezbuild/
├── api/            REST controllers (/api/**)
├── assistant/      chat pipeline, LLM clients, regex fallback, web research, reply templates
├── build/          build planner, catalogue snapshot, part-name resolver
├── config/         security chains, MongoDB, caches, seeder, typed properties
├── controller/     Thymeleaf page controllers (+ admin/)
├── dto/            request/response records
├── model/          MongoDB documents and enums
├── order/          order lifecycle and events
├── payment/        SSLCommerz, Stripe and sandbox gateways, confirmation, reconciliation
├── repository/     Spring Data repositories
├── security/       JWT, authentication provider, lockout, rate limiting
├── service/        business logic (catalogue, cart, compatibility engine, search, mail...)
└── util/           formatting and hashing helpers
src/main/resources/
├── templates/      Thymeleaf pages and fragments
├── static/         CSS, JS, images (media/p = product photos, media/c = category icons, media/ui = logo)
├── prompts/        LLM prompt templates
└── seed/           products.csv (the catalogue the seeder loads)
```

**Security overview:**
- Pages use form login, sessions, CSRF protection and remember-me.
- `/api/**` is stateless JWT: 15-minute access tokens and rotating, hashed refresh tokens with reuse detection.
- Passwords use BCrypt. Access is controlled by roles plus fine-grained authorities via `@PreAuthorize`.
- Accounts lock after 5 failed sign-ins, and there are per-IP rate limits.
- Security headers include CSP, and there's a CORS allow-list.
- Requests for another user's orders, builds or chats return 404.

---

## REST API

A Postman collection and environment are in `docs/postman/`. Run **Auth → Login** first to get a token.

Main groups:
- `/api/auth`
- `/api/products`
- `/api/cart`
- `/api/builds`
- `/api/chat`
- `/api/orders`
- `/api/payments`
- `/api/admin`

Errors always use the same JSON shape (`status`, `error`, `message`, `path`, `timestamp`, `fieldErrors`).

---

## Testing

```bash
mvn test
```

The unit tests don't need a database. They cover:
- the compatibility engine, including power supply sizing and bottleneck checks
- the build planner, including follow-up requests
- the part resolver and the regex extractor
- JWT handling
- order status transitions
- discount maths
- seed parsing

---

## Catalogue data and images

- `src/main/resources/seed/products.csv` is the catalogue. Specs are `key=value` pairs separated by `;`.
- `tools/enrich_catalog.py` regenerates the detailed specs and scores in that file. Run `python tools/enrich_catalog.py`, then restart with `SEED=true` to update existing products.
- `tools/fetch_images.py` downloads product photos named by SKU (`cpu-01.webp`, ...):
  ```bash
  pip install requests beautifulsoup4 pillow ddgs
  python tools/fetch_images.py src/main/resources/static
  ```
  Products without a photo show their category icon. Admins can also upload images from the product editor.
- Prices marked `approx` in the seed file are estimates. Review them before going live.

---

## Running with Docker

```bash
docker compose up -d mongo        # just MongoDB (single-node replica set), run the app from your IDE
docker compose up --build         # MongoDB + store + ezbuild-ai
```

---

## Deploying to production

Activate the `prod` profile (`SPRING_PROFILES_ACTIVE=prod`). It:
- disables seeding
- enables the template cache and secure cookies

Before going live:
- Set strong `JWT_SECRET` and `REMEMBER_ME_KEY` values.
- Set real payment credentials and point `BASE_URL` at your public HTTPS address.
- Change or remove the demo accounts.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Connection refused ... localhost:27017` | `MONGODB_URI` isn't being picked up. Check that `.env` sits next to `pom.xml` (not `.env.txt`), that the line isn't commented out, and look for the `[EZBuild] Loaded .env` line in the log |
| Atlas timeout | Add your IP under Atlas **Network Access** |
| Blank page after sign-in | Check the log for a Thymeleaf error. Templates must not call beans (`@bean`) inside `th:src`, `th:href` or `th:data-*`. Compute the value in `th:with` first |
| Old styles or logo after an update | Hard refresh (Ctrl+F5). CSS/JS URLs are content-hashed, so this is normally only needed once |
| SSLCommerz or Stripe missing at checkout | Their keys aren't set. The option shows which variables to add |
| Assistant answers feel basic | No Gemini key, so it's running rule-based. Set `GEMINI_API_KEY` |
