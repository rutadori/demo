package com.example.demo.crawler;

import com.example.demo.model.PropertyListing;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * SUUMO의 공개 건물별 목록을 방별 Model로 해석합니다. @Component는 수집 Service에 주입할 Bean을 등록합니다.
 * 건물 제목·주소·교통은 cassetteitem에서, 월세·관리비·구조·면적·상세 링크는 각 방의 tr에서 읽습니다.
 * 한 건물의 첫 번째 방 가격을 다른 방에 재사용하지 않도록 건물과 방의 범위를 분리합니다.
 */
@Component
public class SuumoListingParser implements ListingParser {
    /** 이 파서의 담당 출처를 반환합니다. */
    @Override
    public ListingSource source() { return ListingSource.SUUMO; }

    /** 공개 목록의 방 행을 순회하고 지정 개수까지만 추출합니다. 방별 원본 링크를 그대로 보존합니다. */
    @Override
    public List<PropertyListing> parse(Document document, int limit) {
        List<PropertyListing> results = new ArrayList<>();
        for (Element building : document.select(".cassetteitem")) {
            for (Element row : building.select(".cassetteitem_other tbody tr")) {
                Element link = row.selectFirst("a.js-cassette_link_href[href]");
                if (link == null) continue;
                try {
                    PropertyListing listing = ListingValues.listing(source(), ListingValues.text(building, ".cassetteitem_content-title"), link.absUrl("href"));
                    ListingValues.location(listing, ListingValues.text(building, ".cassetteitem_detail-col1"));
                    ListingValues.transit(listing, ListingValues.text(building, ".cassetteitem_detail-col2 .cassetteitem_detail-text"));
                    listing.setRentYen(ListingValues.yen(ListingValues.text(row, ".cassetteitem_price--rent")));
                    listing.setManagementFeeYen(ListingValues.yen(ListingValues.text(row, ".cassetteitem_price--administration")));
                    listing.setLayout(ListingValues.layout(ListingValues.text(row, ".cassetteitem_madori")));
                    listing.setAreaM2(ListingValues.area(ListingValues.text(row, ".cassetteitem_menseki")));
                    Element image = building.selectFirst(".cassetteitem_object img");
                    if (image != null && image.attr("rel").startsWith("https://img")) listing.setImageUrl(image.attr("rel"));
                    results.add(listing);
                    if (results.size() >= limit) return results;
                } catch (IllegalArgumentException ignored) {
                    // 대상 밖의 링크만 제외합니다. 접근 거부를 우회하거나 대체 주소로 재시도하지 않습니다.
                }
            }
        }
        return results;
    }
}
