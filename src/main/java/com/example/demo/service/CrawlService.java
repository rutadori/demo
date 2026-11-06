package com.example.demo.service;

import com.example.demo.crawler.CrawlException;
import com.example.demo.crawler.ListingParser;
import com.example.demo.crawler.ListingSource;
import com.example.demo.crawler.PublicPageClient;
import com.example.demo.mapper.PropertyMapper;
import com.example.demo.model.CrawlResult;
import com.example.demo.model.PropertyRegion;
import com.example.demo.model.CollectionArea;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 수집 업무 흐름을 조정하는 Model Service입니다. Controller에는 HTTP 요청이나 HTML 파싱 코드를 두지 않습니다.
 * 출처 등록 → 사용 여부 확인 → 공개 HTML 요청 → 사이트별 파싱 → 저장 Service → 결과 보고 순서로 실행합니다.
 * 한 출처가 거부·실패해도 다른 출처는 계속 처리합니다. 매물 데이터를 오류 메시지나 샘플로 대체하지 않습니다.
 * @Service는 Bean 등록, @Value는 설정값 주입입니다. 네트워크 동안 DB 트랜잭션은 열지 않습니다.
 */
@Service
public class CrawlService {
    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);
    private final PublicPageClient client;
    private final PropertyImportService importer;
    private final PropertyMapper mapper;
    private final Map<ListingSource, ListingParser> parsers = new EnumMap<>(ListingSource.class);
    private final Map<String, CrawlResult> results = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final int maxPerSource;

    /** 사이트 파서와 공통 클라이언트·저장 업무를 주입하고 도시·구·출처 조합별 수집 상한을 1~30건으로 제한합니다. */
    public CrawlService(PublicPageClient client, PropertyImportService importer, PropertyMapper mapper,
                        List<ListingParser> parsers, @Value("${house.crawler.max-per-source:12}") int maxPerSource) {
        this.client = client;
        this.importer = importer;
        this.mapper = mapper;
        parsers.forEach(parser -> this.parsers.put(parser.source(), parser));
        this.maxPerSource = Math.max(1, Math.min(30, maxPerSource));
    }

    /** 실행 중인 서버의 마지막 결과를 지역→출처 순서로 반환합니다. 실행 전에도 24개 도시·구를 각각 표시합니다. */
    public List<CrawlResult> latest() {
        List<CrawlResult> list = new ArrayList<>();
        for (CollectionArea area : areas(List.of(PropertyRegion.values()))) {
            for (ListingSource source : ListingSource.values())
                list.add(results.getOrDefault(key(source, area), new CrawlResult(source.code(), source.displayName(), "NOT_RUN", 0,
                        "이 서버 실행에서 아직 수집하지 않았습니다.", null, area.region().getCode(), area.region().getDisplayName() + " · " + area.displayName())));
        }
        return list;
    }

    /** 설정 파일에서 적용한 도시·구·출처 조합별 실제 상한을 화면 안내에 전달합니다. */
    public int getMaxPerSource() { return maxPerSource; }

    /**
     * 여섯 지역의 모든 등록 도시·구와 네 출처를 처리합니다. 도시·구·출처별 재실행은 10분 간격을 두어 새 지역의 첫 수집을 막지 않습니다.
     * 실행 시 명확한 실패 원인을 남기고, 이전에 저장한 매물을 실패 때문에 삭제하지 않습니다.
     * @return 이번 실행 또는 간격 제한으로 유지된 직전 결과
     */
    public List<CrawlResult> collectAll() {
        return collect(List.of(PropertyRegion.values()));
    }

    /** 관리 화면에서 선택한 지역 하나만 수집합니다. 등록되지 않은 코드는 HTTP 요청 전에 거부합니다. */
    public List<CrawlResult> collectRegion(String code) {
        return collect(List.of(PropertyRegion.fromCode(code)));
    }

    /**
     * 검증된 지역 목록을 순서대로 처리합니다. 출처에서 403·429·자동 접근 확인 페이지를 받으면 해당 배치의 추가 요청은 중지합니다.
     * 그 뒤 지역은 접근 거부로 생략했음을 기록하며 정상적인 매물 0건 또는 성공으로 표시하지 않습니다.
     */
    private List<CrawlResult> collect(List<PropertyRegion> regions) {
        if (!lock.tryLock()) throw new IllegalStateException("이미 수집 중입니다. 완료 후 확인해 주세요.");
        try {
            importer.ensureSources();
            var blockedSources = EnumSet.noneOf(ListingSource.class);
            for (CollectionArea area : areas(regions)) {
                for (ListingSource source : ListingSource.values()) {
                    CrawlResult previous = results.get(key(source, area));
                    if (previous != null && previous.getCheckedAt() != null
                            && Duration.between(previous.getCheckedAt(), now()).toMinutes() < 10) continue;
                    CrawlResult result = blockedSources.contains(source)
                            ? result(source, area, "SKIPPED_ACCESS", 0, "앞선 지역에서 이 출처가 접근 거부·자동 접근 확인을 반환하여 이번 배치의 추가 요청을 생략했습니다.")
                            : collectOne(source, area);
                    if (result.getStatus().equals("HTTP_403") || result.getStatus().equals("HTTP_429")
                            || result.getStatus().equals("ACCESS_CHALLENGE")) blockedSources.add(source);
                    results.put(key(source, area), result);
                    log.info("CRAWL_RESULT region={} source={} status={} saved={} message={}", area.code(), source.code(), result.getStatus(), result.getSavedCount(), result.getMessage());
                }
            }
            return latest();
        } finally {
            lock.unlock();
        }
    }

    /** 출처 하나의 파싱·저장 결과를 만들며 HTTP 거부·파싱 실패·DB 오류를 구분해서 보고합니다. */
    private CrawlResult collectOne(ListingSource source, CollectionArea area) {
        try {
            var stored = mapper.findSource(source.code());
            if (stored == null || !Boolean.TRUE.equals(stored.getEnabled()))
                return result(source, area, "DISABLED", 0, "DB에서 수집이 중지된 출처입니다.");
            ListingParser parser = parsers.get(source);
            if (parser == null) return result(source, area, "PARSER_MISSING", 0, "이 출처의 파서가 등록되지 않았습니다.");
            var listings = parser.parse(client.fetch(source, area), maxPerSource);
            if (listings.isEmpty()) return result(source, area, "PARSE_EMPTY", 0, "방별 매물을 읽지 못했습니다. 원본 HTML 구조 또는 검색 결과를 확인해야 합니다.");
            int saved = importer.save(source, area.region(), listings);
            if (saved == 0) return result(source, area, "PARSE_EMPTY", 0, "필수 제목·원본 주소·실제 지역을 검증한 저장 대상이 없습니다.");
            return result(source, area, "SUCCESS", saved, area.displayName() + " 목록에서 " + saved + "건을 저장·갱신했습니다.");
        } catch (CrawlException ex) {
            return result(source, area, ex.getStatus(), 0, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return result(source, area, "INTERRUPTED", 0, "수집 실행이 중단되었습니다.");
        } catch (IOException ex) {
            log.warn("수집 네트워크 오류: {} {}", source.code(), ex.getClass().getSimpleName());
            return result(source, area, "NETWORK_ERROR", 0, "연결·응답 또는 인증서 검증에 실패했습니다. 기존 매물은 유지합니다.");
        } catch (RuntimeException ex) {
            log.error("수집 처리 오류: {}", source.code(), ex);
            return result(source, area, "PROCESSING_ERROR", 0, "매물 파싱 또는 DB 저장 중 오류가 발생했습니다. 서버 로그를 확인해 주세요.");
        }
    }

    /** 출처 표시명·실행 시각을 포함한 화면용 결과 객체를 생성합니다. */
    private CrawlResult result(ListingSource source, CollectionArea area, String status, int saved, String message) {
        return new CrawlResult(source.code(), source.displayName(), status, saved, message, now(), area.region().getCode(), area.region().getDisplayName() + " · " + area.displayName());
    }

    /** 같은 출처라도 다른 지역의 실행 결과·간격 제한이 섞이지 않도록 고유 키를 만듭니다. */
    private String key(ListingSource source, CollectionArea area) { return area.code() + ":" + source.code(); }

    /** 상위 지역의 도시 목록을 펼칩니다. 지역 선택 실행도 해당 지역의 모든 등록 도시를 처리합니다. */
    private List<CollectionArea> areas(List<PropertyRegion> regions) {
        return regions.stream().flatMap(region -> region.getAreas().stream()).toList();
    }

    /** DB에 저장하는 시각과 동일한 일본 표준시(한국과 같은 UTC+9)를 사용합니다. */
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Tokyo")); }
}
