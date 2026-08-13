public record CustomShipGenerationRequest(
        String displayName,
        String declaredShipClass,
        CustomHullClass hullClass,
        CustomCombatClassification combatClassification,
        CustomWeaponDoctrine weaponDoctrine,
        CustomDefenseBias defenseBias,
        int weaponCount,
        java.util.UUID weaponDefinitionId
) {
    public CustomShipGenerationRequest(
            String displayName,
            String declaredShipClass,
            CustomHullClass hullClass,
            CustomCombatClassification combatClassification,
            CustomWeaponDoctrine weaponDoctrine,
            CustomDefenseBias defenseBias,
            int weaponCount
    ) {
        this(displayName, declaredShipClass, hullClass, combatClassification, weaponDoctrine, defenseBias, weaponCount, null);
    }

    public CustomShipGenerationRequest {
        displayName = clean(displayName, "Custom Ship");
        declaredShipClass = clean(declaredShipClass, hullClass == null ? "Frigate" : pretty(hullClass.name()));
        if (hullClass == null) hullClass = CustomHullClass.FRIGATE;
        if (combatClassification == null) combatClassification = CustomCombatClassification.LINE;
        if (weaponDoctrine == null) weaponDoctrine = CustomWeaponDoctrine.BALANCED;
        if (defenseBias == null) defenseBias = CustomDefenseBias.BALANCED;
        weaponCount = MathUtil.clamp(weaponCount, 1, 24);
    }

    private static String clean(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? fallback : text;
    }

    private static String pretty(String value) {
        return value == null ? "Frigate" : value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
