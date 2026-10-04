# Free public demo: Render + Neon

This puts the API on a public URL using free tiers:

- **Neon**: serverless PostgreSQL with the `pgvector` extension (free plan)
- **Render**: runs the Docker image as a web service (free plan, 512 MB)
- **OpenAI**: chat + embeddings (pay-as-you-go; the demo costs cents)

> Free tiers change. Check the current limits on each provider's pricing page.
> A free Render service **sleeps after ~15 minutes idle**, so the first request after that takes
> about a minute while it wakes up.

## 1. Create the database (Neon)

1. Sign up at <https://neon.tech> and create a project (any region close to your Render region).
2. Open **Connection Details** and copy the connection string. It looks like:
   ```
   postgresql://neondb_owner:AbC123@ep-cool-name-123456.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
3. Split it into the three values the app expects:

   | Env var | Value from the example above |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://ep-cool-name-123456.us-east-2.aws.neon.tech/neondb?sslmode=require` |
   | `DB_USERNAME` | `neondb_owner` |
   | `DB_PASSWORD` | `AbC123` |

   Note the `jdbc:` prefix and that the user/password are **removed** from the URL.

You don't need to run any SQL. On first start Flyway enables `vector` and creates the catalogue table,
and Spring AI creates the `vector_store` table and HNSW index.

*(Supabase works the same way: use its "Session pooler" or direct connection string.)*

## 2. Get an OpenAI API key

1. Create a key at <https://platform.openai.com/api-keys>.
2. **Set a monthly spend limit** under Settings → Limits (e.g. $5). The demo URL is public,
   and every question calls the API.

## 3. Deploy on Render

1. Sign up at <https://render.com> with your GitHub account.
2. **New → Blueprint**, pick the `secure-rag-policy-engine` repo and the branch that contains
   `render.yaml`.
3. Render reads `render.yaml` and asks for the values marked `sync: false`:

   | Key | Value |
   |---|---|
   | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | from step 1 |
   | `OPENAI_API_KEY` | from step 2 |
   | `DEMO_PASSWORD` | password for `alice` / `hana` / `felix`, which you can share with recruiters |
   | `ADMIN_PASSWORD` | a **private** password for `admin`, who can add and delete documents |

   `JWT_SECRET` is generated automatically.
4. Click **Apply**. The first build takes about 5–10 minutes (Maven downloads dependencies).
   On first start the 9 sample policies are embedded and stored in Neon.
5. When the service shows **Live**, open:
   - `https://<your-service>.onrender.com/actuator/health` → `{"status":"UP"}`
   - `https://<your-service>.onrender.com/swagger-ui.html` → interactive API docs

## 4. Try it

```bash
URL=https://<your-service>.onrender.com
TOKEN=$(curl -s -X POST $URL/api/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"<DEMO_PASSWORD>"}' | jq -r .accessToken)

curl -s -X POST $URL/api/ask -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"question":"How many days per week can I work remotely?"}' | jq
```

In Swagger UI: call `POST /api/auth/token`, copy `accessToken`, click **Authorize**, paste it,
then try `POST /api/ask`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `password authentication failed` | `DB_USERNAME`/`DB_PASSWORD` wrong, or user/password were left inside `DB_URL` |
| `The connection attempt failed` | `DB_URL` is missing the `jdbc:` prefix or `?sslmode=require` |
| `OpenAI API key must be set` | `OPENAI_API_KEY` is empty |
| `expected 1536 dimensions` | You changed embedding model; set `EMBEDDING_DIMENSIONS` to match and recreate the `vector_store` table |
| Service restarts with out-of-memory | Keep the `JAVA_TOOL_OPTIONS` from `render.yaml`; avoid uploading very large PDFs on the free plan |
| First request is very slow | The free instance was asleep, so wait about a minute |
