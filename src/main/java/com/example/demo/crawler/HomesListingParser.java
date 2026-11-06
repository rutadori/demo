package com.example.demo.crawler;

import org.springframework.stereotype.Component;

/**
 * HOME'S의 출처를 공통 방 행 파서에 지정하는 커넥터입니다. @Component로 수집 Service에 등록합니다.
 * 서버가 403을 반환하면 HTTP 계층에서 중단되므로 파서는 호출되지 않고 접근 거부 결과를 표시합니다.
 */
@Component
public class HomesListingParser extends PortalTableParser {
    /** HOME'S의 고정 호스트·목록·상세 링크 검증 규칙을 제공합니다. */
    @Override
    public ListingSource source() { return ListingSource.HOMES; }
}
