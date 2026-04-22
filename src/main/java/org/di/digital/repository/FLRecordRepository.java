package org.di.digital.repository;

import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.fl.FLDocument;
import org.di.digital.model.fl.FLRecord;
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

        if (iin.isEmpty()) throw new RuntimeException("Person not found by document: " + documentNumber);

        return findByIin(iin, language);
    }

    public FLRecord findByIin(String iin, String language) {
        String personSql = "";
        String docSql = "";
        if(language.equals("ru")){
            personSql = """
            SELECT
                p.IIN as iin, p.SURNAME as surname, p.FIRSTNAME as firstname,
                p.SECONDNAME as secondname, p.BIRTH_DATE as birthdate,
                citizenship.RU_NAME AS citizenship,
                birthCountry.RU_NAME AS birthCountry,
                region.RU_NAME AS birthRegion,
                district.RU_NAME AS birthDistricts,
                CASE 
                    WHEN p.SEX_ID = '1' THEN nat_m.RU_NAME
                    WHEN p.SEX_ID = '2' THEN nat_f.RU_NAME
                END AS nationality
            FROM gbd_fl_30_09_25.person_info p
            LEFT JOIN gbd_fl_30_09_25.DIC_COUNTRY citizenship ON citizenship.ID = p.CITIZENSHIP_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_COUNTRY birthCountry ON birthCountry.ID = p.BIRTH_COUNTRY_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_REGION region ON region.ID = p.BIRTH_REGION_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_DISTRICTS district ON district.ID = p.BIRTH_DISTRICTS_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_NATIONALITY_M nat_m ON nat_m.ID = p.NATIONALTY_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_NATIONALITY_F nat_f ON nat_f.ID = p.NATIONALTY_ID
            WHERE p.IIN = ?
            LIMIT 1
            """;

            docSql = """
            SELECT
                doc_type.RU_NAME AS documentType,
                pd.DOCUMENT_NUMBER AS documentNumber,
                pd.DOCUMENT_BEGIN_DATE AS beginDate,
                pd.DOCUMENT_END_DATE AS endDate,
                doc_issue.RU_NAME AS issueOrg,
                doc_invalid.RU_NAME AS invalidityReason,
                pd.DOCUMENT_INVALIDITY_DATE AS invalidityDate
            FROM gbd_fl_30_09_25.person_documents pd
            LEFT JOIN gbd_fl_30_09_25.DIC_DOCUMENT_TYPE doc_type ON doc_type.ID = pd.DOCUMENT_TYPE_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_DOCUMENT_TYPE doc_issue ON doc_issue.ID = pd.ISSUE_ORGANIZATION_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_DOCUMENT_INVALIDITY doc_invalid ON doc_invalid.ID = pd.DOCUMENT_INVALIDITY_ID
            WHERE pd.IIN = ?
            """;
        }else if(language.equals("kz")){
            personSql = """
            SELECT
                p.IIN as iin, p.SURNAME as surname, p.FIRSTNAME as firstname,
                p.SECONDNAME as secondname, p.BIRTH_DATE as birthdate,
                citizenship.KZ_NAME AS citizenship,
                birthCountry.KZ_NAME AS birthCountry,
                region.KZ_NAME AS birthRegion,
                district.KZ_NAME AS birthDistricts,
                CASE 
                    WHEN p.SEX_ID = '1' THEN nat_m.KZ_NAME
                    WHEN p.SEX_ID = '2' THEN nat_f.KZ_NAME
                END AS nationality
            FROM gbd_fl_30_09_25.person_info p
            LEFT JOIN gbd_fl_30_09_25.DIC_COUNTRY citizenship ON citizenship.ID = p.CITIZENSHIP_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_COUNTRY birthCountry ON birthCountry.ID = p.BIRTH_COUNTRY_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_REGION region ON region.ID = p.BIRTH_REGION_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_DISTRICTS district ON district.ID = p.BIRTH_DISTRICTS_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_NATIONALITY_M nat_m ON nat_m.ID = p.NATIONALTY_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_NATIONALITY_F nat_f ON nat_f.ID = p.NATIONALTY_ID
            WHERE p.IIN = ?
            LIMIT 1
            """;

            docSql = """
            SELECT
                doc_type.KZ_NAME AS documentType,
                pd.DOCUMENT_NUMBER AS documentNumber,
                pd.DOCUMENT_BEGIN_DATE AS beginDate,
                pd.DOCUMENT_END_DATE AS endDate,
                doc_issue.KZ_NAME AS issueOrg,
                doc_invalid.KZ_NAME AS invalidityReason,
                pd.DOCUMENT_INVALIDITY_DATE AS invalidityDate
            FROM gbd_fl_30_09_25.person_documents pd
            LEFT JOIN gbd_fl_30_09_25.DIC_DOCUMENT_TYPE doc_type ON doc_type.ID = pd.DOCUMENT_TYPE_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_DOCUMENT_TYPE doc_issue ON doc_issue.ID = pd.ISSUE_ORGANIZATION_ID
            LEFT JOIN gbd_fl_30_09_25.DIC_DOCUMENT_INVALIDITY doc_invalid ON doc_invalid.ID = pd.DOCUMENT_INVALIDITY_ID
            WHERE pd.IIN = ?
            """;
        }

        FLRecord record = flJdbcTemplate.queryForObject(personSql, (rs, rowNum) -> FLRecord.builder()
                .iin(rs.getString("iin"))
                .fio(String.join(" ",
                        nullToEmpty(rs.getString("surname")),
                        nullToEmpty(rs.getString("firstname")),
                        nullToEmpty(rs.getString("secondname"))
                ).trim())
                .birthDate(parseDate(rs.getString("birthdate")))
                .citizenship(rs.getString("citizenship"))
                .birthCountry(rs.getString("birthCountry"))
                .birthRegion(rs.getString("birthRegion"))
                .birthDistricts(rs.getString("birthDistricts"))
                .nationality(rs.getString("nationality"))
                .build(), iin);

        if (record == null) return null;

        List<FLDocument> documents = flJdbcTemplate.query(docSql, (rs, rowNum) -> FLDocument.builder()
                .documentType(rs.getString("documentType"))
                .documentNumber(rs.getString("documentNumber"))
                .beginDate(parseDate(rs.getString("beginDate")))
                .endDate(parseDate(rs.getString("endDate")))
                .issueOrg(rs.getString("issueOrg"))
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
