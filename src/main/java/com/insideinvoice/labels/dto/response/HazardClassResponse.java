package com.insideinvoice.labels.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HazardClassResponse {

    private String key;
    private String className;
    private String hex;
    private String pattern;
    private String symbol;
    private String textColor;
    private String numeral;
    private float[] cmyk;
    private int[] rgb;
}
