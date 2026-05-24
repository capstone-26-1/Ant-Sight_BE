package com.antsight.backend.domain.antindex;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.common.exception.GlobalExceptionHandler;
import com.antsight.backend.config.JwtAuthFilter;
import com.antsight.backend.config.SecurityConfig;
import com.antsight.backend.domain.antindex.dto.AntIndexLatestResponse;
import com.antsight.backend.domain.antindex.dto.AntIndexRankingResponse;
import com.antsight.backend.domain.antindex.dto.AntIndexSeriesResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AntIndexController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AntIndexControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AntIndexService antIndexService;

    @Test
    void latest_returns_200_with_payload() throws Exception {
        AntIndexLatestResponse payload = new AntIndexLatestResponse(
                "005930", LocalDateTime.parse("2026-05-14T15:30:00"),
                new BigDecimal("74.21"), Sentiment.POSITIVE, 38L);
        when(antIndexService.getLatest("005930")).thenReturn(payload);

        mockMvc.perform(get("/api/ant-index/005930/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticker").value("005930"))
                .andExpect(jsonPath("$.data.score").value(74.21))
                .andExpect(jsonPath("$.data.sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.data.post_count").value(38));
    }

    @Test
    void latest_returns_404_when_no_data() throws Exception {
        when(antIndexService.getLatest("999999"))
                .thenThrow(new ApiException(ErrorCode.ANT_INDEX_NOT_FOUND));

        mockMvc.perform(get("/api/ant-index/999999/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.ANT_INDEX_NOT_FOUND.getMessage()));
    }

    @Test
    void latest_returns_400_when_ticker_invalid() throws Exception {
        mockMvc.perform(get("/api/ant-index/ABCDEF/latest"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void series_returns_200_with_default_interval() throws Exception {
        AntIndexSeriesResponse payload = new AntIndexSeriesResponse("005930", "15m", List.of());
        when(antIndexService.getSeries(eq("005930"), any(), any(), eq("15m"))).thenReturn(payload);

        mockMvc.perform(get("/api/ant-index/005930")
                        .param("from", "2026-05-14T09:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticker").value("005930"))
                .andExpect(jsonPath("$.data.interval").value("15m"));
    }

    @Test
    void series_returns_400_when_interval_invalid() throws Exception {
        mockMvc.perform(get("/api/ant-index/005930")
                        .param("from", "2026-05-14T09:00:00")
                        .param("interval", "30m"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void series_returns_400_when_from_after_to() throws Exception {
        when(antIndexService.getSeries(anyString(), any(), any(), anyString()))
                .thenThrow(new ApiException(ErrorCode.INVALID_DATE_RANGE));

        mockMvc.perform(get("/api/ant-index/005930")
                        .param("from", "2026-05-14T12:00:00")
                        .param("to", "2026-05-14T09:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_DATE_RANGE.getMessage()));
    }

    @Test
    void ranking_returns_200_with_defaults() throws Exception {
        AntIndexRankingResponse payload = new AntIndexRankingResponse("24h", "positive", List.of());
        when(antIndexService.getRanking(eq("24h"), eq("positive"), eq(10))).thenReturn(payload);

        mockMvc.perform(get("/api/ant-index/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.window").value("24h"))
                .andExpect(jsonPath("$.data.direction").value("positive"));
    }

    @Test
    void ranking_returns_400_when_window_invalid() throws Exception {
        mockMvc.perform(get("/api/ant-index/ranking").param("window", "7d"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ranking_returns_400_when_limit_out_of_range() throws Exception {
        mockMvc.perform(get("/api/ant-index/ranking").param("limit", "100"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/ant-index/ranking").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }
}
