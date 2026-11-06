package com.example.demo.crawler;

import com.example.demo.model.PropertyListing;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;

/**
 * 사이트마다 다른 숫자·일본어 단위를 Model의 공통 형식으로 바꾸는 유틸리티입니다.
 * 읽을 수 없는 값은 추측하거나 0으로 채우지 않고 null로 유지합니다. 네트워크·DB에는 접근하지 않습니다.
 */
public final class ListingValues {
    private static final Pattern MONEY = Pattern.compile("([0-9,]+(?:\\.[0-9]+)?)\\s*(万)?\\s*円");
    private static final Pattern AREA = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(?:㎡|m\\s*2|m²)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAYOUT = Pattern.compile("(?<![A-Z0-9])([1-9][0-9]?(?:S?LDK|S?DK|S?K|R))(?![A-Z])");
    private static final Pattern WALK = Pattern.compile("(?:徒歩|歩)\\s*([0-9]+)\\s*分");
    private static final Pattern LOCATION = Pattern.compile("^(東京都|京都府|大阪府|北海道|.+?県)(.+?市[^市区0-9\\s]*?区|.+?[市区町村])");

    /** 정적 메서드만 사용하므로 인스턴스 생성을 막습니다. */
    private ListingValues() { }

    /** 선택 요소가 없으면 null, 있으면 앞뒤 공백을 제거한 텍스트를 반환합니다. */
    public static String text(Element parent, String selector) {
        Element element = parent.selectFirst(selector);
        return element == null ? null : element.text().strip();
    }

    /** 전각 숫자·문자와 호환 단위를 통일해 사이트별 표기 차이를 줄입니다. */
    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
    }

    /** 8.5万円를 85000엔으로 변환합니다. 명시적인 무료·없음만 0으로 인정하고 범위 금액은 추측하지 않습니다. */
    public static Integer yen(String value) {
        String normalized = normalize(value);
        if (normalized.matches(".*[-−]\\s*[0-9].*")) return null;
        if (normalized.matches("(?:なし|無し|不要|無料|無|0)")) return 0;
        if (normalized.contains("~") || normalized.contains("〜") || normalized.contains("～")) return null;
        Matcher match = MONEY.matcher(normalized);
        if (!match.find()) return null;
        try {
            BigDecimal amount = new BigDecimal(match.group(1).replace(",", ""));
            return amount.multiply(match.group(2) == null ? BigDecimal.ONE : BigDecimal.valueOf(10000)).intValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    /** 면적을 소수로 변환하고 DB의 양수·최대 자릿수 제약을 만족하는 값만 반환합니다. */
    public static BigDecimal area(String value) {
        Matcher match = AREA.matcher(normalize(value));
        if (!match.find()) return null;
        BigDecimal area = new BigDecimal(match.group(1));
        return area.signum() > 0 && area.compareTo(new BigDecimal("999999.99")) <= 0 ? area : null;
    }

    /** 설명에서 1K·1LDK 등을 추출합니다. 일본어 원룸 표기는 1R로 통일합니다. */
    public static String layout(String value) {
        String normalized = normalize(value).toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("ワンルーム")) return "1R";
        Matcher match = LAYOUT.matcher(normalized);
        return match.find() ? match.group(1) : null;
    }

    /** 공개 주소에서 도도부현·시구정촌을 구분합니다. 주소가 없을 때 검색 지역을 추측해서 채우지 않습니다. */
    public static void location(PropertyListing listing, String address) {
        listing.setAddress(address);
        Matcher match = LOCATION.matcher(normalize(address));
        if (match.find()) {
            listing.setPrefecture(match.group(1));
            listing.setCity(match.group(2));
        }
    }

    /** 첫 교통 정보에서 역명과 도보 시간을 읽습니다. 버스 시간을 도보 시간으로 바꾸지 않습니다. */
    public static void transit(PropertyListing listing, String value) {
        String normalized = normalize(value);
        Matcher station = Pattern.compile("[「『]([^」』]+)[」』]\\s*駅?|/([^/ ]+駅)").matcher(normalized);
        if (station.find()) {
            String name = station.group(1) != null ? station.group(1) : station.group(2);
            listing.setStationName(name.endsWith("駅") ? name : name + "駅");
        }
        Matcher walk = WALK.matcher(normalized);
        if (!normalized.contains("バス") && walk.find()) {
            int minutes = Integer.parseInt(walk.group(1));
            if (minutes <= Short.MAX_VALUE) listing.setWalkMinutes(minutes);
        }
    }

    /** 허용된 출처의 상세 링크를 검증하고 Model을 만듭니다. sourceId와 수집 시각은 Service가 채웁니다. */
    public static PropertyListing listing(ListingSource source, String title, String href) {
        PropertyListing listing = new PropertyListing();
        listing.setTitle(title == null ? null : title.strip());
        listing.setOriginalUrl(source.canonicalDetail(href));
        listing.setSourceListingKey(source.listingKey(listing.getOriginalUrl()));
        listing.setSourceCode(source.code());
        listing.setSourceName(source.displayName());
        listing.setStatus("ACTIVE");
        return listing;
    }
}
