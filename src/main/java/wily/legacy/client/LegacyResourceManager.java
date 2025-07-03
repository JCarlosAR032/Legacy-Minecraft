package wily.legacy.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import wily.factoryapi.FactoryAPI;
import wily.factoryapi.FactoryAPIPlatform;
import wily.legacy.Legacy4J;
import wily.legacy.Legacy4JClient;
import wily.legacy.client.controller.ControllerBinding;
import wily.legacy.client.controller.ControllerManager;
import wily.legacy.client.screen.ControlTooltip;
import wily.legacy.client.screen.KeyboardScreen;
import wily.legacy.util.JsonUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class LegacyResourceManager implements ResourceManagerReloadListener {
    public static final ResourceLocation GAMEPAD_MAPPINGS = Legacy4J.createModLocation("gamepad_mappings.txt");
    public static final ResourceLocation INTRO_LOCATION = Legacy4J.createModLocation("intro.json");
    public static final ResourceLocation GAMMA_LOCATION = Legacy4J.createModLocation(/*? if >=1.21.2 {*/"gamma" /*?} else {*//*"post_effect/gamma.json"*//*?}*/);
    public static final ResourceLocation DEFAULT_KEYBOARD_LAYOUT_LOCATION = Legacy4J.createModLocation("keyboard_layout/en_us.json");
    public static final ResourceLocation PLAYER_IDENTIFIERS_LOCATION = Legacy4J.createModLocation("player_identifiers.json");

    public static final String CONTROL_TYPES = "control_types.json";
    public static final String COMMON_COLORS = "common_colors.json";
    public static final String COMMON_VALUES = "common_values.json";
    public static final String DEFAULT_KBM_ICONS = "control_tooltips/icons/kbm.json";
    public static final String DEFAULT_CONTROLLER_ICONS = "control_tooltips/icons/controller.json";

    public static LegacyIntro intro = LegacyIntro.EMPTY;

    public static final List<KeyboardScreen.CharButtonBuilder> keyboardButtonBuilders = new ArrayList<>();
    public static ControllerBinding<?> shiftBinding;
    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Minecraft minecraft = Minecraft.getInstance();

        resourceManager.getResource(GAMEPAD_MAPPINGS).ifPresent(r->{
            try {
                ControllerManager.getHandler().applyGamePadMappingsFromBuffer(r.openAsReader());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        //? if <1.21.2 {
        /*if (Legacy4JClient.gammaEffect != null) {
            Legacy4JClient.gammaEffect.close();
        }
        try {
            Legacy4JClient.gammaEffect = new PostChain(minecraft.getTextureManager(), resourceManager, minecraft.getMainRenderTarget(), GAMMA_LOCATION);
            Legacy4JClient.gammaEffect.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        } catch (IOException iOException) {
            Legacy4J.LOGGER.warn("Failed to load gamma: {}", GAMMA_LOCATION, iOException);
        } catch (JsonSyntaxException jsonSyntaxException) {
            Legacy4J.LOGGER.warn("Failed to parse shader: {}", GAMMA_LOCATION, jsonSyntaxException);
        }
        *///?}

        PlayerIdentifier.list.clear();
        resourceManager.getResourceStack(PLAYER_IDENTIFIERS_LOCATION).forEach(r->{
            try {
                GsonHelper.parseArray(r.openAsReader()).forEach(e-> PlayerIdentifier.CODEC.parse(JsonOps.INSTANCE, e).result().ifPresent(p-> PlayerIdentifier.list.put(p.index(),p)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        ControlType.types.clear();
        CommonValue.COMMON_VALUES.forEach((s,c)->c.reset());
        CommonColor.COMMON_COLORS.forEach((s,c)->c.reset());
        resourceManager.getResource(DEFAULT_KEYBOARD_LAYOUT_LOCATION).ifPresent(LegacyResourceManager::setKeyboardLayout);
        JsonUtil.getOrderedNamespaces(resourceManager).forEach(name->{
            resourceManager.getResource(FactoryAPI.createLocation(name, CONTROL_TYPES)).ifPresent(r->{
                try {
                    GsonHelper.parseArray(r.openAsReader()).forEach(e-> {
                        ResourceLocation id;
                        ControlType type;
                        if (e instanceof JsonPrimitive p) {
                            id = FactoryAPI.createLocation(p.getAsString());
                            if (ControlType.types.containsKey(id.toString()) && ControlType.defaultTypes.contains(ControlType.types.get(id.toString()))) return;
                            for (ControlType defaultType : ControlType.defaultTypes) {
                                if (defaultType.getId().equals(id)){
                                    ControlType.types.put(id.toString(), defaultType);
                                    return;
                                }
                            }
                            type = ControlType.create(id,null,false);
                        } else {
                            JsonObject o = e.getAsJsonObject();
                            id = FactoryAPI.createLocation(GsonHelper.getAsString(o,"id"));
                            type = ControlType.create(id,JsonUtil.getJsonStringOrNull(o,"displayName", Component::translatable),GsonHelper.getAsBoolean(o,"isKbm",false));
                        }
                        ControlType.types.put(id.toString(), type);
                    });
                } catch (IOException e) {
                    Legacy4J.LOGGER.warn(e.getMessage());
                }
            });
            resourceManager.getResource(FactoryAPI.createLocation(name, COMMON_COLORS)).ifPresent(r-> {
                try {
                    JsonObject obj = GsonHelper.parse(r.openAsReader());
                    obj.asMap().forEach((s,e)-> {
                        ResourceLocation id = FactoryAPI.createLocation(s);
                        if (CommonColor.COMMON_COLORS.containsKey(id)) CommonColor.COMMON_COLORS.get(id).parse(new Dynamic<>(JsonOps.INSTANCE,e));
                    });
                } catch (IOException e) {
                    Legacy4J.LOGGER.warn(e.getMessage());
                }
            });
            resourceManager.getResource(FactoryAPI.createLocation(name, COMMON_VALUES)).ifPresent(r-> {
                try {
                    JsonObject obj = GsonHelper.parse(r.openAsReader());
                    obj.asMap().forEach((s,e)-> {
                        ResourceLocation id = FactoryAPI.createLocation(s);
                        if (CommonColor.COMMON_VALUES.containsKey(id)) CommonColor.COMMON_VALUES.get(id).parse(new Dynamic<>(JsonOps.INSTANCE,e));
                    });
                } catch (IOException e) {
                    Legacy4J.LOGGER.warn(e.getMessage());
                }
            });
            addKbmIcons(resourceManager, FactoryAPI.createLocation(name,DEFAULT_KBM_ICONS),(s,b)->{
                for (ControlType value : ControlType.types.values()) if (value.isKbm()) value.getIcons().put(s, b);
            });
            addControllerIcons(resourceManager, FactoryAPI.createLocation(name, DEFAULT_CONTROLLER_ICONS),(s, b)->{
                for (ControlType value : ControlType.types.values()) if (!value.isKbm()) value.getIcons().put(s, b);
            });
            for (ControlType value : ControlType.types.values()) {
                ResourceLocation location = FactoryAPI.createLocation(value.getId().getNamespace(),"control_tooltips/icons/%s.json".formatted(value.getId().getPath()));
                if (value.isKbm()) addKbmIcons(resourceManager,location,value.getIcons()::put);
                else addControllerIcons(resourceManager, location, value.getIcons()::put);
            }

            String langKey = minecraft.getLanguageManager().getLanguage(minecraft.getLanguageManager().getSelected()) != null ? minecraft.getLanguageManager().getSelected() : "en_us";
            resourceManager.getResource(FactoryAPI.createLocation(name,"keyboard_layout/%s.json".formatted(langKey))).ifPresent(LegacyResourceManager::setKeyboardLayout);
        });
    }

    public static void setKeyboardLayout(Resource resource){
        try {
            JsonObject obj = GsonHelper.parse(resource.openAsReader());
            keyboardButtonBuilders.clear();
            shiftBinding = obj.has("shiftBinding") ? ControllerBinding.map.get(obj.get("shiftBinding").getAsString()) : ControllerBinding.LEFT_STICK_BUTTON;
            obj.getAsJsonArray("layout").forEach(e->{
                if (e instanceof JsonObject o){
                    keyboardButtonBuilders.add(new KeyboardScreen.CharButtonBuilder(GsonHelper.getAsInt(o,"width",25),GsonHelper.getAsString(o,"chars"),GsonHelper.getAsString(o,"shiftChars",null),JsonUtil.getJsonStringOrNull(o,"binding", ControllerBinding.map::get),JsonUtil.getJsonStringOrNull(o,"icon",FactoryAPI::createLocation),JsonUtil.getJsonStringOrNull(o,"soundEvent",s-> FactoryAPIPlatform.getRegistryValue(FactoryAPI.createLocation(s),BuiltInRegistries.SOUND_EVENT))));
                }else if (e instanceof JsonPrimitive p) keyboardButtonBuilders.add(new KeyboardScreen.CharButtonBuilder(25,p.getAsString(),null,null,null,null));
            });
        } catch (IOException e) {
            Legacy4J.LOGGER.warn(e.getMessage());
        }
    }

    public static void addIcons(ResourceManager resourceManager, ResourceLocation location, BiConsumer<String,JsonObject> addIcon){
        resourceManager.getResource(location).ifPresent(r->{
            try {
                GsonHelper.parse(r.openAsReader()).asMap().forEach((s,o)-> addIcon.accept(s,o.getAsJsonObject()));
            } catch (IOException e) {
                Legacy4J.LOGGER.warn(e.getMessage());
            }
        });
    }

    public static void addControllerIcons(ResourceManager resourceManager, ResourceLocation location, BiConsumer<String, ControlTooltip.LegacyIcon> addIcon){
        addIcons(resourceManager,location,(s,o)->{
            ControllerBinding<?> binding = ControllerBinding.map.get(s);
            if (binding != null) addIcon.accept(s, ControlTooltip.LegacyIcon.create(()->binding.getMapped().state().pressed, JsonUtil.getJsonStringOrNull(o,"icon",String::toCharArray),JsonUtil.getJsonStringOrNull(o,"iconOverlay",String::toCharArray),JsonUtil.getJsonStringOrNull(o,"tipIcon", v-> v.charAt(0)),()-> !binding.getMapped().state().isBlocked(), ControlType::getActiveControllerType));
        });
    }

    public static void addKbmIcons(ResourceManager resourceManager, ResourceLocation location, BiConsumer<String, ControlTooltip.LegacyIcon> addIcon){
        addIcons(resourceManager,location,(s,o)->{
            InputConstants.Key key = InputConstants.getKey(s);
            ControlTooltip.LegacyIcon icon = ControlTooltip.LegacyIcon.create(key, JsonUtil.getJsonStringOrNull(o,"icon",String::toCharArray),JsonUtil.getJsonStringOrNull(o,"iconOverlay",String::toCharArray),JsonUtil.getJsonStringOrNull(o,"tipIcon", v-> v.charAt(0)));
            addIcon.accept(key.getName(), icon);
        });
    }

    public static void loadIntroLocations(ResourceManager resourceManager){
        try {
            LegacyIntro.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(resourceManager.openAsReader(INTRO_LOCATION))).result().ifPresent(i-> intro = i);
        } catch (IOException e) {
            Legacy4J.LOGGER.error(e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "legacy:resource_manager";
    }
}
