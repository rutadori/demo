package com.example.demo.config;

import com.example.demo.service.CrawlService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 명령행에서 최초 수집을 명시적으로 요청했을 때만 실행하는 시작 작업입니다.
 * @ConditionalOnProperty 때문에 기본 실행·테스트에서는 생성되지 않아 외부 사이트에 자동 접속하지 않습니다.
 * 실제 업무는 Controller와 동일한 CrawlService를 호출하므로 SQL·수집 로직을 중복 작성하지 않습니다.
 */
@Component
@ConditionalOnProperty(name = "house.crawler.run-on-start", havingValue = "true")
public class InitialCrawlRunner implements ApplicationRunner {
    private final CrawlService crawlService;

    /** 수집 업무 Bean을 생성자로 주입합니다. */
    public InitialCrawlRunner(CrawlService crawlService) { this.crawlService = crawlService; }

    /** Spring 시작 후 네 출처의 제한된 최초 수집을 한 번 실행합니다. 정기 스케줄러는 아닙니다. */
    @Override
    public void run(ApplicationArguments args) { crawlService.collectAll(); }
}
