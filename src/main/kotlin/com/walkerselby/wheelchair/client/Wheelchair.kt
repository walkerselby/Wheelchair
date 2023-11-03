package com.walkerselby.wheelchair.client

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text


class Wheelchair : ClientModInitializer {
    override fun onInitializeClient() {

        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, registryAccess ->
            dispatcher.register(
                literal("wheelchair")
                    .then(
                        literal("packet")
                            .executes {
                                val packet = ClickSlotC2SPacket(
                                    0,
                                    1,
                                    2,
                                    3,
                                    SlotActionType.SWAP,
                                    ItemStack(Items.IRON_HOE),
                                    Int2ObjectMaps.emptyMap()
                                )
                                try {
                                    it.source.sendFeedback(Formatter.prettyPrintPacket(packet))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    it.source.sendFeedback(Text.literal("Excepted"))
                                }
                                return@executes 0
                            }
                    )
            )
        })

    }
}