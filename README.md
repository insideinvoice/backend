# Inside Invoice API Documentation

## Configuration

The application connects to PostgreSQL using these environment variables:

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://db:5432/inside_invoice` | JDBC connection URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |

**Local development (Docker Compose):** No env vars needed — defaults point to the `db` service.

**Production (e.g., Render + Neon):** Set these in your hosting dashboard:
```
DB_URL=jdbc:postgresql://ep-<project>.us-east-2.aws.neon.tech/neondb?sslmode=require
DB_USERNAME=<neon-username>
DB_PASSWORD=<neon-password>
```

The `?sslmode=require` is required for Neon connections.

Base URL: `http://localhost:8080`

All protected endpoints require `Authorization: Bearer <token>` header.

---

## Authentication

### POST /auth/signup

**Request:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "password123"
}
```

**Response (201):**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "userId": 1,
    "name": "John Doe",
    "email": "john@example.com",
    "businessId": 1,
    "businessSetupCompleted": false
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### POST /auth/login

**Request:**
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "userId": 1,
    "name": "John Doe",
    "email": "john@example.com",
    "businessId": 1,
    "businessSetupCompleted": false
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

> **Note:** After signup/login, check `businessSetupCompleted`. If `false`, redirect user to `/business/setup`.

---

### POST /auth/forgot-password

**Request:**
```json
{
  "email": "john@example.com"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "If the email exists, a reset link has been sent",
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### POST /auth/reset-password

**Request:**
```json
{
  "token": "reset-token-from-email",
  "password": "newpassword123"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Password reset successful",
  "timestamp": "2026-05-22T12:00:00"
}
```

---

## Business

### POST /business/setup

**Request:**
```json
{
  "businessName": "Acme Corp",
  "gstIn": "29ABCDE1234F1Z5",
  "phone": "+919876543210",
  "email": "contact@acme.com",
  "website": "https://acme.com",
  "addressLine1": "123 Main Road",
  "addressLine2": "Koramangala",
  "city": "Bangalore",
  "state": "Karnataka",
  "country": "India",
  "pincode": "560034",
  "invoicePrefix": "ACME"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Business setup completed successfully",
  "data": {
    "id": 1,
    "businessName": "Acme Corp",
    "ownerName": "John Doe",
    "gstIn": "29ABCDE1234F1Z5",
    "phone": "+919876543210",
    "email": "contact@acme.com",
    "website": "https://acme.com",
    "addressLine1": "123 Main Road",
    "addressLine2": "Koramangala",
    "city": "Bangalore",
    "state": "Karnataka",
    "country": "India",
    "pincode": "560034",
    "invoicePrefix": "ACME",
    "nextInvoiceSequence": 2,
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:00"
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### GET /business/me

**Response (200):**
```json
{
  "success": true,
  "message": "Business retrieved successfully",
  "data": {
    "id": 1,
    "businessName": "Acme Corp",
    "ownerName": "John Doe",
    "gstIn": "29ABCDE1234F1Z5",
    "phone": "+919876543210",
    "email": "contact@acme.com",
    "website": "https://acme.com",
    "addressLine1": "123 Main Road",
    "addressLine2": "Koramangala",
    "city": "Bangalore",
    "state": "Karnataka",
    "country": "India",
    "pincode": "560034",
    "invoicePrefix": "ACME",
    "nextInvoiceSequence": 2,
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:00"
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### PUT /business/update

**Request:**
```json
{
  "businessName": "Acme Corp Pvt Ltd",
  "phone": "+919999999999",
  "invoicePrefix": "ACMEPL"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Business updated successfully",
  "data": {
    "id": 1,
    "businessName": "Acme Corp Pvt Ltd",
    "ownerName": "John Doe",
    "gstIn": "29ABCDE1234F1Z5",
    "phone": "+919999999999",
    "email": "contact@acme.com",
    "website": "https://acme.com",
    "addressLine1": "123 Main Road",
    "addressLine2": "Koramangala",
    "city": "Bangalore",
    "state": "Karnataka",
    "country": "India",
    "pincode": "560034",
    "invoicePrefix": "ACMEPL",
    "nextInvoiceSequence": 2,
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:01"
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

## Customers

### POST /customers

**Request:**
```json
{
  "name": "Rajesh Kumar",
  "email": "rajesh@example.com",
  "phone": "+919876543210",
  "billingAddress": "456 Oak Street, Indiranagar",
  "shippingAddress": "456 Oak Street, Indiranagar",
  "gstIn": "29FGHI5678J2K5",
  "city": "Bangalore",
  "state": "Karnataka",
  "country": "India",
  "pincode": "560038"
}
```

**Response (201):**
```json
{
  "success": true,
  "message": "Customer created successfully",
  "data": {
    "id": 1,
    "name": "Rajesh Kumar",
    "email": "rajesh@example.com",
    "phone": "+919876543210",
    "billingAddress": "456 Oak Street, Indiranagar",
    "shippingAddress": "456 Oak Street, Indiranagar",
    "gstIn": "29FGHI5678J2K5",
    "city": "Bangalore",
    "state": "Karnataka",
    "country": "India",
    "pincode": "560038",
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:00"
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### GET /customers

Query params: `page=0`, `size=10`, `sortBy=createdAt`, `sortDir=desc`

**Response (200):**
```json
{
  "success": true,
  "message": "Customers retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "Rajesh Kumar",
        "email": "rajesh@example.com",
        "phone": "+919876543210",
        "billingAddress": "456 Oak Street, Indiranagar",
        "shippingAddress": "456 Oak Street, Indiranagar",
        "gstIn": "29FGHI5678J2K5",
        "city": "Bangalore",
        "state": "Karnataka",
        "country": "India",
        "pincode": "560038",
        "createdAt": "2026-05-22T12:00:00",
        "updatedAt": "2026-05-22T12:00:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "first": true
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### GET /customers/{id}

**Response (200):**
```json
{
  "success": true,
  "message": "Customer retrieved successfully",
  "data": {
    "id": 1,
    "name": "Rajesh Kumar",
    "email": "rajesh@example.com",
    "phone": "+919876543210",
    "billingAddress": "456 Oak Street, Indiranagar",
    "shippingAddress": "456 Oak Street, Indiranagar",
    "gstIn": "29FGHI5678J2K5",
    "city": "Bangalore",
    "state": "Karnataka",
    "country": "India",
    "pincode": "560038",
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:00"
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### PUT /customers/{id}

**Request:**
```json
{
  "name": "Rajesh Kumar Updated",
  "phone": "+919999999999"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Customer updated successfully",
  "data": {
    "id": 1,
    "name": "Rajesh Kumar Updated",
    "email": "rajesh@example.com",
    "phone": "+919999999999",
    "billingAddress": "456 Oak Street, Indiranagar",
    "shippingAddress": "456 Oak Street, Indiranagar",
    "gstIn": "29FGHI5678J2K5",
    "city": "Bangalore",
    "state": "Karnataka",
    "country": "India",
    "pincode": "560038",
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:01"
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### DELETE /customers/{id}

**Response (200):**
```json
{
  "success": true,
  "message": "Customer deleted successfully",
  "timestamp": "2026-05-22T12:00:00"
}
```

---

## Products

### POST /products

**Request:**
```json
{
  "name": "1 inch CPVC Pipe",
  "description": "Chlorinated Polyvinyl Chloride Pipe 1 inch",
  "hsn": "39172390",
  "unit": "pcs",
  "rate": 580.00,
  "gstPercentage": 18.00
}
```

**Response (201):**
```json
{
  "success": true,
  "message": "Product created successfully",
  "data": {
    "id": 1,
    "name": "1 inch CPVC Pipe",
    "description": "Chlorinated Polyvinyl Chloride Pipe 1 inch",
    "hsn": "39172390",
    "unit": "pcs",
    "rate": 580.00,
    "gstPercentage": 18.00,
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:00"
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### GET /products

Query params: `page=0`, `size=10`, `sortBy=createdAt`, `sortDir=desc`

**Response (200):**
```json
{
  "success": true,
  "message": "Products retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "1 inch CPVC Pipe",
        "description": "Chlorinated Polyvinyl Chloride Pipe 1 inch",
        "hsn": "39172390",
        "unit": "pcs",
        "rate": 580.00,
        "gstPercentage": 18.00,
        "createdAt": "2026-05-22T12:00:00",
        "updatedAt": "2026-05-22T12:00:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "first": true
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### GET /products/{id}

**Response (200):**
```json
{
  "success": true,
  "message": "Product retrieved successfully",
  "data": {
    "id": 1,
    "name": "1 inch CPVC Pipe",
    "description": "Chlorinated Polyvinyl Chloride Pipe 1 inch",
    "hsn": "39172390",
    "unit": "pcs",
    "rate": 580.00,
    "gstPercentage": 18.00,
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:00"
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### PUT /products/{id}

**Request:**
```json
{
  "rate": 620.00,
  "gstPercentage": 18.00
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Product updated successfully",
  "data": {
    "id": 1,
    "name": "1 inch CPVC Pipe",
    "description": "Chlorinated Polyvinyl Chloride Pipe 1 inch",
    "hsn": "39172390",
    "unit": "pcs",
    "rate": 620.00,
    "gstPercentage": 18.00,
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:01"
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### DELETE /products/{id}

**Response (200):**
```json
{
  "success": true,
  "message": "Product deleted successfully",
  "timestamp": "2026-05-22T12:00:00"
}
```

---

## Invoices

### POST /invoices

**Request:**
```json
{
  "customerId": 1,
  "invoiceType": "TAX_INVOICE",
  "invoiceDate": "2026-02-18",
  "dueDate": "2026-02-28",
  "placeOfSupply": "Karnataka",
  "paymentTerms": "Net 10 Days",
  "notes": "Thank you for your business",
  "deliveryNote": "Door delivery",
  "referenceNumber": "PO-2026-001",
  "buyerOrderNumber": "BO-2026-001",
  "dispatchDocNumber": "DD-001",
  "dispatchedThrough": "Self",
  "termsOfDelivery": "FOB",
  "otherReferences": "Quotation Q-001",
  "destination": "Bangalore",
  "items": [
    {
      "productId": 1,
      "itemName": "1 inch CPVC Pipe",
      "hsn": "39172390",
      "qty": 20,
      "rate": 580.00,
      "gstPercentage": 18.00
    },
    {
      "productId": 2,
      "itemName": "1 inch CPVC Elbow",
      "hsn": "39172390",
      "qty": 30,
      "rate": 30.00,
      "gstPercentage": 18.00
    }
  ]
}
```

**Response (201):**
```json
{
  "success": true,
  "message": "Invoice created successfully",
  "data": {
    "id": 1,
    "invoiceNumber": "ACME-001",
    "invoiceType": "TAX_INVOICE",
    "customerId": 1,
    "customerName": "Rajesh Kumar",
    "invoiceDate": "2026-02-18",
    "dueDate": "2026-02-28",
    "subtotal": 12500.00,
    "taxAmount": 2250.00,
    "grandTotal": 14750.00,
    "paymentTerms": "Net 10 Days",
    "notes": "Thank you for your business",
    "status": "DRAFT",
    "placeOfSupply": "Karnataka",
    "deliveryNote": "Door delivery",
    "referenceNumber": "PO-2026-001",
    "buyerOrderNumber": "BO-2026-001",
    "dispatchDocNumber": "DD-001",
    "dispatchedThrough": "Self",
    "termsOfDelivery": "FOB",
    "otherReferences": "Quotation Q-001",
    "destination": "Bangalore",
    "createdBy": 1,
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:00",
    "items": [
      {
        "id": 1,
        "productId": 1,
        "itemName": "1 inch CPVC Pipe",
        "hsn": "39172390",
        "qty": 20,
        "rate": 580.00,
        "gstPercentage": 18.00,
        "taxableValue": 11600.00,
        "taxAmount": 2088.00,
        "total": 13688.00
      },
      {
        "id": 2,
        "productId": 2,
        "itemName": "1 inch CPVC Elbow",
        "hsn": "39172390",
        "qty": 30,
        "rate": 30.00,
        "gstPercentage": 18.00,
        "taxableValue": 900.00,
        "taxAmount": 162.00,
        "total": 1062.00
      }
    ]
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

> **Backend calculations:**
> - Item 1: taxableValue = 20 x 580 = 11600, taxAmount = 11600 x 18/100 = 2088, total = 11600 + 2088 = 13688
> - Item 2: taxableValue = 30 x 30 = 900, taxAmount = 900 x 18/100 = 162, total = 900 + 162 = 1062
> - subtotal = 11600 + 900 = 12500
> - taxAmount = 2088 + 162 = 2250
> - grandTotal = 12500 + 2250 = 14750

---

### GET /invoices

Query params: `page=0`, `size=10`, `sortBy=createdAt`, `sortDir=desc`

**Response (200):**
```json
{
  "success": true,
  "message": "Invoices retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "invoiceNumber": "ACME-001",
        "invoiceType": "TAX_INVOICE",
        "customerId": 1,
        "customerName": "Rajesh Kumar",
        "invoiceDate": "2026-02-18",
        "dueDate": "2026-02-28",
        "subtotal": 12500.00,
        "taxAmount": 2250.00,
        "grandTotal": 14750.00,
        "paymentTerms": "Net 10 Days",
        "notes": "Thank you for your business",
        "status": "DRAFT",
        "placeOfSupply": "Karnataka",
        "deliveryNote": "Door delivery",
        "referenceNumber": "PO-2026-001",
        "buyerOrderNumber": "BO-2026-001",
        "dispatchDocNumber": "DD-001",
        "dispatchedThrough": "Self",
        "termsOfDelivery": "FOB",
        "otherReferences": "Quotation Q-001",
        "destination": "Bangalore",
        "createdBy": 1,
        "createdAt": "2026-05-22T12:00:00",
        "updatedAt": "2026-05-22T12:00:00",
        "items": [
          {
            "id": 1,
            "productId": 1,
            "itemName": "1 inch CPVC Pipe",
            "hsn": "39172390",
            "qty": 20,
            "rate": 580.00,
            "gstPercentage": 18.00,
            "taxableValue": 11600.00,
            "taxAmount": 2088.00,
            "total": 13688.00
          },
          {
            "id": 2,
            "productId": 2,
            "itemName": "1 inch CPVC Elbow",
            "hsn": "39172390",
            "qty": 30,
            "rate": 30.00,
            "gstPercentage": 18.00,
            "taxableValue": 900.00,
            "taxAmount": 162.00,
            "total": 1062.00
          }
        ]
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "first": true
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### GET /invoices/{id}

**Response (200):**
```json
{
  "success": true,
  "message": "Invoice retrieved successfully",
  "data": {
    "id": 1,
    "invoiceNumber": "ACME-001",
    "invoiceType": "TAX_INVOICE",
    "customerId": 1,
    "customerName": "Rajesh Kumar",
    "invoiceDate": "2026-02-18",
    "dueDate": "2026-02-28",
    "subtotal": 12500.00,
    "taxAmount": 2250.00,
    "grandTotal": 14750.00,
    "paymentTerms": "Net 10 Days",
    "notes": "Thank you for your business",
    "status": "DRAFT",
    "placeOfSupply": "Karnataka",
    "deliveryNote": "Door delivery",
    "referenceNumber": "PO-2026-001",
    "buyerOrderNumber": "BO-2026-001",
    "dispatchDocNumber": "DD-001",
    "dispatchedThrough": "Self",
    "termsOfDelivery": "FOB",
    "otherReferences": "Quotation Q-001",
    "destination": "Bangalore",
    "createdBy": 1,
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:00",
    "items": [
      {
        "id": 1,
        "productId": 1,
        "itemName": "1 inch CPVC Pipe",
        "hsn": "39172390",
        "qty": 20,
        "rate": 580.00,
        "gstPercentage": 18.00,
        "taxableValue": 11600.00,
        "taxAmount": 2088.00,
        "total": 13688.00
      },
      {
        "id": 2,
        "productId": 2,
        "itemName": "1 inch CPVC Elbow",
        "hsn": "39172390",
        "qty": 30,
        "rate": 30.00,
        "gstPercentage": 18.00,
        "taxableValue": 900.00,
        "taxAmount": 162.00,
        "total": 1062.00
      }
    ]
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### PUT /invoices/{id}

**Request:**
```json
{
  "customerId": 1,
  "invoiceType": "TAX_INVOICE",
  "invoiceDate": "2026-02-18",
  "dueDate": "2026-03-10",
  "placeOfSupply": "Karnataka",
  "paymentTerms": "Net 20 Days",
  "notes": "Updated payment terms",
  "status": "PENDING",
  "deliveryNote": "Door delivery",
  "referenceNumber": "PO-2026-001",
  "buyerOrderNumber": "BO-2026-001",
  "dispatchDocNumber": "DD-001",
  "dispatchedThrough": "Self",
  "termsOfDelivery": "FOB",
  "otherReferences": "Quotation Q-001",
  "destination": "Bangalore",
  "items": [
    {
      "productId": 1,
      "itemName": "1 inch CPVC Pipe",
      "hsn": "39172390",
      "qty": 25,
      "rate": 580.00,
      "gstPercentage": 18.00
    },
    {
      "productId": 2,
      "itemName": "1 inch CPVC Elbow",
      "hsn": "39172390",
      "qty": 30,
      "rate": 30.00,
      "gstPercentage": 18.00
    }
  ]
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Invoice updated successfully",
  "data": {
    "id": 1,
    "invoiceNumber": "ACME-001",
    "invoiceType": "TAX_INVOICE",
    "customerId": 1,
    "customerName": "Rajesh Kumar",
    "invoiceDate": "2026-02-18",
    "dueDate": "2026-03-10",
    "subtotal": 15400.00,
    "taxAmount": 2772.00,
    "grandTotal": 18172.00,
    "paymentTerms": "Net 20 Days",
    "notes": "Updated payment terms",
    "status": "PENDING",
    "placeOfSupply": "Karnataka",
    "deliveryNote": "Door delivery",
    "referenceNumber": "PO-2026-001",
    "buyerOrderNumber": "BO-2026-001",
    "dispatchDocNumber": "DD-001",
    "dispatchedThrough": "Self",
    "termsOfDelivery": "FOB",
    "otherReferences": "Quotation Q-001",
    "destination": "Bangalore",
    "createdBy": 1,
    "createdAt": "2026-05-22T12:00:00",
    "updatedAt": "2026-05-22T12:00:01",
    "items": [
      {
        "id": 3,
        "productId": 1,
        "itemName": "1 inch CPVC Pipe",
        "hsn": "39172390",
        "qty": 25,
        "rate": 580.00,
        "gstPercentage": 18.00,
        "taxableValue": 14500.00,
        "taxAmount": 2610.00,
        "total": 17110.00
      },
      {
        "id": 4,
        "productId": 2,
        "itemName": "1 inch CPVC Elbow",
        "hsn": "39172390",
        "qty": 30,
        "rate": 30.00,
        "gstPercentage": 18.00,
        "taxableValue": 900.00,
        "taxAmount": 162.00,
        "total": 1062.00
      }
    ]
  },
  "timestamp": "2026-05-22T12:00:00"
}
```

---

### DELETE /invoices/{id}

**Response (200):**
```json
{
  "success": true,
  "message": "Invoice deleted successfully",
  "timestamp": "2026-05-22T12:00:00"
}
```

---

## Health Check

### GET /actuator/health

**Response (200):**
```json
{
  "status": "UP"
}
```

### GET /actuator/info

**Response (200):**
```json
{
  "app": {
    "name": "Inside Invoice",
    "description": "SaaS Multi-Tenant Invoicing Platform",
    "version": "1.0.0"
  }
}
```

---

## Error Responses

### Validation Error (400)
```json
{
  "timestamp": "2026-05-22T12:00:00",
  "status": 400,
  "error": "Validation Failed",
  "fieldErrors": {
    "email": "Email must be valid",
    "name": "Name is required"
  }
}
```

### Not Found (404)
```json
{
  "success": false,
  "message": "Customer not found with id: '999'",
  "timestamp": "2026-05-22T12:00:00"
}
```

### Unauthorized (401)
```json
{
  "success": false,
  "message": "Unauthorized. Please provide a valid JWT token.",
  "timestamp": "2026-05-22T12:00:00"
}
```

### Forbidden (403) - Cross-Business Access
```json
{
  "success": false,
  "message": "Business access violation: expected businessId 2 but user belongs to businessId 1",
  "timestamp": "2026-05-22T12:00:00"
}
```

### Conflict (409) - Duplicate
```json
{
  "success": false,
  "message": "User already exists with email: 'john@example.com'",
  "timestamp": "2026-05-22T12:00:00"
}
```

---

## Invoice Numbering

Format: `{PREFIX}-{SEQUENCE}`

Examples: `ACME-001`, `GE-002`, `ACMEPL-003`

- Each business has its own prefix and sequence counter
- Sequence auto-increments safely using pessimistic locks
- Sequence resets per business, not globally

## Invoice Statuses

| Status | Description |
|--------|-------------|
| `DRAFT` | Invoice created, not yet finalized |
| `PENDING` | Invoice sent to customer, awaiting payment |
| `PAID` | Payment received |
| `CANCELLED` | Invoice cancelled |

## Invoice Types

| Type | Description |
|------|-------------|
| `TAX_INVOICE` | Standard GST tax invoice |
| `PROFORMA_INVOICE` | Quotation / Proforma invoice |
