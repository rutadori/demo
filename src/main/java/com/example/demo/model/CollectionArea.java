package com.example.demo.model;

/**
 * 도도부현 아래의 실제 수집 도시·구를 나타내는 불변 Model 값입니다.
 * region은 검색의 상위 지역, cityName은 원문 주소 표기, displayName은 학습 화면의 한글 표시명입니다.
 * 사이트마다 도시 경로가 다르므로 각 공개 목록의 경로 조각을 따로 보관합니다.
 * record는 생성자·접근자·값 비교를 자동 생성하며, 실행 중 경로가 변경되지 않게 합니다.
 */
public record CollectionArea(PropertyRegion region, String cityName, String displayName,
                             String suumoSlug, String portalSlug, String leopalaceSlug) {
    /** 같은 도도부현의 다른 도시가 실행 결과·10분 간격 제한을 공유하지 않도록 고유 코드를 반환합니다. */
    public String code() { return region.getCode() + ":" + suumoSlug; }
}
