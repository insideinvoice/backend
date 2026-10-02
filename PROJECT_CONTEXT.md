# Inside Invoice Backend - AI Context Guide

> **Purpose**: This document provides comprehensive context for AI assistants working on the Inside Invoice backend. Read this file before making any changes to understand the project architecture, conventions, and patterns.

---

## Table of Contents
- [Project Overview](#project-overview)
- [Tech Stack](#tech-stack)
- [Architecture & Patterns](#architecture--patterns)
- [Directory Structure](#directory-structure)
- [Database Schema](#database-schema)
- [API Endpoints](#api-endpoints)
- [Authentication & Security](#authentication--security)
- [Environment Configuration](#environment-configuration)
- [Development Guide](#development-guide)
- [Code Conventions](#code-conventions)
- [Common Tasks](#common-tasks)

---

## Project Overview

**Inside Invoice** is a **SaaS Multi-Tenant Invoicing Platform** built for Indian businesses with GST (Goods and Services Tax) support. Each registered user gets their own isolated business entity, and all data (customers, products, invoices, payments) is scoped per business via `businessId`.

### Key Features
- Multi-tenant architecture with business-level data isolation
- GST-compliant invoice generation with HSN/SAC codes
- Customer and product management
- Payment tracking against invoices
- Admin panel for platform management
- JWT-based authentication with role-based access control

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.5 |
| Build Tool | Maven |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA (Hibernate) |
| DB Migrations | Flyway |
| Security | Spring Security + JWT (jjwt 0.12.6) |
| Password Hashing | BCrypt |
| API Docs | SpringDoc OpenAPI (Swagger UI) v2.6.0 |
| Code Generation | Lombok |
| Validation | Jakarta Bean Validation |
| Monitoring | Spring Boot Actuator |
| Containerization | Docker + Docker Compose |

---

## Architecture & Patterns

### Multi-Tenancy Pattern
- Each user is associated with a `businessId`
- All data entities (Customer, Product, Invoice, Payment) have a `business_id` column
- Repository methods filter by `businessId` to isolate data between tenants
- The `businessId` is extracted from the JWT token on each request

### Module Pattern
Each business domain follows a consistent structure:
```
module/
├── controller/        # REST endpoints
├── dto/
│   ├── request/       # Input DTOs
│   └── response/      # Output DTOs
├── entity/            # JPA entities
├── mapper/            # Entity <-> DTO mappers
├── repository/        # Spring Data JPA repositories
└── service/
    ├── ServiceName.java       # Interface
    └── impl/
        └── ServiceNameImpl.java  # Implementation
```

### API Response Pattern
All endpoints return a standardized `ApiResponse<T>` wrapper:
```json
{
  "success": true/false,
  "message": "...",
  "data": T,
  "timestamp": "2026-...",
  "fieldErrors": {} // only on validation errors
}
```

### Pagination Pattern
List endpoints support pagination with query params: `page`, `size`, `sortBy`, `sortDir`
Returns `PagedResponse<T>` with `content`, `page`, `size`, `totalElements`, `totalPages`, `last`, `first`.

### Invoice Number Generation
- Format: `{PREFIX}-{SEQUENCE}` (e.g., `ACME-001`)
- Sequence is stored per business in `businesses.next_invoice_sequence`
- Uses **pessimistic locking** (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) to prevent race conditions

### Invoice Calculations (Server-side)
- `taxableValue = qty * rate`
- `taxAmount = taxableValue * gstPercentage / 100`
- `total = taxableValue + taxAmount` (per item)
- `subtotal = sum of all item taxableValues`
- `taxAmount = sum of all item taxAmounts`
- `grandTotal = subtotal + taxAmount`

### Payment Processing
- Payments are linked to invoices
- Server validates that payment amount does not exceed remaining balance
- Auto-updates invoice status: `PAID` when fully paid, `PENDING` when partially paid

---

## Directory Structure

```
Inside-Invoice-backend/
├── pom.xml                           # Maven dependencies & plugins
├── Dockerfile                        # Multi-stage Docker build
├── docker-compose.yml                # 3-service setup (db, backend, frontend)
├── start-local.sh                    # Local dev setup script
├── src/main/java/com/insideinvoice/
│   ├── InsideInvoiceApplication.java # Entry point (@SpringBootApplication)
│   ├── auth/                          # Authentication module
│   │   ├── controller/AuthController.java
│   │   ├── dto/request/              # SignupRequest, LoginRequest, etc.
│   │   ├── dto/response/             # ApiResponse, JwtResponse
│   │   ├── entity/User.java, Role.java
│   │   ├── mapper/UserMapper.java
│   │   ├── repository/UserRepository.java
│   │   └── service/AuthService.java, impl/AuthServiceImpl.java
│   ├── business/                      # Business entity module
│   ├── customer/                      # Customer management
│   ├── product/                       # Product catalog
│   ├── invoice/                       # Invoice management
│   ├── payment/                       # Payment tracking
│   ├── contact/                       # Contact form (public)
│   ├── admin/                         # Admin panel
│   ├── security/                      # JWT & Security config
│   ├── common/                        # BaseEntity, Constants, PagedResponse
│   ├── config/                        # DataSeeder, OpenApiConfig, AuditAware
│   ├── exception/                     # Global exception handling
│   └── health/controller/             # Health check endpoint
└── src/main/resources/
    ├── application.yml                # Spring Boot configuration
    └── db/migration/                  # Flyway SQL migrations (V1-V10)
```

---

## Database Schema

### Tables Overview

| Table | Description |
|-------|-------------|
| `businesses` | Business entities (multi-tenant root) |
| `users` | User accounts linked to businesses |
| `customers` | Customer records per business |
| `products` | Product catalog per business |
| `invoices` | Invoices with line items |
| `invoice_items` | Individual line items in invoices |
| `payments` | Payment records against invoices |
| `contacts` | Contact form submissions (public) |

### Key Relationships
```
businesses (1) ──── (N) users
businesses (1) ──── (N) customers
businesses (1) ──── (N) products
businesses (1) ──── (N) invoices
businesses (1) ──── (N) payments
customers (1) ──── (N) invoices
invoices (1) ──── (N) invoice_items
invoices (1) ──── (N) payments
```

### Critical Fields
- All data tables have `business_id` for tenant isolation
- `users.raw_password` stores plaintext password (for admin reference only)
- `businesses.next_invoice_sequence` tracks invoice numbering per business
- `businesses.signature` stores Base64-encoded signature image

---

## API Endpoints

### Authentication (`/api/auth`) - Public
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/signup` | Register new user |
| POST | `/api/auth/login` | Login and get JWT |
| POST | `/api/auth/logout` | Logout and clear JWT |
| POST | `/api/auth/forgot-password` | Request password reset |
| POST | `/api/auth/reset-password` | Reset password with token |
| PUT | `/api/auth/profile` | Update user profile |
| PUT | `/api/auth/change-password` | Change password |

### Business (`/api/business`) - Authenticated
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/business/setup` | Initial business setup |
| GET | `/api/business/me` | Get current business |
| PUT | `/api/business/update` | Update business details |
| POST | `/api/business/signature` | Upload signature (max 1MB) |
| DELETE | `/api/business/signature` | Remove signature |

### Customers (`/api/customers`) - Authenticated
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/customers` | Create customer |
| GET | `/api/customers` | List customers (paginated) |
| POST | `/api/customers/check` | Find by email/phone |
| GET | `/api/customers/{id}` | Get customer by ID |
| PUT | `/api/customers/{id}` | Update customer |
| DELETE | `/api/customers/{id}` | Delete customer |

### Products (`/api/products`) - Authenticated
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/products` | Create product |
| GET | `/api/products` | List products (paginated) |
| GET | `/api/products/{id}` | Get product by ID |
| GET | `/api/products/by-hsn/{hsn}` | Find by HSN/SAC code |
| PUT | `/api/products/{id}` | Update product |
| DELETE | `/api/products/{id}` | Delete product |

### Invoices (`/api/invoices`) - Authenticated
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/invoices` | Create invoice |
| GET | `/api/invoices` | List invoices (paginated) |
| GET | `/api/invoices/{id}` | Get invoice by ID |
| PUT | `/api/invoices/{id}` | Update invoice |
| DELETE | `/api/invoices/{id}` | Delete invoice |

### Payments (`/api/payments`) - Authenticated
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/payments` | Record payment |
| GET | `/api/payments` | List payments (paginated) |
| GET | `/api/payments/{id}` | Get payment by ID |
| GET | `/api/payments/by-invoice/{invoiceId}` | Get payments for invoice |
| DELETE | `/api/payments/{id}` | Delete payment |

### Admin (`/api/admin`) - ADMIN role only
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/users` | List all users |
| GET | `/api/admin/stats` | Dashboard statistics |
| GET | `/api/admin/analytics` | Monthly growth analytics |
| GET | `/api/admin/businesses` | List all businesses |
| GET | `/api/admin/invoices` | List all invoices (cross-business) |
| GET | `/api/admin/customers` | List all customers (cross-business) |
| GET | `/api/admin/products` | List all products (cross-business) |
| PUT | `/api/admin/users/{id}/password` | Update user password |
| PUT | `/api/admin/users/{id}/role` | Change user role |
| DELETE | `/api/admin/users/{id}` | Delete user and business data |

### Public
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/contact` | Submit contact form |
| GET | `/api/heartbeat` | Health check |
| GET | `/actuator/health` | Actuator health |
| GET | `/swagger-ui/**` | Swagger UI |

---

## Authentication & Security

### JWT Configuration
- **Algorithm**: HMAC-SHA (jjwt 0.12.6)
- **Access Token Expiry**: 6 hours
- **Refresh Token Expiry**: 30 days
- **Claims**: `userId`, `businessId`, `userName`, `email`
- **Password Storage**: BCrypt encoded

### Authentication Flow
1. User calls `POST /api/auth/login` with email/username + password
2. Server validates credentials via Spring Security's `AuthenticationManager`
3. Server generates JWT and sets it in:
   - Response body (`JwtResponse.accessToken`)
   - HttpOnly cookie named `jwt` (3600s max-age)
4. Client sends `Authorization: Bearer <token>` header on subsequent requests
5. `JwtAuthenticationFilter` validates JWT and sets `SecurityContext`

### Security Configuration
- CSRF disabled (stateless API)
- Session policy: `STATELESS`
- CORS: allows all origins (`*`), all HTTP methods, credentials
- Role-based access: `@PreAuthorize("hasRole('ADMIN')")`
- Multi-tenant: all queries scoped by `businessId` from JWT

### Public Endpoints (no auth required)
- `/api/auth/**`
- `/api/contact/**`
- `/swagger-ui/**`, `/v3/api-docs/**`
- `/actuator/**`
- `/api/heartbeat`

---

## Environment Configuration

### Required Environment Variables
| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/inside_invoice` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `insideinvoice` | Database username |
| `DB_PASSWORD` | `invoiceinside` | Database password |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile |

### JWT Properties (in application.yml)
| Property | Value |
|----------|-------|
| `app.jwt.secret` | Hex-encoded secret key (64 chars) |
| `app.jwt.expiration-ms` | 21600000 (6 hours) |
| `app.jwt.refresh-expiration-ms` | 2592000000 (30 days) |

### Production (Render + Neon)
```
DB_URL=jdbc:postgresql://ep-<project>.us-east-2.aws.neon.tech/neondb?sslmode=require
DB_USERNAME=<neon-username>
DB_PASSWORD=<neon-password>
```

---

## Development Guide

### Running Locally

**Option A: Docker Compose (recommended)**
```bash
docker-compose up --build
```
Starts: PostgreSQL (5432), Backend (8080), Frontend (5173)

**Option B: Local Development**
```bash
# Start PostgreSQL and create DB
./start-local.sh

# Run the Spring Boot app
mvn spring-boot:run
```

**Option C: Standalone JAR**
```bash
mvn clean package -DskipTests
DB_URL=jdbc:postgresql://localhost:5432/inside_invoice \
DB_USERNAME=insideinvoice \
DB_PASSWORD=invoiceinside \
java -jar target/inside-invoice-1.0.0.jar
```

### Default Admin Account (auto-seeded)
- **Email/Username**: `invoiceinside`
- **Password**: `insideinvoice`

### Useful Endpoints
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health Check: `http://localhost:8080/api/heartbeat`
- Actuator: `http://localhost:8080/actuator/health`

---

## Code Conventions

### Naming Conventions
- **Entities**: Singular, PascalCase (e.g., `Invoice`, `InvoiceItem`)
- **Repositories**: Entity name + `Repository` (e.g., `InvoiceRepository`)
- **Services**: Interface + Impl pattern (e.g., `InvoiceService` / `InvoiceServiceImpl`)
- **Controllers**: Module name + `Controller` (e.g., `InvoiceController`)
- **DTOs**: Purpose-based suffixes (e.g., `CreateInvoiceRequest`, `InvoiceResponse`)

### Java Style
- Use Lombok for boilerplate (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- Use `@Builder.Default` for default values in builders
- Use `@ManyToOne(fetch = FetchType.LAZY)` for relationships
- Use `@JsonIgnore` on back-references to prevent circular serialization
- Use `@Column(nullable = false)` for required fields

### Exception Handling
All exceptions are handled globally via `GlobalExceptionHandler`:
- `ResourceNotFoundException` → 404
- `BadRequestException` → 400
- `UnauthorizedException` → 401
- `BusinessAccessViolationException` → 403
- `DuplicateResourceException` → 409
- Validation errors → 400 with field-level error map

### Data Access Pattern
```java
// Repository methods filter by businessId for tenant isolation
List<Invoice> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

// Use Specification or @Query for complex queries
@Query("SELECT i FROM Invoice i WHERE i.business.id = :businessId AND i.status = :status")
List<Invoice> findByBusinessIdAndStatus(@Param("businessId") Long businessId, @Param("status") InvoiceStatus status);
```

---

## Common Tasks

### Adding a New Module
1. Create package structure: `controller/`, `dto/request/`, `dto/response/`, `entity/`, `mapper/`, `repository/`, `service/`, `service/impl/`
2. Create entity extending `BaseEntity`
3. Create repository interface
4. Create service interface and implementation
5. Create DTOs (request/response)
6. Create mapper
7. Create controller with `@RestController` and `@RequestMapping`
8. Add Flyway migration if new table needed
9. Add security config if custom endpoints needed

### Adding a New Endpoint
1. Add method to controller with appropriate HTTP method annotation
2. Add `@PreAuthorize` if role-based access needed
3. Add service method
4. Add repository method if needed
5. Create/update DTOs
6. Test with Swagger UI

### Database Migration
1. Create new SQL file in `src/main/resources/db/migration/`
2. Name format: `V{number}__{description}.sql`
3. Use Flyway conventions
4. Test migration with `mvn flyway:migrate`

### Testing
- Use Postman or Swagger UI for manual testing
- Test with different user roles (ADMIN vs USER)
- Test multi-tenant isolation (different businessId)
- Test pagination and sorting
- Test error scenarios (validation, not found, unauthorized)

---

## Important Notes

1. **Multi-Tenancy**: Always filter by `businessId` in queries. Never expose data across tenants.
2. **Invoice Calculations**: Server calculates all amounts. Frontend should not calculate totals.
3. **Signature Storage**: Stored as Base64 in database. Max 1MB. Used in invoice PDF generation.
4. **Raw Password**: `users.raw_password` stores plaintext for admin reference. This is intentional.
5. **Invoice Numbers**: Auto-generated with pessimistic locking. Never manually set.
6. **Duplicate Customers**: When creating a customer with same phone/email, existing customer is updated instead of creating duplicate.
7. **Hibernate Config**: `open-in-view: false` - no lazy loading outside transactions.
8. **Batch Size**: Hibernate batch size is 20 for optimal performance.

---

## Related Documentation
- `README.md` - Detailed API documentation with request/response examples
- `docker-compose.yml` - Container orchestration setup
- `Dockerfile` - Multi-stage Docker build configuration
- `pom.xml` - All Maven dependencies and plugins
