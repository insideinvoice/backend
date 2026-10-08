package com.insideinvoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.entity.InvoiceStatus;
import com.insideinvoice.invoice.entity.InvoiceType;
import com.insideinvoice.invoice.repository.InvoiceItemRepository;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.invoice.share.InvoiceShareService;
import com.insideinvoice.payment.entity.Payment;
import com.insideinvoice.payment.repository.PaymentRepository;
import com.insideinvoice.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Base class for security/integration tests.
 *
 * <p>Database: profile {@code test} points only at localhost:5433/{@code inside_invoice_test}.
 * The static initializer hard-fails if that URL is ever non-local and (re)creates the scratch
 * database before the Spring context boots, so every run starts from a clean schema with the
 * real Flyway migrations (including V19). The Neon production database is never reachable
 * from this setup: {@link #testDatasource(DynamicPropertyRegistry)} pins the datasource at
 * the highest property precedence, so even a mis-applied profile cannot fall back to prod.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    protected static final BigDecimal QTY = new BigDecimal("2");
    protected static final BigDecimal RATE = new BigDecimal("100.00");

    static {
        ensureLocalTestDatabase();
    }

    @DynamicPropertySource
    static void testDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/inside_invoice_test");
        registry.add("spring.datasource.username", () -> "insideinvoice");
        registry.add("spring.datasource.password", () -> "invoiceinside");
        registry.add("backup.enabled", () -> "false");
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JwtTokenProvider jwtTokenProvider;
    @Autowired protected BusinessRepository businessRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected CustomerRepository customerRepository;
    @Autowired protected InvoiceRepository invoiceRepository;
    @Autowired protected InvoiceItemRepository invoiceItemRepository;
    @Autowired protected PaymentRepository paymentRepository;
    @Autowired protected InvoiceShareService invoiceShareService;

    protected String bearerFor(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getBusinessId(), user.getName());
    }

    protected User makeUser(String username, Role role) {
        Business business = businessRepository.save(Business.builder()
                .businessName(username + " Co")
                .ownerName(username)
                .invoicePrefix("T" + Math.abs(username.hashCode() % 100000))
                .nextInvoiceSequence(1L)
                .build());
        return userRepository.save(User.builder()
                .name(username)
                .username(username)
                .email(username + "@test.local")
                .password("not-used-in-these-tests")
                .rawPassword("not-used-in-these-tests")
                .role(role)
                .businessId(business.getId())
                .businessSetupCompleted(true)
                .build());
    }

    protected Customer seedCustomer(Long businessId) {
        return customerRepository.save(Customer.builder()
                .businessId(businessId)
                .name("Test Customer " + UUID.randomUUID().toString().substring(0, 8))
                .email("cust-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local")
                .phone("9999999999")
                .billingAddress("1 Test Street")
                .gstIn("29ABCDE1234F1Z5")
                .city("Bengaluru")
                .state("Karnataka")
                .country("India")
                .pincode("560001")
                .build());
    }

    protected Invoice seedInvoice(User owner) {
        Customer customer = seedCustomer(owner.getBusinessId());
        Invoice invoice = Invoice.builder()
                .businessId(owner.getBusinessId())
                .invoiceNumber("INV-TST-" + UUID.randomUUID().toString().substring(0, 12))
                .invoiceType(InvoiceType.TAX_INVOICE)
                .customerId(customer.getId())
                .invoiceDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(15))
                .subtotal(new BigDecimal("200.00"))
                .taxAmount(new BigDecimal("36.00"))
                .grandTotal(new BigDecimal("236.00"))
                .notes("Payment within 15 days")
                .placeOfSupply("Karnataka")
                .status(InvoiceStatus.PENDING)
                .createdBy(owner.getId())
                .items(new ArrayList<>())
                .build();

        InvoiceItem item = InvoiceItem.builder()
                .invoice(invoice)
                .sno(1)
                .itemName("Test Item")
                .hsn("998877")
                .qty(QTY)
                .rate(RATE)
                .gstPercentage(new BigDecimal("18"))
                .taxableValue(new BigDecimal("200.00"))
                .taxAmount(new BigDecimal("36.00"))
                .total(new BigDecimal("236.00"))
                .build();
        invoice.getItems().add(item);
        return invoiceRepository.save(invoice);
    }

    protected Payment seedPayment(Invoice invoice, Long businessId, BigDecimal amount) {
        return paymentRepository.save(Payment.builder()
                .businessId(businessId)
                .invoiceId(invoice.getId())
                .amount(amount)
                .paymentMode("UPI")
                .paymentDate(LocalDate.now())
                .build());
    }

    private static void ensureLocalTestDatabase() {
        String adminUrl = System.getProperty("test.db.admin-url", "jdbc:postgresql://localhost:5433/postgres");
        if (!adminUrl.contains("localhost") && !adminUrl.contains("127.0.0.1")) {
            throw new IllegalStateException(
                    "Refusing to run tests: admin URL is not localhost: " + adminUrl);
        }
        try (Connection connection = DriverManager.getConnection(adminUrl, "insideinvoice", "invoiceinside");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS inside_invoice_test");
            statement.execute("CREATE DATABASE inside_invoice_test");
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not (re)create local test database on localhost:5433 - is PostgreSQL running?", e);
        }
    }
}
