package wily.legacy.mixin.base.client.inventory;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wily.factoryapi.base.client.FactoryGuiGraphics;
import wily.legacy.Legacy4JClient;
import wily.legacy.client.CommonColor;
import wily.legacy.client.screen.CreativeModeScreen;
import wily.legacy.client.screen.LegacyMenuAccess;
import wily.legacy.client.screen.ReplaceableScreen;
import wily.legacy.inventory.LegacySlotDisplay;
import wily.legacy.util.LegacySprites;
import wily.legacy.util.client.LegacyRenderUtil;

import static wily.legacy.util.LegacySprites.SHIELD_SLOT;
import static wily.legacy.util.LegacySprites.SMALL_ARROW;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> implements ReplaceableScreen, LegacyMenuAccess<InventoryMenu> {
    //? if <1.21.2 {
    /*@Shadow @Final private RecipeBookComponent recipeBookComponent;

    @Shadow private boolean widthTooNarrow;

    private ImageButton recipeButton;
    *///?}
    private static final Vec3 EQUIP_SLOT_OFFSET = new Vec3(50,0,0);
    private static final ResourceLocation[] EQUIPMENT_SLOT_SPRITES = new ResourceLocation[]{LegacySprites.HEAD_SLOT,LegacySprites.CHEST_SLOT,LegacySprites.LEGS_SLOT,LegacySprites.FEET_SLOT};

    private boolean canReplace = true;

    public InventoryScreenMixin(InventoryMenu abstractContainerMenu, Inventory inventory, Component component) {
        super(abstractContainerMenu, inventory, component);
    }
    //? if <1.21.5 {
    /*@ModifyExpressionValue(method = "containerTick",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasInfiniteItems()Z"))
    public boolean containerTick(boolean original) {
        return false;
    }
    *///?} else {
    @ModifyExpressionValue(method = "containerTick",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasInfiniteMaterials()Z"))
    public boolean containerTick(boolean original) {
        return false;
    }
    //?}
    //? if >1.20.1 {
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        renderBg(guiGraphics, f, i, j);
    }
    //?} else {
    /*@Shadow @Final private static ResourceLocation RECIPE_BUTTON_LOCATION;
    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
    }
    *///?}
    @Inject(method = "init",at = @At("HEAD"), cancellable = true)
    public void init(CallbackInfo ci) {
        ci.cancel();
        imageWidth = 215;
        imageHeight = 217;
        inventoryLabelX = 14;
        inventoryLabelY = 103;
        super.init();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (i == 0){
                LegacySlotDisplay.override(s,180, 40,new LegacySlotDisplay(){
                    public boolean isVisible() {
                        return LegacyRenderUtil.hasClassicCrafting();
                    }
                });
            } else if (i < 5) {
                LegacySlotDisplay.override(s,111 + s.getContainerSlot() % 2 * 21, 30 + s.getContainerSlot() / 2 * 21,new LegacySlotDisplay(){
                    public boolean isVisible() {
                        return LegacyRenderUtil.hasClassicCrafting();
                    }
                });
            } else if (i < 9) {
                int index = 39 - s.getContainerSlot();
                LegacySlotDisplay.override(s, 14, 14 + index * 21,new LegacySlotDisplay(){
                    public ResourceLocation getIconSprite() {
                        return s.getItem().isEmpty() ? EQUIPMENT_SLOT_SPRITES[index] : null;
                    }
                    public Vec3 getOffset() {
                        return LegacyRenderUtil.hasClassicCrafting() ? Vec3.ZERO : EQUIP_SLOT_OFFSET;
                    }
                });
            } else if (i < menu.slots.size() - 10) {
                LegacySlotDisplay.override(s, 14 + (s.getContainerSlot() - 9) % 9 * 21,116 + (s.getContainerSlot() - 9) / 9 * 21);
            } else if (i < menu.slots.size() - 1) {
                LegacySlotDisplay.override(s, 14 + s.getContainerSlot() * 21,186);
            } else {
                LegacySlotDisplay.override(s,111, 77, new LegacySlotDisplay(){
                    public Vec3 getOffset() {
                        return LegacyRenderUtil.hasClassicCrafting() ? Vec3.ZERO : EQUIP_SLOT_OFFSET;
                    }
                    public ResourceLocation getIconSprite() {
                        return s.getItem().isEmpty() ? SHIELD_SLOT : null;
                    }
                });
            }
        }
        //? <1.21.2 {
        /*this.widthTooNarrow = this.width < 379;
        this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
        if (LegacyOptions.showVanillaRecipeBook.get() && ScreenUtil.hasClassicCrafting()) {
            this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
            recipeButton = this.addRenderableWidget(new ImageButton(this.leftPos + 180, topPos + 71, 20, 18, /^? if >1.20.1 {^/RecipeBookComponent.RECIPE_BUTTON_SPRITES/^?} else {^//^0, 19, RECIPE_BUTTON_LOCATION^//^?}^/, (button) -> {
                this.recipeBookComponent.toggleVisibility();
                this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
                button.setPosition(this.leftPos + 180, topPos + 71);
            }));
            if (recipeBookComponent.isVisible()) recipeButton.setFocused(true);
        } else if (recipeBookComponent.isVisible()) recipeBookComponent.toggleVisibility();
        *///?}
    }

    //? if >=1.21.2 {
    @Inject(method = "getRecipeBookButtonPosition", at = @At("HEAD"), cancellable = true)
    protected void getRecipeBookButtonPosition(CallbackInfoReturnable<ScreenPosition> cir){
        cir.setReturnValue(new ScreenPosition(this.leftPos + 180, topPos + 71));
    }
    //?}

    @Inject(method = "renderBg",at = @At("HEAD"), cancellable = true)
    public void renderBg(GuiGraphics graphics, float f, int i, int j, CallbackInfo ci) {
        ci.cancel();
        FactoryGuiGraphics.of(graphics).blitSprite(LegacySprites.SMALL_PANEL,leftPos,topPos,imageWidth,imageHeight);
        FactoryGuiGraphics.of(graphics).blitSprite(LegacySprites.ENTITY_PANEL,leftPos + 40 + (LegacyRenderUtil.hasClassicCrafting() ? 0 : 50),topPos + 13,63,84);
        Pose pose = minecraft.player.getPose();
        minecraft.player.setPose(Pose.STANDING);
        LegacyRenderUtil.renderEntityInInventoryFollowsMouse(graphics,leftPos + 40 + (LegacyRenderUtil.hasClassicCrafting() ? 0 : 50),topPos + 13,leftPos + 103 + (LegacyRenderUtil.hasClassicCrafting() ? 0 : 50),topPos + 97,35,0.0625f,i,j, minecraft.player);
        minecraft.player.setPose(pose);
        if (LegacyRenderUtil.hasClassicCrafting()) {
            graphics.drawString(this.font, this.title, leftPos + 111, topPos + 16, CommonColor.INVENTORY_GRAY_TEXT.get(), false);
            FactoryGuiGraphics.of(graphics).blitSprite(SMALL_ARROW,leftPos + 158,topPos + 43,16,13);
        }
        //? <1.21.2 {
        /*if (!recipeBookComponent.isVisible() && recipeButton != null && !recipeButton.isHovered()) recipeButton.setFocused(false);
        *///?}
    }
    @Inject(method = "renderLabels",at = @At("HEAD"), cancellable = true)
    public void renderLabels(GuiGraphics guiGraphics, int i, int j, CallbackInfo ci) {
        ci.cancel();
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, CommonColor.INVENTORY_GRAY_TEXT.get(), false);
    }

    public boolean canReplace() {
        return Legacy4JClient.playerHasInfiniteMaterials() && canReplace;
    }

    public void setCanReplace(boolean canReplace) {
        this.canReplace = canReplace;
    }

    public Screen getReplacement() {
        return CreativeModeScreen.getActualCreativeScreenInstance(minecraft);
    }

    @Override
    public int getTipXDiff() {
        return -186;
    }

    @Override
    public boolean allowItemPopping() {
        return true;
    }
}
