package com.example.demo.crawler;

import com.example.demo.model.PropertyRegion;
import com.example.demo.model.CollectionArea;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 네 수집 대상의 고정 주소를 정의합니다. 사용자 입력 URL을 가져오지 않아 내부망·임의 사이트 요청을 방지합니다.
 * 등록된 각 도시·구의 공개 목록 첫 페이지만 사용하며 다음 페이지나 문의 페이지를 따라가지 않습니다.
 * enum은 값이 한정된 상수를 표현합니다. 식별 코드는 property_source.source_code와 동일하게 사용합니다.
 */
public enum ListingSource {
    SUUMO("suumo", "SUUMO", "https://suumo.jp", "/chintai/tokyo/sc_shinjuku/", "/chintai/(?:jnc_|bc_)[A-Za-z0-9_]+/?"),
    HOMES("homes", "LIFULL HOME’S", "https://www.homes.co.jp", "/chintai/tokyo/shinjuku-city/list/", "/chintai/b-[A-Za-z0-9]+/?"),
    ATHOME("athome", "at home", "https://www.athome.co.jp", "/chintai/tokyo/shinjuku-city/list/", "/chintai/[0-9]+/?"),
    LEOPALACE("leopalace", "레오팔레스21", "https://www.leopalace21.com", "/properties/chintai/area/tokyo/shinjuku-ku-13104", "/properties/chintai/[^?#]+/[0-9]+/?");

    private final String code;
    private final String displayName;
    private final URI baseUri;
    private final URI listUri;
    private final String detailPathPattern;

    /** 상수별 식별자·표시명·기본 주소·목록 경로·상세 경로 규칙을 초기화합니다. */
    ListingSource(String code, String displayName, String base, String listPath, String detailPathPattern) {
        this.code = code;
        this.displayName = displayName;
        this.baseUri = URI.create(base);
        this.listUri = baseUri.resolve(listPath);
        this.detailPathPattern = detailPathPattern;
    }

    /** DB와 프로그램에서 사용하는 고정 출처 코드를 반환합니다. */
    public String code() { return code; }

    /** 사용자 화면에 표시할 출처 이름을 반환합니다. */
    public String displayName() { return displayName; }

    /** 출처 등록 및 robots.txt 요청의 기준 주소를 반환합니다. */
    public URI baseUri() { return baseUri; }

    /** 도쿄 목록 주소를 반환하는 기존 파서 테스트용 기본값입니다. 실제 지역 수집은 인자가 있는 메서드를 사용합니다. */
    public URI listUri() { return listUri; }

    /** 출처별 경로 규칙과 등록된 지역 상수로만 공개 목록 URI를 생성합니다. 임의 URL은 받지 않습니다. */
    public URI listUri(PropertyRegion region) {
        return listUri(region.getAreas().getFirst());
    }

    /** 도도부현과 개별 도시·구의 고정 경로를 결합합니다. 수집 상한은 도시·출처 단위로 적용합니다. */
    public URI listUri(CollectionArea area) {
        PropertyRegion region = area.region();
        String path = switch (this) {
            case SUUMO -> "/chintai/" + region.getSuumoPrefectureSlug() + "/sc_" + area.suumoSlug() + "/";
            case HOMES, ATHOME -> "/chintai/" + region.getCode() + "/" + area.portalSlug() + "-city/list/";
            case LEOPALACE -> "/properties/chintai/area/" + region.getCode() + "/" + area.leopalaceSlug();
        };
        return baseUri.resolve(path);
    }

    /** HTTPS·정확한 호스트·기본 포트·인증정보 부재를 확인하여 허용한 출처 주소만 인정합니다. */
    public boolean allows(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && baseUri.getHost().equalsIgnoreCase(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getRawUserInfo() == null;
    }

    /** 목록·로그인·문의 링크를 매물로 저장하지 않도록 상세 페이지 경로까지 확인합니다. */
    public boolean isDetail(URI uri) {
        return allows(uri) && uri.getPath().matches(detailPathPattern);
    }

    /**
     * 원본 상세 경로를 정규화해 추적 파라미터와 fragment가 없는 URL을 만듭니다.
     * 이 네 커넥터는 경로 자체로 매물을 식별하므로 query를 제거해도 같은 매물을 가리킵니다.
     */
    public String canonicalDetail(String href) {
        URI resolved = listUri.resolve(href).normalize();
        if (!isDetail(resolved)) throw new IllegalArgumentException("허용된 출처의 매물 상세 주소가 아닙니다.");
        return baseUri.toString() + resolved.getRawPath();
    }

    /** 상세 URL의 SHA-256을 생성하여 매번 같은 매물에 같은 중복 방지 키를 부여합니다. */
    public String listingKey(String canonicalUrl) {
        try {
            return "url:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalUrl.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK의 SHA-256 알고리즘을 사용할 수 없습니다.", ex);
        }
    }
}
