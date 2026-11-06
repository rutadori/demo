package com.example.demo;

import com.example.demo.config.SecurityConfig;
import com.example.demo.controller.CrawlController;
import com.example.demo.model.CrawlResult;
import com.example.demo.service.CrawlService;
import com.example.demo.service.PropertyService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest로 수집 관리 Controller와 실제 보안·Thymeleaf를 검증합니다.
 * @MockitoBean으로 업무를 대체하여 화면 테스트가 원본 사이트에 요청하거나 DB를 변경하지 않게 합니다.
 */
@WebMvcTest(CrawlController.class)
@Import({SecurityConfig.class, PageSecurityTests.TestUsers.class})
class CrawlControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private CrawlService crawlService;
    @MockitoBean private PropertyService propertyService;

    /** 로그인하지 않은 관리 화면 요청은 로그인으로 이동합니다. */
    @Test
    void anonymousUsersCannotAccessManagement() throws Exception {
        mvc.perform(get("/manage/crawling")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/login"));
        verifyNoInteractions(crawlService);
    }

    /** 실제 HTTP 거부 코드·확인 시각을 화면에 표시하고 수집 폼에 CSRF 토큰을 생성합니다. */
    @Test
    @WithMockUser
    void managementRendersFailureReasonAndCsrf() throws Exception {
        when(crawlService.latest()).thenReturn(List.of(new CrawlResult("homes", "HOME’S", "HTTP_403", 0, "원본 사이트 접근 거부", LocalDateTime.of(2026, 11, 4, 16, 0), "tokyo", "도쿄")));
        mvc.perform(get("/manage/crawling")).andExpect(status().isOk())
                .andExpect(content().string(containsString("HTTP_403")))
                .andExpect(content().string(containsString("11-04 16:00:00")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    /** 토큰이 없는 POST는 거부하고, 인증+CSRF가 있는 POST만 Service를 호출한 뒤 GET으로 이동합니다. */
    @Test
    @WithMockUser
    void collectionRequiresCsrfAndUsesRedirectAfterPost() throws Exception {
        mvc.perform(post("/manage/crawling")).andExpect(status().isForbidden());
        verify(crawlService, never()).collectAll();
        mvc.perform(post("/manage/crawling").with(csrf())).andExpect(redirectedUrl("/manage/crawling"));
        verify(crawlService).collectAll();
    }

    /** 지역을 지정한 POST는 전체 수집을 호출하지 않고 선택한 홋카이도 작업만 Service에 위임합니다. */
    @Test
    @WithMockUser
    void selectedRegionIsPassedToService() throws Exception {
        mvc.perform(post("/manage/crawling").param("region", "hokkaido").with(csrf()))
                .andExpect(redirectedUrl("/manage/crawling?region=hokkaido"));
        verify(crawlService).collectRegion("hokkaido");
        verify(crawlService, never()).collectAll();
    }
}
