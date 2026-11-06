package com.example.demo.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 검색 폼의 GET 파라미터를 받는 MVC Model입니다. Controller의 @ModelAttribute가 같은 이름의 값을 바인딩합니다.
 * @Min은 음수 조건을, @Size는 지나치게 긴 검색어를 검증합니다. 실제 검증은 Controller의 @Valid로 실행합니다.
 * 정렬은 SQL에 요청 문자열을 직접 넣지 않고 Mapper XML의 choose로 미리 정한 구문을 선택합니다.
 */
@Getter
@Setter
public class PropertySearch {
    @Size(max = 50, message = "지역 값이 너무 깁니다.")
    private String prefecture;
    @Size(max = 100, message = "도시·구 값이 너무 깁니다.")
    private String city;
    @Size(max = 100, message = "검색어는 100자 이내로 입력해 주세요.")
    private String keyword;
    @Size(max = 30, message = "방 구조 값이 너무 깁니다.")
    private String layout;
    @Min(value = 0, message = "최소 월세는 0 이상이어야 합니다.")
    private Integer minRent;
    @Min(value = 0, message = "최대 월세는 0 이상이어야 합니다.")
    private Integer maxRent;
    @Min(value = 0, message = "도보 시간은 0 이상이어야 합니다.")
    private Integer walkMinutes;
    @Min(value = 1, message = "출처 번호가 올바르지 않습니다.")
    private Long sourceId;
    @Size(max = 30, message = "정렬 값이 올바르지 않습니다.")
    private String sort = "newest";
    // 사용자의 표시용 검색어를 바꾸지 않고 Service가 정규화한 실제 조회 문자열을 보관합니다.
    private String queryKeyword;

    /**
     * @AssertTrue는 최소 월세가 최대 월세보다 큰 조합을 검증합니다.
     * 한쪽 범위만 입력하는 검색은 정상으로 처리합니다.
     * @return 입력한 월세 범위가 올바르면 true
     */
    @AssertTrue(message = "최소 월세는 최대 월세보다 클 수 없습니다.")
    public boolean isRentRangeValid() {
        return minRent == null || maxRent == null || minRent <= maxRent;
    }
}
