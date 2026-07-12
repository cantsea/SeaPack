package org.seapack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlFiles {
    private YamlFiles() {
    }

    public static Optional<YamlConfiguration> load(Path path, Logger logger, String description) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(path.toFile());
            return Optional.of(configuration);
        } catch (IOException | InvalidConfigurationException exception) {
            logger.warning("Could not read " + description + " '" + path + "': " + exception.getMessage()
                    + ". The existing file was left untouched.");
            return Optional.empty();
        }
    }

    public static void saveAtomic(Path path, YamlConfiguration configuration) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("Configuration path has no parent: " + path);
        }
        Files.createDirectories(parent);
        Path temporary = parent.resolve("." + path.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(
                    temporary,
                    configuration.saveToString(),
                    StandardCharsets.UTF_8
            );
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
