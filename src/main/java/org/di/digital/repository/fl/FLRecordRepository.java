package org.di.digital.repository.fl;

import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.fl.FLDocument;
import org.di.digital.model.fl.FLRecord;
import org.di.digital.model.fl.IssueOrganizationEnum;
import org.di.digital.util.IinParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Slf4j
@Repository
public class FLRecordRepository {
    private final JdbcTemplate flJdbcTemplate;
    public FLRecordRepository(@Qualifier("flJdbcTemplate") JdbcTemplate flJdbcTemplate) {
        this.flJdbcTemplate = flJdbcTemplate;
    }
    public FLRecord findByDocumentNumber(String documentNumber, String language) {
        String docSql = """
            SELECT pd.IIN as iin
            FROM gbd_fl_30_09_25.person_documents pd
            WHERE pd.DOCUMENT_NUMBER = ?
            LIMIT 1
            """;

        String iin = flJdbcTemplate.queryForObject(docSql,
                (rs, rowNum) -> rs.getString("iin"), documentNumber);

        if (iin.isEmpty()) throw new IllegalStateException("Человека с этим документов не найдено: " + documentNumber);

        return findByIin(iin, language);
    }

    public FLRecord findByIin(String iin, String language) {
        String personSql = "";
        String docSql = "";
        if(language.equals("русском")){
            personSql = """
        SELECT
            p.iin as iin, p.sex_id as sexId, p.surname as surname, p.first_name as firstname,
            p.second_name as secondname,
            citizenship.RU_NAME AS citizenship,
            birthCountry.RU_NAME AS birthCountry,
            region.RU_NAME AS birthRegion,
            district.RU_NAME AS birthDistricts,
            nat.RU_NAME AS nationality
        FROM fldb.person_info_gbdfl p
        LEFT JOIN db_fl_ul_dpar.DIC_COUNTRY citizenship ON citizenship.CODE = p.citizenship_id
        LEFT JOIN db_fl_ul_dpar.DIC_COUNTRY birthCountry ON birthCountry.CODE = p.birth_country_id
        LEFT JOIN db_fl_ul_dpar.DIC_REGION region ON region.CODE = p.birth_region_id
        LEFT JOIN db_fl_ul_dpar.DIC_DISTRICTS district ON district.CODE = p.birth_districts_id
        LEFT JOIN db_fl_ul_dpar.nationality nat
            ON nat.CODE = p.nationality_id
            AND nat.SEX = p.sex_id
        WHERE p.iin = ?
        LIMIT 1
        """;

            docSql = """
        SELECT
            doc_type.RU_NAME AS documentType,
            pd.DOCUMENT_NUMBER AS documentNumber,
            pd.DOCUMENT_BEGIN_DATE AS beginDate,
            pd.DOCUMENT_END_DATE AS endDate,
            pd.ISSUE_ORGANIZATION_ID AS issueOrg,
            doc_invalid.RU_NAME AS invalidityReason,
            pd.DOCUMENT_INVALIDITY_DATE AS invalidityDate
        FROM gbd_fl_30_09_25.person_documents pd
        LEFT JOIN gbd_fl_30_09_25.DIC_DOCUMENT_TYPE doc_type ON doc_type.ID = pd.DOCUMENT_TYPE_ID
        LEFT JOIN gbd_fl_30_09_25.DIC_DOCUMENT_INVALIDITY doc_invalid ON doc_invalid.ID = pd.DOCUMENT_INVALIDITY_ID
        WHERE pd.IIN = ?
        ORDER BY
            CASE WHEN doc_invalid.RU_NAME = 'ДОКУМЕНТ ДЕЙСТВИТЕЛЕН' THEN 0 ELSE 1 END ASC, 
            pd.DOCUMENT_END_DATE DESC
        LIMIT 1
        """;
        }else if(language.equals("казахском")){
            personSql = """
        SELECT
            p.iin as iin, p.sex_id as sexId, p.surname as surname, p.first_name as firstname,
            p.second_name as secondname,
            citizenship.KZ_NAME AS citizenship,
            birthCountry.KZ_NAME AS birthCountry,
            region.KZ_NAME AS birthRegion,
            district.KZ_NAME AS birthDistricts,
            nat.KZ_NAME AS nationality
        FROM fldb.person_info_gbdfl p
        LEFT JOIN db_fl_ul_dpar.DIC_COUNTRY citizenship ON citizenship.CODE = p.citizenship_id
        LEFT JOIN db_fl_ul_dpar.DIC_COUNTRY birthCountry ON birthCountry.CODE = p.birth_country_id
        LEFT JOIN db_fl_ul_dpar.DIC_REGION region ON region.CODE = p.birth_region_id
        LEFT JOIN db_fl_ul_dpar.DIC_DISTRICTS district ON district.CODE = p.birth_districts_id
        LEFT JOIN db_fl_ul_dpar.nationality nat
            ON nat.CODE = p.nationality_id
            AND nat.SEX = p.sex_id
        WHERE p.iin = ?
        LIMIT 1
        """;

            docSql = """
        SELECT
            doc_type.KZ_NAME AS documentType,
            pd.DOCUMENT_NUMBER AS documentNumber,
            pd.DOCUMENT_BEGIN_DATE AS beginDate,
            pd.DOCUMENT_END_DATE AS endDate,
            pd.ISSUE_ORGANIZATION_ID AS issueOrg,
            doc_invalid.KZ_NAME AS invalidityReason,
            pd.DOCUMENT_INVALIDITY_DATE AS invalidityDate
        FROM gbd_fl_30_09_25.person_documents pd
        LEFT JOIN gbd_fl_30_09_25.DIC_DOCUMENT_TYPE doc_type ON doc_type.ID = pd.DOCUMENT_TYPE_ID
        LEFT JOIN gbd_fl_30_09_25.DIC_DOCUMENT_INVALIDITY doc_invalid ON doc_invalid.ID = pd.DOCUMENT_INVALIDITY_ID
        WHERE pd.IIN = ?
        ORDER BY
            CASE WHEN doc_invalid.KZ_NAME = 'ҚҰЖАТ ЖАРАМДЫ' THEN 0 ELSE 1 END ASC, 
            pd.DOCUMENT_END_DATE DESC
        LIMIT 1
        """;
        }

        FLRecord record = flJdbcTemplate.queryForObject(personSql, (rs, rowNum) -> {
            String recordIin = rs.getString("iin");

            LocalDate birthDate = IinParser.parseBirthDate(recordIin);

            return FLRecord.builder()
                    .iin(recordIin)
                    .sexId(rs.getString("sexId"))
                    .fio(String.join(" ",
                            nullToEmpty(rs.getString("surname")),
                            nullToEmpty(rs.getString("firstname")),
                            nullToEmpty(rs.getString("secondname"))
                    ).trim())
                    .birthDate(birthDate)
                    .citizenship(rs.getString("citizenship"))
                    .birthCountry(rs.getString("birthCountry"))
                    .birthRegion(rs.getString("birthRegion"))
                    .birthDistricts(rs.getString("birthDistricts"))
                    .nationality(rs.getString("nationality"))
                    .build();
        }, iin);

        if (record == null) return null;

        List<FLDocument> documents = flJdbcTemplate.query(docSql, (rs, rowNum) -> FLDocument.builder()
                .documentType(rs.getString("documentType"))
                .documentNumber(rs.getString("documentNumber"))
                .beginDate(parseDate(rs.getString("beginDate")))
                .endDate(parseDate(rs.getString("endDate")))
                .issueOrg(IssueOrganizationEnum.getNameById(rs.getString("issueOrg"), language))
                .invalidityReason(rs.getString("invalidityReason"))
                .invalidityDate(parseDate(rs.getString("invalidityDate")))
                .build(), iin);
        record.setDocuments(documents);
        return record;
    }
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        String cleaned = dateStr.trim().replaceAll("^\\[|]$", "");

        for (String pattern : List.of(
                "yyyy/MM/dd",
                "yyyy-MM-dd",
                "yyyy/MM/dd:hh:mm:ss a",
                "yyyy-MM-dd:hh:mm:ss a"
        )) {
            try {
                return LocalDate.parse(cleaned, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {}
        }
        log.warn("Cannot parse birthDate: {}", dateStr);
        return null;
    }
    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
