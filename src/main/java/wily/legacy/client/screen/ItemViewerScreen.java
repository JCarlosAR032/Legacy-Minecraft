package wily.legacy.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import wily.factoryapi.base.Stocker;
import wily.legacy.client.ControlType;
import wily.legacy.client.LegacyTipManager;
import wily.legacy.client.controller.ControllerBinding;
import wily.legacy.inventory.LegacySlotDisplay;
import wily.legacy.util.LegacyComponents;
import wily.legacy.util.client.LegacyRenderUtil;
import wily.legacy.util.client.LegacySoundUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ItemViewerScreen extends PanelBackgroundScreen implements LegacyMenuAccess<AbstractContainerMenu> {
    public static final Container layerSelectionGrid = new SimpleContainer(50);
    public final List<ItemStack> layerItems = new ArrayList<>();
    protected final Stocker.Sizeable scrolledList = new Stocker.Sizeable(0);

    protected final AbstractContainerMenu menu;
    protected Slot hoveredSlot = null;
    protected final LegacyScroller scroller = LegacyScroller.create(135, ()->scrolledList);

    public ItemViewerScreen(Screen parent, Function<Screen, Panel> panelConstructor, Component component) {
        super(parent, panelConstructor, component);
        menu = new AbstractContainerMenu(null, -1) {

            @Override
            public ItemStack quickMoveStack(Player player, int i) {
                return null;
            }

            @Override
            public boolean stillValid(Player player) {
                return false;
            }
        };
        for (int i = 0; i < layerSelectionGrid.getContainerSize(); i++) {
            menu.slots.add(LegacySlotDisplay.override(new Slot(layerSelectionGrid, i, 23 + i % 10 * 27, 24 + i / 10 * 27), new LegacySlotDisplay() {
                public int getWidth() {
                    return 27;
                }

                public int getHeight() {
                    return 27;
                }
            }));
        }
        addLayerItems();
        scrolledList.max = Math.max(0, (layerItems.size() - 1) / layerSelectionGrid.getContainerSize());
    }

    protected void addLayerItems() {
        BuiltInRegistries.ITEM.forEach(i-> {
            if (i != Items.AIR) layerItems.add(i.getDefaultInstance());
        });
    }

    @Override
    public void addControlTooltips(ControlTooltip.Renderer renderer) {
        ControlTooltip.setupDefaultScreen(renderer, this).add(()-> ControlType.getActiveType().isKbm() ? ControlTooltip.getKeyIcon(InputConstants.KEY_W) : ControllerBinding.RIGHT_TRIGGER.getIcon(),()->getHoveredSlot() != null && getHoveredSlot().hasItem() && LegacyTipManager.hasTip(getHoveredSlot().getItem()) ? LegacyComponents.WHATS_THIS : null);
    }


    @Override
    protected void init() {
        panel.init();
        scroller.setPosition(panel.x + 299, panel.y + 23);
        scroller.offset(new Vec3(LegacyRenderUtil.hasHorizontalArtifacts() ? 0.0f : 0.5f, 0, 0));
        fillLayerGrid();
    }

    public void fillLayerGrid() {
        for (int i = 0; i < layerSelectionGrid.getContainerSize(); i++) {
            int index = scrolledList.get() * 50 + i;
            layerSelectionGrid.setItem(i, layerItems.size() > index ? layerItems.get(index) : ItemStack.EMPTY);
        }
    }

    @Override
    public boolean mouseScrolled(double d, double e/*? if >1.20.1 {*/, double f/*?}*/, double g) {
        if (scroller.mouseScrolled(g)) fillLayerGrid();
        return super.mouseScrolled(d, e/*? if >1.20.1 {*/, f/*?}*/, g);
    }

    @Override
    public boolean mouseClicked(double d, double e, int i) {
        if (scroller.mouseClicked(d, e, i)) fillLayerGrid();;
        if (hoveredSlot != null) slotClicked(hoveredSlot);
        return super.mouseClicked(d, e, i);
    }

    @Override
    public boolean mouseReleased(double d, double e, int i) {
        scroller.mouseReleased(d, e, i);
        return super.mouseReleased(d, e, i);
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        if (i == InputConstants.KEY_W && hoveredSlot != null && hoveredSlot.hasItem() && LegacyTipManager.setTip(LegacyTipManager.getTip(hoveredSlot.getItem().copy()))) {
            LegacySoundUtil.playSimpleUISound(SoundEvents.UI_BUTTON_CLICK.value(),1.0f);
            return true;
        }
        return super.keyPressed(i, j, k);
    }

    protected void slotClicked(Slot slot) {
    }

    @Override
    public boolean mouseDragged(double d, double e, int i, double f, double g) {
        if (scroller.mouseDragged(e)) fillLayerGrid();;
        return super.mouseDragged(d, e, i, f, g);
    }

    public void setHoveredSlot(Slot hoveredSlot) {
        this.hoveredSlot = hoveredSlot;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);
        setHoveredSlot(null);
        menu.slots.forEach(s -> {
            LegacyIconHolder holder = LegacyRenderUtil.iconHolderRenderer.slotBoundsWithItem(panel.x, panel.y, s);
            holder.render(guiGraphics, i, j, f);
            if (holder.isHovered) {
                if (s.isHighlightable()) holder.renderHighlight(guiGraphics);
                setHoveredSlot(s);
            }
        });
        if (hoveredSlot != null && !hoveredSlot.getItem().isEmpty())
            guiGraphics.setTooltipForNextFrame(font, hoveredSlot.getItem(), i, j);
    }

    @Override
    public void renderDefaultBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        LegacyRenderUtil.renderDefaultBackground(accessor, guiGraphics, false);
        panel.render(guiGraphics, i, j, f);
        renderScroll(guiGraphics,i,j,f);
    }

    protected void renderScroll(GuiGraphics guiGraphics, int i, int j, float f) {
        scroller.render(guiGraphics, i, j, f);
    }

    @Override
    public AbstractContainerMenu getMenu() {
        return menu;
    }

    @Override
    public ScreenRectangle getMenuRectangle() {
        return new ScreenRectangle(panel.x, panel.y, panel.width, panel.height);
    }

    @Override
    public boolean isOutsideClick(int i) {
        return false;
    }

    @Override
    public Slot getHoveredSlot() {
        return hoveredSlot;
    }

    @Override
    public int getTipXDiff() {
        return 0;
    }
}
