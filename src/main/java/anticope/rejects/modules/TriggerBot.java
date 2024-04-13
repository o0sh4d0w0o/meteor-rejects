package anticope.rejects.modules;

import anticope.rejects.MeteorRejectsAddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

import java.util.Set;

public class TriggerBot extends Module {
	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	private final SettingGroup sgDelay = settings.createGroup("Delay");
	private int delayTickAmount = 0;

	public TriggerBot() {
		super(MeteorRejectsAddon.CATEGORY, "triggerbot", "Automatically attack lookat choosen entities.");
	}

	@Override
	public void onDeactivate() {
		delayTickAmount = 0;
	}

	private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
			.name("entities")
			.description("Target entities.")
			.onlyAttackable()
			.build());

	private final Setting<Boolean> tamed = sgGeneral.add(new BoolSetting.Builder()
			.name("tamed")
			.description("Whether or not to attack tamed entities.")
			.defaultValue(false)
			.build());

	private final Setting<Boolean> named = sgGeneral.add(new BoolSetting.Builder()
			.name("named")
			.description("Whether or not to attack named entities.")
			.defaultValue(true)
			.build());

	private final Setting<Boolean> babies = sgGeneral.add(new BoolSetting.Builder()
			.name("babies")
			.description("Whether or not to attack baby variants of the entity.")
			.defaultValue(true)
			.build());

	private final Setting<Delay> smartDelay = sgDelay.add(new EnumSetting.Builder<Delay>()
			.name("type")
			.description("Type of delay.")
			.defaultValue(Delay.Vanilla)
			.build());

	private final Setting<Double> cooldownEfficiency = sgDelay.add(new DoubleSetting.Builder()
			.name("cooldown-efficiency")
			.description(
					"Close to 1 means slower hits and more efficient, close to 0 means faster hits but less efficient.")
			.defaultValue(1.0)
			.min(0.0)
			.sliderMax(1.0)
			.decimalPlaces(2)
			.visible(() -> smartDelay.get() == Delay.Vanilla)
			.build());

	private final Setting<Integer> delayTicks = sgDelay.add(new IntSetting.Builder()
			.name("ticks")
			.description("Ticks between each hits.")
			.defaultValue(3)
			.min(0)
			.sliderMax(40)
			.visible(() -> smartDelay.get() == Delay.Custom)
			.build());

	private final Setting<Boolean> randomDelayEnabled = sgDelay.add(new BoolSetting.Builder()
			.name("random-delay-enabled")
			.description("Adds a random delay between hits to attempt to bypass anti-cheats.")
			.defaultValue(false)
			.visible(() -> smartDelay.get() == Delay.Custom)
			.build());

	private final Setting<Integer> randomDelayMax = sgDelay.add(new IntSetting.Builder()
			.name("random-delay-max")
			.description("The maximum value for random delay.")
			.defaultValue(2)
			.min(0)
			.sliderMax(10)
			.visible(() -> randomDelayEnabled.get() && smartDelay.get() == Delay.Custom)
			.build());

	private boolean entityCheck(Entity entity) {
		Set<EntityType<?>> targetedEntities = entities.get();
		Friends friends = Friends.get();
		boolean isPlayer = entity.equals(mc.player);
		boolean isCamera = entity.equals(mc.cameraEntity);
		boolean isTarget = targetedEntities.contains(entity.getType());

		if (!isTarget || isPlayer || isCamera || !entity.isAlive())
			return false;

		if (entity.hasCustomName() && !named.get()) {
			return false;
		}

		if (entity instanceof LivingEntity) {
			LivingEntity livingEntity = (LivingEntity) entity;

			if (livingEntity.isDead()) {
				return false;
			}
		} else if (!entity.isAlive()) {
			return false;
		}

		if (entity instanceof TameableEntity && !tamed.get())
			return false;

		if (entity instanceof PlayerEntity) {
			PlayerEntity playerEntity = (PlayerEntity) entity;

			if (playerEntity.isCreative() || !friends.shouldAttack(playerEntity)) {
				return false;
			}
		}

		if (entity instanceof AnimalEntity) {
			AnimalEntity animalEntity = (AnimalEntity) entity;

			if (animalEntity.isBaby() && !babies.get()) {
				return false;
			}
		}

		return true;
	}

	private boolean delayCheck() {
		if (smartDelay.get() == Delay.Vanilla) {
			double cooldownEfficiency = 1.0 - this.cooldownEfficiency.get();
			float progressByTick = mc.player.getAttackCooldownProgressPerTick() * (float) cooldownEfficiency;

			return mc.player.getAttackCooldownProgress(progressByTick) >= 1.0f;
		}

		if (delayTickAmount > 0) {
			delayTickAmount--;
			return false;
		} else {
			boolean randomEnabled = randomDelayEnabled.get();
			int randomMax = randomDelayMax.get();
			int delay = delayTicks.get();

			delayTickAmount = delay + (randomEnabled ? (int) Math.round(Math.random() * randomMax) : 0);

			return true;
		}
	}

	@EventHandler
	private void onTick(TickEvent.Pre event) {
		if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR || mc.targetedEntity == null) {
			return;
		}
		;

		if (delayCheck() && entityCheck(mc.targetedEntity)) {
			hitEntity(mc.targetedEntity);
		}
	}

	private void hitEntity(Entity target) {
		mc.interactionManager.attackEntity(mc.player, target);
		mc.player.swingHand(Hand.MAIN_HAND);
	}

	private enum Delay {
		Vanilla,
		Custom
	}
}
