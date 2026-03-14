package com.chinaex123.shipping_box.command.CommandLogic;

import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 统计兑换规则数量命令的执行器
 */
public class CountRulesCommand {

    /**
     * 执行统计规则数量命令
     * @param context 命令上下文
     * @return 命令执行结果（成功返回 1，失败返回 0）
     */
    public static int execute(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();

            List<ExchangeRule> rules = ExchangeRecipeManager.getRules();

            if (rules.isEmpty()) {
                source.sendFailure(Component.translatable("command.shipping_box.rules.no_rules_found")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            // 统计不同类型的规则
            int coinRules = 0;
            int itemRules = 0;
            int dynamicPricingRules = 0;
            int componentRules = 0;

            for (ExchangeRule rule : rules) {
                if (rule.getOutputItem().isCoin()) {
                    coinRules++;
                    if ("dynamic_pricing".equals(rule.getOutputItem().getType())) {
                        dynamicPricingRules++;
                    }
                } else {
                    itemRules++;
                }

                // 检查是否有组件要求
                for (var input : rule.getInputs()) {
                    if (input.getComponents() != null) {
                        componentRules++;
                        break;
                    }
                }
            }

            // 发送统计信息
            source.sendSuccess(
                    () -> Component.translatable("command.shipping_box.rules.count_total", rules.size())
                            .withStyle(ChatFormatting.GOLD),
                    true);

            int finalCoinRules = coinRules;
            source.sendSuccess(
                    () -> Component.translatable("command.shipping_box.rules.count_coin", finalCoinRules)
                            .withStyle(ChatFormatting.YELLOW),
                    false);

            int finalItemRules = itemRules;
            source.sendSuccess(
                    () -> Component.translatable("command.shipping_box.rules.count_item", finalItemRules)
                            .withStyle(ChatFormatting.GREEN),
                    false);

            if (dynamicPricingRules > 0) {
                int finalDynamicPricingRules = dynamicPricingRules;
                source.sendSuccess(
                        () -> Component.translatable("command.shipping_box.rules.count_dynamic", finalDynamicPricingRules)
                                .withStyle(ChatFormatting.AQUA),
                        false);
            }

            if (componentRules > 0) {
                int finalComponentRules = componentRules;
                source.sendSuccess(
                        () -> Component.translatable("command.shipping_box.rules.count_component", finalComponentRules)
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        false);
            }

            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error counting rules: " + e.getMessage()));
            return 0;
        }
    }
}
