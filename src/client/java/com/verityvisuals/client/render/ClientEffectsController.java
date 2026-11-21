package com.verityvisuals.client.render;

import com.verityvisuals.client.config.VisualSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ClientEffectsController {
	private static final Deque<Long> CLICKS = new ArrayDeque<>();
	private static LivingEntity pendingTarget;
	private static int pendingTargetHurtTime;
	private static int pendingTicks;
	private static long hitEffectStarted;
	private static long deathEffectStarted;
	private static float previousHealth = Float.NaN;
	private static boolean deathHandled;

	private ClientEffectsController() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (player == minecraft.player && entity instanceof LivingEntity target) {
				CLICKS.addLast(System.nanoTime());
				pendingTarget = target;
				pendingTargetHurtTime = target.hurtTime;
				pendingTicks = 2;
			}
			return InteractionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(ClientEffectsController::tick);
	}

	private static void tick(Minecraft minecraft) {
		long now = System.nanoTime();
		while (!CLICKS.isEmpty() && now - CLICKS.peekFirst() > 1_000_000_000L) {
			CLICKS.removeFirst();
		}

		if (!VisualSettings.hitEffectEnabled) {
			hitEffectStarted = 0L;
		}
		if (!VisualSettings.deathEffectEnabled) {
			deathEffectStarted = 0L;
		}
		LocalPlayer player = minecraft.player;
		if (player == null) {
			previousHealth = Float.NaN;
			deathHandled = false;
			deathEffectStarted = 0L;
			hitEffectStarted = 0L;
			pendingTarget = null;
			CLICKS.clear();
			return;
		}

		if (pendingTarget != null) {
			if (pendingTarget.hurtTime > pendingTargetHurtTime) {
				hitEffectStarted = now;
				pendingTarget = null;
			} else if (--pendingTicks <= 0) {
				pendingTarget = null;
			}
		}

		float health = player.getHealth();
		if (Float.isNaN(previousHealth) || health > 0.0F) {
			deathHandled = false;
		}
		if (!deathHandled && previousHealth > 0.0F && health <= 0.0F) {
			deathEffectStarted = now;
			deathHandled = true;
		}
		previousHealth = health;
	}

	public static int getClicksPerSecond() {
		return CLICKS.size();
	}

	public static void renderEffects(net.minecraft.client.gui.GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || minecraft.player == null) {
			return;
		}
		long now = System.nanoTime();
		if (VisualSettings.deathEffectEnabled && deathEffectStarted != 0L) {
			float duration = VisualSettings.deathEffectDuration;
			float progress = (now - deathEffectStarted) / 1_000_000_000.0F / duration;
			if (progress >= 1.0F) {
				deathEffectStarted = 0L;
			} else {
				float envelope = (float) Math.sin(Math.PI * Math.max(0.0F, progress));
				int alpha = Math.round(envelope * 0.22F * 255.0F);
				graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(),
						minecraft.getWindow().getGuiScaledHeight(),
						(alpha << 24) | (VisualSettings.deathEffectColor & 0x00FFFFFF));
			}
		}
		if (VisualSettings.hitEffectEnabled && hitEffectStarted != 0L) {
			float progress = (now - hitEffectStarted) / 1_000_000_000.0F / VisualSettings.hitEffectDuration;
			if (progress >= 1.0F) {
				hitEffectStarted = 0L;
			} else {
				float alphaFactor = (1.0F - progress) * VisualSettings.hitEffectIntensity;
				int alpha = Math.round(alphaFactor * 255.0F);
				int color = (alpha << 24) | (VisualSettings.hitEffectColor & 0x00FFFFFF);
				int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
				int centerY = minecraft.getWindow().getGuiScaledHeight() / 2;
				graphics.fill(centerX - 10, centerY - 1, centerX - 5, centerY + 1, color);
				graphics.fill(centerX + 5, centerY - 1, centerX + 10, centerY + 1, color);
				graphics.fill(centerX - 1, centerY - 10, centerX + 1, centerY - 5, color);
				graphics.fill(centerX - 1, centerY + 5, centerX + 1, centerY + 10, color);
			}
		}
	}
}
