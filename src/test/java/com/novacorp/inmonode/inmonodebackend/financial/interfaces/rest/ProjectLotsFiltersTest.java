package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetProjectLotsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.LotCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ProjectQueryService;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.configuration.WebSecurityConfiguration;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectLotsController.class)
@Import(WebSecurityConfiguration.class)
@ActiveProfiles("test")
class ProjectLotsFiltersTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ProjectQueryService queryService;
    @MockitoBean private LotCommandService commandService;
    @MockitoBean private TokenService tokenService;

    @Test
    void publicFiltersReachTheQueryAndNoMatchesRemainAnEmptyGeoJsonCollection() throws Exception {
        when(queryService.handle(any(GetProjectLotsQuery.class))).thenReturn(Result.success(List.of()));
        mvc.perform(get("/api/v1/projects/1/lots").param("minArea", "120").param("maxArea", "150")
                        .param("minPrice", "1000").param("maxPrice", "2000").param("status", "AVAILABLE")
                        .param("west", "-77").param("south", "-13").param("east", "-76").param("north", "-12"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features").isEmpty());
        var captor = ArgumentCaptor.forClass(GetProjectLotsQuery.class);
        verify(queryService).handle(captor.capture());
        var filters = captor.getValue().filters();
        assertEquals(new BigDecimal("120"), filters.minArea());
        assertEquals(new BigDecimal("150"), filters.maxArea());
        assertEquals(new BigDecimal("1000"), filters.minPrice());
        assertEquals(new BigDecimal("2000"), filters.maxPrice());
        assertEquals(LotStatus.AVAILABLE, filters.status());
        assertEquals(-77, filters.bounds().west());
    }

    @Test
    void invalidRangesCoordinatesAndEnumsReturn400BeforeQuerying() throws Exception {
        mvc.perform(get("/api/v1/projects/1/lots").param("minArea", "150").param("maxArea", "120"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/projects/1/lots").param("minPrice", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects/1/lots").param("west", "-77"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects/1/lots").param("status", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(queryService);
    }

    @Test
    void noParametersPreserveTheUnfilteredRequest() throws Exception {
        when(queryService.handle(any(GetProjectLotsQuery.class))).thenReturn(Result.success(List.of()));
        mvc.perform(get("/api/v1/projects/1/lots")).andExpect(status().isOk());
        verify(queryService).handle(new GetProjectLotsQuery(1L));
    }
}
