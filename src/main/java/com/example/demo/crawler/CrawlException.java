package com.example.demo.crawler;

/** 수집 실패 유형을 HTTP·robots·구조 오류로 구분하여 Service와 관리 화면으로 전달하는 예외입니다. */
public class CrawlException extends RuntimeException {
    private final String status;

    /** 실패 코드와 사용자에게 표시할 짧은 원인을 보관합니다. 인증정보나 서버 응답 원문은 포함하지 않습니다. */
    public CrawlException(String status, String message) {
        super(message);
        this.status = status;
    }

    /** 관리 화면과 로그에 사용할 고정 실패 코드를 반환합니다. */
    public String getStatus() { return status; }
}
