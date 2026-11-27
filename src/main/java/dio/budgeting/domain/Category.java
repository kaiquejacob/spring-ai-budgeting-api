package dio.budgeting.domain;

import java.util.Arrays;

public enum Category {
    GROCERIES,
    PHARMA,
    AUTO;

    public static String allowedNamesInPortuguese() {
        var names = Arrays.stream(values()).map(Enum::name).toList();
        if (names.size() == 1) {
            return names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " ou " + names.getLast();
    }
}
