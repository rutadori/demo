package com.example.demo.crawler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP 200의 자동 접근 확인 응답을 정상 매물이나 구조 오류로 오인하지 않는지 외부 접속 없이 검사합니다. */
class PublicPageClientTests {
    /** at home에서 실제 관찰한 認証中 제목을 별도의 접근 확인 실패로 분류합니다. */
    @Test
    void authenticationPageIsReportedAsAccessChallenge() {
        byte[] html = "<html><head><meta charset='UTF-8'><title>【アットホーム】認証中</title></head></html>".getBytes(StandardCharsets.UTF_8);
        CrawlException error = assertThrows(CrawlException.class, () -> PublicPageClient.decodePage(html, URI.create("https://www.athome.co.jp/")));
        assertEquals("ACCESS_CHALLENGE", error.getStatus());
    }

    /** 정상 목록 HTML은 제목에 출처 이름이 있어도 그대로 파서로 넘깁니다. */
    @Test
    void ordinaryListingPageIsReturned() throws Exception {
        byte[] html = "<html><head><meta charset='UTF-8'><title>北海道の賃貸</title></head><body><h1>札幌</h1></body></html>".getBytes(StandardCharsets.UTF_8);
        var document = PublicPageClient.decodePage(html, URI.create("https://suumo.jp/chintai/hokkaido_/sc_sapporoshichuo/"));
        assertEquals("札幌", document.selectFirst("h1").text());
    }
}
