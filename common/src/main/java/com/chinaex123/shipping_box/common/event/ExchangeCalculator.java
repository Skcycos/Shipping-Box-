package com.chinaex123.shipping_box.common.event;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ExchangeCalculator {

    private ExchangeCalculator() {
    }

    public static int getMaxExchanges(ExchangeRule rule, List<ItemStack> availableStacks) {
        int maxExchanges = Integer.MAX_VALUE;

        for (ExchangeRule.InputItem required : rule.getInputs()) {
            int totalCount = 0;
            for (ItemStack stack : availableStacks) {
                if (required.matches(stack)) {
                    totalCount += stack.getCount();
                }
            }

            int possibleExchanges = totalCount / required.getCount();
            if (possibleExchanges < maxExchanges) {
                maxExchanges = possibleExchanges;
            }
        }

        return maxExchanges;
    }

    public static List<ItemStack> calculateConsumedItems(List<ItemStack> initialItems, List<ItemStack> remainingItems) {
        List<ItemStack> consumed = new ArrayList<>();

        List<ItemStack> remainingCopy = new ArrayList<>();
        for (ItemStack stack : remainingItems) {
            remainingCopy.add(stack.copy());
        }

        for (ItemStack initStack : initialItems) {
            ItemStack stackToProcess = initStack.copy();
            int originalCount = stackToProcess.getCount();
            int currentRemaining = 0;

            for (ItemStack remStack : remainingCopy) {
                if (ItemStack.isSameItemSameComponents(stackToProcess, remStack)) {
                    currentRemaining += remStack.getCount();
                    if (currentRemaining >= originalCount) break;
                }
            }

            int consumedCount = originalCount - currentRemaining;

            if (consumedCount > 0) {
                ItemStack consumedStack = stackToProcess.copy();
                consumedStack.setCount(consumedCount);
                consumed.add(consumedStack);
            }
        }

        List<ItemStack> mergedConsumed = new ArrayList<>();
        for (ItemStack stack : consumed) {
            boolean merged = false;
            for (ItemStack existing : mergedConsumed) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(stack.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                mergedConsumed.add(stack);
            }
        }

        return mergedConsumed;
    }

    public static NonNullList<ItemStack> createNonNullList(List<ItemStack> list) {
        NonNullList<ItemStack> result = NonNullList.create();
        result.addAll(list);
        return result;
    }
}
