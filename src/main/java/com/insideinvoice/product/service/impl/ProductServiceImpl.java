package com.insideinvoice.product.service.impl;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.exception.DuplicateResourceException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.product.dto.request.CreateProductRequest;
import com.insideinvoice.product.dto.request.UpdateProductRequest;
import com.insideinvoice.product.dto.response.ProductResponse;
import com.insideinvoice.product.entity.Product;
import com.insideinvoice.product.mapper.ProductMapper;
import com.insideinvoice.product.repository.ProductRepository;
import com.insideinvoice.product.service.ProductService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, Long businessId) {
        assertHsnUnique(request.getHsn(), businessId, null);
        Product product = productMapper.toEntity(request, businessId);
        product = productRepository.save(product);

        log.info("Product created: {} for businessId: {}", product.getId(), businessId);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> getAllProducts(Long businessId, int page, int size, String sortBy, String sortDir) {
        Sort sort = com.insideinvoice.common.PageParams.safeSort(sortBy, sortDir,
                java.util.Set.of("createdAt", "name", "rate", "hsn", "unit"), "createdAt");
        Pageable pageable = com.insideinvoice.common.PageParams.of(page, size, sort);
        Page<Product> products = productRepository.findByBusinessId(businessId, pageable);

        return PagedResponse.<ProductResponse>builder()
                .content(products.getContent().stream().map(productMapper::toResponse).toList())
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .last(products.isLast())
                .first(products.isFirst())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id, Long businessId) {
        Product product = productRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request, Long businessId) {
        Product product = productRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (request.getHsn() != null) {
            assertHsnUnique(request.getHsn(), businessId, id);
        }
        productMapper.updateEntity(product, request);
        product = productRepository.save(product);

        log.info("Product updated: {} for businessId: {}", id, businessId);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findByHsn(String hsn, Long businessId) {
        String trimmed = hsn == null ? "" : hsn.trim();
        List<Product> products = productRepository.findByBusinessIdAndHsn(businessId, trimmed);
        if (products.isEmpty()) {
            // Fallback: match ignoring spaces, dashes and case ("9403 2090" == "9403-2090" == "94032090")
            String normalized = normalizeHsn(trimmed);
            if (!normalized.isEmpty()) {
                products = productRepository.findByBusinessId(businessId).stream()
                        .filter(p -> p.getHsn() != null && normalizeHsn(p.getHsn()).equals(normalized))
                        .toList();
            }
        }
        if (products.isEmpty()) {
            throw new ResourceNotFoundException("Product", "hsn", hsn);
        }
        return productMapper.toResponse(products.get(0));
    }

    private static String normalizeHsn(String hsn) {
        return hsn.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
    }

    /**
     * HSN/SAC codes must be unique per business. Matching ignores spaces,
     * dashes and case so "9403 2090", "9403-2090" and "94032090" collide.
     */
    private void assertHsnUnique(String hsn, Long businessId, Long excludeProductId) {
        String trimmed = hsn == null ? "" : hsn.trim();
        String normalized = normalizeHsn(trimmed);
        if (normalized.isEmpty()) {
            return;
        }
        productRepository.findByBusinessId(businessId).stream()
                .filter(p -> excludeProductId == null || !excludeProductId.equals(p.getId()))
                .filter(p -> p.getHsn() != null && normalizeHsn(p.getHsn()).equals(normalized))
                .findFirst()
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(String.format(
                            "HSN/SAC '%s' is already used by product '%s'", trimmed, existing.getName()));
                });
    }

    @Override
    @Transactional
    public void deleteProduct(Long id, Long businessId) {
        Product product = productRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        productRepository.delete(product);

        log.info("Product deleted: {} for businessId: {}", id, businessId);
    }
}
