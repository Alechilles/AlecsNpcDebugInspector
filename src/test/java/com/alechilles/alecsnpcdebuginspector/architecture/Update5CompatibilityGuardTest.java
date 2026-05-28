package com.alechilles.alecsnpcdebuginspector.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces source-level Update 5 API migrations.
 */
class Update5CompatibilityGuardTest {
    private static final Path MAIN_JAVA = Paths.get("src", "main", "java");
    private static final Path MAIN_RESOURCES = Paths.get("src", "main", "resources");
    private static final List<ForbiddenUsage> FORBIDDEN_USAGES = List.of(
            new ForbiddenUsage(
                    "Update 5 removed Hytale Vector3d/Vector3f/Vector3i classes. Use org.joml vectors instead.",
                    List.of(
                            "com.hypixel.hytale.math.vector.Vector3d",
                            "com.hypixel.hytale.math.vector.Vector3f",
                            "com.hypixel.hytale.math.vector.Vector3i"
                    )
            ),
            new ForbiddenUsage(
                    "Update 5 removed Hytale vector helper methods. Use JOML equivalents.",
                    List.of(
                            ".distanceTo(",
                            ".squaredLength(",
                            ".subtract("
                    )
            ),
            new ForbiddenUsage(
                    "Update 5 moved active hotbar state to inventory components.",
                    List.of(
                            ".getActiveHotbarItem(",
                            ".getActiveHotbarSlot("
                    )
            ),
            new ForbiddenUsage(
                    "Update 5 moved player chat sending to PlayerRef.",
                    List.of(
                            "player.sendMessage("
                    )
            ),
            new ForbiddenUsage(
                    "Update 5 Part 9 renamed force APIs to velocity APIs.",
                    List.of(
                            ".forceVelocity(",
                            ".addForce(",
                            ".getForce("
                    )
            ),
            new ForbiddenUsage(
                    "Update 5 Part 9 removed unkeyed CustomUIHud APIs.",
                    List.of(
                            "super(playerRef)",
                            ".setCustomHud(",
                            ".getCustomHud()",
                            "CustomUIHud.DEFAULT_KEY"
                    )
            )
    );

    @Test
    void sourceAvoidsRemovedUpdate5Apis() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path sourceFile : listFiles(MAIN_JAVA, ".java")) {
            List<String> lines = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (ForbiddenUsage forbiddenUsage : FORBIDDEN_USAGES) {
                    if (forbiddenUsage.matches(line)) {
                        violations.add(toUnixRelativePath(MAIN_JAVA, sourceFile) + ":" + (i + 1)
                                + " -> " + forbiddenUsage.description() + " -> " + line.trim());
                    }
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Update 5 compatibility guard found removed API usage.\nViolations:\n"
                        + String.join("\n", violations)
        );
    }

    @Test
    void manifestUsesUpdate5ServerVersionRange() throws IOException {
        Path manifest = MAIN_RESOURCES.resolve("manifest.json");
        String text = Files.readString(manifest, StandardCharsets.UTF_8);

        assertTrue(
                text.contains("\"ServerVersion\": \"^0.5.0\""),
                "Update 5 uses semantic server version ranges; manifest.json should target ^0.5.0"
        );
    }

    private record ForbiddenUsage(String description, List<String> tokens) {
        boolean matches(String line) {
            for (String token : tokens) {
                if (line.contains(token)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static List<Path> listFiles(Path root, String suffix) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    private static String toUnixRelativePath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}
