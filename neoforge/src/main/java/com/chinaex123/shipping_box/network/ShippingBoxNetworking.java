package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.common.network.PacketExchangeEffects;
import com.chinaex123.shipping_box.common.network.PacketShowSuccessMessage;
import com.chinaex123.shipping_box.common.network.PacketSoldCountSync;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ShippingBoxNetworking {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ShippingBox.MOD_ID);

        registrar.playToClient(
                PacketShowSuccessMessage.TYPE,
                PacketShowSuccessMessage.STREAM_CODEC,
                PacketHandlers::handleShowSuccess
        );

        registrar.playToServer(
                PacketPlayerPlaceItem.TYPE,
                PacketPlayerPlaceItem.STREAM_CODEC,
                PacketPlayerPlaceItem::handle
        );

        registrar.playToClient(
                PacketSyncRecipes.TYPE,
                PacketSyncRecipes.STREAM_CODEC,
                PacketSyncRecipes::handle
        );

        registrar.playToClient(
                PacketSoldCountSync.TYPE,
                PacketSoldCountSync.STREAM_CODEC,
                PacketHandlers::handleSoldCountSync
        );

        registrar.playToClient(
                PacketExchangeEffects.TYPE,
                PacketExchangeEffects.STREAM_CODEC,
                PacketHandlers::handleExchangeEffects
        );

        registrar.playToClient(
                PacketStartLocalWebEditor.TYPE,
                PacketStartLocalWebEditor.STREAM_CODEC,
                PacketStartLocalWebEditor::handle
        );

        registrar.playToServer(
                PacketEditorSaveRules.TYPE,
                PacketEditorSaveRules.STREAM_CODEC,
                PacketEditorSaveRules::handle
        );

        registrar.playToServer(
                PacketEditorReadFile.TYPE,
                PacketEditorReadFile.STREAM_CODEC,
                PacketEditorReadFile::handle
        );

        registrar.playToClient(
                PacketEditorReadFileResult.TYPE,
                PacketEditorReadFileResult.STREAM_CODEC,
                PacketEditorReadFileResult::handle
        );

        registrar.playToClient(
                PacketEditorSaveRulesResult.TYPE,
                PacketEditorSaveRulesResult.STREAM_CODEC,
                PacketEditorSaveRulesResult::handle
        );

        registrar.playToServer(
                PacketEditorReloadRequest.TYPE,
                PacketEditorReloadRequest.STREAM_CODEC,
                PacketEditorReloadRequest::handle
        );
    }

    public static void syncRecipesToClient(ServerPlayer player) {
        try {
            String rulesJson = ExchangeRecipeManager.serializeRulesToJson();
            PacketSyncRecipes packet = new PacketSyncRecipes(rulesJson);
            PacketDistributor.sendToPlayer(player, packet);
        } catch (Exception e) {
        }
    }
}
