package com.insideinvoice.labels.render;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Postal address used in ship-from / ship-to blocks. Drawn uppercase for ship-to. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelAddress {

    private String name;
    private String company;
    @Builder.Default
    private List<String> addressLines = new ArrayList<>();
    private String city;
    private String state;
    private String pincode;
    private String country;
    private String phone;

    public boolean isEmpty() {
        return isBlank(name) && isBlank(company) && isEmptyLines() && isBlank(city) && isBlank(phone);
    }

    private boolean isEmptyLines() {
        return addressLines == null || addressLines.stream().allMatch(LabelAddress::isBlank);
    }

    /** "CITY STATE PIN" line per spec. */
    public String cityLine() {
        return join(city, state, pincode);
    }

    public String joinedLines() {
        if (addressLines == null) {
            return "";
        }
        return addressLines.stream().filter(l -> !isBlank(l)).collect(Collectors.joining(", "));
    }

    public List<String> nonBlankLines() {
        List<String> out = new ArrayList<>();
        if (!isBlank(name)) {
            out.add(name.trim());
        }
        if (!isBlank(company)) {
            out.add(company.trim());
        }
        String joined = joinedLines();
        if (!isBlank(joined)) {
            out.add(joined);
        }
        String cityLine = cityLine();
        if (!isBlank(cityLine)) {
            out.add(cityLine);
        }
        if (!isBlank(country)) {
            out.add(country.trim());
        }
        if (!isBlank(phone)) {
            out.add("PH: " + phone.trim());
        }
        return out;
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!isBlank(p)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
