package com.example.demo.controller;

import com.example.demo.model.PropertySearch;
import com.example.demo.service.PropertyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 브라우저의 URL을 Thymeleaf HTML 화면에 연결하는 MVC의 Controller입니다.
 * @Controller는 이 클래스를 Spring MVC의 화면 처리 대상으로 등록합니다.
 * @RestController와 달리 반환한 문자열을 응답 본문이 아닌 '뷰 이름'으로 해석합니다.
 * application.properties의 prefix(classpath:/templates/Boot/)와 기본 suffix(.html)를
 * 뷰 이름 앞뒤에 붙여 실제 파일을 찾습니다. 예를 들어 index는 Boot/index.html입니다.
 * 매물 조회는 PropertyService에 위임하고 결과만 Model에 넣습니다. SQL·HTTP 수집·HTML 파싱은 직접 하지 않습니다.
 * 로그인과 추천 안내 화면도 연결하지만 실제 회원가입·통근 추천은 아직 수행하지 않습니다.
 */
@Controller
public class PageController {
    private final PropertyService propertyService;

    /** 생성자 주입으로 매물 조회 업무를 받습니다. Controller가 Mapper를 직접 호출하지 않게 분리합니다. */
    public PageController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    /**
     * @GetMapping은 지정한 주소의 HTTP GET 요청을 이 메서드에 연결합니다.
     * 메인 주소 / 및 로그인 성공 후 이동을 처리합니다. HTML이 templates에 존재하는 것만으로는
     * URL이 만들어지지 않으므로 이 매핑이 없으면 인증에 성공해도 메인에서 404가 발생합니다.
     * @return 메인 화면의 뷰 이름
     */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("propertyList", propertyService.recent());
        model.addAttribute("sources", propertyService.sources());
        model.addAttribute("regions", propertyService.regions());
        return "index";
    }

    /**
     * @GetMapping은 GET /qna 요청을 예시 질문·답변 화면에 연결합니다.
     * 반환값 qna/list는 Thymeleaf의 prefix·suffix와 결합하여 Boot/qna/list.html을 찾습니다.
     * 지금은 화면 구현 단계이므로 질문·답변은 HTML에 고정하며 DB 조회나 문의 저장을 수행하지 않습니다.
     * 추후 실제 문의 기능을 추가할 때에는 Service에서 조회한 결과를 Model에 담아 View로 전달합니다.
     * @return 공통 메뉴·레이아웃을 사용하는 Q&A 목록의 뷰 이름
     */
    @GetMapping("/qna")
    public String qna() {
        return "qna/list";
    }

    /**
     * GET /member/login에서 직접 만든 로그인 화면을 제공합니다.
     * @GetMapping이 GET만 처리하므로 POST 인증은 SecurityFilterChain이 담당합니다.
     * error, logout 쿼리 파라미터에 따른 안내 표시는 HTML에서 처리합니다.
     * @return 로그인 화면의 뷰 이름
     */
    @GetMapping("/member/login")
    public String login() {
        return "member/login";
    }

    /**
     * @GetMapping으로 회원가입 화면을 연결합니다. 현재 단계는 화면 미리보기이며
     * 회원 DB 저장 및 검증용 POST 메서드는 회원 기능을 구현하는 단계에서 추가합니다.
     * @return 회원가입 화면의 뷰 이름
     */
    @GetMapping("/member/join")
    public String join() {
        return "member/join";
    }

    /**
     * @GetMapping으로 매물 검색 화면을 연결합니다. HTML에서 검색 파라미터를 읽어 입력값을 유지합니다.
     * @ModelAttribute는 GET 조건을 검색 DTO에 넣고 @Valid는 숫자 범위·문자 길이를 검증합니다.
     * BindingResult는 검증 결과이며 반드시 해당 ModelAttribute 바로 뒤에 선언합니다.
     * 검증 성공 시 Service로 DB 검색하고 결과를 propertyList·sources라는 Model 이름으로 전달합니다.
     * 검증 실패 시 SQL은 실행하지 않고 조건 수정 안내와 빈 목록을 표시합니다.
     * @return 매물 목록 화면의 뷰 이름
     */
    @GetMapping("/property/list")
    public String propertyList(@Valid @ModelAttribute("search") PropertySearch search,
                               BindingResult validation, Model model) {
        if (validation.hasErrors()) {
            model.addAttribute("searchError", "검색 조건이 올바르지 않습니다. 숫자 입력값과 최소·최대 월세 범위를 확인해 주세요.");
            model.addAttribute("propertyList", List.of());
        } else {
            model.addAttribute("propertyList", propertyService.search(search));
        }
        model.addAttribute("sources", propertyService.sources());
        model.addAttribute("regions", propertyService.regions());
        model.addAttribute("cities", propertyService.cities(search.getPrefecture()));
        return "property/list";
    }

    /**
     * @GetMapping으로 기존 상세 주소를 안내 화면에 연결합니다.
     * id가 있으면 Service에서 실제 노출 매물을 조회하고 없으면 목록 선택 안내를 표시합니다.
     * 통합 목록의 매물 카드는 원본 상세 주소로 바로 이동하므로 이 화면을 경유하지 않습니다.
     * @return 매물 상세 화면의 뷰 이름
     */
    @GetMapping("/property/detail")
    public String propertyDetail(@RequestParam(required = false) Long id, Model model) {
        model.addAttribute("property", propertyService.find(id));
        return "property/detail";
    }

    /**
     * @GetMapping의 배열로 추천 안내 주소와 기존 폼 제출 주소를 같은 준비 화면에 연결합니다.
     * 현재는 지도·교통 API나 DB를 조회하지 않으므로 입력 폼은 비활성화하고 결과는 표시하지 않습니다.
     * 실제 추천 알고리즘은 추후 Service에서 구현하고 결과 모델을 뷰에 전달합니다.
     * @return 회사·학교 근처 추천 화면의 뷰 이름
     */
    @GetMapping({"/recommend/search", "/recommend/result"})
    public String recommend() {
        return "recommend/search";
    }
}
