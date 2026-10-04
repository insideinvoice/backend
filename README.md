# Inside Invoice Backend

## Quick Start

### Local Development
```bash
mvn spring-boot:run
```
Uses `application-local.yaml` — connects to Docker PostgreSQL on port 5433, backup disabled.

### Production
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```
Or set in Railway: `SPRING_PROFILES_ACTIVE=prod`

---

## Database

### Profiles

| File | Profile | Database | Backup |
|------|---------|----------|--------|
| `application-local.yaml` | local | Docker PostgreSQL (localhost:5433) | Off |
| `application-prod.yaml` | prod | Neon (env vars) | On, 23:00 IST |

### Local Docker Setup

**Create container:**
```bash
docker run -d --name insideinvoice-db \
  -e POSTGRES_DB=inside_invoice \
  -e POSTGRES_USER=insideinvoice \
  -e POSTGRES_PASSWORD=invoiceinside \
  -p 5433:5432 \
  postgres:18-alpine
```

**Start/Stop container:**
```bash
docker start insideinvoice-db
docker stop insideinvoice-db
```

> No changes needed in `application.yaml` after restart. App connects automatically.

### Sync Production Data to Local

**Step 1: Dump from Neon prod (run in terminal):**
```bash
/opt/homebrew/opt/postgresql@18/bin/pg_dump "postgresql://neondb_owner:npg_59kryoHMLmSD@ep-proud-sky-b48o74qh.c-6.us-east-2.aws.neon.tech/neondb?sslmode=require" --no-privileges --no-owner -f /tmp/prod.sql
```

**Step 2: Drop existing tables (if any):**
```bash
PGPASSWORD=invoiceinside psql -h localhost -p 5433 -U insideinvoice -d inside_invoice -c "DO \$\$ DECLARE r RECORD; BEGIN FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.tablename) || ' CASCADE'; END LOOP; END \$\$;"
```

**Step 3: Restore to local Docker:**
```bash
PGPASSWORD=invoiceinside psql -h localhost -p 5433 -U insideinvoice -d inside_invoice -f /tmp/prod.sql
```

**Step 4: Verify:**
```bash
PGPASSWORD=invoiceinside psql -h localhost -p 5433 -U insideinvoice -d inside_invoice -c "
SELECT 'users' as t, COUNT(*) FROM users
UNION ALL SELECT 'customers', COUNT(*) FROM customers
UNION ALL SELECT 'invoices', COUNT(*) FROM invoices;
"
```

### Check Data

```bash
# Version
PGPASSWORD=invoiceinside psql -h localhost -p 5433 -U insideinvoice -d inside_invoice -c "SELECT version();"

# Users
PGPASSWORD=invoiceinside psql -h localhost -p 5433 -U insideinvoice -d inside_invoice -c "SELECT id, name, email FROM users;"

# Customers
PGPASSWORD=invoiceinside psql -h localhost -p 5433 -U insideinvoice -d inside_invoice -c "SELECT id, name, email FROM customers;"

# Invoices
PGPASSWORD=invoiceinside psql -h localhost -p 5433 -U insideinvoice -d inside_invoice -c "SELECT id, invoice_number, grand_total FROM invoices;"

# All table counts
PGPASSWORD=invoiceinside psql -h localhost -p 5433 -U insideinvoice -d inside_invoice -c "
SELECT 'users' as t, COUNT(*) FROM users
UNION ALL SELECT 'businesses', COUNT(*) FROM businesses
UNION ALL SELECT 'customers', COUNT(*) FROM customers
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'invoices', COUNT(*) FROM invoices
UNION ALL SELECT 'invoice_items', COUNT(*) FROM invoice_items;
"
```

### Check Dump File
```bash
wc -l /tmp/prod.sql
head -20 /tmp/prod.sql
```

### Resend API Key

**Required for email functionality.**

| Variable | Description |
|----------|-------------|
| `APP_MAIL_RESEND_API_KEY` | Your Resend API key (get it from [resend.com](https://resend.com)) |

Set this environment variable when running the app, or add it to your hosting platform (Railway, Render, etc.):

```bash
export APP_MAIL_RESEND_API_KEY=re_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
```

> Note: The backend `EmailService` reads this via `@Value("${app.mail.resend-api-key:}")`, which maps to the `APP_MAIL_RESEND_API_KEY` environment variable.
### Production Environment Variables (Railway)
```
DATABASE_URL=jdbc:postgresql://ep-xxx.neon.tech/inside_invoice?sslmode=require
DATABASE_USERNAME=your_neon_user
DATABASE_PASSWORD=your_neon_password
SPRING_PROFILES_ACTIVE=prod
```

### Backup

- **Schedule:** Daily at 23:00 IST
- **Storage:** Google Drive (encrypted)
- **Retention:** 90 days
- **Encryption:** AES-256-GCM

**Restore backup:**
```bash
~/Desktop/restore-backup.sh
```

**Manual restore:**
```bash
# Decrypt
python3 -c "
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
key = bytes.fromhex('475cdadfe93e31872f0deddfa46d5b2cd7a04c2600e6c3d6663230f23be0f6b6')
data = open('backup.sql.enc','rb').read()
plain = AESGCM(key).decrypt(data[4:16], data[16:], None)
open('backup.sql','wb').write(plain)
"

# Restore to Neon
psql "$DATABASE_URL_UNPOOLED" -f backup.sql
```

### Docker Cleanup
```bash
# Remove container
docker stop insideinvoice-db && docker rm insideinvoice-db

# Remove volume (deletes all data)
docker volume rm insideinvoice-data

# Remove dump file
rm /tmp/prod.sql
```

---

## API

Base URL: `http://localhost:8080`

All protected endpoints require `Authorization: Bearer <token>`.

### Auth

**POST /auth/signup**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "password123"
}
```

**POST /auth/login**
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

### Business

**POST /business/setup**
```json
{
  "businessName": "Acme Corp",
  "gstIn": "29ABCDE1234F1Z5",
  "phone": "+919876543210",
  "email": "contact@acme.com",
  "website": "https://acme.com",
  "addressLine1": "123 Main Road",
  "city": "Bangalore",
  "state": "Karnataka",
  "country": "India",
  "pincode": "560034",
  "invoicePrefix": "ACME"
}
```

### Customers

**POST /customers**
```json
{
  "name": "Rajesh Kumar",
  "email": "rajesh@example.com",
  "phone": "+919876543210",
  "billingAddress": "456 Oak Street",
  "shippingAddress": "456 Oak Street",
  "gstIn": "29FGHI5678J2K5",
  "city": "Bangalore",
  "state": "Karnataka",
  "country": "India",
  "pincode": "560038"
}
```

### Products

**POST /products**
```json
{
  "name": "1 inch CPVC Pipe",
  "description": "Chlorinated Polyvinyl Chloride Pipe",
  "hsn": "39172390",
  "unit": "pcs",
  "rate": 580.00,
  "gstPercentage": 18.00
}
```

### Invoices

**POST /invoices**
```json
{
  "customerId": 1,
  "invoiceType": "TAX_INVOICE",
  "invoiceDate": "2026-02-18",
  "dueDate": "2026-02-28",
  "placeOfSupply": "Karnataka",
  "paymentTerms": "Net 10 Days",
  "notes": "Thank you for your business",
  "items": [
    {
      "productId": 1,
      "itemName": "1 inch CPVC Pipe",
      "hsn": "39172390",
      "qty": 20,
      "rate": 580.00,
      "gstPercentage": 18.00
    }
  ]
}
```

### Endpoints

| Endpoint | Description |
|----------|-------------|
| `POST /auth/signup` | Register user |
| `POST /auth/login` | Login |
| `POST /business/setup` | Setup business |
| `GET /business/me` | Get business |
| `POST /customers` | Create customer |
| `GET /customers` | List customers |
| `POST /products` | Create product |
| `GET /products` | List products |
| `POST /invoices` | Create invoice |
| `GET /invoices` | List invoices |
| `GET /actuator/health` | Health check |
