package com.example.demo.crawler;

import com.example.demo.model.PropertyListing;
import java.util.List;
import org.jsoup.nodes.Document;

/**
 * 사이트별 HTML 해석 규칙의 공통 인터페이스입니다. HTTP·DB 접근은 담당하지 않는 순수 파싱 계층입니다.
 * 같은 입력 HTML로 반복 테스트할 수 있고, 한 사이트 구조가 바뀌면 해당 구현만 수정합니다.
 */
public interface ListingParser {
    /** 이 파서가 담당하는 고정 출처를 반환합니다. */
    ListingSource source();

    /** 목록 HTML에서 최대 limit개의 실제 방 정보를 읽습니다. 구조가 맞지 않으면 빈 목록을 반환합니다. */
    List<PropertyListing> parse(Document document, int limit);
}
