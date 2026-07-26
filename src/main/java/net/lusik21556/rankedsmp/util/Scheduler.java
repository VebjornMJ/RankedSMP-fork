package net.lusik21556.rankedsmp.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal tick-counted delayed task queue - the Fabric-side replacement for
 * Bukkit's {@code BukkitScheduler.runTaskLater}. Driven once per server tick
 * from {@code ServerTickEvents.END_SERVER_TICK}.
 */
public class Scheduler {
	private record Task(long executeAtTick, Runnable action) {
	}

	private final List<Task> tasks = new ArrayList<>();
	private long currentTick = 0;

	public void tick() {
		currentTick++;
		if (tasks.isEmpty()) {
			return;
		}
		List<Runnable> toRun = new ArrayList<>();
		tasks.removeIf(task -> {
			if (task.executeAtTick() <= currentTick) {
				toRun.add(task.action());
				return true;
			}
			return false;
		});
		for (Runnable action : toRun) {
			action.run();
		}
	}

	/** Runs {@code action} after {@code delayTicks} server ticks (20 ticks = 1 second). */
	public void runLater(long delayTicks, Runnable action) {
		tasks.add(new Task(currentTick + delayTicks, action));
	}

	public void clear() {
		tasks.clear();
	}
}
