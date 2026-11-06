package com.example.demo.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 한 출처의 마지막 수집 결과를 관리 화면으로 전달하는 Model DTO입니다. 실제 DB 매물 개수와 이번 처리 건수는 구분합니다.
 * @AllArgsConstructor는 결과 생성용 생성자를, @Getter는 Thymeleaf 조회용 getter를 Lombok으로 생성합니다.
 * 결과는 현재 서버 실행 중 메모리에 유지됩니다. 서버 재시작 후에도 매물 자체는 MySQL에 남습니다.
 */
@Getter
@AllArgsConstructor
public class CrawlResult {
    private String sourceCode;
    private String sourceName;
    private String status;
    private int savedCount;
    private String message;
    private LocalDateTime checkedAt;
    private String regionCode;
    private String regionName;

    /** 내부 상태 코드를 화면에서 이해할 수 있는 짧은 한글 표현으로 변환합니다. */
    public String getStatusLabel() {
        return switch (status) {
            case "SUCCESS" -> "수집 완료";
            case "NOT_RUN" -> "수집 전";
            case "DISABLED" -> "수집 중지";
            case "PARSE_EMPTY" -> "구조 확인 필요";
            case "COOLDOWN" -> "잠시 후 다시 실행";
            case "SKIPPED_ACCESS" -> "접근 거부로 생략";
            case "ACCESS_CHALLENGE" -> "자동 접근 확인 페이지";
            default -> "수집 실패 · " + status;
        };
    }
}
