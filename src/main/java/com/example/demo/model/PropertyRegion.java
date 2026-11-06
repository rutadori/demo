package com.example.demo.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 검색 화면과 수집 업무에서 함께 사용하는 여섯 지역의 고정 설정입니다.
 * @Getter는 Thymeleaf와 Service가 읽을 접근 메서드를 생성합니다. DB에는 prefecture의 일본어 원문을 저장합니다.
 * 지역별로 등록된 여러 도시·구의 목록을 수집합니다. 각 도시의 첫 공개 목록 페이지만 사용합니다.
 * 사이트마다 다른 URL 표기는 여기에서 관리하고 실제 URI 생성·HTTP 접근은 수집 계층에 맡깁니다.
 * 새 지역을 추가할 때 이 상수와 공개 목록 주소 규칙을 확인하면 검색 선택지·지역 링크·관리 화면이 함께 늘어납니다.
 */
@Getter
public enum PropertyRegion {
    TOKYO("tokyo", "東京都", "도쿄", "신주쿠구", "新宿区", "tokyo", "shinjuku", "shinjuku", "shinjuku-ku-13104"),
    OSAKA("osaka", "大阪府", "오사카", "오사카시 기타구", "大阪市北区", "osaka", "osakashikita", "osaka_kita", "osaka-shi-kita-ku-27127"),
    KYOTO("kyoto", "京都府", "교토", "교토시 시모교구", "京都市下京区", "kyoto", "kyotoshishimogyo", "kyoto_shimogyo", "kyoto-shi-shimogyo-ku-26106"),
    FUKUOKA("fukuoka", "福岡県", "후쿠오카", "후쿠오카시 하카타구", "福岡市博多区", "fukuoka", "fukuokashihakata", "fukuoka_hakata", "fukuoka-shi-hakata-ku-40132"),
    AICHI("aichi", "愛知県", "아이치 · 나고야", "나고야시 나카구", "名古屋市中区", "aichi", "nagoyashinaka", "nagoya_naka", "nagoya-shi-naka-ku-23106"),
    HOKKAIDO("hokkaido", "北海道", "홋카이도", "삿포로시 주오구", "札幌市中央区", "hokkaido_", "sapporoshichuo", "sapporo_chuo", "sapporo-shi-chuo-ku-01101");

    private final String code;
    private final String prefecture;
    private final String displayName;
    private final String representativeCity;
    private final String cityName;
    private final String suumoPrefectureSlug;
    private final String suumoCitySlug;
    private final String portalCitySlug;
    private final String leopalaceCitySlug;

    /** 지역별 화면 표시·DB 표기·출처별 대표 도시 경로를 초기화합니다. 사용자가 입력한 주소를 저장하지 않습니다. */
    PropertyRegion(String code, String prefecture, String displayName, String representativeCity, String cityName,
                   String suumoPrefectureSlug, String suumoCitySlug, String portalCitySlug, String leopalaceCitySlug) {
        this.code = code;
        this.prefecture = prefecture;
        this.displayName = displayName;
        this.representativeCity = representativeCity;
        this.cityName = cityName;
        this.suumoPrefectureSlug = suumoPrefectureSlug;
        this.suumoCitySlug = suumoCitySlug;
        this.portalCitySlug = portalCitySlug;
        this.leopalaceCitySlug = leopalaceCitySlug;
    }


    /**
     * 도도부현별 네 도시·구를 반환합니다. 기존 대표 구도 남겨 두어 이전 지역의 갱신이 계속됩니다.
     * URL 표기는 사이트의 공개 도시 목록 경로를 사용하며, 실제 저장 지역은 HTML 주소를 파싱하여 검증합니다.
     * 새 도시를 늘릴 때는 이 고정 설정에 추가하면 수집·관리 범위가 함께 확장됩니다.
     */
    public List<CollectionArea> getAreas() {
        CollectionArea original = new CollectionArea(this, cityName, representativeCity,
                suumoCitySlug, portalCitySlug, leopalaceCitySlug);
        return switch (this) {
            case TOKYO -> List.of(original,
                    area("世田谷区", "세타가야구", "setagaya", "setagaya", "setagaya-ku-13112"),
                    area("渋谷区", "시부야구", "shibuya", "shibuya", "shibuya-ku-13113"),
                    area("豊島区", "도시마구", "toshima", "toshima", "toshima-ku-13116"));
            case OSAKA -> List.of(original,
                    area("大阪市中央区", "오사카시 주오구", "osakashichuo", "osaka_chuo", "osaka-shi-chuo-ku-27128"),
                    area("大阪市天王寺区", "오사카시 덴노지구", "osakashitennoji", "osaka_tennoji", "osaka-shi-tennoji-ku-27109"),
                    area("豊中市", "도요나카시", "toyonaka", "toyonaka", "toyonaka-shi-27203"));
            case KYOTO -> List.of(original,
                    area("京都市中京区", "교토시 나카교구", "kyotoshinakagyo", "kyoto_nakagyo", "kyoto-shi-nakagyo-ku-26104"),
                    area("京都市左京区", "교토시 사쿄구", "kyotoshisakyo", "kyoto_sakyo", "kyoto-shi-sakyo-ku-26103"),
                    area("宇治市", "우지시", "uji", "uji", "uji-shi-26204"));
            case FUKUOKA -> List.of(original,
                    area("福岡市中央区", "후쿠오카시 주오구", "fukuokashichuo", "fukuoka_chuo", "fukuoka-shi-chuo-ku-40133"),
                    area("北九州市小倉北区", "기타큐슈시 고쿠라키타구", "kitakyushushikokurakita", "kitakyushu_kokurakita", "kitakyushu-shi-kokurakita-ku-40106"),
                    area("久留米市", "구루메시", "kurume", "kurume", "kurume-shi-40203"));
            case AICHI -> List.of(original,
                    area("名古屋市中村区", "나고야시 나카무라구", "nagoyashinakamura", "nagoya_nakamura", "nagoya-shi-nakamura-ku-23105"),
                    area("豊田市", "도요타시", "toyota", "toyota", "toyota-shi-23211"),
                    area("岡崎市", "오카자키시", "okazaki", "okazaki", "okazaki-shi-23202"));
            case HOKKAIDO -> List.of(original,
                    area("旭川市", "아사히카와시", "asahikawa", "asahikawa", "asahikawa-shi-01204"),
                    area("函館市", "하코다테시", "hakodate", "hakodate", "hakodate-shi-01202"),
                    area("小樽市", "오타루시", "otaru", "otaru", "otaru-shi-01203"));
        };
    }

    /** 현재 도도부현에 속하는 도시 설정을 생성합니다. 외부 사용자 입력은 경로에 사용하지 않습니다. */
    private CollectionArea area(String city, String name, String suumo, String portal, String leopalace) {
        return new CollectionArea(this, city, name, suumo, portal, leopalace);
    }

    /** 지역 카드와 관리 화면에 대표 한 곳 대신 실제 등록한 전체 도시·구 범위를 표시합니다. */
    public String getCollectionSummary() {
        return getAreas().stream().map(CollectionArea::displayName).collect(Collectors.joining(" · "));
    }

    /** 관리 폼의 고정 코드를 enum으로 바꾸며 등록하지 않은 지역 값은 수집 전에 거부합니다. */
    public static PropertyRegion fromCode(String code) {
        return Arrays.stream(values()).filter(region -> region.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 수집 지역입니다."));
    }
}
