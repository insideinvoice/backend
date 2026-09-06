#!/bin/bash

# Start PostgreSQL locally for Inside Invoice development
# Automatically sets environment variables for local development
# Just run: ./start-local.sh

set -e

# Check if PostgreSQL is already running
echo "📦 Checking PostgreSQL..."
if pg_isready -h localhost -p 5432 &> /dev/null; then
    echo "✅ PostgreSQL is already running"
else
    echo "⏳ Starting PostgreSQL..."
    brew services start postgresql@16
    sleep 2

    if ! pg_isready -h localhost -p 5432 &> /dev/null; then
        echo "❌ PostgreSQL failed to start"
        exit 1
    fi
    echo "✅ PostgreSQL started"
fi
echo ""

# Create user if not exists
echo "🔧 Setting up database and user..."
DB_ADMIN=$(whoami)
if ! psql -U "$DB_ADMIN" -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='insideinvoice';" | grep -q "1"; then
    if psql -U "$DB_ADMIN" -d postgres -c "CREATE USER insideinvoice WITH PASSWORD 'invoiceinside';"; then
        echo "✅ User 'insideinvoice' created"
    else
        echo "❌ Failed to create user 'insideinvoice'"
        exit 1
    fi
else
    echo "✅ User 'insideinvoice' already exists"
fi

# Create database if not exists
if ! psql -U "$DB_ADMIN" -d postgres -tAc "SELECT datname FROM pg_database WHERE datname='inside_invoice';" | grep -q "inside_invoice"; then
    if psql -U "$DB_ADMIN" -d postgres -c "CREATE DATABASE inside_invoice OWNER insideinvoice;"; then
        echo "✅ Database 'inside_invoice' created"
    else
        echo "❌ Failed to create database 'inside_invoice'"
        exit 1
    fi
else
    echo "✅ Database 'inside_invoice' already exists"
fi

# Grant privileges and permissions
psql -U "$DB_ADMIN" -d postgres -c "ALTER USER insideinvoice WITH SUPERUSER CREATEDB;" || true
psql -U "$DB_ADMIN" -d inside_invoice -c "GRANT ALL PRIVILEGES ON DATABASE inside_invoice TO insideinvoice;" || true
psql -U "$DB_ADMIN" -d inside_invoice -c "GRANT ALL ON SCHEMA public TO insideinvoice;" || true
psql -U "$DB_ADMIN" -d inside_invoice -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO insideinvoice;" || true
psql -U "$DB_ADMIN" -d inside_invoice -c "GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO insideinvoice;" || true
psql -U "$DB_ADMIN" -d inside_invoice -c "GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO insideinvoice;" || true
psql -U "$DB_ADMIN" -d inside_invoice -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO insideinvoice;" || true
psql -U "$DB_ADMIN" -d inside_invoice -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO insideinvoice;" || true
psql -U "$DB_ADMIN" -d inside_invoice -c "ALTER DEFAULT PRIVILEGES FOR ROLE insideinvoice IN SCHEMA public GRANT ALL ON TABLES TO insideinvoice;" || true
psql -U "$DB_ADMIN" -d inside_invoice -c "ALTER DEFAULT PRIVILEGES FOR ROLE insideinvoice IN SCHEMA public GRANT ALL ON SEQUENCES TO insideinvoice;" || true
# Transfer ownership of all tables and sequences to insideinvoice
psql -U "$DB_ADMIN" -d inside_invoice -c "
DO \$\$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE 'ALTER TABLE public.' || quote_ident(r.tablename) || ' OWNER TO insideinvoice';
    END LOOP;
    FOR r IN (SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public') LOOP
        EXECUTE 'ALTER SEQUENCE public.' || quote_ident(r.sequence_name) || ' OWNER TO insideinvoice';
    END LOOP;
END \$\$;
" || true

echo ""

# Set environment variables
export DB_URL=jdbc:postgresql://localhost:5432/inside_invoice
export DB_USERNAME=insideinvoice
export DB_PASSWORD=invoiceinside
export SPRING_PROFILES_ACTIVE=dev

# Colors for terminal output
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo ""
echo -e "${GREEN}====================================================${NC}"
echo -e "${GREEN}🎉 PostgreSQL Started & Database Ready!${NC}"
echo -e "${GREEN}====================================================${NC}"
echo ""
echo -e "${YELLOW}${BOLD}📊 DBVIEWER / DBEAVER CONNECTION CREDENTIALS${NC}"
echo -e "----------------------------------------------------"
echo -e "  ${BOLD}Host:${NC}         ${CYAN}localhost${NC}"
echo -e "  ${BOLD}Port:${NC}         ${CYAN}5432${NC}"
echo -e "  ${BOLD}Database:${NC}     ${CYAN}inside_invoice${NC}"
echo -e "  ${BOLD}Username:${NC}     ${CYAN}insideinvoice${NC}"
echo -e "  ${BOLD}Password:${NC}     ${CYAN}invoiceinside${NC}"
echo ""
echo -e "  ${BOLD}JDBC URL:${NC}     ${CYAN}jdbc:postgresql://localhost:5432/inside_invoice${NC}"
echo -e "----------------------------------------------------"
echo ""
echo -e "${YELLOW}${BOLD}⚙️  ALTERNATIVE ADMIN CREDENTIALS (postgres)${NC}"
echo -e "----------------------------------------------------"
echo -e "  ${BOLD}Database:${NC}     ${CYAN}inside_invoice${NC} (or postgres)"
echo -e "  ${BOLD}Username:${NC}     ${CYAN}postgres${NC}"
echo -e "  ${BOLD}Password:${NC}     ${CYAN}(empty or postgres)${NC}"
echo -e "----------------------------------------------------"
echo ""
echo -e "${BOLD}💻 TERMINAL COMMANDS${NC}"
echo -e "----------------------------------------------------"
echo -e "  Connect via psql (insideinvoice user):"
echo -e "    psql -h localhost -p 5432 -U insideinvoice -d inside_invoice"
echo ""
echo -e "  Connect via psql (admin user):"
echo -e "    psql -h localhost -p 5432 -U postgres"
echo ""
echo -e "  Start Spring Boot app:"
echo -e "    source ./start-local.sh && mvn spring-boot:run"
echo -e "${GREEN}====================================================${NC}"
echo ""
