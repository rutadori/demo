package com.example.demo.mapper;

import com.example.demo.model.PropertyListing;
import com.example.demo.model.PropertySearch;
import com.example.demo.model.PropertySource;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Model의 DB 접근 계층입니다. @Mapper가 인터페이스를 MyBatis 프록시 Bean으로 등록합니다.
 * 실제 SQL은 resources/mapper/PropertyMapper.xml에 같은 메서드 이름으로 작성합니다.
 * Controller는 이 인터페이스를 직접 호출하지 않으며 반드시 Service를 거칩니다.
 */
@Mapper
public interface PropertyMapper {
    /** 출처 기본 정보를 등록하거나 표시명·기본 주소를 갱신합니다. 기존 사용 중지 상태는 보존합니다. */
    int upsertSource(PropertySource source);

    /** source_code로 출처 한 건을 조회합니다. @Param은 XML에서 사용할 파라미터 이름을 고정합니다. */
    PropertySource findSource(@Param("code") String code);

    /** 출처별 노출 중인 매물 수를 함께 조회하여 검색 메뉴와 수집 관리 화면에 제공합니다. */
    List<PropertySource> findSources();

    /** 현재 저장된 노출 매물의 도시·구를 중복 없이 반환합니다. 지역을 선택하면 그 안의 도시만 제공합니다. */
    List<String> findCities(@Param("prefecture") String prefecture);

    /** 출처+원본 식별값이 같으면 기존 행을 갱신하고, 처음 발견한 매물만 새로 삽입합니다. */
    int upsertListing(PropertyListing listing);

    /** 검증한 조건으로 ACTIVE 매물을 조회합니다. 한 번에 최대 120건을 표시합니다. */
    List<PropertyListing> findListings(PropertySearch search);

    /** 메인 화면에서 사용할 최근 등록 매물 최대 6건을 조회합니다. */
    List<PropertyListing> findRecentListings();

    /** 기존 내부 상세 URL에서 실제로 존재하는 노출 매물 한 건을 조회합니다. */
    PropertyListing findListing(@Param("id") Long id);
}
