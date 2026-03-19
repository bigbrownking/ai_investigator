package org.di.digital.model.fl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FLRecord {
    private String fio;

    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonFormat(pattern = "yyyy/MM/dd")
    private LocalDate birthDate; //BIRTH_DATE
    private String citizenship; //CITIZENSHIP_ID
    private String nationality; //NATIONALTY_ID
    private String iin; //IIN
    private String birthCountry; //BIRTH_COUNTRY_ID
    private String birthRegion; //BIRTH_REGION_ID
    private String birthDistricts; //BIRTH_DISTRICT_ID

    private List<FLDocument> documents;
}
