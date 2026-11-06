package com.example.demo.service;

import com.example.demo.crawler.ListingSource;
import com.example.demo.mapper.PropertyMapper;
import com.example.demo.model.PropertyListing;
import com.example.demo.model.PropertyRegion;
import com.example.demo.model.PropertySource;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 결과의 DB 저장만 담당하는 Model의 업무 계층입니다. @Service로 등록하고 @Transactional로 저장을 묶습니다.
 * 네트워크 요청은 이 클래스 밖에서 끝내므로 원본 응답을 기다리는 동안 DB 트랜잭션을 잡아두지 않습니다.
 * 수집 Service와 다른 Bean으로 분리해야 Spring의 트랜잭션 프록시가 실제로 적용됩니다.
 */
@Service
public class PropertyImportService {
    private final PropertyMapper mapper;

    /** 생성자 주입으로 MyBatis Mapper를 받아 DB 작업을 위임합니다. */
    public PropertyImportService(PropertyMapper mapper) { this.mapper = mapper; }

    /**
     * 네 출처의 기본 정보를 중복 없이 등록합니다. 기존 매물·출처 번호와 사용 중지 상태를 보존합니다.
     * @Transactional은 출처 등록 중 오류가 발생하면 이번 등록 묶음을 롤백합니다.
     */
    @Transactional
    public void ensureSources() {
        for (ListingSource definition : ListingSource.values()) {
            PropertySource source = new PropertySource();
            source.setSourceCode(definition.code());
            source.setSourceName(definition.displayName());
            source.setBaseUrl(definition.baseUri().toString());
            mapper.upsertSource(source);
        }
    }

    /**
     * URL·필수값·문자열 길이·원본 주소의 도도부현이 선택 지역과 일치하는 실제 수집 결과만 저장합니다.
     * 재수집은 기존 행을 갱신합니다. URL의 지역 이름으로 공개 주소를 덮어쓰지 않습니다.
     * 목록 일부를 가져온 단계이므로 목록에 없던 기존 매물을 종료시키거나 삭제하지 않습니다.
     * @return 중복 갱신을 포함해 DB에 정상 반영한 매물 수
     */
    @Transactional
    public int save(ListingSource definition, PropertyRegion region, List<PropertyListing> listings) {
        PropertySource source = mapper.findSource(definition.code());
        if (source == null || !Boolean.TRUE.equals(source.getEnabled())) return 0;
        int saved = 0;
        for (PropertyListing listing : listings) {
            if (listing.getTitle() == null || listing.getTitle().isBlank()) continue;
            // 수집 URL에 적힌 지역 이름으로 데이터를 덮어쓰지 않습니다. 실제 공개 주소가 일치한 매물만 저장합니다.
            if (!region.getPrefecture().equals(listing.getPrefecture())) continue;
            String url;
            try { url = definition.canonicalDetail(listing.getOriginalUrl()); }
            catch (IllegalArgumentException ex) { continue; }
            listing.setSourceId(source.getId());
            listing.setSourceListingKey(definition.listingKey(url));
            listing.setOriginalUrl(url);
            listing.setTitle(shorten(listing.getTitle(), 255));
            listing.setAddress(shorten(listing.getAddress(), 500));
            listing.setStationName(shorten(listing.getStationName(), 100));
            listing.setImageUrl(validImage(definition, listing.getImageUrl()));
            listing.setLastSeenAt(LocalDateTime.now(ZoneId.of("Asia/Tokyo")));
            mapper.upsertListing(listing);
            saved++;
        }
        return saved;
    }

    /** 빈 문자열은 NULL로 통일하고 DB 컬럼 길이에 맞게 표시 문구를 제한합니다. */
    private String shorten(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }

    /** 실제 관찰한 출처별 이미지 호스트만 허용합니다. 이미지가 불확실하면 기존 화면의 기본 사진을 사용합니다. */
    private String validImage(ListingSource source, String value) {
        if (value == null || value.length() > 2048) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null) return null;
            String host = uri.getHost();
            return (source == ListingSource.SUUMO && host.matches("img[0-9]+\\.suumo\\.com"))
                    || (source == ListingSource.LEOPALACE && host.equals("leopalace21.imagewave.pictures"))
                    || (source == ListingSource.ATHOME && host.equals("www.athome.co.jp") && uri.getPath().startsWith("/image_files/")) ? value : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
