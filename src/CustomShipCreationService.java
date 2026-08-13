import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CustomShipCreationService {
    private final CustomShipRegistry registry;
    private final CustomShipImageProcessor imageProcessor;

    public CustomShipCreationService() {
        this(new CustomShipRegistry(), new CustomShipImageProcessor());
    }

    public CustomShipCreationService(CustomShipRegistry registry, CustomShipImageProcessor imageProcessor) {
        this.registry = registry == null ? new CustomShipRegistry() : registry;
        this.imageProcessor = imageProcessor == null ? new CustomShipImageProcessor() : imageProcessor;
    }

    public CreationResult createFromPng(Path sourcePng, CustomShipGenerationRequest request) throws IOException {
        CustomShipDefinition definition = CustomShipGenerator.generate(request);
        CustomShipImageProcessor.ProcessedImage image = imageProcessor.processPng(sourcePng, definition, registry);
        registry.save(definition);
        return new CreationResult(definition, image, registry.folderFor(definition.id));
    }

    public List<CustomShipDefinition> savedShips() throws IOException {
        return registry.loadAll();
    }

    public Optional<CustomShipDefinition> load(UUID id) {
        return registry.load(id);
    }

    public boolean delete(UUID id) throws IOException {
        return registry.delete(id);
    }

    public List<String> validationFailures(CustomShipDefinition definition) {
        return registry.validationFailures(definition);
    }

    public record CreationResult(
            CustomShipDefinition definition,
            CustomShipImageProcessor.ProcessedImage image,
            Path folder
    ) {}
}
