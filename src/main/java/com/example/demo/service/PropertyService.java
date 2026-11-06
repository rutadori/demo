package com.example.demo.service;

import com.example.demo.mapper.PropertyMapper;
import com.example.demo.model.PropertyListing;
import com.example.demo.model.PropertyRegion;
import com.example.demo.model.PropertySearch;
import com.example.demo.model.PropertySource;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매물 조회의 업무 계층입니다. @Service가 Spring Bean으로 등록하고 Controller와 Mapper 사이를 연결합니다.
 * @Transactional(readOnly=true)는 조회 트랜잭션임을 선언합니다. HTML·HTTP 수집은 담당하지 않습니다.
 * Controller는 이 서비스의 결과만 Model에 넣으며 SQL이나 HTML 파싱을 직접 작성하지 않습니다.
 */
@Service
@Transactional(readOnly = true)
public class PropertyService {
    private final PropertyMapper mapper;
    private static final Map<String, String> KEYWORD_ALIASES = Map.ofEntries(
            Map.entry("신주쿠", "新宿"), Map.entry("도쿄", "東京"), Map.entry("오사카", "大阪"), Map.entry("교토", "京都"),
            Map.entry("후쿠오카", "福岡"), Map.entry("아이치", "愛知"), Map.entry("나고야", "名古屋"),
            Map.entry("홋카이도", "北海道"), Map.entry("삿포로", "札幌"),
            Map.entry("세타가야", "世田谷"), Map.entry("시부야", "渋谷"), Map.entry("도시마", "豊島"),
            Map.entry("아사히카와", "旭川"), Map.entry("하코다테", "函館"), Map.entry("오타루", "小樽"),
            Map.entry("도요나카", "豊中"), Map.entry("우지", "宇治"), Map.entry("구루메", "久留米"),
            Map.entry("도요타", "豊田"), Map.entry("오카자키", "岡崎"), Map.entry("나카무라", "中村"),
            Map.entry("기타큐슈", "北九州"), Map.entry("고쿠라", "小倉"), Map.entry("나카교", "中京"),
            Map.entry("사쿄", "左京"), Map.entry("덴노지", "天王寺"), Map.entry("주오", "中央"));

    /** 검색·메인·관리 화면에 동일한 여섯 지역 설정을 전달하여 선택지가 서로 달라지지 않도록 합니다. */
    public List<PropertyRegion> regions() {
        return List.of(PropertyRegion.values());
    }

    /** 생성자 주입으로 DB Mapper를 받습니다. 생성자가 하나이므로 @Autowired를 생략할 수 있습니다. */
    public PropertyService(PropertyMapper mapper) {
        this.mapper = mapper;
    }

    /** 검증한 검색 조건을 정규화하고 바인딩 SQL로 조회합니다. 표시용 keyword 값은 그대로 둡니다. */
    public List<PropertyListing> search(PropertySearch search) {
        String keyword = search.getKeyword() == null ? "" : Normalizer.normalize(search.getKeyword().strip(), Normalizer.Form.NFKC);
        search.setQueryKeyword(KEYWORD_ALIASES.getOrDefault(keyword, keyword));
        return mapper.findListings(search);
    }

    /** 실제 DB에 존재하는 도시만 검색 선택지로 제공합니다. 수집 실패한 도시를 매물이 있는 것처럼 표시하지 않습니다. */
    public List<String> cities(String prefecture) {
        return mapper.findCities(prefecture);
    }

    /** 메인 화면에 필요한 최근 매물 최대 6건을 반환합니다. 외부 사이트에 접속하지 않습니다. */
    public List<PropertyListing> recent() {
        return mapper.findRecentListings();
    }

    /** DB에 등록된 출처와 출처별 노출 매물 개수를 반환합니다. */
    public List<PropertySource> sources() {
        return mapper.findSources();
    }

    /** 번호가 없으면 null을, 있으면 해당 노출 매물을 반환하여 기존 상세 주소를 지원합니다. */
    public PropertyListing find(Long id) {
        return id == null ? null : mapper.findListing(id);
    }
}
