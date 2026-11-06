package com.example.demo;

import com.example.demo.crawler.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 외부 사이트에 재접속하지 않고 방별 가격·원본 링크·단위 변환·수집 규칙을 검증하는 회귀 테스트입니다. */
class ListingParserTests {
    /** SUUMO의 같은 건물에 있는 다른 방을 각각의 가격·상세 URL로 구분하고 수집 상한을 지킵니다. */
    @Test
    void suumoUsesEachRoomAndCanonicalLinks() throws Exception {
        var parser = new SuumoListingParser();
        var listings = parser.parse(fixture("suumo.html", ListingSource.SUUMO), 12);
        assertEquals(2, listings.size());
        assertEquals(85000, listings.getFirst().getRentYen());
        assertEquals(new BigDecimal("25.37"), listings.getFirst().getAreaM2());
        assertEquals("東京都", listings.getFirst().getPrefecture());
        assertEquals("新宿区", listings.getFirst().getCity());
        assertEquals("新宿駅", listings.getFirst().getStationName());
        assertEquals(7, listings.getFirst().getWalkMinutes());
        assertEquals("https://suumo.jp/chintai/jnc_000000001/", listings.getFirst().getOriginalUrl());
        assertEquals(100000, listings.get(1).getRentYen());
        assertEquals(0, listings.get(1).getManagementFeeYen());
        assertNotEquals(listings.getFirst().getSourceListingKey(), listings.get(1).getSourceListingKey());
        assertEquals(1, parser.parse(fixture("suumo.html", ListingSource.SUUMO), 1).size());
    }

    /** 레오팔레스의 건물 공통 링크 대신 실제 호실 링크와 해당 방의 월세를 저장합니다. */
    @Test
    void leopalaceUsesRoomLinksInsteadOfBuildingLinks() throws Exception {
        var listings = new LeopalaceListingParser().parse(fixture("leopalace.html", ListingSource.LEOPALACE), 12);
        assertEquals(2, listings.size());
        assertTrue(listings.getFirst().getOriginalUrl().endsWith("/101"));
        assertTrue(listings.getFirst().getTitle().endsWith("101号室"));
        assertEquals(91000, listings.getFirst().getRentYen());
        assertEquals(6500, listings.getFirst().getManagementFeeYen());
        assertEquals(new BigDecimal("21.76"), listings.getFirst().getAreaM2());
        assertEquals(9, listings.getFirst().getWalkMinutes());
        assertEquals(100000, listings.get(1).getRentYen());
    }

    /** 전각 숫자와 만 엔을 변환하고 미표시·범위·소수 엔은 추측하지 않습니다. */
    @Test
    void numericNormalizationPreservesUnknownValues() {
        assertEquals(85000, ListingValues.yen("８．５万円"));
        assertEquals(6500, ListingValues.yen("（共益費 6,500円）"));
        assertEquals(0, ListingValues.yen("不要"));
        assertNull(ListingValues.yen("—"));
        assertNull(ListingValues.yen("8万円〜9万円"));
        assertNull(ListingValues.yen("0.5円"));
        assertNull(ListingValues.yen("-5000円"));
        assertEquals("1R", ListingValues.layout("ワンルーム"));
    }

    /** 더 구체적인 Allow, wildcard·쿼리 제한, 다른 봇 그룹과의 구분을 검증합니다. */
    @Test
    void robotsHonorsSpecificRulesAndAgentGroups() {
        String rules = "User-agent: OtherBot\nDisallow: /\nUser-agent: *\nDisallow: /private/\nAllow: /private/public/\nDisallow: /*?*sort=\n";
        assertTrue(RobotsPolicy.isAllowed(rules, URI.create("https://suumo.jp/chintai/"), "NihonHouseLearning"));
        assertFalse(RobotsPolicy.isAllowed(rules, URI.create("https://suumo.jp/private/a"), "NihonHouseLearning"));
        assertTrue(RobotsPolicy.isAllowed(rules, URI.create("https://suumo.jp/private/public/a"), "NihonHouseLearning"));
        assertFalse(RobotsPolicy.isAllowed(rules, URI.create("https://suumo.jp/chintai/?sort=1"), "NihonHouseLearning"));
        assertFalse(RobotsPolicy.isAllowed(rules, URI.create("https://suumo.jp/chintai/"), "OtherBot"));
    }

    /** 호스트 위장·임의 URL·문의 링크를 거부하고 추적 파라미터가 바뀌어도 같은 키를 사용합니다. */
    @Test
    void sourceValidationRejectsUnrelatedAndNonDetailLinks() {
        assertThrows(IllegalArgumentException.class, () -> ListingSource.SUUMO.canonicalDetail("https://suumo.jp.evil.test/chintai/jnc_1/"));
        assertThrows(IllegalArgumentException.class, () -> ListingSource.SUUMO.canonicalDetail("http://127.0.0.1/"));
        assertThrows(IllegalArgumentException.class, () -> ListingSource.LEOPALACE.canonicalDetail("/properties/common/tokyo/test"));
        assertEquals(ListingSource.SUUMO.canonicalDetail("/chintai/jnc_1/?a=1"), ListingSource.SUUMO.canonicalDetail("/chintai/jnc_1/?a=2"));
    }

    /** HOME'S의 응답이 접근 거부 문구뿐이면 매물을 만들지 않으며, 방 행을 제공할 때만 추출합니다. */
    @Test
    void blockedPortalParsersDoNotInventListingsFromEmptyPages() {
        assertTrue(new HomesListingParser().parse(Jsoup.parse("<h1>Access denied</h1>"), 12).isEmpty());
        assertTrue(new AtHomeListingParser().parse(Jsoup.parse("<h1>Access denied</h1>"), 12).isEmpty());
        for (ListingParser parser : java.util.List.of(new HomesListingParser())) {
            String path = "/chintai/b-123/";
            var html = "<article><h2>테스트 매물</h2><table><tr><td class='price'>8万円</td><td>1K 20㎡</td><td><a href='" + path + "'>詳細</a></td></tr></table></article>";
            var items = parser.parse(Jsoup.parse(html, parser.source().baseUri().toString()), 12);
            assertEquals(1, items.size());
            assertEquals(80000, items.getFirst().getRentYen());
        }
    }

    /** at home의 방별 금액·호실·주소와 따옴표 밖에 駅가 있는 역명 표기를 검사합니다. */
    @Test
    void atHomeParsesObservedRoomSections() throws Exception {
        var listings = new AtHomeListingParser().parse(fixture("athome.html", ListingSource.ATHOME), 12);
        assertEquals(1, listings.size());
        assertEquals(241500, listings.getFirst().getRentYen());
        assertEquals(10500, listings.getFirst().getManagementFeeYen());
        assertEquals("東京都", listings.getFirst().getPrefecture());
        assertEquals("新宿区", listings.getFirst().getCity());
        assertEquals("高田馬場駅", listings.getFirst().getStationName());
        assertEquals("https://www.athome.co.jp/chintai/1234567890/", listings.getFirst().getOriginalUrl());
    }

    /** 로컬 테스트 자료를 읽습니다. 테스트 과정에서 원본 사이트에는 접속하지 않습니다. */
    private Document fixture(String name, ListingSource source) throws Exception {
        try (var stream = getClass().getResourceAsStream("/crawler/" + name)) {
            assertNotNull(stream);
            return Jsoup.parse(stream, StandardCharsets.UTF_8.name(), source.listUri().toString());
        }
    }
}
