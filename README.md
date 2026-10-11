# EZBuild

EZBuild is an online PC component store for Bangladesh with an AI build assistant. Customers tell the assistant their budget and what the PC is for, in English, Bangla or Banglish, and it suggests compatible builds from parts that are actually in stock.

This repository holds two applications:

| Folder | What it is | Default port |
|---|---|---|
| [`ezbuild/`](ezbuild/README.md) | The store: shop, cart, checkout, PC builder, AI assistant, admin panel | 1010 |
| [`ezbuild-ai/`](ezbuild-ai/README.md) | Optional AI microservice that holds the Gemini key and runs the two LLM calls | 1020 |

Most people only need `ezbuild/`. It runs the AI inside the same app by default. Start with **[ezbuild/README.md](ezbuild/README.md)**.

## Quick start

```bash
cd ezbuild
cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env
# edit .env and set MONGODB_URI (MongoDB Atlas or local replica set)
mvn spring-boot:run
```

Open http://localhost:1010. On a fresh database the app seeds a catalogue and demo accounts.

## Tech at a glance

Java 21 · Spring Boot 4 · Spring Data MongoDB · Thymeleaf · Spring Security (sessions + JWT) · Spring AI with Google Gemini · SSLCommerz · Stripe · Caffeine · Docker
