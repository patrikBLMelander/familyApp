package com.familyapp.application.family;

import com.familyapp.application.cache.CacheService;
import com.familyapp.domain.i18n.LocalizedException;
import com.familyapp.infrastructure.family.FamilyEntity;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A family can switch its wallet currency to a supported one, and only to that. */
class FamilyCurrencyTest {

    private static final UUID FAMILY = UUID.randomUUID();

    private FamilyJpaRepository families;
    private FamilyEntity entity;
    private FamilyService service;

    @BeforeEach
    void setUp() {
        families = mock(FamilyJpaRepository.class);
        entity = new FamilyEntity();
        entity.setId(FAMILY);
        entity.setName("Svensson");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        when(families.findById(FAMILY)).thenReturn(Optional.of(entity));
        when(families.save(any(FamilyEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new FamilyService(families, mock(FamilyMemberJpaRepository.class),
                mock(PasswordEncoder.class), mock(CacheService.class));
    }

    @Test
    void existingFamiliesDefaultToKronor() {
        assertThat(service.getFamilyById(FAMILY).currency()).isEqualTo("SEK");
    }

    @Test
    void supportedCurrencyIsSaved() {
        assertThat(service.updateCurrency(FAMILY, "EUR").currency()).isEqualTo("EUR");
        assertThat(entity.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void unsupportedCurrencyIsRejected() {
        assertThatThrownBy(() -> service.updateCurrency(FAMILY, "BTC"))
                .isInstanceOf(LocalizedException.class)
                .hasMessage("family.currency.unsupported");
        verify(families, never()).save(any());
    }
}
