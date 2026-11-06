package com.example.demo.crawler;

import com.example.demo.model.PropertyListing;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 레오팔레스의 서버 렌더링 목록을 건물·호실별로 읽는 파서입니다. @Component로 자동 등록합니다.
 * CSS Modules의 매번 달라질 수 있는 해시 접미사 대신 의미 있는 클래스 접두사를 선택합니다.
 * 건물 안내 주소(common)가 아니라 실제 방의 chintai/.../호실 주소를 저장합니다.
 */
@Component
public class LeopalaceListingParser implements ListingParser {
    /** 이 파서의 담당 출처를 반환합니다. */
    @Override
    public ListingSource source() { return ListingSource.LEOPALACE; }

    /** 각 건물 안의 방별 가격·면적·호실 링크를 읽어 최대 limit건을 반환합니다. */
    @Override
    public List<PropertyListing> parse(Document document, int limit) {
        List<PropertyListing> results = new ArrayList<>();
        for (Element building : document.select("[class^=ApartmentItem_apartment-item]")) {
            Element heading = building.selectFirst("a[class^=ApartmentItemDetails_apartment-item-details]");
            if (heading == null) continue;
            var captions = heading.select("span[class^=Caption_caption__]");
            for (Element room : building.select("div[class^=RoomItem_room-item__]")) {
                Element link = room.selectFirst("a[class^=RoomItem_link__][href]");
                if (link == null) continue;
                try {
                    String title = ListingValues.text(heading, "h3");
                    var roomCaptions = room.select("div[class^=RoomInfo_room-info] span[class^=Caption_caption__]");
                    if (roomCaptions.size() >= 3) title += " · " + roomCaptions.get(2).text();
                    PropertyListing listing = ListingValues.listing(source(), title, link.absUrl("href"));
                    ListingValues.location(listing, captions.isEmpty() ? null : captions.get(0).text());
                    ListingValues.transit(listing, captions.size() < 2 ? null : captions.get(1).text());
                    listing.setRentYen(ListingValues.yen(ListingValues.text(room, "p[class^=Price_fee__]")));
                    listing.setManagementFeeYen(ListingValues.yen(ListingValues.text(room, "span[class^=Price_expenses__]")));
                    listing.setLayout(ListingValues.layout(roomCaptions.text()));
                    listing.setAreaM2(ListingValues.area(roomCaptions.text()));
                    Element image = heading.selectFirst("img[src]");
                    if (image != null) listing.setImageUrl(image.absUrl("src"));
                    results.add(listing);
                    if (results.size() >= limit) return results;
                } catch (IllegalArgumentException ignored) {
                    // 문의·건물 목록처럼 방 상세에 해당하지 않는 주소는 저장하지 않습니다.
                }
            }
        }
        return results;
    }
}
