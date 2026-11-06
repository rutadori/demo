package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 부동산 웹 애플리케이션을 시작하는 진입점입니다.
 * @SpringBootApplication은 설정 클래스 선언, 자동 설정, 컴포넌트 스캔을 함께 적용합니다.
 * 이 클래스의 패키지인 com.example.demo와 그 하위에 있는 @Controller, @Configuration,
 * 추후 추가할 @Service 등을 찾아 Spring 객체로 등록합니다. 상위 패키지는 기본 탐색 범위가 아닙니다.
 */
@SpringBootApplication
public class DemoApplication {

    /**
     * JVM이 실행하는 시작 메서드입니다. Spring 컨테이너와 내장 웹 서버를 구동합니다.
     * 서버 포트와 DB·Thymeleaf 설정은 application.properties에서 읽습니다.
     * @param args 실행 시 전달하는 설정 인자 (예: --server.port=8085)
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
