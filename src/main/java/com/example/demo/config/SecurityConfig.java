package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 사이트의 접근 권한과 로그인·로그아웃을 Spring Security 필터로 설정하는 클래스입니다.
 * 인터셉터를 사용하지 않으며, 요청은 Controller에 도착하기 전에 보안 필터를 통과합니다.
 * @Configuration은 이 클래스를 설정 정보로 등록합니다. 메인 클래스가 있는
 * com.example.demo의 하위 패키지에 두어야 기본 컴포넌트 스캔으로 발견됩니다.
 * 회원 DB 기능을 만들기 전이므로 계정은 Spring Boot의 기본 임시 계정을 사용합니다.
 * 아이디는 user, 비밀번호는 실행 콘솔의 Using generated security password 값을 사용합니다.
 */
@Configuration
public class SecurityConfig {

    /**
     * @Bean으로 보안 필터 체인을 Spring 컨테이너에 등록합니다.
     * 화면을 먼저 확인할 수 있도록 공개 화면의 GET 요청과 CSS·정적 이미지를 허용합니다.
     * 나머지 요청에는 로그인이 필요하며, 회원가입 저장 등 아직 없는 기능을 허용하지 않습니다.
     * GET /member/login은 Controller가 HTML을 보여주고, 같은 URL의 POST는
     * Spring Security가 username/password를 읽어 인증하므로 로그인 POST Controller는 불필요합니다.
     * 성공하면 반드시 메인 화면으로, 실패하면 로그인 화면의 오류 안내로 이동합니다.
     * CSRF 보호는 기본값 그대로 유지합니다. Thymeleaf의 th:action이 POST 폼에 토큰을 넣습니다.
     *
     * @param http Spring Security가 제공하는 HTTP 보안 설정 객체
     * @return 모든 웹 요청에 적용할 보안 필터 체인
     * @throws Exception 보안 필터 체인 구성에 실패한 경우
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/images/**", "/error", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/member/login", "/member/join",
                                "/property/list", "/property/detail",
                                "/recommend/search", "/recommend/result", "/qna").permitAll()
                        // 학습용 관리 기능은 현재 임시 계정으로 로그인한 사용자에게만 허용합니다.
                        .requestMatchers("/manage/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/member/login")
                        .loginProcessingUrl("/member/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/member/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/member/logout")
                        .logoutSuccessUrl("/member/login?logout")
                        .permitAll()
                );

        return http.build();
    }
}
