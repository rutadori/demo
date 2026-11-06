package com.example.demo;

import com.example.demo.crawler.*;
import com.example.demo.mapper.PropertyMapper;
import com.example.demo.model.*;
import com.example.demo.service.PropertyImportService;
import java.net.URI;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 지역 경로·홋카이도 주소 해석·잘못된 지역 데이터 저장 방지를 외부 접속 없이 검증합니다. */
class PropertyRegionTests {
    /** SUUMO의 hokkaido_ 표기와 레오팔레스 도시 경로 등 출처별 고정 주소 차이를 확인합니다. */
    @Test
    void regionUrisStayOnAllowedHostsAndUseCorrectHokkaidoPaths() {
        for (PropertyRegion region : PropertyRegion.values()) {
            for (ListingSource source : ListingSource.values()) assertTrue(source.allows(source.listUri(region)));
        }
        assertEquals(URI.create("https://suumo.jp/chintai/hokkaido_/sc_sapporoshichuo/"), ListingSource.SUUMO.listUri(PropertyRegion.HOKKAIDO));
        assertTrue(ListingSource.ATHOME.listUri(PropertyRegion.HOKKAIDO).getPath().contains("sapporo_chuo-city"));
        assertTrue(ListingSource.LEOPALACE.listUri(PropertyRegion.HOKKAIDO).getPath().endsWith("sapporo-shi-chuo-ku-01101"));
    }

    /** 홋카이도와 정령지정도시의 구를 분리하고 도쿄 주소도 기존 방식으로 읽는지 확인합니다. */
    @Test
    void locationKeepsCityAndWardTogether() {
        PropertyListing listing = new PropertyListing();
        ListingValues.location(listing, "北海道札幌市中央区南一条西１");
        assertEquals("北海道", listing.getPrefecture());
        assertEquals("札幌市中央区", listing.getCity());
        ListingValues.location(listing, "大阪府大阪市北区天満１");
        assertEquals("大阪市北区", listing.getCity());
        ListingValues.location(listing, "東京都新宿区西新宿１");
        assertEquals("新宿区", listing.getCity());
    }

    /** 신규 수집 대상의 주소를 정확히 분리합니다. 중간에 村이 들어간 나카무라구도 구까지 유지합니다. */
    @Test
    void addedCitiesAndNakamuraWardAreParsedWithoutTruncation() {
        for (PropertyRegion region : PropertyRegion.values()) {
            for (CollectionArea area : region.getAreas()) {
                PropertyListing listing = new PropertyListing();
                ListingValues.location(listing, region.getPrefecture() + area.cityName() + "テスト１丁目");
                assertEquals(region.getPrefecture(), listing.getPrefecture());
                assertEquals(area.cityName(), listing.getCity());
                for (ListingSource source : ListingSource.values()) assertTrue(source.allows(source.listUri(area)));
            }
        }
    }

    /** at home이 도도부현을 생략한 주소를 공개 검색 문맥의 北海道로 보완하는지 확인합니다. */
    @Test
    void atHomeUsesPublishedHokkaidoContext() throws Exception {
        String html;
        try (var stream = getClass().getResourceAsStream("/crawler/athome.html")) {
            assertNotNull(stream);
            html = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("新宿区(東京都)", "札幌市中央区(北海道)").replace("新宿区テスト３丁目", "札幌市中央区南一条西３丁目");
        }
        var listings = new AtHomeListingParser().parse(Jsoup.parse(html, ListingSource.ATHOME.listUri(PropertyRegion.HOKKAIDO).toString()), 12);
        assertEquals("北海道", listings.getFirst().getPrefecture());
        assertEquals("札幌市中央区", listings.getFirst().getCity());
    }

    /** URL의 지역 이름만 믿고 실제 도쿄 주소를 홋카이도 매물로 바꿔 저장하지 않도록 검사합니다. */
    @Test
    void importerRejectsMismatchedPrefectureWithoutRelabeling() {
        PropertyMapper mapper = mock(PropertyMapper.class);
        PropertySource enabled = new PropertySource();
        enabled.setId(1L);
        enabled.setEnabled(true);
        when(mapper.findSource("suumo")).thenReturn(enabled);
        PropertyListing tokyoListing = ListingValues.listing(ListingSource.SUUMO, "테스트", "/chintai/jnc_1/");
        tokyoListing.setPrefecture("東京都");
        assertEquals(0, new PropertyImportService(mapper).save(ListingSource.SUUMO, PropertyRegion.HOKKAIDO, List.of(tokyoListing)));
        assertEquals("東京都", tokyoListing.getPrefecture());
        verify(mapper, never()).upsertListing(any());
    }
}
