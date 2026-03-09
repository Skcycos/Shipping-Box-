package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.network.ClientSoldCountCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = ShippingBox.MOD_ID, value = Dist.CLIENT)
public class ClientModEvents {

    /**
     * 玩家登出事件监听器 (客户端)
     * <p>
     * 当玩家从服务器断开连接时调用此方法，用于执行客户端相关的清理操作。
     * 主要负责清除客户端的销售数量缓存，释放内存资源并确保数据一致性。
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientSoldCountCache.clearCache();
    }
}
