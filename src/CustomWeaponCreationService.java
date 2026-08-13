import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CustomWeaponCreationService {
    private final CustomWeaponRegistry registry;
    private final CustomWeaponAssetProcessor assetProcessor;

    public CustomWeaponCreationService() {
        this(new CustomWeaponRegistry(), new CustomWeaponAssetProcessor());
    }

    public CustomWeaponCreationService(CustomWeaponRegistry registry, CustomWeaponAssetProcessor assetProcessor) {
        this.registry = registry == null ? new CustomWeaponRegistry() : registry;
        this.assetProcessor = assetProcessor == null ? new CustomWeaponAssetProcessor() : assetProcessor;
    }

    public CreationResult createFromPngs(Path turretPng,
                                         Path projectilePng,
                                         CustomWeaponGenerationRequest request) throws IOException {
        CustomWeaponDefinition definition = CustomWeaponGenerator.generate(request);
        CustomWeaponAssetProcessor.ProcessedWeaponAssets assets =
                assetProcessor.processPngs(turretPng, projectilePng, definition, registry);
        registry.save(definition);
        return new CreationResult(definition, assets, registry.folderFor(definition.id));
    }

    public List<CustomWeaponDefinition> savedWeapons() throws IOException {
        return registry.loadAll();
    }

    public Optional<CustomWeaponDefinition> load(UUID id) {
        return registry.load(id);
    }

    public boolean delete(UUID id) throws IOException {
        return registry.delete(id);
    }

    public List<String> validationFailures(CustomWeaponDefinition definition) {
        return registry.validationFailures(definition);
    }

    public record CreationResult(
            CustomWeaponDefinition definition,
            CustomWeaponAssetProcessor.ProcessedWeaponAssets assets,
            Path folder
    ) {}
}
