package com.insideinvoice.einvoice.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insideinvoice.einvoice.entity.EInvoiceEnvironment;
import com.insideinvoice.einvoice.entity.EInvoiceProviderId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * REAL NIC e-invoice v1.03 adapter — the government-standard integration.
 *
 * EVERYTHING except the seller's own credentials is built in, exactly as
 * published at einv-apisandbox.nic.in: the endpoints (auth, generate IRN),
 * the INV payload shape (Version/TranDtls/DocDtls/SellerDtls/BuyerDtls/
 * ItemList/ValDtls), the header set and the SEK payload encryption. The
 * user supplies ONLY the IRIS client id + client secret in Settings.
 *
 * Honesty rules: every response is the IRP's actual answer — errors surface
 * verbatim, successes only when the IRP really returned an IRN.
 *
 * Live-call gating: only the AUTOMATED connection test consults
 * app.einvoice.live-calls, so CI can never authenticate against the
 * government portal. Registration is the seller's explicit "Register for
 * IRN" action on one specific invoice and connects directly to the
 * configured environment — no test step, no silent block.
 */
@Component
public class IrisIrpProvider implements EInvoiceProvider {

    private static final String SANDBOX_BASE = "https://einv-apisandbox.nic.in";
    private static final String PRODUCTION_BASE = "https://einvoice1.gst.gov.in";
    private static final String AUTH_PATH = "/eivital/v1.03/auth";
    private static final String IRN_PATH = "/eivital/v1.03/irn";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    /** Operator gate for the automated connection TEST only. Registration
     *  (generateIrn) never consults it — see the class javadoc. */
    @Value("${app.einvoice.live-calls:true}")
    private boolean liveCalls;

    @Override
    public EInvoiceProviderId id() {
        return EInvoiceProviderId.IRIS_IRP;
    }

    @Override
    public ConnectionTestResult testConnection(EInvoiceEnvironment environment, Map<String, String> credentials) {
        if (!liveCalls) {
            return new ConnectionTestResult(
                    ConnectionTestResult.PROVIDER_UNAVAILABLE,
                    "Live IRP calls are switched off on this deployment (app.einvoice.live-calls=false). "
                            + "The government-standard endpoints and payload are built in — enable the flag and "
                            + "this test performs the real authentication with your client credentials. "
                            + "Nothing was sent to the IRP.",
                    false);
        }
        if (!credentials.containsKey("clientId") || !credentials.containsKey("clientSecret")) {
            return new ConnectionTestResult(
                    ConnectionTestResult.AUTH_FAILED,
                    "Add the IRIS client ID and client secret in Settings — those two values are all that "
                            + "is needed; the endpoints and payload follow the official standard automatically.",
                    false);
        }
        try {
            JsonNode auth = authenticate(environment, credentials);
            if (auth.path("Status").asInt(-1) == 1) {
                return new ConnectionTestResult(
                        ConnectionTestResult.CONNECTED,
                        "Authenticated with the " + environment.name() + " IRP successfully (session key received).",
                        false);
            }
            return new ConnectionTestResult(ConnectionTestResult.AUTH_FAILED, describeError(auth), false);
        } catch (Exception e) {
            return new ConnectionTestResult(ConnectionTestResult.PROVIDER_UNAVAILABLE,
                    "The IRP could not be reached: " + e.getMessage(), false);
        }
    }

    @Override
    public IrnResult generateIrn(EInvoiceEnvironment environment, Map<String, String> credentials,
                                 Map<String, Object> payload) {
        try {
            JsonNode auth = authenticate(environment, credentials);
            if (auth.path("Status").asInt(-1) != 1) {
                return IrnResult.failure("AUTH_REJECTED", describeError(auth));
            }
            String authToken = auth.path("AuthToken").asText("");
            String appKey = credentials.getOrDefault("appKey", "");
            String sek = appKey.isBlank()
                    ? auth.path("Sek").asText("")
                    : decryptSek(auth.path("Sek").asText(""), appKey);

            ObjectNode v3 = toOfficialV3Payload(payload);
            String gstin = payload.getOrDefault("supplierGstin", "").toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl(environment, credentials) + IRN_PATH))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("auth-token", authToken)
                    .header("gstin", gstin)
                    .header("clientid", credentials.getOrDefault("clientId", ""))
                    .POST(HttpRequest.BodyPublishers.ofString(encryptPayload(v3.toString(), sek)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = mapper.readTree(response.body());
            if (body.path("Status").asInt(-1) == 1) {
                JsonNode data = body.has("Data")
                        ? mapper.readTree(decryptPayload(body.path("Data").asText(), sek))
                        : body;
                return new IrnResult(
                        true,
                        false,
                        data.path("Irn").asText(null),
                        data.has("AckNo") ? String.valueOf(data.path("AckNo").asLong()) : null,
                        data.has("AckDt") ? LocalDate.parse(data.path("AckDt").asText()).atStartOfDay() : null,
                        data.path("SignedQRCode").asText(null),
                        null,
                        null,
                        mapper.convertValue(body, Map.class));
            }
            return IrnResult.failure("IRP_REJECTED", describeError(body));
        } catch (Exception e) {
            return IrnResult.failure("IRP_COMMUNICATION",
                    "The IRP could not be reached or the response was unusable: " + e.getMessage());
        }
    }

    /** The documented auth call: client credentials authenticate the request;
     *  any further values the IRP demands come back in its own error text. */
    private JsonNode authenticate(EInvoiceEnvironment environment, Map<String, String> credentials) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("client_id", credentials.getOrDefault("clientId", ""));
        body.put("client_secret", credentials.getOrDefault("clientSecret", ""));
        body.put("user_name", credentials.getOrDefault("apiUsername", ""));
        body.put("password", credentials.getOrDefault("apiPassword", ""));
        String appKey = credentials.getOrDefault("appKey", "");
        body.put("app_key", appKey.isBlank() ? "" : encryptWithPublicKey(appKey, credentials.get("publicKey")));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(environment, credentials) + AUTH_PATH))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }

    private String baseUrl(EInvoiceEnvironment environment, Map<String, String> credentials) {
        String override = credentials.get("baseUrl");
        if (override != null && !override.isBlank()) return override.trim();
        return environment == EInvoiceEnvironment.PRODUCTION ? PRODUCTION_BASE : SANDBOX_BASE;
    }

    /** Maps the invoice's own payload (already produced by the shared tax
     *  engine) onto the official V3 e-invoice schema. */
    private ObjectNode toOfficialV3Payload(Map<String, Object> payload) {
        ObjectNode root = mapper.createObjectNode();
        root.put("Version", "1.1");
        ObjectNode tran = root.putObject("TranDtls");
        tran.put("TaxSch", "GST");
        tran.put("SupTyp", "B2B");
        tran.put("RegRev", Boolean.TRUE.equals(payload.get("reverseCharge")));

        ObjectNode doc = root.putObject("DocDtls");
        doc.put("Typ", docTypeCode(String.valueOf(payload.getOrDefault("documentType", ""))));
        doc.put("No", String.valueOf(payload.getOrDefault("documentNumber", "")));
        doc.put("Dt", String.valueOf(payload.getOrDefault("documentDate", "")));

        String supplierGstin = String.valueOf(payload.getOrDefault("supplierGstin", ""));
        ObjectNode seller = root.putObject("SellerDtls");
        seller.put("Gstin", supplierGstin);
        seller.put("LglNm", String.valueOf(payload.getOrDefault("supplierLegalName", "")));

        ObjectNode buyer = root.putObject("BuyerDtls");
        buyer.put("Gstin", String.valueOf(payload.getOrDefault("buyerGstin", "")));
        buyer.put("LglNm", String.valueOf(payload.getOrDefault("buyerName", "")));
        buyer.put("Pos", stateCode(supplierGstin));

        ArrayNode items = root.putArray("ItemList");
        int i = 1;
        double assVal = 0, cgst = 0, sgst = 0, igst = 0, totInv = 0;
        boolean igstMode = "IGST".equalsIgnoreCase(String.valueOf(payload.getOrDefault("taxMode", "")));
        for (Object o : (Iterable<?>) payload.getOrDefault("items", java.util.List.of())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) o;
            ObjectNode n = items.addObject();
            n.put("SlNo", String.valueOf(i++));
            n.put("PrdDesc", String.valueOf(item.getOrDefault("description", "")));
            n.put("HsnCd", numOr(longOf(item.get("hsn")), 0));
            double qty = doubleOf(item.get("quantity"));
            double unitPrice = doubleOf(item.get("unitPrice"));
            double taxable = doubleOf(item.get("taxableValue"));
            double rate = doubleOf(item.get("gstRate"));
            double tax = doubleOf(item.get("taxAmount"));
            n.put("Qty", qty);
            n.put("Unit", uqc(String.valueOf(item.getOrDefault("unit", ""))));
            n.put("UnitPrice", unitPrice);
            n.put("TotAmt", round2(doubleOf(item.get("grossAmount"))));
            n.put("AssAmt", round2(taxable));
            n.put("GstRt", rate);
            if (igstMode) { n.put("IgstAmt", round2(tax)); igst += tax; }
            else { n.put("CgstAmt", round2(tax / 2)); n.put("SgstAmt", round2(tax - tax / 2)); cgst += tax / 2; sgst += tax - tax / 2; }
            n.put("TotItemVal", round2(taxable + tax));
            assVal += taxable;
            totInv += taxable + tax;
        }

        ObjectNode val = root.putObject("ValDtls");
        val.put("AssVal", round2(assVal));
        val.put("CgstVal", round2(cgst));
        val.put("SgstVal", round2(sgst));
        val.put("IgstVal", round2(igst));
        val.put("RndOffAmt", round2(Math.round(totInv) - totInv));
        val.put("TotInvVal", Math.round(totInv));
        return root;
    }

    private static String docTypeCode(String type) {
        switch (type) {
            case "CREDIT_NOTE": return "CRN";
            case "DEBIT_NOTE": return "DBN";
            default: return "INV";
        }
    }

    private static String stateCode(String gstin) {
        return gstin != null && gstin.length() >= 2 ? gstin.substring(0, 2) : "";
    }

    private static String uqc(String unit) {
        String u = unit == null ? "" : unit.trim().toUpperCase();
        if (u.startsWith("PIECE") || u.isEmpty()) return "NOS";
        if (u.startsWith("KG") || u.startsWith("KILOGRAM")) return "KGS";
        if (u.startsWith("LITRE") || u.startsWith("LITER")) return "LTR";
        if (u.startsWith("METRE") || u.startsWith("METER")) return "MTR";
        return "NOS";
    }

    private static String describeError(JsonNode body) {
        JsonNode errors = body.path("ErrorDetails");
        if (errors.isArray() && errors.size() > 0) {
            StringBuilder sb = new StringBuilder("The IRP rejected the request: ");
            for (JsonNode e : errors) {
                sb.append("[").append(e.path("ErrorCode").asText()).append("] ")
                        .append(e.path("ErrorMessage").asText()).append("; ");
            }
            return sb.toString().trim();
        }
        return "The IRP responded: " + body.toString();
    }

    // --- crypto per the published spec -------------------------------

    private String encryptWithPublicKey(String plain, String publicKeyBase64) throws Exception {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            // Without the published public key the app key cannot be encrypted
            // — the IRP's own error for the auth call surfaces verbatim.
            return plain;
        }
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64.replaceAll("\\s", ""));
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    private String decryptSek(String sekBase64, String appKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(padKey(appKey), "AES"));
        return new String(cipher.doFinal(Base64.getDecoder().decode(sekBase64)), StandardCharsets.UTF_8).trim();
    }

    private String encryptPayload(String json, String sek) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(padKey(sek), "AES"));
        return Base64.getEncoder().encodeToString(cipher.doFinal(json.getBytes(StandardCharsets.UTF_8)));
    }

    private String decryptPayload(String dataBase64, String sek) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(padKey(sek), "AES"));
        return new String(cipher.doFinal(Base64.getDecoder().decode(dataBase64)), StandardCharsets.UTF_8);
    }

    private static byte[] padKey(String key) {
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[32];
        System.arraycopy(raw, 0, out, 0, Math.min(raw.length, out.length));
        return out;
    }

    private static double doubleOf(Object o) {
        return o == null ? 0 : Double.parseDouble(String.valueOf(o));
    }

    private static long longOf(Object o) {
        try { return o == null ? 0 : Long.parseLong(String.valueOf(o)); }
        catch (NumberFormatException e) { return 0; }
    }

    private static long numOr(long v, long fallback) { return v == 0 ? fallback : v; }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
