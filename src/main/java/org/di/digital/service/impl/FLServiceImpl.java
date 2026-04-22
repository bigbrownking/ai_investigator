package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.fl.FLAddress;
import org.di.digital.model.fl.FLRecord;
import org.di.digital.repository.FLAddressRepository;
import org.di.digital.repository.FLRecordRepository;
import org.di.digital.service.FLService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FLServiceImpl implements FLService {
    private final FLAddressRepository flAddressRepository;
    private final FLRecordRepository flRecordRepository;
    @Override
    public FLRecord getInfoByDocument(String documentType, String number, String language) {
        return switch (documentType.toUpperCase()) {
            case "ИИН" -> flRecordRepository.findByIin(number, language);
            case "ПАСПОРТ" -> flRecordRepository.findByDocumentNumber(number, language);
            default -> throw new RuntimeException("Unsupported document type: " + documentType);
        };
    }
    @Override
    public FLRecord getInfoAbout(String iin, String language) {
        return flRecordRepository.findByIin(iin, language);
    }

    @Override
    public FLAddress getRegAddressAbout(String iin, String language) {
        return flAddressRepository.findByIin(iin);
    }
}
