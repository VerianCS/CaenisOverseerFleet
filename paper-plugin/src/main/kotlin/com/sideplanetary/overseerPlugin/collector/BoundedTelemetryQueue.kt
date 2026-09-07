package com.sideplanetary.overseerPlugin.collector

import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReferenceArray

// SPSC ring: only the Paper main thread offers; only the telemetry worker drains.
class BoundedTelemetryQueue(private val capacity: Int) {
    private val values = AtomicReferenceArray<BlockBreakTelemetry?>(capacity)
    private val head = AtomicLong()
    private val tail = AtomicLong()
    val dropped = AtomicLong()
    fun offer(event: BlockBreakTelemetry) {
        val write = tail.get()
        if(write-head.get() >= capacity) { dropped.incrementAndGet(); return }
        values.set((write % capacity).toInt(),event)
        tail.lazySet(write+1)
    }
    fun drainTo(target: MutableList<BlockBreakTelemetry>, maximum: Int): Int {
        var read=head.get()
        val end=minOf(tail.get(),read+maximum)
        while(read<end) {
            val index=(read%capacity).toInt()
            values.getAndSet(index,null)?.let(target::add)
            read++
        }
        head.lazySet(read)
        return target.size
    }
    fun size() = (tail.get()-head.get()).toInt()
    fun isEmpty() = head.get() == tail.get()
}
