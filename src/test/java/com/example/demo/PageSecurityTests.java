package com.example.demo;

import com.example.demo.config.SecurityConfig;
import com.example.demo.controller.PageController;
import java.math.BigDecimal;
import com.example.demo.model.PropertyListing;
import com.example.demo.model.PropertySearch;
import com.example.demo.model.PropertySource;
import com.example.demo.model.PropertyRegion;
import com.example.demo.service.PropertyService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 화면의 404, Thymeleaf 파일 탐색, 인증과 CSRF를 함께 검증하는 회귀 테스트입니다.
 * @WebMvcTest는 DB 없이 MVC·Thymeleaf·보안 필터를 실행하고,
 * @Import는 실제 보안 설정과 테스트 전용 계정을 연결합니다.
 * 테스트 계정은 운영 코드나 application.properties에 등록되지 않습니다.
 */
@WebMvcTest(PageController.class)
@Import({SecurityConfig.class, PageSecurityTests.TestUsers.class})
class PageSecurityTests {

    // @Autowired는 테스트 환경의 MVC 요청 실행 도구를 주입합니다.
    @Autowired
    private MockMvc mvc;

    // MVC 테스트는 실제 DB·외부 사이트를 호출하지 않고 Service 결과만 대체합니다.
    @MockitoBean
    private PropertyService propertyService;

    /** 실제 서비스와 같은 여섯 지역 선택지를 주어 화면의 선택 유지·홋카이도 표시를 검증합니다. */
    @BeforeEach
    void provideRegions() {
        when(propertyService.regions()).thenReturn(List.of(PropertyRegion.values()));
    }

    /** Q&A를 포함한 공개 화면 8개의 응답 본문까지 렌더링하여 404와 fragment 경로 오류를 검출합니다. */
    @Test
    void publicPagesRenderWithSharedLayout() throws Exception {
        for (String path : new String[]{"/", "/member/login", "/member/join",
                "/property/list?area=tokyo", "/property/detail?id=1",
                "/recommend/search", "/recommend/result?placeName=Tokyo&commute=30", "/qna"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/css/style.css")))
                    .andExpect(content().string(not(containsString("th:replace="))));
        }
    }

    /** 비로그인 메뉴에는 로그인만 표시되고, 인증용 POST 폼에는 CSRF 토큰이 생성되어야 합니다. */
    @Test
    void anonymousLoginPageHasCsrfAndAnonymousMenu() throws Exception {
        mvc.perform(get("/member/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("href=\"/member/login\"")))
                .andExpect(content().string(not(containsString("action=\"/member/logout\""))));
    }

    /** 올바른 폼 인증 후 메인으로 리다이렉트하여 기존 로그인 후 404 문제를 방지합니다. */
    @Test
    void successfulLoginRedirectsHome() throws Exception {
        mvc.perform(formLogin("/member/login").user("tester").password("test-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("tester"));
    }

    /** 잘못된 비밀번호는 인증하지 않고 오류 파라미터가 붙은 로그인 화면으로 돌려보냅니다. */
    @Test
    void failedLoginReturnsToLoginPage() throws Exception {
        mvc.perform(formLogin("/member/login").user("tester").password("wrong"))
                .andExpect(redirectedUrl("/member/login?error"))
                .andExpect(unauthenticated());
        mvc.perform(get("/member/login?error"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아이디 또는 비밀번호가 올바르지 않습니다.")));
    }

    /** CSRF 보호를 끄지 않았는지 확인하여 토큰 없는 로그인 POST가 거부되는지 검사합니다. */
    @Test
    void loginWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/member/login").param("username", "tester").param("password", "test-password"))
                .andExpect(status().isForbidden());
    }

    /** @WithMockUser는 로그인 상태를 만들며 메뉴에서 로그아웃만 보이는지 검증합니다. */
    @Test
    @WithMockUser
    void authenticatedHomeShowsLogoutMenu() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/member/logout\"")))
                .andExpect(content().string(not(containsString("href=\"/member/login\""))));
    }

    /** 로그인 상태에서 CSRF가 있는 POST 로그아웃 후 세션 인증이 제거되는지 검사합니다. */
    @Test
    @WithMockUser
    void logoutReturnsToLoginPage() throws Exception {
        mvc.perform(post("/member/logout").with(csrf()))
                .andExpect(redirectedUrl("/member/login?logout"))
                .andExpect(unauthenticated());
        mvc.perform(get("/member/login?logout"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("로그아웃되었습니다.")));
    }

    /** 실제 DB 없이 Model에 매물을 넣어 반복 카드, 원본 URL, 엔 금액 표시를 검증합니다. */
    @Test
    void suppliedListingsRenderDirectOriginalLinks() throws Exception {
        when(propertyService.recent()).thenReturn(List.of(listingForView()));
        when(propertyService.search(any(PropertySearch.class))).thenReturn(List.of(listingForView()));
        when(propertyService.find(null)).thenReturn(listingForView());
        for (String path : new String[]{"/", "/property/list", "/property/detail"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"https://example.com/rooms/123\"")))
                    .andExpect(content().string(containsString("rel=\"noopener noreferrer\"")))
                    .andExpect(content().string(containsString("¥85,000 / 월")))
                    .andExpect(content().string(containsString("관리비 ¥0")))
                    .andExpect(content().string(not(containsString("class=\"empty-state\""))));
        }
    }

    /** 누락 값은 빈 상태 문구로 표시하고 HTML 제목·실행 가능한 URL을 그대로 출력하지 않습니다. */
    @Test
    void incompleteListingEscapesTextAndOmitsUnsafeLinks() throws Exception {
        PropertyListing listing = new PropertyListing();
        listing.setTitle("<script>alert(1)</script>");
        listing.setOriginalUrl("javascript:alert(1)");
        when(propertyService.search(any(PropertySearch.class))).thenReturn(List.of(listing));
        mvc.perform(get("/property/list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("월세 정보 없음")))
                .andExpect(content().string(containsString("관리비 미표시")))
                .andExpect(content().string(containsString("원본 링크 준비 중")))
                .andExpect(content().string(not(containsString("javascript:alert"))));
    }

    /** GET 조건이 선택값으로 유지되고, 출처는 제공받은 목록에서 생성되는지 확인합니다. */
    @Test
    void searchFormRetainsConditionsAndSuppliedSources() throws Exception {
        PropertySource source = new PropertySource();
        source.setId(7L);
        source.setSourceName("테스트 출처");
        when(propertyService.sources()).thenReturn(List.of(source));
        mvc.perform(get("/property/list").param("prefecture", "東京都").param("layout", "1K")
                        .param("minRent", "50000").param("maxRent", "100000")
                        .param("walkMinutes", "10").param("keyword", "新宿").param("sort", "rentAsc")
                        .param("sourceId", "7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"東京都\" selected=\"selected\"")))
                .andExpect(content().string(containsString("value=\"1K\" selected=\"selected\"")))
                .andExpect(content().string(containsString("value=\"rentAsc\" selected=\"selected\"")))
                .andExpect(content().string(containsString("value=\"新宿\"")))
                .andExpect(content().string(containsString("value=\"100000\"")))
                .andExpect(content().string(containsString("value=\"7\" selected=\"selected\">테스트 출처")));
    }

    /** 데이터 없는 상태는 안내로, 미구현 추천·가입은 입력을 막은 준비 화면으로 표시합니다. */
    @Test
    void emptyAndUpcomingPagesDoNotShowInventedResults() throws Exception {
        mvc.perform(get("/property/list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("표시할 매물이 없습니다")))
                .andExpect(content().string(not(containsString("¥85,000"))));
        for (String path : new String[]{"/member/join", "/recommend/search"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<fieldset disabled")))
                    .andExpect(content().string(not(containsString("혼고산초메역"))));
        }
    }

    /** 숫자가 아닌 값·음수·역전된 월세 범위는 SQL 조회 전에 검증 오류 안내를 표시합니다. */
    @Test
    void invalidSearchConditionsDoNotReachDatabaseService() throws Exception {
        for (String value : new String[]{"-1", "not-a-number"}) {
            mvc.perform(get("/property/list").param("minRent", value))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("검색 조건이 올바르지 않습니다.")));
        }
        mvc.perform(get("/property/list").param("minRent", "100000").param("maxRent", "50000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("검색 조건이 올바르지 않습니다.")));
        org.mockito.Mockito.verify(propertyService, org.mockito.Mockito.never()).search(any());
    }

    /** 메인과 검색 목록 모두 홋카이도 선택지를 사용하며 선택 후에도 값이 유지되는지 검사합니다. */
    @Test
    void hokkaidoIsAvailableInHomeAndSearch() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"北海道\"")))
                .andExpect(content().string(containsString("홋카이도")));
        mvc.perform(get("/property/list").param("prefecture", "北海道"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"北海道\" selected=\"selected\"")));
    }

    /** 이후 조회 DTO의 화면 속성을 재현합니다. 운영 코드나 DB에는 이 예시를 넣지 않습니다. */
    private PropertyListing listingForView() {
        PropertyListing listing = new PropertyListing();
        listing.setTitle("테스트 매물");
        listing.setSourceName("테스트 출처");
        listing.setOriginalUrl("https://example.com/rooms/123");
        listing.setRentYen(85000);
        listing.setManagementFeeYen(0);
        listing.setAddress("東京都新宿区");
        listing.setLayout("1K");
        listing.setAreaM2(new BigDecimal("25.50"));
        listing.setStationName("新宿駅");
        listing.setWalkMinutes(7);
        return listing;
    }

    /** @TestConfiguration은 자동으로 운영 환경에 포함되지 않는 테스트 전용 설정입니다. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestUsers {

        /** @Bean으로 인증 검증용 메모리 계정을 생성합니다. {noop}는 테스트에서만 사용합니다. */
        @Bean
        UserDetailsService testUserDetailsService() {
            return new InMemoryUserDetailsManager(User.withUsername("tester")
                    .password("{noop}test-password").roles("USER").build());
        }
    }
}
