package com.example.demo.model;

import lombok.Getter;
import lombok.Setter;

/**
 * property_source의 한 행을 표현하는 Model DTO입니다. 출처 선택 메뉴와 수집 사용 여부를 전달합니다.
 * @Getter/@Setter는 MyBatis가 DB 값을 넣고 Thymeleaf가 표시값을 읽을 접근 메서드를 생성합니다.
 * enabled는 DB의 is_enabled에 대응하며, 이미 중지한 출처를 재수집 시 임의로 활성화하지 않습니다.
 */
@Getter
@Setter
public class PropertySource {
    private Long id;
    private String sourceCode;
    private String sourceName;
    private String baseUrl;
    private Boolean enabled;
    private Long listingCount;
}
