package pl.kuba6000.ae2webintegration.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import pl.kuba6000.ae2webintegration.core.ae2request.async.GetIcons;
import pl.kuba6000.ae2webintegration.core.config.Config;
import pl.kuba6000.ae2webintegration.core.config.ConfigBootstrap;
import pl.kuba6000.ae2webintegration.core.interfaces.IAEGenericStack;
import pl.kuba6000.ae2webintegration.core.interfaces.IAEGrid;
import pl.kuba6000.ae2webintegration.core.interfaces.IAEKey;
import pl.kuba6000.ae2webintegration.core.utils.IconStore;

/**
 * Covers the /icon request path end to end against a real temporary icons directory: resolution
 * through the hashcode map, placeholder fallbacks, batch limits, and path-traversal rejection.
 */
class GetIconsTest {

    private static final String STONE_PNG = "fakeStonePngBytes";
    private static final String PLACEHOLDER_BASE64 = Base64.getEncoder()
        .encodeToString(IconStore.getPlaceholder());

    @TempDir
    Path configDir;

    @BeforeEach
    void setUp() throws IOException {
        Config.init(configDir.toFile());
        ConfigBootstrap.iconsDirectoryValue = () -> "icons";
        Files.createDirectories(
            Config.getIconsDirectory()
                .toPath()
                .resolve("minecraft"));
        Files.writeString(
            Config.getIconsDirectory()
                .toPath()
                .resolve("minecraft")
                .resolve("stone.png"),
            STONE_PNG);
        AE2Controller.hashcodeToStack.clear();
        AE2Controller.hashcodeToStack.put(1, stackWithItem("minecraft:stone"));
        AE2Controller.hashcodeToStack.put(2, stackWithItem("gregtech:gt.metaitem.01/32600"));
    }

    @AfterEach
    void tearDown() {
        AE2Controller.hashcodeToStack.clear();
        ConfigBootstrap.iconsDirectoryValue = () -> "";
    }

    private static IAEGenericStack stackWithItem(String itemId) {
        return new IAEGenericStack() {

            @Override
            public IAEKey web$what() {
                return new IAEKey() {

                    @Override
                    public String web$getItemID() {
                        return itemId;
                    }

                    @Override
                    public String web$getDisplayName() {
                        return itemId;
                    }

                    @Override
                    public boolean web$isCraftable(IAEGrid grid) {
                        return false;
                    }

                    @Override
                    public boolean web$isSameType(IAEKey other) {
                        return other.web$getItemID()
                            .equals(itemId);
                    }
                };
            }

            @Override
            public long web$amount() {
                return 1;
            }

            @Override
            public IAEGenericStack web$copy() {
                return this;
            }
        };
    }

    private static JsonArray run(String itemsParam) {
        Map<String, String> params = new HashMap<>();
        params.put("items", itemsParam);
        GetIcons request = new GetIcons();
        request.handle(params);
        JsonObject response = JsonParser.parseString(request.getJSON())
            .getAsJsonObject();
        assertEquals(
            "OK",
            response.get("status")
                .getAsString(),
            request.getJSON());
        return response.getAsJsonArray("data");
    }

    private static JsonObject entryFor(JsonArray data, int hashcode) {
        for (JsonElement element : data) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.get("hashcode")
                .getAsInt() == hashcode) {
                return entry;
            }
        }
        throw new AssertionError("no entry for hashcode " + hashcode + " in " + data);
    }

    @Test
    void resolvesAKnownHashcodeToItsFileContents() {
        JsonArray data = run("1");
        assertEquals(
            Base64.getEncoder()
                .encodeToString(STONE_PNG.getBytes()),
            entryFor(data, 1).get("pngData")
                .getAsString());
    }

    @Test
    void aBatchAnswersEveryRequestedHashcode() {
        JsonArray data = run("1,2,999999");
        assertEquals(3, data.size());
    }

    @Test
    void anUnknownHashcodeAnswersWithThePlaceholderInsteadOfAnError() {
        JsonArray data = run("999999");
        assertEquals(
            PLACEHOLDER_BASE64,
            entryFor(data, 999999).get("pngData")
                .getAsString());
    }

    @Test
    void aMissingItemsParameterIsAnsweredNotThrown() {
        GetIcons request = new GetIcons();
        request.handle(new HashMap<>());
        assertTrue(
            request.getJSON()
                .contains("\"status\":\"NO_PARAM\""),
            request.getJSON());
    }

    @Test
    void anOversizedBatchIsRejected() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i <= GetIcons.MAX_BATCH_SIZE; i++) {
            if (i > 0) items.append(',');
            items.append(i);
        }
        GetIcons request = new GetIcons();
        request.handle(Map.of("items", items.toString()));
        assertTrue(
            request.getJSON()
                .contains("\"status\":\"TOO_MANY_ITEMS\""),
            request.getJSON());
    }

    @Test
    void aTraversalItemIdNeverReadsOutsideTheIconsDirectory() throws IOException {
        Path secret = configDir.resolve("secret.png");
        Files.writeString(secret, "topSecret");
        AE2Controller.hashcodeToStack.put(3, stackWithItem("../secret"));
        try {
            JsonArray data = run("3");
            assertEquals(
                PLACEHOLDER_BASE64,
                entryFor(data, 3).get("pngData")
                    .getAsString());
        } finally {
            Files.deleteIfExists(secret);
        }
    }

    // --- IconStore.sanitizeRelativePath ---

    @Test
    void sanitizationAcceptsTypicalRegistryNames() {
        assertEquals("minecraft/stone.png", IconStore.sanitizeRelativePath("minecraft:stone"));
        assertEquals(
            "gregtech/gt.metaitem.01/32600.png",
            IconStore.sanitizeRelativePath("gregtech:gt.metaitem.01/32600"));
    }

    @Test
    void sanitizationRejectsEscapesAndJunk() {
        assertEquals(null, IconStore.sanitizeRelativePath("..\\secret"));
        assertEquals(null, IconStore.sanitizeRelativePath("../secret"));
        assertEquals(null, IconStore.sanitizeRelativePath("a/../b"));
        assertEquals(null, IconStore.sanitizeRelativePath(""));
        assertEquals(null, IconStore.sanitizeRelativePath(null));
        assertEquals(null, IconStore.sanitizeRelativePath("has space:x"));
        assertEquals(null, IconStore.sanitizeRelativePath("x:y\nz"));
    }
}
