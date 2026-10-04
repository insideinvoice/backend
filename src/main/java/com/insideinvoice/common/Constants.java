package com.insideinvoice.common;

public final class Constants {

    private Constants() {}

    public static final String DEFAULT_PAGE_SIZE = "10";
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String SORT_BY_CREATED_AT = "createdAt";
    public static final String SORT_DIRECTION_DESC = "desc";

    public static final String CLAIM_BUSINESS_ID = "businessId";
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USER_NAME = "userName";

    public static final String API_AUTH = "/api/auth/**";
    public static final String API_CONTACT = "/api/contact/**";
    public static final String API_EMAIL_INBOUND = "/api/email/inbound";
    public static final String API_SWAGGER = "/swagger-ui/**";
    public static final String API_API_DOCS = "/v3/api-docs/**";
    public static final String API_ACTUATOR = "/actuator/**";
    public static final String API_HEALTH = "/api/heartbeat";

    public static final int INVOICE_PREFIX_MAX_LENGTH = 10;

    public static final double GST_MAX_PERCENTAGE = 100.0;
    public static final int SCALE_CALCULATION = 2;
}
