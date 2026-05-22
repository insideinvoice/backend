package com.insideinvoice.product.service;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.product.dto.request.CreateProductRequest;
import com.insideinvoice.product.dto.request.UpdateProductRequest;
import com.insideinvoice.product.dto.response.ProductResponse;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request, Long businessId);

    PagedResponse<ProductResponse> getAllProducts(Long businessId, int page, int size, String sortBy, String sortDir);

    ProductResponse getProduct(Long id, Long businessId);

    ProductResponse updateProduct(Long id, UpdateProductRequest request, Long businessId);

    void deleteProduct(Long id, Long businessId);
}
