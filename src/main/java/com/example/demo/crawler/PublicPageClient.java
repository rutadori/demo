package com.example.demo.crawler;

import com.example.demo.model.PropertyRegion;
import com.example.demo.model.CollectionArea;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * 공개 페이지를 가져오는 HTTP 계층입니다. @Component로 수집 Service에 주입됩니다.
 * 정해진 네 호스트의 robots.txt와 목록 한 페이지만 읽고, CAPTCHA·쿠키·인증·프록시 우회를 하지 않습니다.
 * TLS 인증서 검증은 JDK 기본값으로 유지합니다. 브라우저인 척 User-Agent를 바꾸지 않습니다.
 * timeout·응답 크기 상한·요청 간 간격을 두며 HTTP 거부 응답은 그대로 실패로 반환합니다.
 */
@Component
public class PublicPageClient {
    private static final String PRODUCT = "NihonHouseLearning";
    private static final int MAX_BYTES = 5_000_000;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<ListingSource, RobotsSnapshot> robotsCache = new ConcurrentHashMap<>();

    /**
     * 지역별 목록 경로를 robots.txt에 대조한 뒤 요청합니다. 같은 호스트의 robots 응답은 한 시간 재사용합니다.
     * 지역이 늘어나도 robots.txt를 매번 다시 요청하지 않으며, 캐시된 내용으로 각 지역 경로를 개별 검사합니다.
     * HTML meta charset은 Jsoup이 판별하도록 바이트로 전달합니다.
     */
    public Document fetch(ListingSource source, PropertyRegion region) throws IOException, InterruptedException {
        return fetch(source, region.getAreas().getFirst());
    }

    /** 개별 도시·구 경로도 robots.txt와 응답 검증을 동일하게 통과시킨 뒤 공개 HTML만 반환합니다. */
    public Document fetch(ListingSource source, CollectionArea area) throws IOException, InterruptedException {
        URI listUri = source.listUri(area);
        RobotsSnapshot snapshot = robotsCache.get(source);
        if (snapshot == null || System.currentTimeMillis() - snapshot.loadedAt() > Duration.ofHours(1).toMillis()) {
            Response loaded = request(source, source.baseUri().resolve("/robots.txt"));
            snapshot = new RobotsSnapshot(loaded, System.currentTimeMillis());
            robotsCache.put(source, snapshot);
        }
        Response robots = snapshot.response();
        if (robots.status != 404) {
            requireSuccess(robots.status);
            if (!RobotsPolicy.isAllowed(new String(robots.body, StandardCharsets.UTF_8), listUri, PRODUCT))
                throw new CrawlException("ROBOTS_DENIED", "robots.txt가 이 목록 경로의 수집을 제한합니다.");
        }
        Thread.sleep(1500);
        Response page = request(source, listUri);
        requireSuccess(page.status);
        return decodePage(page.body, listUri);
    }

    /**
     * 정상 상태 코드여도 자동 접근 확인 HTML일 수 있으므로 제목을 확인합니다.
     * 이 응답을 빈 매물 목록으로 오인하지 않고 별도 실패로 전달하며 확인 페이지를 자동 통과하지 않습니다.
     * package 범위 메서드로 분리하여 외부 접속 없이 실제 관찰한 응답 형태를 회귀 테스트할 수 있습니다.
     */
    static Document decodePage(byte[] body, URI uri) throws IOException {
        Document document = Jsoup.parse(new ByteArrayInputStream(body), null, uri.toString());
        String title = document.title();
        if (title.contains("認証中") || title.equalsIgnoreCase("Just a moment...") || title.equalsIgnoreCase("Access Denied"))
            throw new CrawlException("ACCESS_CHALLENGE", "원본이 매물 목록 대신 자동 접근 확인 페이지를 반환했습니다. 확인 절차는 우회하지 않습니다.");
        return document;
    }

    /** robots 응답과 재요청 시점을 묶는 내부 캐시 값입니다. 매물 HTML은 캐시하지 않습니다. */
    private record RobotsSnapshot(Response response, long loadedAt) { }

    /** HTTP 상태와 제한된 응답 바이트를 함께 반환하는 내부 값입니다. */
    private record Response(int status, byte[] body) { }

    /** 허용 호스트에 한 번 요청합니다. 외부 리다이렉트와 무제한 응답 다운로드를 허용하지 않습니다. */
    private Response request(ListingSource source, URI uri) throws IOException, InterruptedException {
        if (!source.allows(uri)) throw new CrawlException("INVALID_URL", "허용된 수집 주소가 아닙니다.");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("User-Agent", PRODUCT + "/0.1 (local educational project)")
                .header("Accept", "text/html,text/plain;q=0.9").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            byte[] body = stream.readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) throw new CrawlException("RESPONSE_TOO_LARGE", "응답 크기가 학습용 수집 한도를 넘었습니다.");
            return new Response(response.statusCode(), body);
        }
    }

    /** 성공 응답만 파싱합니다. 403·405·429 등을 정상적인 매물 0건으로 위장하지 않습니다. */
    private void requireSuccess(int status) {
        if (status < 200 || status >= 300)
            throw new CrawlException("HTTP_" + status, "원본 사이트가 HTTP " + status + "로 응답했습니다. 접근 거부 응답은 우회하지 않습니다.");
    }
}
