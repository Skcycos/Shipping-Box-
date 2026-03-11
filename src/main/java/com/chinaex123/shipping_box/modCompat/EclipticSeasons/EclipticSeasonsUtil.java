package com.chinaex123.shipping_box.modCompat.EclipticSeasons;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class EclipticSeasonsUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(EclipticSeasonsUtil.class);

    private static boolean modLoaded = false;

    // EclipticUtil 相关反射
    private static Class<?> eclipticUtilClass = null;
    private static Method getSolarTermMethod = null;
    private static Method getSeasonMethod = null;
    private static Method getSerializedNameMethod = null;
    private static Class<?> solarTermEnumClass = null;

    // CommonConfig 相关反射
    private static Field lastingDaysOfEachTermField = null;

    // 缓存对象，避免重复反射调用
    private static Object cachedSolarTerm = null;
    private static Level lastCheckedLevel = null;
    private static String cachedSeasonName = null;
    private static Integer cachedSeasonIndex = null;
    private static Integer cachedTermDuration = null;

    static {
        try {
            // 尝试加载节气模组的核心类
            eclipticUtilClass = Class.forName("com.teamtea.eclipticseasons.api.util.EclipticUtil");

            // 获取 getNowSolarTerm 方法 - 这是静态方法，不需要 INSTANCE
            Class<?> levelClass = Class.forName("net.minecraft.world.level.Level");
            getSolarTermMethod = eclipticUtilClass.getMethod("getNowSolarTerm", levelClass);
            getSolarTermMethod.setAccessible(true);

            // 获取 SolarTerm 枚举类
            solarTermEnumClass = Class.forName("com.teamtea.eclipticseasons.api.constant.solar.SolarTerm");

            // 获取 getSeason() 方法
            getSeasonMethod = solarTermEnumClass.getMethod("getSeason");
            getSeasonMethod.setAccessible(true);

            // 获取 Season 类的 getSerializedName() 方法
            Class<?> seasonClass = Class.forName("com.teamtea.eclipticseasons.api.constant.solar.Season");

            getSerializedNameMethod = seasonClass.getMethod("getSerializedName");
            getSerializedNameMethod.setAccessible(true);

            // 尝试加载配置类
            try {
                Class<?> commonConfigClass = Class.forName("com.teamtea.eclipticseasons.config.CommonConfig");

                // Season 是静态内部类
                Class<?> seasonClassConfig = Class.forName("com.teamtea.eclipticseasons.config.CommonConfig$Season");

                // 获取 lastingDaysOfEachTerm 字段（这是 ModConfigSpec.IntValue 类型）
                lastingDaysOfEachTermField = seasonClassConfig.getField("lastingDaysOfEachTerm");
                lastingDaysOfEachTermField.setAccessible(true);
            } catch (Exception configError) {
                // 配置类加载失败，但核心功能仍可用
            }

            modLoaded = true;

        } catch (Exception e) {
            modLoaded = false;
            LOGGER.error("[Shipping Box]加载节气模组联动失败: {}", e.getMessage());
        }
    }

    /**
     * 检查节气模组是否已加载
     */
    public static boolean isAvailable() {
        return modLoaded;
    }

    /**
     * 获取当前世界的节气对象
     * @param level 世界实例
     * @return 节气对象，如果无法获取则返回 null
     */
    @Nullable
    private static Object getSolarTerm(Level level) {
        if (!isAvailable() || level == null || getSolarTermMethod == null) {
            return null;
        }

        // 如果缓存有效，直接返回
        if (cachedSolarTerm != null && level == lastCheckedLevel) {
            return cachedSolarTerm;
        }

        try {
            // 调用静态方法 EclipticUtil.getNowSolarTerm(level)
            cachedSolarTerm = getSolarTermMethod.invoke(null, level);
            lastCheckedLevel = level;
            return cachedSolarTerm;
        } catch (Exception e) {
            cachedSolarTerm = null;
            return null;
        }
    }

    /**
     * 获取当前世界的季节（带缓存）
     * @param level 世界实例
     * @return 季节名称（spring/summer/autumn/winter），如果无法获取则返回 null
     */
    @Nullable
    public static String getSeason(Level level) {
        // 如果缓存有效，直接返回
        if (cachedSeasonName != null && level == lastCheckedLevel) {
            return cachedSeasonName;
        }

        Object solarTermObj = getSolarTerm(level);

        if (solarTermObj != null && getSeasonMethod != null) {
            try {
                // 调用 solarTerm.getSeason()
                Object seasonObj = getSeasonMethod.invoke(solarTermObj);

                if (seasonObj != null && getSerializedNameMethod != null) {
                    // 调用 season.getSerializedName()
                    String seasonName = (String) getSerializedNameMethod.invoke(seasonObj);

                    if (seasonName != null && !seasonName.equals("none")) {
                        cachedSeasonName = seasonName.toLowerCase();
                        return cachedSeasonName;
                    }
                }
            } catch (Exception e) {
                // 反射调用失败，返回 null
            }
        }

        cachedSeasonName = null;
        return null;
    }

    /**
     * 获取当前世界的子季节索引 (0-5)（带缓存）
     * 基于节气在 6 个节气中的位置计算
     * @param level 世界实例
     * @return 子季节索引 (0-5)，如果无法获取则返回 -1
     */
    public static int getSubSeasonIndex(Level level) {
        // 如果缓存有效，直接返回
        if (cachedSeasonIndex != null && level == lastCheckedLevel) {
            return cachedSeasonIndex;
        }

        Object solarTermObj = getSolarTerm(level);

        if (solarTermObj != null && solarTermEnumClass != null) {
            try {
                // 获取节气的 ordinal 值
                Method ordinalMethod = solarTermEnumClass.getMethod("ordinal");
                ordinalMethod.setAccessible(true);
                int ordinal = (Integer) ordinalMethod.invoke(solarTermObj);

                // 每 6 个节气为一个季节，每 2 个节气为一个子季节
                // 所以子季节索引 = (ordinal % 6) / 2
                cachedSeasonIndex = (ordinal % 6) / 2;
                return cachedSeasonIndex;
            } catch (Exception e) {
                // 反射调用失败，返回 -1
            }
        }

        cachedSeasonIndex = null;
        return -1;
    }

    /**
     * 获取当前世界的子季节名称
     * @param level 世界实例
     * @return 子季节名称（Early/Mid/Late 或类似），如果无法获取则返回 null
     */
    @Nullable
    public static String getSubSeason(Level level) {
        int index = getSubSeasonIndex(level);
        if (index >= 0 && index <= 5) {
            String[] subSeasonNames = {"Early", "Mid", "Late", "Early", "Mid", "Late"};
            return subSeasonNames[index];
        }
        return null;
    }

    /**
     * 获取每个节气的持续天数（从配置读取）（带缓存）
     * @param level 世界实例（未使用，保留用于未来扩展）
     * @return 节气持续天数，默认 7 天，失败返回 -1
     */
    public static int getSolarTermDuration(Level level) {
        // 如果已缓存，直接返回
        if (cachedTermDuration != null) {
            return cachedTermDuration;
        }

        if (!isAvailable() || lastingDaysOfEachTermField == null) {
            return 7; // 默认值
        }

        try {
            // 获取 CommonConfig.Season.lastingDaysOfEachTerm 的值（这是 ModConfigSpec.IntValue）
            Object intValueObj = lastingDaysOfEachTermField.get(null);

            if (intValueObj != null) {
                // 调用 get() 方法获取实际的整数值
                Method getMethod = intValueObj.getClass().getMethod("get");
                getMethod.setAccessible(true);
                Object valueObj = getMethod.invoke(intValueObj);

                if (valueObj instanceof Integer) {
                    cachedTermDuration = (Integer) valueObj;
                    return cachedTermDuration;
                }
            }
        } catch (Exception e) {
            // 获取失败返回默认值
        }

        return 7;
    }

    /**
     * 获取当前季节的总天数
     * @param level 世界实例
     * @return 季节总天数（默认 42 天 = 6 个节气 × 7 天），失败返回 -1
     */
    public static int getSeasonDuration(Level level) {
        int termDuration = getSolarTermDuration(level);
        return termDuration * 6; // 一个季节有 6 个节气
    }

    /**
     * 获取当前季节的第几天
     * @param level 世界实例
     * @return 当前季节的日期（1 到季节总天数），失败返回 -1
     */
    public static int getCurrentSeasonDate(Level level) {
        if (!isAvailable() || level == null) {
            return -1;
        }

        try {
            // 使用缓存的类引用，避免重复的 Class.forName()
            Method getNowSolarDayMethod = eclipticUtilClass.getMethod("getNowSolarDay", Level.class);
            getNowSolarDayMethod.setAccessible(true);

            // 获取 INSTANCE
            Field instanceField = eclipticUtilClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            Object instance = instanceField.get(null);

            int seasonDay = (Integer) getNowSolarDayMethod.invoke(instance, level);

            int termDuration = getSolarTermDuration(level);
            int seasonDuration = termDuration * 6; // 42 天

            // 计算在当前季节的日期
            int seasonDate = (seasonDay % seasonDuration) + 1;

            return Math.max(1, Math.min(seasonDate, seasonDuration));
        } catch (Exception e) {
            // 获取失败返回 -1
        }

        return -1;
    }

    /**
     * 检查指定季节列表是否包含当前季节
     * @param level 世界实例
     * @param targetSeasons 目标季节列表
     * @return 当前季节在目标列表中返回 true，否则返回 false
     */
    public static boolean isInSeasons(Level level, List<String> targetSeasons) {
        String currentSeason = getSeason(level);

        if (currentSeason == null || targetSeasons == null || targetSeasons.isEmpty()) {
            return false;
        }

        // 检查是否包含"all"，如果是则直接返回 true
        if (targetSeasons.contains("all")) {
            return true;
        }

        return targetSeasons.contains(currentSeason);
    }
}
