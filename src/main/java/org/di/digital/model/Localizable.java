package org.di.digital.model;

public interface Localizable {
    String getRuName();
    String getKzName();
    default String getRuCity() { return null; }
    default String getKzCity() { return null; }
}
