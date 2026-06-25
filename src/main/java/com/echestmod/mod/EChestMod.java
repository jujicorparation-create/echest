package com.echestmod.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.List;

@Mod(modid = EChestMod.MODID, name = EChestMod.NAME, version = EChestMod.VERSION, clientSideOnly = true)
public class EChestMod {

    public static final String MODID   = "echestmod";
    public static final String NAME    = "EChest Auto Deposit";
    public static final String VERSION = "1.0.0";

    private static final int INTERVAL     = 6000;
    private static final int MAX_PER_SLOT = 32;

    private static final List<Item> VALUABLES = Arrays.asList(
        Item.getItemFromBlock(Blocks.DIAMOND_BLOCK),
        Item.getItemFromBlock(Blocks.IRON_BLOCK),
        Item.getItemFromBlock(Blocks.EMERALD_BLOCK),
        Item.getItemFromBlock(Blocks.GOLD_BLOCK)
    );

    private int     tickCount     = 0;
    private boolean waitingEchest = false;
    private int     waitTick      = 0;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        if (waitingEchest) {
            waitTick++;
            if (waitTick > 60) { waitingEchest = false; waitTick = 0; }
            return;
        }

        tickCount++;
        if (tickCount >= INTERVAL) {
            tickCount = 0;
            if (hasValuables(mc)) {
                mc.player.sendChatMessage("/team echest");
                waitingEchest = true;
                waitTick = 0;
            }
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onGuiOpen(GuiOpenEvent event) {
        if (!waitingEchest) return;
        if (!(event.getGui() instanceof GuiChest)) return;

        GuiChest gui = (GuiChest) event.getGui();
        ContainerChest container = (ContainerChest) gui.inventorySlots;
        IInventory lower = container.getLowerChestInventory();

        if (lower.getSizeInventory() != 27) return;

        waitingEchest = false;
        waitTick = 0;

        Minecraft mc = Minecraft.getMinecraft();

        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ex) { return; }
            for (int inv = 0; inv < 36; inv++) {
                ItemStack stack = mc.player.inventory.getStackInSlot(inv);
                if (stack.isEmpty() || !VALUABLES.contains(stack.getItem())) continue;
                deposit(mc, container, lower, inv, stack);
            }
        }).start();
    }

    private void deposit(Minecraft mc, ContainerChest container, IInventory lower,
                         int playerSlot, ItemStack original) {
        Item item = original.getItem();
        int remaining = original.getCount();

        for (int es = 0; es < 27 && remaining > 0; es++) {
            ItemStack es_stack = lower.getStackInSlot(es);
            if (es_stack.isEmpty() || es_stack.getItem() != item) continue;
            int cur = es_stack.getCount();
            if (cur >= MAX_PER_SLOT) continue;
            int toAdd = Math.min(MAX_PER_SLOT - cur, remaining);
            remaining = doClick(mc, container, lower, es, playerSlot, item, remaining, toAdd);
        }

        for (int es = 0; es < 27 && remaining > 0; es++) {
            if (!lower.getStackInSlot(es).isEmpty()) continue;
            int toAdd = Math.min(MAX_PER_SLOT, remaining);
            remaining = doClick(mc, container, lower, es, playerSlot, item, remaining, toAdd);
        }
    }

    private int doClick(Minecraft mc, ContainerChest container, IInventory lower,
                        int echestSlot, int playerSlot, Item item,
                        int remaining, int toAdd) {
        ItemStack curEchest = lower.getStackInSlot(echestSlot);
        int newCount = (curEchest.isEmpty() ? 0 : curEchest.getCount()) + toAdd;
        lower.setInventorySlotContents(echestSlot, new ItemStack(item, newCount));

        int left = remaining - toAdd;
        mc.player.inventory.setInventorySlotContents(playerSlot,
            left <= 0 ? ItemStack.EMPTY : new ItemStack(item, left));

        Slot slot = container.inventorySlots.get(echestSlot);
        mc.playerController.windowClick(container.windowId, slot.slotNumber, 0,
            net.minecraft.inventory.ClickType.PICKUP, mc.player);

        return left;
    }

    private boolean hasValuables(Minecraft mc) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.inventory.getStackInSlot(i);
            if (!s.isEmpty() && VALUABLES.contains(s.getItem())) return true;
        }
        return false;
    }
}
