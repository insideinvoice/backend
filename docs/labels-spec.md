ROLE
You are a senior full-stack engineer (React, Spring Boot, PostgreSQL) and a print/label-engineering specialist. You are extending an EXISTING production-bound app called "Inside Invoice". Do NOT break, rename, or refactor any existing feature, route, table, or style. Inspect the repo first, follow its existing conventions (folder structure, naming, auth, API client, UI components, theme, error handling), and add this as a NEW, SEPARATE module.

STACK
- Frontend: React (reuse existing router, layout, sidebar, UI kit, API client, toasts, tables)
- Backend: Spring Boot (Java 17+), Maven/Gradle as already used
- DB: PostgreSQL (use the existing migration tool: Flyway/Liquibase; if none, add Flyway)
- PDF: Apache PDFBox 3.x (preferred, vector drawing) — or OpenPDF if PDFBox is already absent and OpenPDF is present. Barcodes: ZXing core, rendered as VECTOR rectangles into the PDF (never raster images for barcodes).

FEATURE OVERVIEW
Add a new module "Labels" with two label types: (1) Shipping Labels, (2) Hazmat (Dangerous Goods) Labels. Output is print-ready PDF designed for direct thermal label printers (Zebra ZD220/ZD420/ZT410, Rollo, Dymo 4XL, TSC, Brother QL-1100) used to stick labels on carton boxes, the same way Amazon Seller Central / FBA box labels and carrier labels (UPS/FedEx/USPS) are laid out.

SIDEBAR (add a new group "Labels", matching the existing sidebar style, icons, active state, collapsed state, and mobile drawer)
- Shipping Label  -> /labels/shipping/new   (create form + live preview)
- View Shipping   -> /labels/shipping       (list of stored shipping labels)
- Hazmat Label    -> /labels/hazmat/new     (create form + live preview)
- View Hazmat     -> /labels/hazmat         (list of stored hazmat labels)
Respect existing role/permission checks. Add permissions like LABEL_CREATE, LABEL_VIEW, LABEL_DELETE if the app has a permission model.

=====================================================
PART A — PHYSICAL SPECIFICATION (PIXEL-PERFECT RULES)
=====================================================
Units: build every layout in MILLIMETERS/INCHES in a single constants file, convert to PDF points (1 in = 72 pt, 1 mm = 2.8346457 pt). Origin handling: PDFBox origin is bottom-left; write a helper that lets me specify coordinates from TOP-left in mm.

Printer targets
- Default: 203 DPI thermal (8 dots/mm). Optional 300 DPI mode (11.81 dots/mm). 
- All coordinates, line weights, font sizes and barcode module widths must snap to the printer dot grid so nothing blurs: at 203 DPI, 1 dot = 0.125 mm = 0.354 pt. Round every x/y/width/height to a multiple of 1 dot. Minimum line weight 1 dot at 203 DPI (use 2 dots for borders = 0.25 mm). 
- PDF page size == label size exactly, no margins added by the PDF, zero scaling. Set the PDF viewer preference PrintScaling = None and embed so printing is "Actual size". Set MediaBox = CropBox = TrimBox = label size.
- Pure black (#000000, DeviceGray 0 / K=100) for everything on thermal output EXCEPT hazmat diamond color fills (see Part C). Provide a "Thermal B/W mode" toggle that renders hazmat diamonds as black outline + class symbol + hatch/pattern so they print on monochrome thermal printers, and a "Colour mode" for colour printers/label printers (Epson ColorWorks, Brother, Afinia).
- No transparency, no gradients, no shadows, no rounded raster images. Vector only (except an optional logo, which must be 1-bit dithered at the printer DPI when B/W mode is on).

Supported label sizes (selectable per label, default marked):
 Shipping: 
 - 4 x 6 in (101.6 x 152.4 mm) -> DEFAULT (Amazon/UPS/FedEx/USPS/DHL standard) = 288 x 432 pt
 - 4 x 8 in, 4 x 4 in, 4 x 3 in, 100 x 150 mm, 100 x 100 mm (A6-ish)
 - A4 sheet with 2-up / 4-up layout (two 4x6 / four A6 per page) for laser/inkjet users
 - US Letter 2-up (Amazon "2 labels per sheet" 8.5 x 11 style)
 Hazmat:
 - 4 x 4 in (101.6 x 101.6 mm) diamond label on a square label (DEFAULT)
 - 4 x 6 in with diamond top + proper shipping name / UN block below
 - 100 x 100 mm (DOT/IATA minimum diamond size is 100 mm x 100 mm)
 - Optional 50 x 50 mm (for small packages; limited-quantity / excepted-quantity min 50 mm)

Typography
- Embed fonts (subset) in the PDF; do not rely on system fonts. Use Liberation Sans or Arimo (metric-compatible with Arial/Helvetica, which is what Amazon/carrier labels use) for body, bold weight for headers. Use "OCR-B" or the same Arimo for human-readable barcode text. Bundle TTFs in resources and also support Devanagari/Arabic fallbacks only if the address contains those scripts (fallback font Noto Sans) so Indian addresses don't render as boxes.
- Size scale (203 DPI-safe, in points): ship-to name 14–16 bold, address 11–12 bold uppercase, ship-from 7–8 regular, service/zone box 20–28 bold, tracking text 9, barcode text 8–9, footer 6. Do NOT go below 6 pt.
- Auto-fit: if an address line is too long, shrink font in 0.5 pt steps down to a floor, then wrap to max lines; never let text overflow its box. Implement a text-fitting helper and unit test it.
- All ship-to text uppercase, left-aligned, line-height 1.15.

Barcodes (critical)
- Linear: Code 128 (Set B/C auto-optimization) for tracking/reference; GS1-128 (with FNC1 and Application Identifiers, e.g. (00) SSCC-18, (420) ship-to postal, (91)/(92) internal) for logistics labels.
- 2D: GS1 DataMatrix or QR (for URL/invoice link) and PDF417 for carrier-style routing code (optional, behind a flag), Data Matrix quiet zone 1 module.
- Module width (X-dimension): minimum 2 dots at 203 DPI = 0.25 mm; default 3 dots = 0.375 mm (≈15 mil) for the tracking barcode; scale so the barcode fits the printable width but ALWAYS an integer number of dots per module (no fractional module widths — this is what makes scanning perfect).
- Bar height: tracking barcode ≥ 15% of symbol length and ≥ 12.7 mm (0.5 in); default 20–25 mm.
- Quiet zone: ≥ 10 modules each side for Code 128.
- Draw bars as filled rectangles (vector). Human-readable text centered below with 1.5 mm gap.
- Validate: compute and verify the SSCC-18 check digit (mod 10 / GS1 algorithm) and GTIN-14/UPC check digits; reject invalid input with a clear error.
- Add a backend unit test that decodes each generated barcode back with ZXing (render PDF -> 300 DPI image via PDFBox -> ZXing decode) and asserts the decoded value equals the input.

=====================================================
PART B — SHIPPING LABEL LAYOUT (4x6 in, portrait, Amazon/carrier style)
=====================================================
Draw on a 101.6 x 152.4 mm canvas with 3 mm (24 dots) safe inner margin and a 0.25 mm black outer keyline OFF by default (toggle "Print border" for laser printing). Layout zones from the TOP, all separated by 0.5 mm solid black rules:

Zone 1 — Header strip (height 16 mm)
 - Left: "FROM:" label (6 pt bold) then sender name (bold), company, address lines, city/state/PIN, country, phone (7–8 pt). 
 - Right (width 35 mm): carrier logo placeholder box / carrier name text (e.g., "UPS GROUND", "FEDEX EXPRESS", "DHL", "BLUE DART", "DELHIVERY", "INDIA POST" — configurable), ship date "SHIP DATE: DD MMM YYYY", weight "WT: 2.50 KG", dims "DIM: 40x30x20 CM", "CARTON 1 OF 3".

Zone 2 — Ship-to block (height ~38 mm)
 - Left vertical caption "SHIP TO:" rotated 90° (6 pt) OR top-left caption.
 - Recipient name 14–16 pt bold UPPERCASE; company; address lines 11–12 pt bold; "CITY STATE PIN" line larger; country; phone 8 pt.
 - Right side box (30 x 30 mm): large service code / sort code (e.g., "ND", "2D", "GND", route code) in 28–36 pt bold, white-on-black inverse box for the primary service indicator (as UPS/FedEx do), with the destination postal/PIN under it.

Zone 3 — Routing / 2D code row (height ~30 mm)
 - Left: 2D code (GS1 DataMatrix or MaxiCode-style area; default Data Matrix 22 x 22 mm) encoding ship-to postal + country + tracking + service.
 - Right: Tracking number text ("TRACKING #: 1Z 999 AA1 01 2345 6784" grouped format per carrier), service name, billing ("PREPAID" / "COD ₹ AMOUNT" / "BILL TO THIRD PARTY"), reference fields: "INV: INV-0001", "PO: ...", "REF: ...".

Zone 4 — Main tracking barcode (height ~42 mm, full width, THE hero element)
 - Code 128 barcode full printable width (≥ 90 mm), height 24 mm, human readable text underneath 9 pt bold, then carrier-style "TRACKING #:" line. Add (Amazon-style) FBA fields when label type = FBA box: "FBA SHIPMENT ID: FBA15XXXXXXX", "BOX ID: FBA15XXXXXXXU000001" (shipment ID + U + 6-digit box number), "FROM: <warehouse code>", "CREATED: date", "SHIP TO: <FC code, e.g., BLR7>", "PRODUCT UNITS: n", with the Box ID Code 128 barcode.

Zone 5 — Footer (height ~8 mm)
 - Left: small "Generated by Inside Invoice" 5.5–6 pt; centre: carrier compliance text (optional); right: label id / page "1/1", and print timestamp.
 - Handling icon row (optional, toggles): "THIS WAY UP" arrows, "FRAGILE" (wine-glass), "KEEP DRY" (umbrella), "DO NOT STACK", "HANDLE WITH CARE" drawn as vector paths per ISO 780 pictograms, each 10 x 10 mm.

Also support these shipping label presets (select in a dropdown, each is its own layout class implementing a common LabelRenderer interface):
 1. "Standard Carrier 4x6" (above)
 2. "Amazon FBA Box Label" (4x6, Box ID + shipment ID + FC code, Amazon-style typography and bold ship-to FC block)
 3. "GS1-128 SSCC Pallet/Carton Logistics Label" (4x6, SSCC-18 barcode with (00) AI, (02) GTIN, (37) count, (10) batch/lot, (15)/(17) dates, (400) PO, (420) postal; human-readable AI text per GS1 General Specifications; ship-from/ship-to areas per GS1 Logistic Label guidelines)
 4. "Simple Address Label 4x6 / A6" (minimal)
 5. "Return Label" (reverse ship-from/ship-to, "RETURN" banner)
 6. "FNSKU / Product Label 30-up (Avery 5167/ 2-5/8 x 1 in)" for Amazon item labels: FNSKU Code 128 + product title (first 50 chars) + condition (e.g., NEW), 66.7 x 25.4 mm cells, 30 per US Letter sheet, 3 mm gaps etc. (optional but include if time permits)

Multi-carton: user enters "Number of ccartons" N -> generate N labels, each with "CARTON i OF N", sequential box IDs/SSCC serials (increment the serial reference of SSCC and recompute the check digit), combined into ONE multi-page PDF (one label per page) and also downloadable individually.

=====================================================
PART C — HAZMAT / DANGEROUS GOODS LABEL LAYOUT
=====================================================
Follow 49 CFR 172 (US DOT), IATA DGR, and IMDG/UN Model Regulations. Labels are SQUARE-ON-POINT DIAMONDS, minimum 100 x 100 mm, with:
 - Outer diamond edge, then a solid inner border line 5 mm inside the edge (line weight ~ 0.75–1 mm; make the line weight 1 mm at full size and scale proportionally for other sizes) — exact scaling rule: for a diamond of side S (default S = 100 mm), inner border offset = 0.05 * S, line width = 0.01 * S (min 2 dots), hazard-class numeral height = 0.127 * S (min 12.7 mm at 100 mm), symbol height = ~0.30 * S placed in the upper half, text (class name / division) in the middle band, class numeral centered in the lower corner (underlined for class 9), compatibility group letter for class 1 beside the division number. Corners rounded with radius 0 (sharp) — keep it a true square rotated 45°.
 - Rotate the square 45° (diamond orientation) and center it on the label; the label rectangle is the bounding box if "diamond only" 4x4 in mode is used.

Provide ALL of these label definitions as data (a JSON/enum config + SVG-like vector path drawing code), each fully drawn with vector paths (no raster):
 Class 1 Explosives — orange (#FF6600 / Pantone 151), black exploding-bomb symbol, divisions 1.1–1.6 with compatibility group letters (A–S), "1.4G", etc.
 Class 2.1 Flammable Gas — red (#E4002B), white or black flame, "2"
 Class 2.2 Non-Flammable, Non-Toxic Gas — green (#00A651), gas cylinder, "2"
 Class 2.3 Toxic Gas — white, skull & crossbones, "2"
 Class 3 Flammable Liquid — red, flame, "3"
 Class 4.1 Flammable Solid — white with 7 vertical red stripes, flame, "4"
 Class 4.2 Spontaneous Combustible — upper half white / lower half red, flame, "4"
 Class 4.3 Dangerous When Wet — blue (#0072BC), flame, "4"
 Class 5.1 Oxidizer — yellow (#FFD700), flame over circle, "5.1"
 Class 5.2 Organic Peroxide — upper half red / lower half yellow, flame, "5.2"
 Class 6.1 Toxic — white, skull & crossbones, "6"
 Class 6.2 Infectious Substance — white, three crescents biohazard symbol, with required text "INFECTIOUS SUBSTANCE – In case of damage or leakage notify public health authority" (as in the IATA/DOT label)
 Class 7 Radioactive I (white), II and III (upper half yellow / lower half white), trefoil, with RADIOACTIVE text, contents/activity/transport index boxes
 Class 8 Corrosive — upper half white / lower half black, test tubes dripping on hand & metal, "8" in white
 Class 9 Miscellaneous — upper half white with 7 black vertical stripes, "9" underlined
 Class 9A Lithium Battery Mark (rectangular, 100 x 100 mm, red hatched border, battery symbol, "UN3480/3481/3090/3091", phone number for info) and the new Lithium battery handling label per IATA DGR 2022+ 
 Limited Quantity mark (diamond, upper and lower corners black, "Y" for air limited quantity)
 Excepted Quantity mark (square with hatched red border and "*" / "E" for class and name of consignor)
 Environmentally Hazardous Substance mark (fish and tree)
 Orientation arrows (two red/black arrows, rectangular 74 x 105 mm and 105 x 148 mm — IATA 7.2.3.1)
 Cargo Aircraft Only (CAO) orange rectangle label 110 x 120 mm
 Magnetized material, Dry Ice (UN1845 Class 9), Keep away from heat, Fragile, Danger/Hazard "Overpack" marking

Hazmat label data fields (form):
 - UN/ID number (validated 4-digit, "UN" prefix auto-added, pulled from an in-app UN number table you seed with the 500 most common substances: UN number, proper shipping name, class/division, subsidiary risks, packing group, special provisions, limited quantity limit, passenger/cargo aircraft allowed Y/N, ERG guide #). Provide autocomplete on UN number or name.
 - Proper Shipping Name, Technical name (in parentheses where required), Packing Group (I/II/III), Net quantity (e.g., "2 L"), number of packages, Consignor/Consignee names & addresses, Emergency contact 24-h phone (CHEMTREC / local), SDS reference, "Overpack" checkbox, Marine pollutant checkbox, Transport mode (Road ADR / Air IATA / Sea IMDG / Rail), Lithium battery details (Wh rating, packing instruction 965–970 section IA/IB/II).
 - Hazmat "Package Marking Block" below the diamond on 4x6 layout: UN number in large bold (e.g., "UN1203"), Proper shipping name uppercase, Net Qty, Consignee/Consignor blocks, 24-hour emergency phone, plus ERG guide number.
 - Optionally also render a DOT "placard-style" 250 x 250 mm vehicle placard as a separate A4/A3 PDF (class diamond + 4-digit UN number in the 15–25 mm black-bordered orange panel (UN ID panel 300 x 120 mm)).

Thermal B/W fallback: for monochrome thermal printing, render the diamond with black 1 mm outer + inner border, the class symbol in solid black, class numeral, and the colour identified by a fill pattern (stripes for 4.1, half-fill for 4.2/5.2/8, etc.); show a small text "COLOR: RED" under the label OR put a coloured dot symbol only in colour mode. Make this toggle prominent in the UI.

=====================================================
PART D — BACKEND (Spring Boot)
=====================================================
Package: com.insideinvoice.labels (adapt to the app's base package).
- Entities: ShippingLabel, HazmatLabel, LabelAddress (embedded), LabelFile (PDF binary).
- Tables (Flyway migrations):
  shipping_labels(id UUID pk, company_id/tenant_id (follow existing multi-tenant column if exists), invoice_id nullable FK, label_number varchar unique, preset varchar, label_size varchar, dpi int, carrier, service_level, tracking_number, ship_date, from_json jsonb, to_json jsonb, package_weight_kg numeric, dims_cm jsonb, carton_index int, carton_total int, reference_fields jsonb, barcode_payloads jsonb, thermal_mode bool, status enum(DRAFT, GENERATED, PRINTED, VOID), pdf_path varchar OR pdf_bytes bytea, pdf_sha256 char(64), created_by, created_at, updated_at, deleted_at)
  hazmat_labels(id UUID pk, tenant, invoice_id nullable, label_number, un_number, proper_shipping_name, technical_name, hazard_class, division, compat_group, packing_group, subsidiary_risks jsonb, net_quantity, package_count, transport_mode, label_type enum(CLASS_DIAMOND, LITHIUM, LIMITED_QTY, EXCEPTED_QTY, ENV_HAZARD, ORIENTATION, CAO, OVERPACK, PLACARD), consignor_json, consignee_json, emergency_phone, erg_guide, lithium_wh numeric, label_size, color_mode enum(COLOR, THERMAL_BW), status, pdf_path/bytes, pdf_sha256, created_by, created_at, updated_at, deleted_at)
  un_numbers_reference(un_number pk, proper_shipping_name, class, division, compat_group, packing_group, subsidiary_risks, erg_guide, ltd_qty, pax_allowed, cao_allowed, special_provisions) -> seeded via Flyway SQL.
  label_audit(id, label_id, label_type, action, user_id, timestamp, ip)
- Store PDF bytes in bytea (compressed) or in the existing file storage (S3/local) if the app already has one; keep sha256 for integrity; downloads stream with Content-Type: application/pdf and Content-Disposition inline/attachment.
- REST endpoints (under /api/labels), JSON + validation (Jakarta Validation) + pagination, sorting, filtering, search:
  POST /api/labels/shipping            -> create + generate PDF, returns metadata + id
  POST /api/labels/shipping/preview    -> returns PDF bytes (not saved) for live preview
  GET  /api/labels/shipping            -> paged list (filters: date range, carrier, status, invoice, search by tracking/label no/recipient)
  GET  /api/labels/shipping/{id}       -> detail
  GET  /api/labels/shipping/{id}/pdf   -> PDF stream
  POST /api/labels/shipping/{id}/duplicate, PUT /{id} (regenerate), DELETE /{id} (soft delete), POST /{id}/mark-printed, POST /bulk-pdf (merge many labels into one PDF, ordered)
  Same set for /api/labels/hazmat plus GET /api/labels/hazmat/un-numbers?q= (autocomplete) and GET /api/labels/hazmat/classes (class metadata).
  Also: POST /api/labels/shipping/from-invoice/{invoiceId} -> pre-fills ship-to from the invoice customer, items count/weight from invoice items, reference INV number.
- Generation architecture: interface LabelRenderer { byte[] render(LabelSpec spec) }, implementations per preset; PdfCanvas helper class wrapping PDFBox with mm/dot-grid snapping, text-fitting, rotated text, vector barcode drawing, vector path drawing (SVG path subset M/L/C/Z) for hazmat symbols. Pure functions, deterministic output (same input => byte-identical PDF apart from creation date; set fixed document info dates optionally).
- Golden-file tests: render each preset with fixed sample data, rasterize at 203 DPI, compare against stored baseline PNGs with a tolerance of 0 for barcode regions and ≤ 0.1% pixel diff elsewhere. Include sample fixtures in src/test/resources.
- Add ZPL export as a secondary output for Zebra printers: POST /api/labels/shipping/{id}/zpl returns ZPL II (^XA ... ^XZ) with ^PW812 (4in @203dpi), ^LL1218 (6in), ^BC for Code 128, ^BX for DataMatrix, ^A0N fonts, so users can send raw ZPL. Hazmat ZPL: graphic via ^GFA from the 1-bit rasterized diamond.
- Security: tenant isolation on every query, authorization on endpoints, input sanitization, max field lengths, no HTML injection in text, rate-limit PDF generation, audit log.

=====================================================
PART E — FRONTEND (React)
=====================================================
Reuse the existing design system, theme (light/dark if exists), and sidebar component. Must be fully responsive and usable on mobile.

Pages
1. Shipping Label (create) — two-column layout: left form (accordion sections), right live PDF preview (re-render debounced 400 ms by calling /preview and showing via <iframe>/pdf.js canvas; show the label at TRUE physical aspect ratio with a ruler/size badge "4 × 6 in · 203 DPI").
   Sections: Label setup (preset, size, DPI, thermal/colour, border toggle, quantity of cartons), Ship From (saved addresses dropdown + manual), Ship To (pick customer from existing Inside Invoice customers OR manual; pincode lookup optional), Shipment (carrier, service, tracking # (manual or auto-generate internal ID), ship date, weight, dimensions, billing type, COD amount), References (invoice no, PO, ref1, ref2, notes), Handling icons toggles, FBA/GS1 fields (conditional). 
   Actions: "Generate & Save", "Save as Draft", "Download PDF", "Print" (opens PDF in new window with window.print(), and shows a printer-setup hint: "Scale: 100% / Actual size, Margins: None, Paper: 4x6 in"), "Download ZPL", "Duplicate", "Add to batch".
2. View Shipping — data table: label no, tracking, recipient, carrier, size, cartons, status badge, created date/by; filters, search, bulk select -> "Print selected (merged PDF)", row actions (View, Download, Print, Duplicate, Void, Delete); click row -> side drawer with large preview + metadata + audit trail; empty state; skeleton loaders; pagination.
3. Hazmat Label (create) — class picker as a visual grid of the 9 class diamonds (rendered as live SVG using the SAME path data as the PDF renderer so UI and PDF match), UN number autocomplete that auto-fills name/class/PG/ERG; form + live preview as above; warnings (e.g., "Class 1 not allowed by air", "Lithium > 100 Wh requires Class 9 label") — informational validation with a disclaimer that the shipper is responsible for regulatory compliance.
4. View Hazmat — same list pattern with a class colour chip.
Component structure: /features/labels/{shipping,hazmat,components,api,hooks,utils}. Use React Query (or the app's existing data layer), react-hook-form + zod validation, i18n-ready strings. Accessible (labels, aria, keyboard). Do not add heavy dependencies unless needed (pdf.js or react-pdf for preview is acceptable).
Printing UX: a "Print Setup Guide" modal explaining Zebra/Rollo/Dymo driver settings (paper size 4x6, no scaling, 203 dpi, darkness 10–15, speed 4 ips) and Chrome print dialog settings (Margins: None, Scale: 100, Headers/footers off).

=====================================================
PART F — QUALITY BAR / ACCEPTANCE CRITERIA
=====================================================
1. Generated 4x6 PDF is exactly 288 x 432 pt, 1 page per label, fonts embedded, vector-only, prints at 100% with zero clipping on a Zebra ZD420 and Rollo.
2. Every barcode scans with a phone scanner and a handheld laser scanner; automated ZXing round-trip test passes in CI.
3. SSCC/GTIN check digits validated; invalid data rejected with human-readable error.
4. No text overflow for the longest realistic Indian and US addresses (test fixtures with 3 address lines, 60-char names, Unicode names).
5. Hazmat diamonds geometrically exact (true 45° rotated square, inner border at 5% offset, numeral heights per spec), colours exact (give the CMYK and hex values in constants), and preview SVG == PDF output.
6. Existing app features and tests untouched and still passing; all new DB changes via forward-only migrations; new code covered by unit + integration tests (Testcontainers Postgres).
7. Provide: migration files, backend code, frontend code, sample seed data (20 UN numbers + 5 sample shipping labels), README section "Labels module" with printer setup, a Postman/OpenAPI spec, and a final checklist of what was and wasn't implemented.
8. Add a visible disclaimer in the UI and README: generated carrier labels are for internal/pre-printed layouts; official carrier tracking numbers/labels must come from the carrier API (UPS/FedEx/DHL/Delhivery/etc.) and hazmat compliance is the shipper's responsibility.

WORKFLOW
First scan the repo and print a short plan (files to add/change, migration names, risks). Then implement in this order: (1) migrations + entities, (2) PdfCanvas + barcode utilities + tests, (3) shipping renderers, (4) hazmat renderers + symbol path library, (5) REST APIs, (6) React pages + sidebar, (7) tests + README. After each stage, run the build/tests and fix failures before moving on. Do not leave TODOs or placeholder code; ask me only if something is truly blocking. 