package com.insideinvoice.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insideinvoice.exception.DuplicateResourceException;
import com.insideinvoice.product.dto.request.CreateProductRequest;
import com.insideinvoice.product.dto.request.UpdateProductRequest;
import com.insideinvoice.product.dto.response.ProductResponse;
import com.insideinvoice.product.entity.Product;
import com.insideinvoice.product.mapper.ProductMapper;
import com.insideinvoice.product.repository.ProductRepository;
import com.insideinvoice.product.service.impl.ProductServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductServiceImplTest {

    private ProductRepository repository;
    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        service = new ProductServiceImpl(repository, new ProductMapper());
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Product product(long id, long businessId, String name, String hsn) {
        Product p = Product.builder()
                .businessId(businessId)
                .name(name)
                .hsn(hsn)
                .rate(BigDecimal.TEN)
                .gstPercentage(BigDecimal.TEN)
                .build();
        p.setId(id);
        return p;
    }

    private static CreateProductRequest createRequest(String name, String hsn) {
        return CreateProductRequest.builder()
                .name(name)
                .hsn(hsn)
                .rate(BigDecimal.TEN)
                .gstPercentage(BigDecimal.TEN)
                .build();
    }

    @Test
    void createRejectsDuplicateHsnForSameBusinessIgnoringFormatting() {
        when(repository.findByBusinessId(7L))
                .thenReturn(List.of(product(1L, 7L, "Chair", "9403 2090")));

        assertThatThrownBy(() -> service.createProduct(createRequest("Table", "9403-2090"), 7L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Chair")
                .hasMessageContaining("9403-2090");

        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void createAllowsSameHsnForAnotherBusiness() {
        when(repository.findByBusinessId(7L))
                .thenReturn(List.of(product(1L, 7L, "Chair", "94032090")));
        when(repository.findByBusinessId(8L))
                .thenReturn(List.of(product(2L, 8L, "Sofa", "111111")));

        ProductResponse created = service.createProduct(createRequest("Table", "94032090"), 8L);

        assertThat(created.getName()).isEqualTo("Table");
        verify(repository).save(any(Product.class));
    }

    @Test
    void createSkipsLookupWhenHsnIsBlank() {
        ProductResponse created = service.createProduct(createRequest("Service", "   "), 7L);

        assertThat(created.getName()).isEqualTo("Service");
        verify(repository, never()).findByBusinessId(any());
    }

    @Test
    void updateRejectsHsnOwnedByAnotherProduct() {
        Product self = product(1L, 7L, "Chair", "94032090");
        when(repository.findByIdAndBusinessId(1L, 7L)).thenReturn(Optional.of(self));
        when(repository.findByBusinessId(7L))
                .thenReturn(List.of(self, product(2L, 7L, "Table", "999999")));

        UpdateProductRequest request = UpdateProductRequest.builder().hsn("999999").build();

        assertThatThrownBy(() -> service.updateProduct(1L, request, 7L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Table");
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void updateAcceptsTheProductsOwnHsn() {
        Product self = product(1L, 7L, "Chair", "94032090");
        when(repository.findByIdAndBusinessId(1L, 7L)).thenReturn(Optional.of(self));
        when(repository.findByBusinessId(7L))
                .thenReturn(List.of(self, product(2L, 7L, "Table", "999999")));

        UpdateProductRequest request = UpdateProductRequest.builder().hsn("9403 2090").build();

        ProductResponse updated = service.updateProduct(1L, request, 7L);

        assertThat(updated.getHsn()).isEqualTo("9403 2090");
        verify(repository).save(any(Product.class));
    }
}
