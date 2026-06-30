package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.common.event.ExchangeRule;
import com.chinaex123.shipping_box.common.event.ExchangeRuleParser;
import com.chinaex123.shipping_box.common.event.ExchangeRuleRegistry;
import com.google.gson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = ShippingBox.MOD_ID)
public class ExchangeRecipeManager extends SimplePreparableReloadListener<List<ExchangeRule>> {

    private static final String CONFIG_FOLDER = "exchange_rules";

    private static final List<String> pendingErrorMessages = new ArrayList<>();

    @Override
    protected List<ExchangeRule> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<ExchangeRule> rules = new ArrayList<>();
        List<String> currentErrors = new ArrayList<>();

        if (!net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
            loadConfigRules(rules, currentErrors);
        }

        try {
            var resources = resourceManager.listResources(CONFIG_FOLDER, path -> path.getPath().endsWith(".json"));

            for (ResourceLocation resourceLocation : resources.keySet()) {
                try {
                    Optional<Resource> resourceOptional = resourceManager.getResource(resourceLocation);
                    if (resourceOptional.isPresent()) {
                        Resource resource = resourceOptional.get();
                        try (InputStream inputStream = resource.open();
                             BufferedReader reader = new BufferedReader(
                                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                            if (json.has("rules") && json.get("rules").isJsonArray()) {
                                JsonArray rulesArray = json.getAsJsonArray("rules");

                                int ruleIndex = 0;
                                for (JsonElement element : rulesArray) {
                                    try {
                                        JsonObject ruleObj = element.getAsJsonObject();

                                        ExchangeRule rule = ExchangeRuleParser.parseRule(ruleObj);

                                        if (ExchangeRuleParser.validateRule(rule)) {
                                            rules.add(rule);
                                        } else {
                                            String validationError = ExchangeRuleParser.getValidationErrorDetails(rule);
                                            if (validationError != null && !validationError.isEmpty()) {
                                                currentErrors.add(String.format("error.shipping_box.rule_validation_failed|%d|%s|%s",
                                                        ruleIndex + 1, resourceLocation.getPath(), validationError));
                                            } else {
                                                currentErrors.add(String.format("error.shipping_box.rule_validation_failed|%d|%s|%s",
                                                        ruleIndex + 1, resourceLocation.getPath(), "unknown_error"));
                                            }
                                        }
                                    } catch (JsonParseException e) {
                                        currentErrors.add(String.format("error.shipping_box.json_parse_error|%s|%s",
                                                resourceLocation.getPath(), e.getMessage()));
                                    } catch (Exception e) {
                                        currentErrors.add(String.format("error.shipping_box.rule_parse_error|%s|%s",
                                                resourceLocation.getPath(), e.getMessage()));
                                    }
                                    ruleIndex++;
                                }
                            } else {
                                currentErrors.add(String.format("error.shipping_box.missing_rules_array|%s",
                                        resourceLocation.getPath()));
                            }
                        }
                    }
                } catch (Exception e) {
                    currentErrors.add(String.format("error.shipping_box.resource_load_error|%s|%s",
                            resourceLocation.getPath(), e.getMessage()));
                }
            }

        } catch (Exception e) {
            currentErrors.add(String.format("error.shipping_box.scan_error|%s", e.getMessage()));
        }

        if (!currentErrors.isEmpty()) {
            synchronized (pendingErrorMessages) {
                pendingErrorMessages.addAll(currentErrors);
            }
        }

        return rules;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!pendingErrorMessages.isEmpty()) {
            synchronized (pendingErrorMessages) {
                if (ServerLifecycleHooks.getCurrentServer() != null && !ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().isEmpty()) {
                    for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                        player.displayClientMessage(Component.translatable("message.shipping_box.recipe_error_title"), false);

                        for (String errorMsg : pendingErrorMessages) {
                            Component errorComponent = parseLocalizedError(errorMsg);
                            player.displayClientMessage(errorComponent, false);
                        }

                        player.displayClientMessage(Component.translatable("message.shipping_box.recipe_error_help"), false);
                    }
                    pendingErrorMessages.clear();
                }
            }
        }
    }

    private static Component parseLocalizedError(String errorString) {
        try {
            String[] parts = errorString.split("\\|");
            String key = parts[0];

            if (parts.length == 1) {
                return Component.translatable(key).withStyle(ChatFormatting.RED);
            } else {
                String[] params = new String[parts.length - 1];
                System.arraycopy(parts, 1, params, 0, parts.length - 1);

                Object[] paramObjects = new Object[params.length];
                for (int i = 0; i < params.length; i++) {
                    paramObjects[i] = Component.literal(params[i]);
                }

                return Component.translatable(key, paramObjects).withStyle(ChatFormatting.RED);
            }
        } catch (Exception e) {
            return Component.literal(errorString).withStyle(ChatFormatting.RED);
        }
    }

    @Override
    protected void apply(List<ExchangeRule> rules, ResourceManager resourceManager, ProfilerFiller profiler) {
        ExchangeRuleRegistry.setRules(rules);
    }

    public static List<ExchangeRule> getRules() {
        return ExchangeRuleRegistry.getRules();
    }

    public static ExchangeRule findMatchingRule(List<ItemStack> availableStacks) {
        return ExchangeRuleParser.findMatchingRule(ExchangeRuleRegistry.getRules(), availableStacks);
    }

    public static List<ItemStack> consumeInputs(ExchangeRule rule, List<ItemStack> availableStacks) {
        return ExchangeRuleParser.consumeInputs(rule, availableStacks);
    }

    public static String serializeRulesToJson() {
        return ExchangeRuleParser.serializeRulesToJson(ExchangeRuleRegistry.getRules());
    }

    public static void setClientRules(String json) {
        ExchangeRuleRegistry.setRules(ExchangeRuleParser.deserializeRulesFromJson(json));
    }

    private void loadConfigRules(List<ExchangeRule> rules, List<String> errors) {
        try {
            Path dir = getExternalRulesDir();
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return;
            }

            try (var stream = Files.walk(dir)) {
                List<Path> files = stream
                        .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();

                for (Path file : files) {
                    try {
                        String raw = Files.readString(file, StandardCharsets.UTF_8);
                        JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                        loadRulesFromJson(json, file.toString(), rules, errors);
                    } catch (JsonParseException e) {
                        errors.add(String.format("error.shipping_box.json_parse_error|%s|%s",
                                file.toString(), e.getMessage()));
                    } catch (Exception e) {
                        errors.add(String.format("error.shipping_box.resource_load_error|%s|%s",
                                file.toString(), e.getMessage()));
                    }
                }
            }
        } catch (Exception e) {
            errors.add(String.format("error.shipping_box.resource_load_error|%s|%s",
                    getExternalRulesDir().toString(), e.getMessage()));
        }
    }

    private Path getExternalRulesDir() {
        if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
            return FMLPaths.GAMEDIR.get().resolve("kubejs/data/shipping_box/exchange_rules");
        }
        return FMLPaths.CONFIGDIR.get().resolve("shipping_box/exchange_rules");
    }

    private void loadRulesFromJson(JsonObject json, String source, List<ExchangeRule> rules, List<String> errors) {
        if (json.has("rules") && json.get("rules").isJsonArray()) {
            JsonArray rulesArray = json.getAsJsonArray("rules");

            int ruleIndex = 0;
            for (JsonElement element : rulesArray) {
                try {
                    JsonObject ruleObj = element.getAsJsonObject();

                    ExchangeRule rule = ExchangeRuleParser.parseRule(ruleObj);

                    if (ExchangeRuleParser.validateRule(rule)) {
                        rules.add(rule);
                    } else {
                        String validationError = ExchangeRuleParser.getValidationErrorDetails(rule);
                        if (validationError != null && !validationError.isEmpty()) {
                            errors.add(String.format("error.shipping_box.rule_validation_failed|%d|%s|%s",
                                    ruleIndex + 1, source, validationError));
                        } else {
                            errors.add(String.format("error.shipping_box.rule_validation_failed|%d|%s|%s",
                                    ruleIndex + 1, source, "unknown_error"));
                        }
                    }
                } catch (JsonParseException e) {
                    errors.add(String.format("error.shipping_box.json_parse_error|%s|%s",
                            source, e.getMessage()));
                } catch (Exception e) {
                    errors.add(String.format("error.shipping_box.rule_parse_error|%s|%s",
                            source, e.getMessage()));
                }
                ruleIndex++;
            }
        } else {
            errors.add(String.format("error.shipping_box.missing_rules_array|%s", source));
        }
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ExchangeRecipeManager());
    }
}
