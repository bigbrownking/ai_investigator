package org.di.digital.repository;

import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.fl.FLAddress;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
@Slf4j
@Repository
public class FLAddressRepository {
    private final JdbcTemplate flJdbcTemplate;
    public FLAddressRepository(@Qualifier("flJdbcTemplate") JdbcTemplate flJdbcTemplate) {
        this.flJdbcTemplate = flJdbcTemplate;
    }

    public FLAddress findByIin(String iin) {
        String sql = "SELECT * FROM db_fl_ul_dpar.reg_address WHERE `ИИН/БИН` = ?";

        log.info("The query is {}", sql);
        return flJdbcTemplate.queryForObject(sql, (rs, rowNum) -> FLAddress.builder()
                .iin(rs.getString("ИИН/БИН"))
                .reason(rs.getString("Причина регистрации рус."))
                .address(rs.getString("Адрес на русском"))
                .addressType(rs.getString("Название типа адреса"))
                .addressCode(rs.getString("Код региона адреса"))
                .build(), iin);
    }
}
