package com.antsight.backend.domain.candle;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.common.exception.GlobalExceptionHandler;
import com.antsight.backend.config.JwtAuthFilter;
import com.antsight.backend.config.SecurityConfig;
import com.antsight.backend.domain.candle.dto.CandleSeriesResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CandleController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CandleControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean CandleService candleService;

    @Test
    void range_returns_200_with_default_with_ant_index() throws Exception {
        CandleSeriesResponse payload = new CandleSeriesResponse("005930", true, List.of());
        when(candleService.findRangeWithAntIndex(anyString(), any(), any(), anyBoolean()))
                .thenReturn(payload);

        mockMvc.perform(get("/api/candles/005930")
                        .param("from", "2026-05-14T09:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticker").value("005930"))
                .andExpect(jsonPath("$.data.with_ant_index").value(true));
    }

    @Test
    void range_returns_400_when_ticker_invalid() throws Exception {
        mockMvc.perform(get("/api/candles/ABCDEF")
                        .param("from", "2026-05-14T09:00:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void range_returns_400_when_from_after_to() throws Exception {
        when(candleService.findRangeWithAntIndex(anyString(), any(), any(), anyBoolean()))
                .thenThrow(new ApiException(ErrorCode.INVALID_DATE_RANGE));

        mockMvc.perform(get("/api/candles/005930")
                        .param("from", "2026-05-14T12:00:00")
                        .param("to", "2026-05-14T09:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_DATE_RANGE.getMessage()));
    }

    @Test
    void range_returns_400_when_range_exceeds_7_days() throws Exception {
        when(candleService.findRangeWithAntIndex(anyString(), any(), any(), anyBoolean()))
                .thenThrow(new ApiException(ErrorCode.DATE_RANGE_TOO_LARGE));

        mockMvc.perform(get("/api/candles/005930")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-15T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.DATE_RANGE_TOO_LARGE.getMessage()));
    }
}
