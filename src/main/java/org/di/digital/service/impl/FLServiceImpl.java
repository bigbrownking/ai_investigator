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
    public FLRecord getInfoByDocument(String documentType, String number) {
        return switch (documentType.toUpperCase()) {
            case "ИИН" -> flRecordRepository.findByIin(number);
            case "ПАСПОРТ" -> flRecordRepository.findByDocumentNumber(number);
            default -> throw new RuntimeException("Unsupported document type: " + documentType);
        };
    }
    @Override
    public FLRecord getInfoAbout(String iin) {
        return flRecordRepository.findByIin(iin);
    }

    @Override
    public FLAddress getRegAddressAbout(String iin) {
        return flAddressRepository.findByIin(iin);
    }
}
