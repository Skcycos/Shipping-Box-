package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.chinaex123.shipping_box.menu.AutoShippingBoxMenu;
import com.chinaex123.shipping_box.menu.ShippingBoxMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

/**
 * 模组菜单类型注册；
 * 为普通/自动售货箱注册自定义 MenuType，以便绑定自定义 Screen 在左侧渲染账单面板。
 * <p>
 * 客户端工厂通过 {@code openMenu} 的额外数据缓冲读取 BlockPos（普通售货箱还读取玩家 UUID），
 * 再从客户端世界获取对应的方块实体来重建菜单。
 */
public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, ShippingBox.MOD_ID);

    /** 普通售货箱菜单类型 */
    public static final DeferredHolder<MenuType<?>, MenuType<ShippingBoxMenu>> SHIPPING_BOX =
            MENU_TYPES.register("shipping_box", () -> createShippingBoxType());

    /** 自动售货箱菜单类型 */
    public static final DeferredHolder<MenuType<?>, MenuType<AutoShippingBoxMenu>> AUTO_SHIPPING_BOX =
            MENU_TYPES.register("auto_shipping_box", () -> createAutoShippingBoxType());

    /** 普通售货箱：缓冲区写入 [BlockPos, UUID]，客户端据此重建菜单 */
    private static MenuType<ShippingBoxMenu> createShippingBoxType() {
        return net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                (windowId, inv, buf) -> {
                    BlockPos pos = buf.readBlockPos();
                    UUID uuid = buf.readUUID();
                    var be = Minecraft.getInstance().level.getBlockEntity(pos);
                    if (be instanceof ShippingBoxBlockEntity sbbe) {
                        return new ShippingBoxMenu(windowId, inv, sbbe, uuid);
                    }
                    // 极端情况（方块未加载）下用临时实体兜底，避免客户端崩溃
                    return new ShippingBoxMenu(windowId, inv, dummyShippingBox(pos), uuid);
                }
        );
    }

    /** 自动售货箱：缓冲区写入 [BlockPos]，客户端据此重建菜单 */
    private static MenuType<AutoShippingBoxMenu> createAutoShippingBoxType() {
        return net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                (windowId, inv, buf) -> {
                    BlockPos pos = buf.readBlockPos();
                    var be = Minecraft.getInstance().level.getBlockEntity(pos);
                    if (be instanceof AutoShippingBoxBlockEntity asbe) {
                        return new AutoShippingBoxMenu(windowId, inv, asbe);
                    }
                    return new AutoShippingBoxMenu(windowId, inv, dummyAutoBox(pos));
                }
        );
    }

    private static ShippingBoxBlockEntity dummyShippingBox(BlockPos pos) {
        return new ShippingBoxBlockEntity(pos,
                com.chinaex123.shipping_box.init.ModBlocks.SHIPPING_BOX.get().defaultBlockState());
    }

    private static AutoShippingBoxBlockEntity dummyAutoBox(BlockPos pos) {
        return new AutoShippingBoxBlockEntity(pos,
                com.chinaex123.shipping_box.init.ModBlocks.AUTO_SHIPPING_BOX.get().defaultBlockState());
    }

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
