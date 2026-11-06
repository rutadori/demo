package com.example.demo;

import com.example.demo.crawler.*;
import com.example.demo.mapper.PropertyMapper;
import com.example.demo.model.PropertySource;
import com.example.demo.model.PropertyRegion;
import com.example.demo.model.CollectionArea;
import com.example.demo.service.CrawlService;
import com.example.demo.service.PropertyImportService;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 외부 HTTP와 DB 저장을 Mockito로 대체하여 출처별 실패 격리와 중복 실행 간격을 검증합니다. */
class CrawlServiceTests {
    /** HOME'S가 거부해도 다른 출처는 처리하고 즉시 다시 실행하면 추가 HTTP 요청을 보내지 않습니다. */
    @Test
    void deniedSourceDoesNotStopOtherSourcesAndCooldownPreventsRepeatedFetch() throws Exception {
        PublicPageClient client = mock(PublicPageClient.class);
        PropertyImportService importer = mock(PropertyImportService.class);
        PropertyMapper mapper = mock(PropertyMapper.class);
        PropertySource enabled = new PropertySource();
        enabled.setEnabled(true);
        when(mapper.findSource(anyString())).thenReturn(enabled);
        for (ListingSource source : List.of(ListingSource.SUUMO, ListingSource.ATHOME, ListingSource.LEOPALACE)) {
            String file = source == ListingSource.SUUMO ? "suumo" : source == ListingSource.ATHOME ? "athome" : "leopalace";
            try (var stream = getClass().getResourceAsStream("/crawler/" + file + ".html")) {
                when(client.fetch(eq(source), any(CollectionArea.class))).thenReturn(Jsoup.parse(stream, "UTF-8", source.listUri().toString()));
            }
        }
        when(client.fetch(eq(ListingSource.HOMES), any(CollectionArea.class))).thenThrow(new CrawlException("HTTP_403", "접근 거부"));
        when(importer.save(any(), any(), anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(2)).size());
        CrawlService service = new CrawlService(client, importer, mapper, List.of(new SuumoListingParser(), new HomesListingParser(), new AtHomeListingParser(), new LeopalaceListingParser()), 12);
        var results = service.collectRegion("tokyo");
        assertEquals(12, results.stream().filter(result -> result.getStatus().equals("SUCCESS")).count());
        assertEquals("HTTP_403", results.get(1).getStatus());
        assertEquals(0, results.get(1).getSavedCount());
        verify(importer, never()).save(eq(ListingSource.HOMES), any(), anyList());
        service.collectRegion("tokyo");
        for (CollectionArea area : PropertyRegion.TOKYO.getAreas()) {
            for (ListingSource source : List.of(ListingSource.SUUMO, ListingSource.ATHOME, ListingSource.LEOPALACE))
                verify(client, times(1)).fetch(source, area);
        }
        verify(client, times(1)).fetch(eq(ListingSource.HOMES), any(CollectionArea.class));
    }

    /** 한 지역에서 접근 거부를 받으면 나머지 지역의 동일 호스트에 반복 요청하지 않고 생략 원인을 기록합니다. */
    @Test
    void batchStopsRepeatedRequestsToDeniedHostAcrossRegions() throws Exception {
        PublicPageClient client = mock(PublicPageClient.class);
        PropertyImportService importer = mock(PropertyImportService.class);
        PropertyMapper mapper = mock(PropertyMapper.class);
        PropertySource enabled = new PropertySource();
        enabled.setEnabled(true);
        when(mapper.findSource(anyString())).thenReturn(enabled);
        for (ListingSource source : ListingSource.values()) {
            when(client.fetch(eq(source), any(CollectionArea.class))).thenThrow(new CrawlException("HTTP_403", "접근 거부"));
        }
        CrawlService service = new CrawlService(client, importer, mapper, List.of(new SuumoListingParser(), new HomesListingParser(), new AtHomeListingParser(), new LeopalaceListingParser()), 12);
        var results = service.collectAll();
        assertEquals(96, results.size());
        assertEquals(92, results.stream().filter(result -> result.getStatus().equals("SKIPPED_ACCESS")).count());
        for (ListingSource source : ListingSource.values()) verify(client, times(1)).fetch(eq(source), any(CollectionArea.class));
        verify(importer, never()).save(any(), any(), anyList());
    }

    /** 한 지역의 모든 도시를 각각 수집하고 재실행은 막으며 다른 지역은 즉시 실행합니다. 또한 잘못된 지역 코드는 거부합니다. */
    @Test
    void cooldownIsScopedToRegionAndSource() throws Exception {
        PublicPageClient client = mock(PublicPageClient.class);
        PropertyImportService importer = mock(PropertyImportService.class);
        PropertyMapper mapper = mock(PropertyMapper.class);
        PropertySource enabled = new PropertySource();
        enabled.setEnabled(true);
        when(mapper.findSource(anyString())).thenReturn(enabled);
        when(client.fetch(any(), any(CollectionArea.class))).thenReturn(Jsoup.parse("<html></html>"));
        CrawlService service = new CrawlService(client, importer, mapper, List.of(new SuumoListingParser(), new HomesListingParser(), new AtHomeListingParser(), new LeopalaceListingParser()), 12);
        service.collectRegion("tokyo");
        service.collectRegion("hokkaido");
        service.collectRegion("hokkaido");
        for (ListingSource source : ListingSource.values()) {
            for (CollectionArea area : PropertyRegion.TOKYO.getAreas()) verify(client, times(1)).fetch(source, area);
            for (CollectionArea area : PropertyRegion.HOKKAIDO.getAreas()) verify(client, times(1)).fetch(source, area);
        }
        assertThrows(IllegalArgumentException.class, () -> service.collectRegion("unknown"));
    }
}
