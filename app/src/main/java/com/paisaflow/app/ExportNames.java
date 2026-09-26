package com.paisaflow.app;

import java.util.Locale;

final class ExportNames {
    private ExportNames() {}

    static String transactions(int dateKey) {
        return String.format(Locale.US, "PaisaFlow_backup_%04d-%02d-%02d.csv",
                dateKey / 10000, (dateKey / 100) % 100, dateKey % 100);
    }
}
