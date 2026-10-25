package net.notaglitch.detailstick;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DebugStickState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * DetailStick - a <b>server-side</b> NeoForge mod that turns the vanilla debug stick into a
 * survival-friendly block state editor that cannot be used to duplicate items.
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li><b>No custom item.</b> The mod operates on the vanilla {@code minecraft:debug_stick}, so
 *       players can connect with a vanilla client and need nothing installed.</li>
 *   <li><b>Works in survival.</b> The vanilla debug stick only functions for players that
 *       {@code canUseGameMasterBlocks()} - which requires <i>creative mode</i>. To make it usable in
 *       survival, this mod performs the block-state edit itself from a server-side interaction
 *       handler and cancels the (inert) vanilla interaction.</li>
 *   <li><b>Dupe protection.</b> "Count" properties that would let a player duplicate items
 *       (turtle egg {@code eggs}, sea pickle {@code pickles}, candle {@code candles}, snow
 *       {@code layers}) are filtered out entirely - they can neither be selected nor changed. The
 *       blocked set lives in {@link Config}.</li>
 * </ul>
 *
 * <p>Controls match vanilla: left-click a block to select the next editable property, right-click to
 * cycle the selected property's value, sneak to go backwards.</p>
 */
@Mod(DetailStick.MODID)
public class DetailStick {
    public static final String MODID = "detailstick";
    private static final Logger LOGGER = LogUtils.getLogger();

    public DetailStick(IEventBus modEventBus, ModContainer modContainer) {
        // The DebugStickGuard handler is auto-registered to the game event bus via
        // @EventBusSubscriber, so we only need to register the config here.
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        LOGGER.info("DetailStick loaded (server-side). Dupe-protected block state properties: {}",
                Config.blockedProperties);
    }

    @EventBusSubscriber(modid = MODID)
    public static final class DebugStickGuard {

        private DebugStickGuard() {}

        /**
         * Names a freshly-crafted debug stick "Detail Stick". Done in code rather than in the recipe
         * result because a {@code custom_name} component in a data-pack recipe is fragile; setting it
         * on the crafted stack is reliable and still fully server-side.
         */
        @SubscribeEvent
        public static void onItemCrafted(final PlayerEvent.ItemCraftedEvent event) {
            ItemStack crafting = event.getCrafting();
            if (crafting.is(Items.DEBUG_STICK) && !crafting.has(DataComponents.CUSTOM_NAME)) {
                crafting.set(DataComponents.CUSTOM_NAME,
                        Component.literal("Detail Stick").withStyle(style -> style.withItalic(false)));
            }
        }

        /** Right-click: cycle the value of the selected property. */
        @SubscribeEvent
        public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
            if (event.getLevel().isClientSide()) {
                return;
            }
            ItemStack stack = event.getItemStack();
            if (!stack.is(Items.DEBUG_STICK) || !hasPermission(event.getEntity())) {
                return;
            }

            // Only take over the interaction if we actually edited the block. For blocks we won't
            // touch (e.g. a blocked namespace), leave the event alone so the block's own right-click
            // behaviour still works.
            if (handle(event.getEntity(), event.getLevel(), event.getPos(), stack, true)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }

        /** Left-click: select the next editable property. */
        @SubscribeEvent
        public static void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
            if (event.getLevel().isClientSide()) {
                return;
            }
            // Only act on the initial press, not hold/abort/stop repeats.
            if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
                return;
            }
            ItemStack stack = event.getItemStack();
            if (!stack.is(Items.DEBUG_STICK) || !hasPermission(event.getEntity())) {
                return;
            }

            // Prevent the block from being mined only if we actually handled the selection.
            if (handle(event.getEntity(), event.getLevel(), event.getPos(), stack, false)) {
                event.setCanceled(true);
            }
        }

        private static boolean hasPermission(Player player) {
            // When permission is required, allow ops (permission level >= 2). We intentionally do
            // NOT use canUseGameMasterBlocks() here because that also requires creative mode, which
            // would defeat the survival use case.
            return !Config.requirePermission || player.hasPermissions(2);
        }

        /**
         * Core debug-stick logic, mirroring vanilla but (a) running server-side regardless of game
         * mode and (b) skipping any dupe-enabling property or blocked-namespace block.
         *
         * @param setValue {@code true} = cycle the selected property's value (right-click);
         *                 {@code false} = select the next property (left-click).
         * @return {@code true} if the Detail Stick handled the interaction (and the caller should
         *         cancel the vanilla event), {@code false} if the block was left untouched.
         */
        private static boolean handle(Player player, Level level, net.minecraft.core.BlockPos pos,
                                      ItemStack stack, boolean setValue) {
            BlockState state = level.getBlockState(pos);
            Holder<Block> blockHolder = state.getBlockHolder();

            // Refuse to operate on blocks from a blocked namespace (e.g. Copycats+ copycat blocks,
            // which can be duped or broken via block-state edits). We do NOT consume the interaction
            // so the block's own behaviour (and vanilla's inert debug stick) is left untouched.
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (Config.isBlockedNamespace(blockId.getNamespace())) {
                message(player, "The Detail Stick cannot edit " + blockId.getNamespace() + " blocks");
                return false;
            }

            // Properties the Detail Stick is allowed to touch (blocked = treated as nonexistent).
            List<Property<?>> editable = new ArrayList<>();
            for (Property<?> property : state.getProperties()) {
                if (!Config.isBlocked(property.getName())) {
                    editable.add(property);
                }
            }

            if (editable.isEmpty()) {
                message(player, "No editable properties on " + blockName(state));
                return true;
            }

            DebugStickState debugState = stack.get(DataComponents.DEBUG_STICK_STATE);
            if (debugState == null) {
                debugState = DebugStickState.EMPTY;
            }

            Property<?> selected = debugState.properties().get(blockHolder);
            // Drop a stale/blocked selection.
            if (selected == null || !editable.contains(selected)) {
                selected = editable.get(0);
            }

            boolean backwards = player.isShiftKeyDown();

            if (setValue) {
                BlockState newState = cycleState(state, selected, backwards);
                level.setBlock(pos, newState, Block.UPDATE_ALL_IMMEDIATE);
                message(player, "\"" + selected.getName() + "\" to " + getValueName(newState, selected));
            } else {
                Property<?> next = getRelative(editable, selected, backwards);
                stack.set(DataComponents.DEBUG_STICK_STATE, debugState.withProperty(blockHolder, next));
                message(player, "selected \"" + next.getName() + "\" (" + getValueName(state, next) + ")");
            }

            return true;
        }

        private static <T extends Comparable<T>> BlockState cycleState(BlockState state, Property<T> property, boolean backwards) {
            return state.setValue(property, getRelative(property.getPossibleValues(), state.getValue(property), backwards));
        }

        private static <T> T getRelative(Iterable<T> values, T current, boolean backwards) {
            List<T> list = (values instanceof List<T> l) ? l : toList(values);
            if (list.isEmpty()) {
                return current;
            }
            int index = current == null ? -1 : list.indexOf(current);
            int size = list.size();
            return backwards ? list.get(((index - 1) % size + size) % size) : list.get((index + 1) % size);
        }

        private static <T> List<T> toList(Iterable<T> values) {
            if (values instanceof Collection<T> c) {
                return new ArrayList<>(c);
            }
            List<T> list = new ArrayList<>();
            values.forEach(list::add);
            return list;
        }

        private static <T extends Comparable<T>> String getValueName(BlockState state, Property<T> property) {
            return property.getName(state.getValue(property));
        }

        private static String blockName(BlockState state) {
            return state.getBlock().getName().getString();
        }

        private static void message(Player player, String text) {
            if (player instanceof ServerPlayer serverPlayer) {
                // Literal component: vanilla clients have no lang file for this mod.
                serverPlayer.sendSystemMessage(Component.literal(text), true);
            }
        }
    }
}
