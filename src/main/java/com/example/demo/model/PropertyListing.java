package com.example.demo.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * MVC의 Model에서 사용하는 매물 데이터 전달 객체입니다. HTML 파싱, HTTP 요청, SQL 실행은 하지 않습니다.
 * 수집 결과를 Service에서 Mapper로 전달하고, 조회 결과를 Controller에서 Thymeleaf로 전달할 때 사용합니다.
 * @Getter와 @Setter는 Lombok이 필드 접근 메서드를 생성하도록 합니다. MyBatis와 Thymeleaf가 이 메서드를 사용합니다.
 * 금액·도보 시간은 정보가 없는 NULL과 실제 0을 구분하기 위해 기본형 int 대신 Integer를 사용합니다.
 * sourceName·sourceCode는 출처 테이블을 JOIN해서 조회하는 화면용 정보입니다.
 */
@Getter
@Setter
public class PropertyListing {
    private Long id;
    private Long sourceId;
    private String sourceListingKey;
    private String sourceName;
    private String sourceCode;
    private String title;
    private String originalUrl;
    private String prefecture;
    private String city;
    private String address;
    private Integer rentYen;
    private Integer managementFeeYen;
    private String layout;
    private BigDecimal areaM2;
    private String stationName;
    private Integer walkMinutes;
    private String imageUrl;
    private String status;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
