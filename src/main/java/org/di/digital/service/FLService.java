package org.di.digital.service;

import org.di.digital.model.fl.FLAddress;
import org.di.digital.model.fl.FLRecord;

import java.util.List;

public interface FLService {
    FLRecord getInfoByDocument(String documentType, String number, String language);
    FLRecord getInfoAbout(String iin, String language);
    FLAddress getRegAddressAbout(String iin, String language);
}
