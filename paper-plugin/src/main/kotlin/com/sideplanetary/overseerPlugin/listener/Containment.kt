package com.sideplanetary.overseerPlugin.listener

import org.bukkit.command.*
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.*
import org.bukkit.event.entity.EntityDamageByEntityEvent
import java.util.UUID

class Containment : Listener,CommandExecutor {
    private val frozen=HashMap<UUID,Long>()
    private fun frozen(player: Player): Boolean {
        val expires=frozen[player.uniqueId] ?: return false
        if(expires<System.currentTimeMillis()) { frozen.remove(player.uniqueId); return false }
        return true
    }
    override fun onCommand(sender: CommandSender,command: Command,label: String,args: Array<out String>): Boolean {
        if(sender !is ConsoleCommandSender && sender !is RemoteConsoleCommandSender) { sender.sendMessage("Console access required."); return true }
        if(args.size!=2 || args[0] !in listOf("freeze","unfreeze")) { sender.sendMessage("Usage: caenis <freeze|unfreeze> <uuid>"); return true }
        val id=runCatching { UUID.fromString(args[1]) }.getOrNull() ?: run { sender.sendMessage("Invalid UUID."); return true }
        frozen.entries.removeIf { it.value < System.currentTimeMillis() }
        if(args[0]=="freeze") frozen[id]=System.currentTimeMillis()+300000 else frozen.remove(id)
        sender.sendMessage(if(args[0]=="freeze") "Player frozen for at most five minutes." else "Player released.")
        return true
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun move(e: PlayerMoveEvent) { if(frozen(e.player) && (e.from.x!=e.to?.x || e.from.y!=e.to?.y || e.from.z!=e.to?.z)) e.isCancelled=true }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun breakBlock(e: BlockBreakEvent) { if(frozen(e.player)) e.isCancelled=true }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun place(e: BlockPlaceEvent) { if(frozen(e.player)) e.isCancelled=true }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun interact(e: PlayerInteractEvent) { if(frozen(e.player)) e.isCancelled=true }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun drop(e: PlayerDropItemEvent) { if(frozen(e.player)) e.isCancelled=true }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun damage(e: EntityDamageByEntityEvent) { if((e.damager as? Player)?.let(::frozen)==true) e.isCancelled=true }
}
