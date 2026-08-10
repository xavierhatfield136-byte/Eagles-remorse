public enum RefitHullFilter {
    ALL("ALL", null),
    PICKET("PICKET", ShopHullCategory.ESCORT),
    LINE("LINE", ShopHullCategory.LINE),
    CAPITAL("CAPITAL", ShopHullCategory.CAPITAL),
    TITAN("TITAN", ShopHullCategory.TITAN);

    private final String label;
    private final ShopHullCategory category;

    RefitHullFilter(String label, ShopHullCategory category) {
        this.label = (label == null || label.isBlank()) ? name() : label;
        this.category = category;
    }

    public String label() {
        return label;
    }

    public boolean matches(ShipRole role) {
        return this == ALL || (role != null && ShopHullCategory.forRole(role) == category);
    }
}
