package com.example.demo;

import com.example.demo.mapper.PropertyMapper;
import com.example.demo.model.PropertyListing;
import com.example.demo.model.PropertySearch;
import com.example.demo.model.PropertySource;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 선택한 실제 DB(MySQL 또는 PostgreSQL)에서 MyBatis XML·중복 갱신·검색 조건을 검증합니다.
 * @SpringBootTest는 실제 설정을 로드하며 기본 supabase 프로필은 팀 공용 PostgreSQL에 연결합니다.
 * @Transactional 때문에 테스트가 삽입한 고유 출처와 매물은 테스트 종료 시 자동 롤백되며 기존 수집 매물은 건드리지 않습니다.
 * 외부 사이트 수집은 기본 설정에서 비활성화되지만 Supabase DB 연결에는 네트워크가 필요합니다.
 */
@SpringBootTest
@Transactional
class PropertyMapperTests {
    @Autowired
    private PropertyMapper mapper;

    /** 동일 출처·동일 식별값을 두 번 저장해도 한 행을 유지하며 월세와 마지막 발견 시각을 갱신합니다. */
    @Test
    void repeatedListingUpdatesOneRow() {
        PropertySource source = source();
        PropertyListing listing = listing(source.getId(), "same-key", 85000, "테스트 매물");
        mapper.upsertListing(listing);
        listing.setRentYen(87000);
        listing.setLastSeenAt(listing.getLastSeenAt().plusHours(1));
        mapper.upsertListing(listing);
        PropertySearch search = new PropertySearch();
        search.setSourceId(source.getId());
        var found = mapper.findListings(search);
        assertEquals(1, found.size());
        assertEquals(87000, found.getFirst().getRentYen());
        assertEquals(source.getSourceName(), found.getFirst().getSourceName());
        assertEquals(listing.getLastSeenAt().withNano(0), found.getFirst().getLastSeenAt());
    }

    /** 지역·가격·구조·출처·키워드·정렬을 실제 바인딩 SQL로 검증하고 와일드카드 확장을 방지합니다. */
    @Test
    void filtersAndOrderingUseBoundValues() {
        PropertySource source = source();
        mapper.upsertListing(listing(source.getId(), "first", 100000, "테스트 100%"));
        mapper.upsertListing(listing(source.getId(), "second", 50000, "테스트 두 번째"));
        PropertySearch search = new PropertySearch();
        search.setSourceId(source.getId());
        search.setPrefecture("東京都");
        search.setLayout("1K");
        search.setSort("rentAsc");
        assertEquals(50000, mapper.findListings(search).getFirst().getRentYen());
        search.setMinRent(80000);
        assertEquals(1, mapper.findListings(search).size());
        search.setMinRent(null);
        search.setQueryKeyword("%");
        assertEquals(1, mapper.findListings(search).size());
        search.setQueryKeyword("' OR 1=1 --");
        assertTrue(mapper.findListings(search).isEmpty());
        search.setQueryKeyword(null);
        search.setSort("rent_yen; DROP TABLE property_listing");
        assertEquals(2, mapper.findListings(search).size());
    }

    /** 선택한 실제 DB에서 도쿄와 홋카이도 조건이 서로의 매물을 섞지 않는지 확인합니다. */
    @Test
    void prefectureFilterSeparatesHokkaidoFromTokyo() {
        PropertySource source = source();
        PropertyListing tokyo = listing(source.getId(), "tokyo", 85000, "도쿄 테스트");
        PropertyListing hokkaido = listing(source.getId(), "hokkaido", 45000, "홋카이도 테스트");
        hokkaido.setPrefecture("北海道");
        mapper.upsertListing(tokyo);
        mapper.upsertListing(hokkaido);
        PropertySearch search = new PropertySearch();
        search.setSourceId(source.getId());
        search.setPrefecture("北海道");
        var found = mapper.findListings(search);
        assertEquals(1, found.size());
        assertEquals("홋카이도 테스트", found.getFirst().getTitle());
        search.setPrefecture("東京都");
        assertEquals("도쿄 테스트", mapper.findListings(search).getFirst().getTitle());
    }

    /** 같은 도도부현의 도시 조건이 실제 SQL에서 분리되고 SQL 문자가 입력돼도 결과를 확장하지 않는지 확인합니다. */
    @Test
    void cityFilterSeparatesWardsWithinSamePrefecture() {
        PropertySource source = source();
        PropertyListing setagaya = listing(source.getId(), "setagaya", 85000, "세타가야 테스트");
        setagaya.setCity("世田谷区");
        PropertyListing shibuya = listing(source.getId(), "shibuya", 95000, "시부야 테스트");
        shibuya.setCity("渋谷区");
        mapper.upsertListing(setagaya);
        mapper.upsertListing(shibuya);
        PropertySearch search = new PropertySearch();
        search.setSourceId(source.getId());
        search.setPrefecture("東京都");
        search.setCity("世田谷区");
        assertEquals("세타가야 테스트", mapper.findListings(search).getFirst().getTitle());
        assertEquals(1, mapper.findListings(search).size());
        assertTrue(mapper.findCities("東京都").containsAll(java.util.List.of("世田谷区", "渋谷区")));
        search.setCity("' OR 1=1 --");
        assertTrue(mapper.findListings(search).isEmpty());
    }

    /** 트랜잭션 안에서만 존재하는 고유 테스트 출처를 만듭니다. */
    private PropertySource source() {
        PropertySource source = new PropertySource();
        source.setSourceCode("test_" + UUID.randomUUID());
        source.setSourceName("롤백되는 테스트 출처");
        source.setBaseUrl("https://example.com");
        mapper.upsertSource(source);
        return mapper.findSource(source.getSourceCode());
    }

    /** Mapper 검증에 필요한 필수 필드와 검색값을 갖는 테스트 매물을 구성합니다. */
    private PropertyListing listing(Long sourceId, String key, int rent, String title) {
        PropertyListing listing = new PropertyListing();
        listing.setSourceId(sourceId);
        listing.setSourceListingKey(key);
        listing.setTitle(title);
        listing.setOriginalUrl("https://example.com/rooms/" + key);
        listing.setPrefecture("東京都");
        listing.setLayout("1K");
        listing.setRentYen(rent);
        listing.setLastSeenAt(LocalDateTime.of(2026, 11, 4, 15, 0));
        return listing;
    }
}
