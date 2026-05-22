package com.insideinvoice.product.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.common.Constants;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.product.dto.request.CreateProductRequest;
import com.insideinvoice.product.dto.request.UpdateProductRequest;
import com.insideinvoice.product.dto.response.ProductResponse;
import com.insideinvoice.product.service.ProductService;
import com.insideinvoice.security.CurrentUser;
import com.insideinvoice.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management APIs")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create a new product")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @CurrentUser UserPrincipal currentUser) {
        ProductResponse response = productService.createProduct(request, currentUser.getBusinessId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all products with pagination")
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> getAllProducts(
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = Constants.SORT_BY_CREATED_AT) String sortBy,
            @RequestParam(defaultValue = Constants.SORT_DIRECTION_DESC) String sortDir,
            @CurrentUser UserPrincipal currentUser) {
        PagedResponse<ProductResponse> response = productService.getAllProducts(
                currentUser.getBusinessId(), page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        ProductResponse response = productService.getProduct(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request,
            @CurrentUser UserPrincipal currentUser) {
        ProductResponse response = productService.updateProduct(id, request, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        productService.deleteProduct(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
    }
}
