package io.taraxacum.finaltech.core.factory;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.taraxacum.finaltech.FinalTech;
import io.taraxacum.finaltech.api.dto.BlockTask;
import io.taraxacum.finaltech.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockTaskFactory<T> {
    private Map<T, Boolean> lockMap = new HashMap<>();
    private List<BlockTask<T>> taskQueue = new LinkedList<>();

    private Boolean work = false;
    private final Object lock = new Object();

    private final Object registerLock = new Object();

    private static final BlockTaskFactory<Location> instance = new BlockTaskFactory<>();

    private BlockTaskFactory() {

    }

    public void registerRunnable(@Nonnull BlockTask<T> blockTask) {
        synchronized (this.registerLock) {
            this.taskQueue.add(blockTask);
            for (T object : blockTask.objects()) {
                if (!this.lockMap.containsKey(object)) {
                    this.lockMap.put(object, false);
                }
            }
        }
    }

    @SafeVarargs
    public final void registerRunnable(@Nonnull SlimefunItem slimefunItem, @Nonnull Boolean sync, @Nonnull Runnable runnable, @Nonnull T... objects) {
        this.registerRunnable(new BlockTask<>(slimefunItem, sync, runnable, objects));
    }

    public void tick() {
        long beginTime = System.nanoTime();
        synchronized (this.lock) {
            if (this.work) {
                try {
                    while (this.work) {
                        this.lock.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            this.work = true;
            List<BlockTask<T>> taskQueue;
            Map<T, Boolean> lockMap;
            synchronized (this.registerLock) {
                taskQueue = this.taskQueue;
                lockMap = this.lockMap;
                this.taskQueue = new ArrayList<>();
                this.lockMap = new HashMap<>();
            }
            AtomicBoolean wait = new AtomicBoolean(true);
            while (!taskQueue.isEmpty()) {
                wait.set(true);
                Iterator<BlockTask<T>> iterator = taskQueue.iterator();
                while (iterator.hasNext()) {
                    BlockTask<T> blockTask = iterator.next();
                    boolean lock = false;
                    for (T object : blockTask.objects()) {
                        if (lockMap.get(object)) {
                            lock = true;
                            break;
                        }
                    }
                    if (!lock) {
                        for (T object : blockTask.objects()) {
                            lockMap.put(object, true);
                        }
                        if (blockTask.sync()) {
                            BukkitTask bukkitTask = Bukkit.getScheduler().runTask(JavaPlugin.getPlugin(FinalTech.class), () -> {
                                blockTask.runnable().run();
                                for (T object : blockTask.objects()) {
                                    lockMap.put(object, false);
                                }
                                synchronized (BlockTaskFactory.this.lock) {
                                    BlockTaskFactory.this.lock.notifyAll();
                                }
                            });
                        } else {
                            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getPlugin(FinalTech.class), () -> {
                                blockTask.runnable().run();
                                for (T object : blockTask.objects()) {
                                    lockMap.put(object, false);
                                }
                                synchronized (BlockTaskFactory.this.lock) {
                                    BlockTaskFactory.this.lock.notifyAll();
                                }
                            });
                        }
                        iterator.remove();
                        wait.set(false);
                    }
                }
                if (wait.get()) {
                    try {
                        this.lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        this.work = false;
                        return;
                    }
                }
            }

            long endTime = System.nanoTime();

            {
                Bukkit.getLogger().info(TextUtil.colorRandomString("----------------"));
                Bukkit.getLogger().info(TextUtil.colorRandomString("FINALTECH_ALL") + " §f:§9 " + (endTime - beginTime));
                Bukkit.getLogger().info(TextUtil.colorRandomString("----------------"));
            }

            this.work = false;
            this.lock.notifyAll();
        }
    }

    public static BlockTaskFactory<Location> getInstance() {
        return instance;
    }
}
