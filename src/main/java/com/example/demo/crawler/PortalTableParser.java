package com.example.demo.crawler;

import com.example.demo.model.PropertyListing;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * HOME'S에서 공개 목록 HTML을 제공할 때 사용할 방 행 기반 파서입니다.
 * 상세 링크와 같은 방 행에 실제 엔 금액이 있는 경우에만 저장하여 메뉴·광고 링크가 매물로 섞이지 않게 합니다.
 * 현재 환경의 HOME'S는 403으로 응답하므로 이 파서는 실사이트 수집 성공이 검증된 상태가 아닙니다.
 * 접근이 가능해져도 구조가 맞지 않으면 빈 결과를 반환하고 수집 Service가 구조 확인 필요 상태로 표시합니다.
 */
public abstract class PortalTableParser implements ListingParser {
    /**
     * 하위 클래스가 담당 출처를 지정하면 동일 출처의 실제 상세 링크를 중심으로 방별 정보를 추출합니다.
     * 테이블 행 또는 room/unit/item 블록이 없거나 가격을 읽을 수 없으면 저장 대상에서 제외합니다.
     */
    @Override
    public List<PropertyListing> parse(Document document, int limit) {
        List<PropertyListing> results = new ArrayList<>();
        var seen = new HashSet<String>();
        for (Element link : document.select("a[href]")) {
            String url;
            try { url = source().canonicalDetail(link.absUrl("href")); }
            catch (IllegalArgumentException ex) { continue; }
            if (seen.contains(url)) continue;
            Element row = link.closest("tr, [class*=room], [class*=Room], [class*=unit], [class*=Unit], article");
            if (row == null) continue;
            String price = ListingValues.text(row, "[class*=rent], [class*=Rent], [class*=price], [class*=Price]");
            Integer rent = ListingValues.yen(price);
            if (rent == null) continue;
            Element building = link.closest("[class*=mergeBuilding], [class*=property], [class*=Property], article");
            if (building == null) building = row;
            String title = ListingValues.text(building, "[class*=bukkenName], [class*=propertyName], h2, h3");
            if (title == null || title.isBlank()) continue;
            PropertyListing listing = ListingValues.listing(source(), title, url);
            ListingValues.location(listing, ListingValues.text(building, "[class*=address], [class*=Address], [itemprop=streetAddress]"));
            ListingValues.transit(listing, ListingValues.text(building, "[class*=access], [class*=Access], [class*=traffic]"));
            listing.setRentYen(rent);
            listing.setManagementFeeYen(ListingValues.yen(ListingValues.text(row, "[class*=management], [class*=commonFee]")));
            listing.setLayout(ListingValues.layout(row.text()));
            listing.setAreaM2(ListingValues.area(row.text()));
            seen.add(url);
            results.add(listing);
            if (results.size() >= limit) break;
        }
        return results;
    }
}
