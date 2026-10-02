#!/bin/bash
# Restore Inside Invoice backup
# Usage: ./restore.sh <encrypted_file>

set -e

if [ -z "$1" ]; then
    echo "Usage: ./restore.sh <encrypted_file>"
    echo "Example: ./restore.sh insideinvoice-2026-09-27-00-37.dump.enc"
    exit 1
fi

ENCRYPTED_FILE="$1"
DECRYPTED_FILE="${ENCRYPTED_FILE%.enc}"
DB_HOST="localhost"
DB_PORT="5433"
DB_NAME="inside_invoice"
DB_USER="postgres"
DB_PASSWORD="postgres"

# Your encryption key (same as in application.yml)
ENCRYPTION_KEY="475cdadfe93e31872f0deddfa46d5b2cd7a04c2600e6c3d6663230f23be0f6b6"

echo "=== Step 1: Decrypt backup ==="
java -cp target/inside-invoice-1.0.0.jar \
  -Dbackup.encryption.key="$ENCRYPTION_KEY" \
  com.insideinvoice.backup.service.EncryptionService \
  "$ENCRYPTED_FILE" "$DECRYPTED_FILE" 2>/dev/null || \
python3 -c "
import sys
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import binascii

key = bytes.fromhex('$ENCRYPTION_KEY')

with open('$ENCRYPTED_FILE', 'rb') as f:
    data = f.read()

version = int.from_bytes(data[0:4], 'big')
iv = data[4:16]
ciphertext = data[16:]

aesgcm = AESGCM(key)
plaintext = aesgcm.decrypt(iv, ciphertext, None)

with open('$DECRYPTED_FILE', 'wb') as f:
    f.write(plaintext)

print(f'Decrypted: $DECRYPTED_FILE ({len(plaintext)} bytes)')
"

echo "=== Step 2: Restore to PostgreSQL ==="
PGPASSWORD=$DB_PASSWORD pg_restore \
  --verbose \
  --clean \
  --no-acl \
  --no-owner \
  -h $DB_HOST \
  -p $DB_PORT \
  -U $DB_USER \
  -d $DB_NAME \
  "$DECRYPTED_FILE"

echo "=== Step 3: Verify tables ==="
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "
SELECT 'users' as table_name, COUNT(*) as count FROM users
UNION ALL SELECT 'customers', COUNT(*) FROM customers
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'invoices', COUNT(*) FROM invoices
UNION ALL SELECT 'invoice_items', COUNT(*) FROM invoice_items
UNION ALL SELECT 'payments', COUNT(*) FROM payments
UNION ALL SELECT 'businesses', COUNT(*) FROM businesses;
"

echo "=== Step 4: Cleanup ==="
rm -f "$DECRYPTED_FILE"
echo "Done! Restored backup: $ENCRYPTED_FILE"
