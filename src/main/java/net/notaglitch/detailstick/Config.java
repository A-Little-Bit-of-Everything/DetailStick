package net.notaglitch.detailstick;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side configuration for the Detail Stick.
 *
 * <p>The Detail Stick is a survival-friendly debug stick. The main difference from the vanilla
 * debug stick is that it refuses to edit "count"-style block state properties that can be abused
 * to duplicate items (e.g. turtle eggs, sea pickles, candles, snow layers). Those properties
 * control how many physical items a block represents, so incrementing them with a debug stick
 * effectively creates free items out of thin air.</p>
 */
@EventBusSubscriber(modid = DetailStick.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * The default set of block state property names the Detail Stick refuses to edit, because
     * changing them lets a player create items/fluids from nothing:
     * <ul>
     *     <li>{@code eggs}       - turtle eggs (1-4)</li>
     *     <li>{@code pickles}    - sea pickles (1-4)</li>
     *     <li>{@code candles}    - candles / all dyed candles (1-4)</li>
     *     <li>{@code layers}     - snow layers (1-8)</li>
     *     <li>{@code waterlogged} - adding/removing water from a block is a free water source</li>
     * </ul>
     */
    private static final List<String> DEFAULT_BLOCKED_PROPERTIES = List.of(
            "eggs",
            "pickles",
            "candles",
            "layers",
            "waterlogged"
    );

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_PROPERTIES = BUILDER
            .comment(
                    "Block state property names the Detail Stick is NOT allowed to edit.",
                    "These are properties that can be abused to duplicate items or fluids",
                    "(e.g. turtle eggs, sea pickles, candles, snow layers, waterlogging).",
                    "Property names are matched case-insensitively.")
            .defineListAllowEmpty("blockedProperties", DEFAULT_BLOCKED_PROPERTIES, Config::validatePropertyName);

    /**
     * The default set of mod namespaces whose blocks the Detail Stick refuses to touch entirely.
     * Some modded blocks (notably Copycats+ copycat blocks in the {@code copycats} namespace) store
     * their appearance/contents in block states that can be duplicated or corrupted by editing them
     * with a debug stick, so the safest option is to ignore those blocks completely.
     */
    private static final List<String> DEFAULT_BLOCKED_NAMESPACES = List.of(
            "copycats"
    );

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_NAMESPACES = BUILDER
            .comment(
                    "Mod namespaces whose blocks the Detail Stick will NOT operate on at all.",
                    "Any block whose registry id starts with '<namespace>:' is skipped entirely -",
                    "no property can be selected or changed on it. Use this for mods whose blocks",
                    "can be duped or broken via block state edits (e.g. Copycats+ 'copycats').",
                    "Namespaces are matched case-insensitively.")
            .defineListAllowEmpty("blockedNamespaces", DEFAULT_BLOCKED_NAMESPACES, Config::validateNamespace);

    private static final ModConfigSpec.BooleanValue REQUIRE_PERMISSION = BUILDER
            .comment(
                    "If true, only operators (permission level 2 or higher) can edit block states",
                    "with the Detail Stick. If false, any player holding it can edit block states.",
                    "Either way, anyone can craft and hold the stick - this only gates its use.")
            .define("requirePermission", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    /** Lower-cased set of blocked property names, refreshed on config load. */
    public static Set<String> blockedProperties = DEFAULT_BLOCKED_PROPERTIES.stream()
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    /** Lower-cased set of blocked mod namespaces, refreshed on config load. */
    public static Set<String> blockedNamespaces = DEFAULT_BLOCKED_NAMESPACES.stream()
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    public static boolean requirePermission = true;

    private static boolean validatePropertyName(final Object obj) {
        return obj instanceof String s && !s.isBlank();
    }

    private static boolean validateNamespace(final Object obj) {
        return obj instanceof String s && !s.isBlank();
    }

    /**
     * @return {@code true} if the Detail Stick is forbidden from editing the given property.
     */
    public static boolean isBlocked(final String propertyName) {
        return propertyName != null && blockedProperties.contains(propertyName.toLowerCase(Locale.ROOT));
    }

    /**
     * @return {@code true} if the Detail Stick is forbidden from operating on blocks from the given
     *         mod namespace.
     */
    public static boolean isBlockedNamespace(final String namespace) {
        return namespace != null && blockedNamespaces.contains(namespace.toLowerCase(Locale.ROOT));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        blockedProperties = BLOCKED_PROPERTIES.get().stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        blockedNamespaces = BLOCKED_NAMESPACES.get().stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        requirePermission = REQUIRE_PERMISSION.get();
    }
}
