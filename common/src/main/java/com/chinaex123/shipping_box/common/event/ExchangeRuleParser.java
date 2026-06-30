package com.chinaex123.shipping_box.common.event;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ExchangeRuleParser {

    private static final int MAX_RULE_COUNT = 1_000_000;

    private ExchangeRuleParser() {
    }

    public static ExchangeRule parseRule(JsonObject json) {
        ExchangeRule rule = new ExchangeRule();
        List<ExchangeRule.InputItem> inputs = new ArrayList<>();

        if (json.has("input") && json.get("input").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("input")) {
                JsonObject inputObj = element.getAsJsonObject();
                ExchangeRule.InputItem input = parseInputItem(inputObj);
                inputs.add(input);
            }
        } else if (json.has("input") && json.get("input").isJsonObject()) {
            JsonObject inputObj = json.getAsJsonObject("input");
            ExchangeRule.InputItem input = parseInputItem(inputObj);
            inputs.add(input);
        }

        rule.setInputs(inputs);

        JsonObject outputObj = json.getAsJsonObject("output");
        ExchangeRule.OutputItem output = parseOutputItem(outputObj);
        rule.setOutput(output);

        return rule;
    }

    public static ExchangeRule.InputItem parseInputItem(JsonObject inputObj) {
        ExchangeRule.InputItem input = new ExchangeRule.InputItem();

        if (inputObj.has("tag")) {
            input.setTag(inputObj.get("tag").getAsString());
        } else if (inputObj.has("item")) {
            input.setItem(inputObj.get("item").getAsString());
        }

        if (inputObj.has("components")) {
            JsonElement componentsElement = inputObj.get("components");
            if (componentsElement.isJsonObject()) {
                input.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                input.setComponents(componentsElement.getAsString());
            }
        }

        if (inputObj.has("count")) {
            input.setCount(inputObj.get("count").getAsInt());
        }

        return input;
    }

    public static ExchangeRule.OutputItem parseOutputItem(JsonObject outputObj) {
        ExchangeRule.OutputItem output = new ExchangeRule.OutputItem();

        if (outputObj.has("type") && "dynamic_pricing".equals(outputObj.get("type").getAsString())) {
            output.setType("dynamic_pricing");

            if (outputObj.has("coin") && outputObj.get("coin").getAsBoolean()) {
                output.setCoin(true);
            } else {
                if (outputObj.has("item")) {
                    output.setItem(outputObj.get("item").getAsString());
                }
            }

            if (outputObj.has("dynamic_properties") && outputObj.get("dynamic_properties").isJsonObject()) {
                JsonObject dynamicPropsObj = outputObj.getAsJsonObject("dynamic_properties");
                ExchangeRule.DynamicPricingProperties dynamicProps = parseDynamicPricingProperties(dynamicPropsObj);
                output.setDynamicProperties(dynamicProps);
            }

            return output;
        }

        if (outputObj.has("coin") && outputObj.get("coin").getAsBoolean()) {
            output.setCoin(true);
            if (outputObj.has("count")) {
                int count = outputObj.get("count").getAsInt();
                output.setCount(count);
            }
            return output;
        }

        if (outputObj.has("type") && "weight".equals(outputObj.get("type").getAsString())) {
            output.setType("weight");

            if (outputObj.has("items") && outputObj.get("items").isJsonArray()) {
                List<ExchangeRule.WeightedItem> weightedItems = new ArrayList<>();
                JsonArray itemsArray = outputObj.getAsJsonArray("items");

                for (JsonElement itemElement : itemsArray) {
                    JsonObject itemObj = itemElement.getAsJsonObject();
                    ExchangeRule.WeightedItem weightedItem = parseWeightedItem(itemObj);
                    weightedItems.add(weightedItem);
                }

                output.setItems(weightedItems);
            }

            return output;
        }

        if (outputObj.has("type") && "ecliptic_seasons".equals(outputObj.get("type").getAsString())) {
            output.setType("ecliptic_seasons");

            if (outputObj.has("item")) {
                output.setItem(outputObj.get("item").getAsString());
            }

            if (outputObj.has("count")) {
                output.setCount(outputObj.get("count").getAsInt());
            }

            if (outputObj.has("components")) {
                JsonElement componentsElement = outputObj.get("components");
                if (componentsElement.isJsonObject()) {
                    output.setComponents(componentsElement.getAsJsonObject());
                } else if (componentsElement.isJsonPrimitive()) {
                    output.setComponents(componentsElement.getAsString());
                }
            }

            if (outputObj.has("ecliptic_seasons") && outputObj.get("ecliptic_seasons").isJsonObject()) {
                JsonObject ecsPropsObj = outputObj.getAsJsonObject("ecliptic_seasons");
                ExchangeRule.EclipticSeasonsProperties ecsProps = parseEclipticSeasonsProperties(ecsPropsObj);
                output.setEclipticSeasonsProperties(ecsProps);
            }

            return output;
        }

        if (outputObj.has("item")) {
            output.setItem(outputObj.get("item").getAsString());
        }

        if (outputObj.has("components")) {
            JsonElement componentsElement = outputObj.get("components");
            if (componentsElement.isJsonObject()) {
                output.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                output.setComponents(componentsElement.getAsString());
            }
        }

        if (outputObj.has("count")) {
            output.setCount(outputObj.get("count").getAsInt());
        }

        return output;
    }

    public static ExchangeRule.WeightedItem parseWeightedItem(JsonObject itemObj) {
        ExchangeRule.WeightedItem weightedItem = new ExchangeRule.WeightedItem();

        if (itemObj.has("item")) {
            weightedItem.setItem(itemObj.get("item").getAsString());
        }
        if (itemObj.has("count")) {
            weightedItem.setCount(itemObj.get("count").getAsInt());
        }
        if (itemObj.has("weight")) {
            weightedItem.setWeight(itemObj.get("weight").getAsInt());
        }

        if (itemObj.has("components")) {
            JsonElement componentsElement = itemObj.get("components");
            if (componentsElement.isJsonObject()) {
                weightedItem.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                weightedItem.setComponents(componentsElement.getAsString());
            }
        }

        return weightedItem;
    }

    private static ExchangeRule.DynamicPricingProperties parseDynamicPricingProperties(JsonObject dynamicPropsObj) {
        ExchangeRule.DynamicPricingProperties dynamicProps = new ExchangeRule.DynamicPricingProperties();

        if (dynamicPropsObj.has("threshold") && dynamicPropsObj.get("threshold").isJsonArray()) {
            JsonArray thresholdArray = dynamicPropsObj.getAsJsonArray("threshold");
            int[] thresholds = new int[thresholdArray.size()];
            for (int i = 0; i < thresholdArray.size(); i++) {
                thresholds[i] = thresholdArray.get(i).getAsInt();
            }
            dynamicProps.setThreshold(thresholds);
        }

        if (dynamicPropsObj.has("value") && dynamicPropsObj.get("value").isJsonArray()) {
            JsonArray valueArray = dynamicPropsObj.getAsJsonArray("value");
            int[] values = new int[valueArray.size()];
            for (int i = 0; i < valueArray.size(); i++) {
                values[i] = valueArray.get(i).getAsInt();
            }
            dynamicProps.setValue(values);
        }

        if (dynamicPropsObj.has("day")) {
            dynamicProps.setDay(dynamicPropsObj.get("day").getAsInt());
        }

        return dynamicProps;
    }

    private static ExchangeRule.EclipticSeasonsProperties parseEclipticSeasonsProperties(JsonObject ecsPropsObj) {
        ExchangeRule.EclipticSeasonsProperties ecsProps = new ExchangeRule.EclipticSeasonsProperties();

        if (ecsPropsObj.has("season") && ecsPropsObj.get("season").isJsonArray()) {
            JsonArray seasonArray = ecsPropsObj.getAsJsonArray("season");
            List<String> seasons = new ArrayList<>();
            for (JsonElement seasonElement : seasonArray) {
                seasons.add(seasonElement.getAsString());
            }
            ecsProps.setSeason(seasons);
        }

        if (ecsPropsObj.has("seasonal_only")) {
            ecsProps.setSeasonal_only(ecsPropsObj.get("seasonal_only").getAsBoolean());
        }

        if (ecsPropsObj.has("add_season_bonus")) {
            ecsProps.setAdd_season_bonus(ecsPropsObj.get("add_season_bonus").getAsInt());
        }

        if (ecsPropsObj.has("reduce_season_bonus")) {
            ecsProps.setReduce_season_bonus(ecsPropsObj.get("reduce_season_bonus").getAsInt());
        }

        return ecsProps;
    }

    public static boolean validateRule(ExchangeRule rule) {
        for (ExchangeRule.InputItem input : rule.getInputs()) {
            if (!validateInputItem(input)) {
                return false;
            }
        }
        return validateOutputItem(rule.getOutputItem());
    }

    private static boolean validateInputItem(ExchangeRule.InputItem input) {
        if (!isPositiveRuleNumber(input.getCount())) {
            return false;
        }

        if (input.getTag() != null && !input.getTag().isEmpty()) {
            try {
                String tagId = input.getTag().startsWith("#") ? input.getTag().substring(1) : input.getTag();
                ResourceLocation tagResource = ResourceLocation.tryParse(tagId);
                return tagResource != null;
            } catch (Exception e) {
                return false;
            }
        } else if (input.getItem() != null && !input.getItem().isEmpty()) {
            return validateItemWithComponents(input.getItem());
        }

        return false;
    }

    private static boolean validateOutputItem(ExchangeRule.OutputItem output) {
        if (output == null) {
            return false;
        }

        if (output.isCoin()) {
            if ("dynamic_pricing".equals(output.getType())) {
                return validateDynamicPricing(output.getDynamicProperties());
            }
            return isPositiveRuleNumber(output.getCount());
        }

        if ("dynamic_pricing".equals(output.getType())) {
            return output.getItem() != null && !output.getItem().isEmpty()
                    && isPositiveRuleNumber(output.getCount())
                    && validateDynamicPricing(output.getDynamicProperties())
                    && validateItemWithComponents(output.getItem());
        }

        if ("weight".equals(output.getType()) && output.getItems() != null && !output.getItems().isEmpty()) {
            long totalWeight = 0L;
            for (ExchangeRule.WeightedItem weightedItem : output.getItems()) {
                if (weightedItem.getItem() == null || weightedItem.getItem().isEmpty()
                        || !isPositiveRuleNumber(weightedItem.getCount())
                        || weightedItem.getWeight() <= 0
                        || !validateItemWithComponents(weightedItem.getItem())) {
                    return false;
                }
                totalWeight += weightedItem.getWeight();
                if (totalWeight > Integer.MAX_VALUE) {
                    return false;
                }
            }
            return totalWeight > 0;
        }

        if ("ecliptic_seasons".equals(output.getType())) {
            if (output.getItem() == null || output.getItem().isEmpty() || !isPositiveRuleNumber(output.getCount())) {
                return false;
            }

            if (output.getEclipticSeasonsProperties() == null) {
                return false;
            }

            var ecsProps = output.getEclipticSeasonsProperties();
            if (ecsProps.getSeason() == null || ecsProps.getSeason().isEmpty()) {
                return false;
            }

            return validateItemWithComponents(output.getItem());
        }

        if (output.getItem() == null || output.getItem().isEmpty() || !isPositiveRuleNumber(output.getCount())) {
            return false;
        }

        return validateItemWithComponents(output.getItem());
    }

    private static boolean isPositiveRuleNumber(int value) {
        return value > 0 && value <= MAX_RULE_COUNT;
    }

    private static boolean validateDynamicPricing(ExchangeRule.DynamicPricingProperties properties) {
        if (properties == null) {
            return false;
        }

        int[] thresholds = properties.getThreshold();
        int[] values = properties.getValue();
        if (thresholds == null || values == null || thresholds.length == 0 || thresholds.length != values.length) {
            return false;
        }

        for (int i = 0; i < thresholds.length; i++) {
            if (thresholds[i] < 0 || !isPositiveRuleNumber(values[i])) {
                return false;
            }
            if (i > 0 && thresholds[i] <= thresholds[i - 1]) {
                return false;
            }
        }

        return true;
    }

    private static boolean validateItemWithComponents(String itemString) {
        try {
            String itemId = itemString;

            int componentStart = itemString.indexOf('[');
            int componentEnd = itemString.lastIndexOf(']');

            if (componentStart > 0 && componentEnd > componentStart) {
                itemId = itemString.substring(0, componentStart);
                String componentString = itemString.substring(componentStart + 1, componentEnd);

                if (!validateComponentString(componentString)) {
                    return false;
                }
            }

            ResourceLocation itemResource = ResourceLocation.tryParse(itemId);
            if (itemResource == null) {
                return false;
            }

            return BuiltInRegistries.ITEM.containsKey(itemResource);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateComponentString(String componentString) {
        if (componentString == null || componentString.isEmpty()) {
            return true;
        }

        String[] components = componentString.split(",");
        for (String comp : components) {
            comp = comp.trim();
            if (comp.isEmpty()) continue;

            int equalsIndex = comp.indexOf('=');
            if (equalsIndex <= 0) {
                return false;
            }

            String componentName = comp.substring(0, equalsIndex).trim();
            ResourceLocation componentId = ResourceLocation.tryParse(componentName);
            if (componentId == null) {
                return false;
            }

            String componentValue = comp.substring(equalsIndex + 1).trim();
            if (componentValue.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public static String getValidationErrorDetails(ExchangeRule rule) {
        try {
            if (rule.getInputs() == null || rule.getInputs().isEmpty()) {
                return "missing_input";
            }

            for (ExchangeRule.InputItem input : rule.getInputs()) {
                if (input.getItem() == null && input.getTag() == null) {
                    return "invalid_input_item";
                }
                if (input.getItem() != null && !input.getItem().isEmpty()) {
                    if (!BuiltInRegistries.ITEM.containsKey(Objects.requireNonNull(ResourceLocation.tryParse(input.getItem())))) {
                        return "unknown_item|" + input.getItem();
                    }
                }
                if (input.getTag() != null && !input.getTag().isEmpty()) {
                    String tagId = input.getTag().startsWith("#") ? input.getTag().substring(1) : input.getTag();
                    if (ResourceLocation.tryParse(tagId) == null) {
                        return "invalid_tag|" + input.getTag();
                    }
                }
            }

            if (rule.getOutputItem() == null) {
                return "missing_output";
            }

            var output = rule.getOutputItem();

            if (output.isCoin()) {
                if ("dynamic_pricing".equals(output.getType())) {
                    if (output.getDynamicProperties() == null) {
                        return "missing_dynamic_properties";
                    }
                    int[] thresholds = output.getDynamicProperties().getThreshold();
                    int[] values = output.getDynamicProperties().getValue();
                    if (thresholds == null || values == null) {
                        return "missing_threshold_or_value";
                    }
                    if (thresholds.length != values.length) {
                        return "threshold_value_mismatch";
                    }
                    for (int i = 1; i < thresholds.length; i++) {
                        if (thresholds[i] <= thresholds[i - 1]) {
                            return "threshold_not_increasing";
                        }
                    }
                }
                return null;
            }

            if ("dynamic_pricing".equals(output.getType())) {
                if (output.getItem() == null || output.getItem().isEmpty()) {
                    return "missing_output_item";
                }
                if (output.getDynamicProperties() == null) {
                    return "missing_dynamic_properties";
                }
                int[] thresholds = output.getDynamicProperties().getThreshold();
                int[] values = output.getDynamicProperties().getValue();
                if (thresholds == null || values == null) {
                    return "missing_threshold_or_value";
                }
                if (thresholds.length != values.length) {
                    return "threshold_value_mismatch";
                }
                for (int i = 1; i < thresholds.length; i++) {
                    if (thresholds[i] <= thresholds[i - 1]) {
                        return "threshold_not_increasing";
                    }
                }
            }

            if ("weight".equals(output.getType())) {
                if (output.getItems() == null || output.getItems().isEmpty()) {
                    return "missing_weighted_items";
                }
                for (ExchangeRule.WeightedItem item : output.getItems()) {
                    if (item.getItem() == null || item.getItem().isEmpty()) {
                        return "invalid_weighted_item";
                    }
                    if (item.getWeight() <= 0) {
                        return "invalid_weight";
                    }
                }
            }

            if ("ecliptic_seasons".equals(output.getType())) {
                if (output.getItem() == null || output.getItem().isEmpty()) {
                    return "missing_output_item";
                }
                if (output.getEclipticSeasonsProperties() == null) {
                    return "missing_ecliptic_seasons_properties";
                }
                var ecsProps = output.getEclipticSeasonsProperties();
                if (ecsProps.getSeason() == null || ecsProps.getSeason().isEmpty()) {
                    return "missing_season_list";
                }
                for (String season : ecsProps.getSeason()) {
                    if (!isValidSeason(season)) {
                        return "invalid_season|" + season;
                    }
                }
            }

            if (output.getItem() == null || output.getItem().isEmpty()) {
                return "missing_output_item";
            }

            return null;
        } catch (Exception e) {
            return "validation_exception|" + e.getMessage();
        }
    }

    public static boolean isValidSeason(String season) {
        if (season == null || season.isEmpty()) {
            return false;
        }
        return "all".equals(season) ||
                "spring".equals(season) ||
                "summer".equals(season) ||
                "autumn".equals(season) ||
                "winter".equals(season);
    }

    public static ExchangeRule findMatchingRule(List<ExchangeRule> rules, List<ItemStack> availableStacks) {
        ExchangeRule bestMatch = null;
        int bestMatchScore = -1;

        for (ExchangeRule rule : rules) {
            if (matchesRule(rule, availableStacks)) {
                int matchScore = calculateMatchPrecision(rule);

                if (matchScore > bestMatchScore) {
                    bestMatch = rule;
                    bestMatchScore = matchScore;
                }
            }
        }

        return bestMatch;
    }

    private static boolean matchesRule(ExchangeRule rule, List<ItemStack> availableStacks) {
        int[] requiredCounts = new int[rule.getInputs().size()];
        boolean[] satisfied = new boolean[rule.getInputs().size()];

        for (int i = 0; i < rule.getInputs().size(); i++) {
            requiredCounts[i] = rule.getInputs().get(i).getCount();
        }

        for (ItemStack stack : availableStacks) {
            if (stack.isEmpty()) continue;

            for (int i = 0; i < rule.getInputs().size(); i++) {
                if (!satisfied[i] && rule.getInputs().get(i).matches(stack)) {
                    int canConsume = Math.min(stack.getCount(), requiredCounts[i]);
                    requiredCounts[i] -= canConsume;

                    if (requiredCounts[i] <= 0) {
                        satisfied[i] = true;
                    }
                    break;
                }
            }
        }

        boolean allSatisfied = true;
        for (int i = 0; i < rule.getInputs().size(); i++) {
            if (!satisfied[i]) {
                allSatisfied = false;
            }
        }

        return allSatisfied;
    }

    private static int calculateMatchPrecision(ExchangeRule rule) {
        int precision = 0;

        for (ExchangeRule.InputItem input : rule.getInputs()) {
            if (input.getComponents() != null) {
                precision += 100;

                if (input.getComponents() instanceof JsonObject componentsObj) {
                    precision += componentsObj.size() * 10;
                    precision += calculateNestingDepth(componentsObj) * 5;
                } else if (input.getComponents() instanceof String componentStr) {
                    String[] parts = componentStr.split(",");
                    precision += parts.length * 10;

                    for (String part : parts) {
                        if (part.contains("=")) {
                            precision += 2;
                        }
                    }
                }
            } else if (input.getItem() != null && !input.getItem().isEmpty()) {
                precision += 10;
            } else if (input.getTag() != null && !input.getTag().isEmpty()) {
                precision += 5;
            }

            if (input.getCount() > 1) {
                precision += 1;
            }
        }

        if (rule.getInputs().size() > 1) {
            precision += rule.getInputs().size() * 2;
        }

        return precision;
    }

    private static int calculateNestingDepth(JsonObject obj) {
        int maxDepth = 0;

        for (var entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            int currentDepth = 1;

            if (value.isJsonObject()) {
                currentDepth += calculateNestingDepth(value.getAsJsonObject());
            } else if (value.isJsonArray()) {
                currentDepth += 1;
            }

            if (currentDepth > maxDepth) {
                maxDepth = currentDepth;
            }
        }

        return maxDepth;
    }

    public static List<ItemStack> consumeInputs(ExchangeRule rule, List<ItemStack> availableStacks) {
        List<ItemStack> remaining = new ArrayList<>(availableStacks);

        for (ExchangeRule.InputItem required : rule.getInputs()) {
            for (int j = 0; j < remaining.size(); j++) {
                ItemStack stack = remaining.get(j);
                if (required.matches(stack)) {
                    if (stack.getCount() > required.getCount()) {
                        stack.setCount(stack.getCount() - required.getCount());
                    } else if (stack.getCount() == required.getCount()) {
                        remaining.remove(j);
                    }
                    break;
                }
            }
        }

        return remaining;
    }

    public static String serializeRulesToJson(List<ExchangeRule> rules) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject root = new JsonObject();
            JsonArray rulesArray = new JsonArray();

            for (ExchangeRule rule : rules) {
                JsonObject ruleObj = new JsonObject();

                if (rule.getInputs().size() == 1) {
                    ruleObj.add("input", serializeInputItem(rule.getInputs().getFirst()));
                } else {
                    JsonArray inputsArray = new JsonArray();
                    for (ExchangeRule.InputItem input : rule.getInputs()) {
                        inputsArray.add(serializeInputItem(input));
                    }
                    ruleObj.add("input", inputsArray);
                }

                ruleObj.add("output", serializeOutputItem(rule.getOutputItem()));

                rulesArray.add(ruleObj);
            }

            root.add("rules", rulesArray);
            return gson.toJson(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static List<ExchangeRule> deserializeRulesFromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray rulesArray = root.getAsJsonArray("rules");

            List<ExchangeRule> clientRules = new ArrayList<>();

            for (JsonElement element : rulesArray) {
                JsonObject ruleObj = element.getAsJsonObject();
                ExchangeRule rule = new ExchangeRule();

                List<ExchangeRule.InputItem> inputs = new ArrayList<>();
                if (ruleObj.get("input").isJsonArray()) {
                    for (JsonElement inputElement : ruleObj.getAsJsonArray("input")) {
                        inputs.add(deserializeInputItem(inputElement.getAsJsonObject()));
                    }
                } else {
                    inputs.add(deserializeInputItem(ruleObj.getAsJsonObject("input")));
                }
                rule.setInputs(inputs);

                rule.setOutput(deserializeOutputItem(ruleObj.getAsJsonObject("output")));

                clientRules.add(rule);
            }

            return clientRules;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static JsonObject serializeInputItem(ExchangeRule.InputItem input) {
        JsonObject obj = new JsonObject();

        if (input.getItem() != null) {
            obj.addProperty("item", input.getItem());
        }
        if (input.getTag() != null) {
            obj.addProperty("tag", input.getTag());
        }

        if (input.getComponents() != null) {
            if (input.getComponents() instanceof JsonObject) {
                obj.add("components", (JsonObject) input.getComponents());
            } else if (input.getComponents() instanceof String) {
                obj.addProperty("components", (String) input.getComponents());
            }
        }

        obj.addProperty("count", input.getCount());

        return obj;
    }

    private static JsonObject serializeOutputItem(ExchangeRule.OutputItem output) {
        JsonObject obj = new JsonObject();

        if (output.isCoin()) {
            obj.addProperty("coin", true);
            obj.addProperty("count", output.getCount());

            if ("dynamic_pricing".equals(output.getType())) {
                obj.addProperty("type", "dynamic_pricing");

                if (output.getDynamicProperties() != null) {
                    JsonObject dynamicPropsObj = serializeDynamicPricingProperties(output.getDynamicProperties());
                    obj.add("dynamic_properties", dynamicPropsObj);
                }
            }
            return obj;
        }

        if ("dynamic_pricing".equals(output.getType()) && output.getDynamicProperties() != null) {
            obj.addProperty("type", "dynamic_pricing");
            if (output.getItem() != null) {
                obj.addProperty("item", output.getItem());
            }

            JsonObject dynamicPropsObj = serializeDynamicPricingProperties(output.getDynamicProperties());
            obj.add("dynamic_properties", dynamicPropsObj);
            return obj;
        }

        if ("weight".equals(output.getType()) && output.getItems() != null) {
            obj.addProperty("type", "weight");
            JsonArray itemsArray = new JsonArray();

            for (ExchangeRule.WeightedItem weightedItem : output.getItems()) {
                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("item", weightedItem.getItem());
                itemObj.addProperty("count", weightedItem.getCount());
                itemObj.addProperty("weight", weightedItem.getWeight());

                if (weightedItem.getComponents() != null) {
                    if (weightedItem.getComponents() instanceof JsonObject) {
                        itemObj.add("components", (JsonObject) weightedItem.getComponents());
                    } else if (weightedItem.getComponents() instanceof String) {
                        itemObj.addProperty("components", (String) weightedItem.getComponents());
                    }
                }

                itemsArray.add(itemObj);
            }

            obj.add("items", itemsArray);
            return obj;
        }

        if ("ecliptic_seasons".equals(output.getType())) {
            obj.addProperty("type", "ecliptic_seasons");
            obj.addProperty("item", output.getItem());
            obj.addProperty("count", output.getCount());

            if (output.getComponents() != null) {
                if (output.getComponents() instanceof JsonObject) {
                    obj.add("components", (JsonObject) output.getComponents());
                } else if (output.getComponents() instanceof String) {
                    obj.addProperty("components", (String) output.getComponents());
                }
            }

            if (output.getEclipticSeasonsProperties() != null) {
                obj.add("ecliptic_seasons", serializeEclipticSeasonsProperties(output.getEclipticSeasonsProperties()));
            }

            return obj;
        }

        obj.addProperty("item", output.getItem());
        obj.addProperty("count", output.getCount());

        if (output.getComponents() != null) {
            if (output.getComponents() instanceof JsonObject) {
                obj.add("components", (JsonObject) output.getComponents());
            } else if (output.getComponents() instanceof String) {
                obj.addProperty("components", (String) output.getComponents());
            }
        }

        return obj;
    }

    private static JsonObject serializeDynamicPricingProperties(ExchangeRule.DynamicPricingProperties props) {
        JsonObject dynamicPropsObj = new JsonObject();

        if (props.getThreshold() != null) {
            JsonArray thresholdArray = new JsonArray();
            for (int threshold : props.getThreshold()) {
                thresholdArray.add(threshold);
            }
            dynamicPropsObj.add("threshold", thresholdArray);
        }

        if (props.getValue() != null) {
            JsonArray valueArray = new JsonArray();
            for (int value : props.getValue()) {
                valueArray.add(value);
            }
            dynamicPropsObj.add("value", valueArray);
        }

        dynamicPropsObj.addProperty("day", props.getDay());

        return dynamicPropsObj;
    }

    private static JsonObject serializeEclipticSeasonsProperties(ExchangeRule.EclipticSeasonsProperties props) {
        JsonObject ecsPropsObj = new JsonObject();

        if (props.getSeason() != null) {
            JsonArray seasonArray = new JsonArray();
            for (String season : props.getSeason()) {
                seasonArray.add(season);
            }
            ecsPropsObj.add("season", seasonArray);
        }

        ecsPropsObj.addProperty("seasonal_only", props.isSeasonal_only());
        ecsPropsObj.addProperty("add_season_bonus", props.getAdd_season_bonus());
        ecsPropsObj.addProperty("reduce_season_bonus", props.getReduce_season_bonus());

        return ecsPropsObj;
    }

    private static ExchangeRule.InputItem deserializeInputItem(JsonObject obj) {
        ExchangeRule.InputItem input = new ExchangeRule.InputItem();

        if (obj.has("item")) {
            input.setItem(obj.get("item").getAsString());
        }
        if (obj.has("tag")) {
            input.setTag(obj.get("tag").getAsString());
        }

        if (obj.has("components")) {
            JsonElement componentsElement = obj.get("components");
            if (componentsElement.isJsonObject()) {
                input.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                input.setComponents(componentsElement.getAsString());
            }
        }

        if (obj.has("count")) {
            input.setCount(obj.get("count").getAsInt());
        }

        return input;
    }

    private static ExchangeRule.OutputItem deserializeOutputItem(JsonObject obj) {
        ExchangeRule.OutputItem output = new ExchangeRule.OutputItem();

        if (obj.has("coin") && obj.get("coin").getAsBoolean()) {
            output.setCoin(true);
            if (obj.has("count")) {
                output.setCount(obj.get("count").getAsInt());
            }

            if (obj.has("type") && "dynamic_pricing".equals(obj.get("type").getAsString())) {
                output.setType("dynamic_pricing");

                if (obj.has("dynamic_properties") && obj.get("dynamic_properties").isJsonObject()) {
                    JsonObject dynamicPropsObj = obj.getAsJsonObject("dynamic_properties");
                    ExchangeRule.DynamicPricingProperties dynamicProps = parseDynamicPricingProperties(dynamicPropsObj);
                    output.setDynamicProperties(dynamicProps);
                }
            }
            return output;
        }

        if (obj.has("type") && "dynamic_pricing".equals(obj.get("type").getAsString())) {
            output.setType("dynamic_pricing");

            if (obj.has("item")) {
                output.setItem(obj.get("item").getAsString());
            }

            if (obj.has("dynamic_properties") && obj.get("dynamic_properties").isJsonObject()) {
                JsonObject dynamicPropsObj = obj.getAsJsonObject("dynamic_properties");
                ExchangeRule.DynamicPricingProperties dynamicProps = parseDynamicPricingProperties(dynamicPropsObj);
                output.setDynamicProperties(dynamicProps);
            }

            return output;
        }

        if (obj.has("type") && "weight".equals(obj.get("type").getAsString())) {
            output.setType("weight");

            if (obj.has("items") && obj.get("items").isJsonArray()) {
                List<ExchangeRule.WeightedItem> weightedItems = new ArrayList<>();
                JsonArray itemsArray = obj.getAsJsonArray("items");

                for (JsonElement itemElement : itemsArray) {
                    JsonObject itemObj = itemElement.getAsJsonObject();
                    ExchangeRule.WeightedItem weightedItem = deserializeWeightedItem(itemObj);
                    weightedItems.add(weightedItem);
                }

                output.setItems(weightedItems);
            }

            return output;
        }

        if (obj.has("type") && "ecliptic_seasons".equals(obj.get("type").getAsString())) {
            output.setType("ecliptic_seasons");

            if (obj.has("item")) {
                output.setItem(obj.get("item").getAsString());
            }

            if (obj.has("count")) {
                output.setCount(obj.get("count").getAsInt());
            }

            if (obj.has("components")) {
                JsonElement componentsElement = obj.get("components");
                if (componentsElement.isJsonObject()) {
                    output.setComponents(componentsElement.getAsJsonObject());
                } else if (componentsElement.isJsonPrimitive()) {
                    output.setComponents(componentsElement.getAsString());
                }
            }

            if (obj.has("ecliptic_seasons") && obj.get("ecliptic_seasons").isJsonObject()) {
                JsonObject ecsPropsObj = obj.getAsJsonObject("ecliptic_seasons");
                ExchangeRule.EclipticSeasonsProperties ecsProps = parseEclipticSeasonsProperties(ecsPropsObj);
                output.setEclipticSeasonsProperties(ecsProps);
            }

            return output;
        }

        output.setItem(obj.get("item").getAsString());
        output.setCount(obj.get("count").getAsInt());

        if (obj.has("components")) {
            JsonElement componentsElement = obj.get("components");
            if (componentsElement.isJsonObject()) {
                output.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                output.setComponents(componentsElement.getAsString());
            }
        }

        return output;
    }

    private static ExchangeRule.WeightedItem deserializeWeightedItem(JsonObject itemObj) {
        ExchangeRule.WeightedItem weightedItem = new ExchangeRule.WeightedItem();

        weightedItem.setItem(itemObj.get("item").getAsString());
        weightedItem.setCount(itemObj.get("count").getAsInt());
        weightedItem.setWeight(itemObj.get("weight").getAsInt());

        if (itemObj.has("components")) {
            JsonElement componentsElement = itemObj.get("components");
            if (componentsElement.isJsonObject()) {
                weightedItem.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                weightedItem.setComponents(componentsElement.getAsString());
            }
        }

        return weightedItem;
    }
}
