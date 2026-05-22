package com.insideinvoice.product.mapper;

import com.insideinvoice.product.dto.request.CreateProductRequest;
import com.insideinvoice.product.dto.request.UpdateProductRequest;
import com.insideinvoice.product.dto.response.ProductResponse;
import com.insideinvoice.product.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public Product toEntity(CreateProductRequest request, Long businessId) {
        return Product.builder()
                .businessId(businessId)
                .name(request.getName())
                .description(request.getDescription())
                .hsn(request.getHsn())
                .unit(request.getUnit())
                .rate(request.getRate())
                .gstPercentage(request.getGstPercentage())
                .build();
    }

    public ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .hsn(product.getHsn())
                .unit(product.getUnit())
                .rate(product.getRate())
                .gstPercentage(product.getGstPercentage())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public void updateEntity(Product product, UpdateProductRequest request) {
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getHsn() != null) product.setHsn(request.getHsn());
        if (request.getUnit() != null) product.setUnit(request.getUnit());
        if (request.getRate() != null) product.setRate(request.getRate());
        if (request.getGstPercentage() != null) product.setGstPercentage(request.getGstPercentage());
    }
}
