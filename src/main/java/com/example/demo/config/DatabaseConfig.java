package com.example.demo.config;

import java.util.Properties;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis가 실제 접속 DB에 맞는 SQL을 선택하도록 돕는 설정 클래스입니다.
 * @Configuration은 설정 Bean 등록 대상이라는 뜻이며 Controller·Service의 업무는 바꾸지 않습니다.
 * PostgreSQL용 SQL을 추가해도 이전 MySQL DB로 검증·복귀할 수 있도록 두 DB를 함께 지원합니다.
 */
@Configuration
public class DatabaseConfig {
    /**
     * @Bean으로 DB 제품명 판별기를 등록하면 MyBatis Spring Boot가 SqlSessionFactory에 자동 적용합니다.
     * JDBC 메타데이터의 제품명을 고정 코드 mysql·postgresql로 바꾸고 Mapper의 _databaseId에서 사용합니다.
     * 요청 파라미터로 SQL 종류를 선택하지 않아 사용자가 DB별 구문을 임의로 주입할 수 없습니다.
     * @return 실제 접속한 DB 제품명을 판별하는 MyBatis 설정 객체
     */
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties names = new Properties();
        names.setProperty("MySQL", "mysql");
        names.setProperty("PostgreSQL", "postgresql");
        provider.setProperties(names);
        return provider;
    }
}
