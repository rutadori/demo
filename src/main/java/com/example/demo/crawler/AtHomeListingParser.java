package com.example.demo.crawler;

import com.example.demo.model.PropertyListing;
import com.example.demo.model.PropertyRegion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * at home의 실제 공개 목록에서 확인한 property-card와 room-info-section 구조를 읽습니다.
 * @Component가 파서를 Bean으로 등록합니다. 건물 정보와 방별 가격을 구분하고 동일 링크는 한 번만 반환합니다.
 * 접근 거부 응답이 발생하면 HTTP 계층에서 중단하며 페이지 내부의 비공개 API는 호출하지 않습니다.
 */
@Component
public class AtHomeListingParser implements ListingParser {
    /** at home의 고정 호스트·목록·상세 링크 검증 규칙을 제공합니다. */
    @Override
    public ListingSource source() { return ListingSource.ATHOME; }

    /** 같은 건물 안의 방을 각자의 월세·호실·상세 링크로 분리하고 페이지에서 공개한 위치만 사용합니다. */
    @Override
    public List<PropertyListing> parse(Document document, int limit) {
        List<PropertyListing> results = new ArrayList<>();
        var seen = new HashSet<String>();
        String regionContext = document.select(".filter-item__detail").text();
        for (Element building : document.select(".property-card")) {
            String name = ListingValues.text(building, ".property-title");
            String address = ListingValues.text(building, ".info-item--location");
            // 이 페이지는 주소에서 도도부현을 생략하므로 화면에 공개된 검색 지역 표기로 보완합니다.
            if (address != null) {
                String originalAddress = address;
                for (PropertyRegion region : PropertyRegion.values()) {
                    if (region.getAreas().stream().anyMatch(area -> originalAddress.startsWith(area.cityName())) && regionContext.contains(region.getPrefecture())) {
                        address = region.getPrefecture() + address;
                        break;
                    }
                }
            }
            for (Element room : building.select(".room-info-section")) {
                Element link = room.selectFirst(".room-info-section__room-detail > a[href]");
                if (link == null) continue;
                try {
                    String url = source().canonicalDetail(link.absUrl("href"));
                    if (!seen.add(url)) continue;
                    String roomNumber = ListingValues.text(room, ".room-number .room-info-item__text");
                    PropertyListing listing = ListingValues.listing(source(), name == null ? null : name + (roomNumber == null ? "" : " · " + roomNumber), url);
                    ListingValues.location(listing, address);
                    ListingValues.transit(listing, ListingValues.text(building, ".info-item--station p"));
                    listing.setRentYen(ListingValues.yen(ListingValues.text(room, ".price .rent")));
                    listing.setManagementFeeYen(ListingValues.yen(ListingValues.text(room, ".price > .room-info-item__text:not(.rent)")));
                    listing.setLayout(ListingValues.layout(ListingValues.text(room, ".layout-size")));
                    listing.setAreaM2(ListingValues.area(ListingValues.text(room, ".layout-size")));
                    Element image = building.selectFirst(".property-card__image-slider img[src]");
                    if (image != null) listing.setImageUrl(image.absUrl("src"));
                    results.add(listing);
                    if (results.size() >= limit) return results;
                } catch (IllegalArgumentException ignored) {
                    // 숫자로 된 방 상세 주소에 맞지 않는 링크만 제외합니다.
                }
            }
        }
        return results;
    }
}
