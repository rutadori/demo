package com.example.demo.controller;

import com.example.demo.service.CrawlService;
import com.example.demo.service.PropertyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 로그인한 학습 사용자의 수집 요청을 받는 MVC Controller입니다. @Controller의 반환값은 Thymeleaf 뷰 이름입니다.
 * @RequestMapping은 모든 주소 앞에 /manage/crawling을 붙입니다. Spring Security가 이 주소의 인증을 확인합니다.
 * HTTP·Jsoup·Mapper를 직접 사용하지 않고 Service만 호출합니다. GET은 조회, DB 변경은 CSRF가 있는 POST로 처리합니다.
 */
@Controller
@RequestMapping("/manage/crawling")
public class CrawlController {
    private final CrawlService crawlService;
    private final PropertyService propertyService;

    /** 두 업무 Service를 생성자 주입합니다. Controller는 SQL이나 수집 규칙을 알 필요가 없습니다. */
    public CrawlController(CrawlService crawlService, PropertyService propertyService) {
        this.crawlService = crawlService;
        this.propertyService = propertyService;
    }

    /** GET 요청에서 DB 출처별 매물 수와 이번 서버 실행의 최근 수집 결과를 Model에 넣어 표시합니다. */
    @GetMapping
    public String page(@RequestParam(defaultValue = "all") String region, Model model) {
        model.addAttribute("sources", propertyService.sources());
        model.addAttribute("crawlResults", crawlService.latest());
        model.addAttribute("regions", propertyService.regions());
        model.addAttribute("selectedRegion", region);
        model.addAttribute("maxPerSource", crawlService.getMaxPerSource());
        return "manage/crawling";
    }

    /**
     * POST로 수집을 실행한 뒤 GET으로 리다이렉트합니다. 새로고침이 같은 POST를 반복하지 않게 합니다.
     * RedirectAttributes의 flash 값은 다음 화면에 한 번만 표시할 실행 안내입니다.
     */
    @PostMapping
    public String collect(@RequestParam(defaultValue = "all") String region, RedirectAttributes redirect) {
        try {
            if ("all".equals(region)) crawlService.collectAll();
            else crawlService.collectRegion(region);
            if (!"all".equals(region)) redirect.addAttribute("region", region);
            redirect.addFlashAttribute("crawlMessage", "지역별 수집 결과를 확인해 주세요. 같은 도시·구·출처는 10분 이내 다시 요청하지 않습니다.");
        } catch (IllegalStateException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("crawlMessage", ex.getMessage());
        }
        return "redirect:/manage/crawling";
    }
}
