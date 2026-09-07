package com.sideplanetary.overseerPlugin

import com.enderstorage.sentinel.dto.Heartbeat
import com.enderstorage.sentinel.dto.PlayerSnapshot
import com.sideplanetary.overseerPlugin.collector.*
import com.sideplanetary.overseerPlugin.config.SentinelConfig
import com.sideplanetary.overseerPlugin.listener.BlockBreakListener
import com.sideplanetary.overseerPlugin.listener.Containment
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class OverseerPlugin : JavaPlugin() {
    private var dispatcher: TelemetryDispatcher?=null
    private var heartbeat: BukkitTask?=null
    override fun onEnable() {
        saveDefaultConfig()
        val settings=try { SentinelConfig.load(config) } catch(error: IllegalArgumentException) {
            logger.severe(error.message); server.pluginManager.disablePlugin(this); return
        }
        val queue=BoundedTelemetryQueue(settings.queueCapacity)
        val worker=TelemetryDispatcher(settings,queue,dataFolder.toPath().resolve("spool"),logger)
        dispatcher=worker
        server.pluginManager.registerEvents(BlockBreakListener(queue,settings.instanceId) { worker.configuration },this)
        val containment=Containment()
        server.pluginManager.registerEvents(containment,this)
        getCommand("caenis")?.setExecutor(containment)
        heartbeat=server.scheduler.runTaskTimer(this,Runnable {
            val runtime=Runtime.getRuntime()
            val worlds=server.worlds
            worker.latestHealth=Heartbeat(
                tps=server.tps.map { it.coerceIn(0.0,20.0) },mspt=server.averageTickTime.coerceAtLeast(0.0),
                heapUsed=runtime.totalMemory()-runtime.freeMemory(),heapCommitted=runtime.totalMemory(),heapMax=runtime.maxMemory(),
                loadedChunks=worlds.sumOf { it.chunkCount },entities=worlds.sumOf { it.entityCount },
                players=server.onlinePlayers.map { PlayerSnapshot(it.uniqueId.toString(),it.name,it.ping.coerceAtLeast(0),it.world.name) },
                worlds=worlds.map { it.name },queueDepth=queue.size(),droppedEvents=queue.dropped.get(),spoolBytes=worker.spoolBytes,
                version=pluginMeta.version,sampledAt=System.currentTimeMillis())
        },1,100)
        worker.start()
        logger.info("Caenis telemetry enabled for instance ${settings.instanceId}.")
    }
    override fun onDisable() { heartbeat?.cancel(); dispatcher?.close() }
}
