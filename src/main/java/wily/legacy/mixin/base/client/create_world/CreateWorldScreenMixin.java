package wily.legacy.mixin.base.client.create_world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wily.factoryapi.base.Bearer;
import wily.factoryapi.base.client.UIAccessor;
import wily.legacy.Legacy4JClient;
import wily.legacy.client.*;
import wily.legacy.client.screen.*;
import wily.legacy.util.client.LegacyRenderUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen implements ControlTooltip.Event{
    @Shadow @Final private WorldCreationUiState uiState;
    @Shadow @Final private static Component GAME_MODEL_LABEL;
    @Shadow @Final private static Component NAME_LABEL;


    @Shadow public abstract void popScreen();

    @Shadow protected abstract void onCreate();

    protected Bearer<Boolean> trustPlayers = Bearer.of(true);
    protected Panel panel;
    protected PublishScreen publishScreen;
    protected PackAlbum.Selector resourceAssortSelector;

    protected CreateWorldScreenMixin(Component component) {
        super(component);
    }
    private CreateWorldScreen self(){
        return (CreateWorldScreen) (Object) this;
    }

    @Inject(method = "<init>",at = @At("RETURN"))
    public void initReturn(Minecraft minecraft, Screen screen, WorldCreationContext worldCreationContext, Optional optional, OptionalLong optionalLong,/*? if >=1.21.2 {*/ CreateWorldCallback createWorldCallback, /*?}*/ CallbackInfo ci){
        uiState.setDifficulty(LegacyOptions.createWorldDifficulty.get());
        panel = Panel.createPanel(this, p-> (width - (p.width + (LegacyRenderUtil.hasTooltipBoxes(UIAccessor.of(this)) ? 160 : 0))) / 2, p-> (height - p.height) / 2, 245, 228);
        resourceAssortSelector = PackAlbum.Selector.resources(panel.x + 13, panel.y + 106, 220,45, !LegacyRenderUtil.hasTooltipBoxes());
        publishScreen = new PublishScreen(this, uiState.getGameMode().gameType);
    }

    @Override
    public void added() {
        super.added();
        OptionsScreen.setupSelectorControlTooltips(ControlTooltip.Renderer.of(this), this);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    public void init(CallbackInfo ci) {
        ci.cancel();
        panel.init();
        addRenderableOnly(panel);
        EditBox nameEdit = new EditBox(font, panel.x + 13, panel.y + 25,220, 20, Component.translatable("selectWorld.enterName"));
        nameEdit.setValue(uiState.getName());
        nameEdit.setResponder(uiState::setName);
        uiState.addListener(worldCreationUiState -> nameEdit.setTooltip(Tooltip.create(Component.translatable("selectWorld.targetFolder", Component.literal(worldCreationUiState.getTargetFolder()).withStyle(ChatFormatting.ITALIC)))));
        setInitialFocus(nameEdit);
        addRenderableWidget(nameEdit);
        LegacySliderButton<WorldCreationUiState.SelectedGameMode> gameModeButton = addRenderableWidget(new LegacySliderButton<>(panel.x + 13, panel.y + 51, 220,16, b -> b.getDefaultMessage(GAME_MODEL_LABEL,b.getObjectValue().displayName),b->Tooltip.create(uiState.getGameMode().getInfo()),uiState.getGameMode(),()-> List.of(WorldCreationUiState.SelectedGameMode.SURVIVAL, WorldCreationUiState.SelectedGameMode.HARDCORE, WorldCreationUiState.SelectedGameMode.CREATIVE), b->uiState.setGameMode(b.getObjectValue())));
        uiState.addListener(worldCreationUiState -> gameModeButton.active = !worldCreationUiState.isDebug());
        LegacySliderButton<Difficulty> difficultyButton = addRenderableWidget(new LegacySliderButton<>(panel.x + 13, panel.y + 77, 220,16, b -> b.getDefaultMessage(Component.translatable("options.difficulty"),b.getObjectValue().getDisplayName()),b->Tooltip.create(uiState.getDifficulty().getInfo()),uiState.getDifficulty(),()-> Arrays.asList(Difficulty.values()), b->uiState.setDifficulty(b.getObjectValue())));
        uiState.addListener(worldCreationUiState -> {
            difficultyButton.setObjectValue(uiState.getDifficulty());
            difficultyButton.active = !uiState.isHardcore();
        });

        addRenderableWidget(Button.builder(Component.translatable( "createWorld.tab.more.title"), button -> minecraft.setScreen(new WorldMoreOptionsScreen(self(), trustPlayers))).bounds(panel.x + 13, panel.y + 172,220,20).build());
        addRenderableWidget(Button.builder(Component.translatable("selectWorld.create"), button -> this.onCreate()).bounds(panel.x + 13, panel.y + 197,220,20).build());
        addRenderableWidget(new TickBox(panel.x+ 14, panel.y+155,220,publishScreen.publish, b-> PublishScreen.PUBLISH, b->null, button -> {
            if (button.selected) minecraft.setScreen(publishScreen);
            button.selected = publishScreen.publish = false;
        }));
        resourceAssortSelector.setX(panel.x + 13);
        resourceAssortSelector.setY(panel.y + 106);
        addRenderableWidget(resourceAssortSelector);
        this.uiState.onChanged();
    }

    @Inject(method = "repositionElements", at = @At("HEAD"), cancellable = true)
    public void repositionElements(CallbackInfo ci) {
        rebuildWidgets();
        ci.cancel();
    }

    @Inject(method = /*? if >=1.21.2 {*/ "createWorldAndCleanup"/*?} else {*//*"createNewWorld"*//*?}*/,at = @At("RETURN"))
    private void onCreate(CallbackInfo ci) {
        resourceAssortSelector.applyChanges(true);
        Legacy4JClient.serverPlayerJoinConsumer = s->{
            LegacyClientWorldSettings.of(s.server.getWorldData()).setTrustPlayers(trustPlayers.get());
            s.server.getPlayerList().sendPlayerPermissionLevel(s);
            publishScreen.publish((IntegratedServer) s.server);
            LegacyClientWorldSettings.of(minecraft.getSingleplayerServer().getWorldData()).setSelectedResourceAlbum(resourceAssortSelector.getSelectedAlbum());
        };
    }

    @ModifyExpressionValue(method = "createNewWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;createWorldOpenFlows()Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;"))
    private WorldOpenFlows createNewWorld(WorldOpenFlows original) {
        return LegacyOptions.saveCache.get() ? new WorldOpenFlows(minecraft, LegacySaveCache.currentWorldSource) : original;
    }

    @Inject(method = "createNewWorldDirectory", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelStorageSource;createAccess(Ljava/lang/String;)Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"))
    private /*? >=1.21.2 {*/static/*?}*/ void createNewWorldDirectory(/*? >=1.21.2 {*/Minecraft minecraft, String string, @Nullable Path path, /*?}*/CallbackInfoReturnable<Optional<LevelStorageSource.LevelStorageAccess>> cir) {
        if (!LegacyOptions.saveCache.get()) return;
        try {
            LevelStorageSource.LevelStorageAccess access = LegacySaveCache.currentWorldSource.createAccess(/*? <1.21.2 {*//*uiState.getTargetFolder()*//*?} else {*/string/*?}*/);
            access.close();
            if (Files.exists(access.getDimensionPath(Level.OVERWORLD))) FileUtils.deleteDirectory(access.getDimensionPath(Level.OVERWORLD).toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ModifyExpressionValue(method = "createNewWorldDirectory", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getLevelSource()Lnet/minecraft/world/level/storage/LevelStorageSource;"))
    private /*? if >=1.21.2 {*/static/*?}*/ LevelStorageSource createNewWorldDirectory(LevelStorageSource original) {
        return LegacyOptions.saveCache.get() ? LegacySaveCache.currentWorldSource : original;
    }

    //? if >1.20.1 {
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
    }
    //?}

    @Override
    public boolean mouseScrolled(double d, double e/*? if >1.20.1 {*/, double f/*?}*/, double g) {
        if (resourceAssortSelector.scrollableRenderer.mouseScrolled(g)) return true;
        return super.mouseScrolled(d, e/*? if >1.20.1 {*/, f/*?}*/, g);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void render(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        ci.cancel();
        LegacyRenderUtil.renderDefaultBackground(UIAccessor.of(this), guiGraphics, false);
        resourceAssortSelector.renderTooltipBox(guiGraphics,panel);
        super.render(guiGraphics, i, j, f);
        guiGraphics.drawString(font,NAME_LABEL, panel.x + 14, panel.y + 15, CommonColor.INVENTORY_GRAY_TEXT.get(),false);
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        return super.keyPressed(i,j,k);
    }

    @Override
    public void onClose() {
        LegacyRenderUtil.playBackSound();
        popScreen();
    }
}
