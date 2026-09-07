package com.sideplanetary.overseerPlugin.listener

import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import com.enderstorage.sentinel.dto.AgentConfiguration
import com.sideplanetary.overseerPlugin.collector.BoundedTelemetryQueue
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.player.*
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.potion.PotionEffectType
import java.util.UUID

class BlockBreakListener(private val queue: BoundedTelemetryQueue,private val instanceId: String,private val config: () -> AgentConfiguration) : Listener {
    private data class Damage(val world: UUID,val x: Int,val y: Int,val z: Int,val started: Long,val fingerprint: String,var reliable: Boolean)
    private val damage=HashMap<UUID,Damage>()
    private val faces=arrayOf(intArrayOf(1,0,0),intArrayOf(-1,0,0),intArrayOf(0,1,0),intArrayOf(0,-1,0),intArrayOf(0,0,1),intArrayOf(0,0,-1))
    private fun submerged(player: Player) = player.eyeLocation.block.type in setOf(Material.WATER,Material.BUBBLE_COLUMN)
    private fun tracked(block: Block)=config().debugSampling || block.type.name.endsWith("_ORE") || block.type in setOf(Material.ANCIENT_DEBRIS,Material.OBSIDIAN,Material.CRYING_OBSIDIAN,Material.ANVIL,Material.REINFORCED_DEEPSLATE)
    private fun fingerprint(player: Player): String {
        val item=player.inventory.itemInMainHand
        return listOf(item.type.name,item.getEnchantmentLevel(Enchantment.EFFICIENCY),
            player.getPotionEffect(PotionEffectType.HASTE)?.amplifier,player.getPotionEffect(PotionEffectType.MINING_FATIGUE)?.amplifier,
            submerged(player),player.isOnGround,player.inventory.helmet?.getEnchantmentLevel(Enchantment.AQUA_AFFINITY)).joinToString(":")
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    fun start(event: BlockDamageEvent) {
        if(!config().enabled || event.player.gameMode != GameMode.SURVIVAL || !tracked(event.block)) return
        val b=event.block
        damage[event.player.uniqueId]=Damage(b.world.uid,b.x,b.y,b.z,System.nanoTime(),fingerprint(event.player),!event.instaBreak)
    }
    @EventHandler fun abort(event: BlockDamageAbortEvent) { damage.remove(event.player.uniqueId) }
    @EventHandler fun quit(event: PlayerQuitEvent) { damage.remove(event.player.uniqueId) }
    @EventHandler fun held(event: PlayerItemHeldEvent) { damage[event.player.uniqueId]?.reliable=false }
    @EventHandler fun potion(event: EntityPotionEffectEvent) { damage[event.entity.uniqueId]?.reliable=false }
    @EventHandler fun moved(event: PlayerMoveEvent) {
        damage[event.player.uniqueId]?.let { if(it.fingerprint != fingerprint(event.player)) it.reliable=false }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    fun broke(event: BlockBreakEvent) {
        val p=event.player
        val start=damage.remove(p.uniqueId)
        val b=event.block
        if(!config().enabled || p.gameMode != GameMode.SURVIVAL || !tracked(b)) return
        var mask=0
        var known=true
        faces.forEachIndexed { index,offset ->
            val x=b.x+offset[0]; val y=b.y+offset[1]; val z=b.z+offset[2]
            // Never load a neighbor chunk to inspect an edge block.
            if(y<b.world.minHeight || y>=b.world.maxHeight || !b.world.isChunkLoaded(x shr 4,z shr 4)) known=false
            else {
                val adjacent=b.world.getBlockAt(x,y,z)
                if(!adjacent.type.isOccluding || adjacent.isLiquid) mask=mask or (1 shl index)
            }
        }
        val same=start != null && start.world==b.world.uid && start.x==b.x && start.y==b.y && start.z==b.z
        val reliable=same && start!!.reliable && start.fingerprint==fingerprint(p)
        val item=p.inventory.itemInMainHand
        val haste=(p.getPotionEffect(PotionEffectType.HASTE)?.amplifier ?: -1)+1
        val fatigue=(p.getPotionEffect(PotionEffectType.MINING_FATIGUE)?.amplifier ?: -1)+1
        queue.offer(BlockBreakTelemetry(
            playerId=p.uniqueId,playerName=p.name,timestampMs=System.currentTimeMillis(),world=b.world.name,
            x=b.x,y=b.y,z=b.z,blockType=b.type.name,toolUsed=item.type.name,
            toolEfficiencyLevel=item.getEnchantmentLevel(Enchantment.EFFICIENCY),hasHaste=haste>0,hasMiningFatigue=fatigue>0,
            isExposedToAirOrCave=mask!=0,breakDeltaMs=if(reliable) ((System.nanoTime()-start!!.started)/1000000).coerceIn(0,3600000).toInt() else null,
            instanceId=instanceId,hasteLevel=haste,fatigueLevel=fatigue,underwater=submerged(p),onGround=p.isOnGround,
            aquaAffinity=(p.inventory.helmet?.getEnchantmentLevel(Enchantment.AQUA_AFFINITY) ?: 0)>0,
            canHarvest=b.isPreferredTool(item),gameMode=p.gameMode.name,exposureMask=mask,topologyKnown=known,timingReliable=reliable,
            nativeBreakSpeed=b.getBreakSpeed(p).toDouble()
        ))
    }
}
