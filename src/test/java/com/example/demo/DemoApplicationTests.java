package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @SpringBootTest로 실제 애플리케이션의 설정을 로드해 Spring 컨테이너 시작 여부를 확인합니다.
 * 웹 요청과 로그인 동작은 별도의 PageSecurityTests에서 검증합니다.
 */
@SpringBootTest
class DemoApplicationTests {

    /** @Test는 이 메서드를 테스트로 실행하며, 설정 로딩에 예외가 생기면 실패합니다. */
    @Test
    void contextLoads() {
    }

}
