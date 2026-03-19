package org.di.digital.service;

import org.di.digital.model.fl.FLAddress;
import org.di.digital.model.fl.FLRecord;

import java.util.List;

public interface FLService {
    FLRecord getInfoByDocument(String documentType, String number);
    FLRecord getInfoAbout(String iin);
    FLAddress getRegAddressAbout(String iin);
}
