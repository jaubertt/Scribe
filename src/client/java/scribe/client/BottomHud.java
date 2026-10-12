package scribe.client;

import scribe.client.mixin.HudStateAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

public final class BottomHud {
	public static final int HOTBAR = 23;
	public static final int CONTEXTUAL_BAR = 29;
	public static final int LOCATOR_ARROWS = 35;
	public static final int EXPERIENCE_LEVEL = 36;
	public static final int HEALTH_LINE = 39;
	public static final int ROW = 10;
	public static final int HELD_ITEM_NAME = 59;
	public static final int HELD_ITEM_NAME_NO_HEALTH = 45;
	public static final int OVERLAY_MESSAGE = 72;
	public static final int SPECTATOR_ACTION = 35;
	public static final int BACKDROP = 2;
	private static final int MAX_VEHICLE_HEARTS = 30;
	private static final int EXPERIENCE_BAR_DISPLAY_TICKS = 100;

	private BottomHud() {
	}

	public enum Bar {
		EMPTY, EXPERIENCE, LOCATOR, JUMPABLE_VEHICLE
	}

	public static final class State {
		public GameType mode = GameType.SURVIVAL;
		public boolean cameraPlayer = true;
		public boolean spectatorMenu;
		public float health = 20;
		public float maxHealth = 20;
		public int displayHealth = 20;
		public float absorption;
		public boolean regeneration;
		public int armor;
		public float saturation = 5;
		public boolean air;
		public int vehicleHearts;
		public Bar bar = Bar.EXPERIENCE;
		public int experienceLevel;
		public boolean heldItemName;
		public boolean overlayMessage;
		public boolean backdrop;

		public State copy() {
			State s = new State();
			s.mode = this.mode;
			s.cameraPlayer = this.cameraPlayer;
			s.spectatorMenu = this.spectatorMenu;
			s.health = this.health;
			s.maxHealth = this.maxHealth;
			s.displayHealth = this.displayHealth;
			s.absorption = this.absorption;
			s.regeneration = this.regeneration;
			s.armor = this.armor;
			s.saturation = this.saturation;
			s.air = this.air;
			s.vehicleHearts = this.vehicleHearts;
			s.bar = this.bar;
			s.experienceLevel = this.experienceLevel;
			s.heldItemName = this.heldItemName;
			s.overlayMessage = this.overlayMessage;
			s.backdrop = this.backdrop;
			return s;
		}
	}

	public record Row(String what, int top) {
	}

	public static int height(State s) {
		int top = 0;
		for (Row row : rows(s)) {
			top = Math.max(top, row.top());
		}
		return top;
	}

	public static List<Row> rows(State s) {
		List<Row> rows = new ArrayList<>();
		boolean spectator = s.mode == GameType.SPECTATOR;
		boolean survival = s.mode.isSurvival();
		int backdrop = s.backdrop ? BACKDROP : 0;
		if (spectator) {
			if (s.spectatorMenu) {
				rows.add(new Row("spectator hotbar", HOTBAR));
			}
		} else if (s.cameraPlayer) {
			rows.add(new Row("hotbar", HOTBAR));
		}
		if (survival && s.cameraPlayer) {
			health(s, rows);
		}
		if (s.cameraPlayer && s.vehicleHearts > 0) {
			rows.add(new Row("vehicle hearts", HEALTH_LINE + (vehicleRows(s.vehicleHearts) - 1) * ROW));
		}
		switch (s.bar) {
			case EXPERIENCE -> rows.add(new Row("experience bar", CONTEXTUAL_BAR));
			case JUMPABLE_VEHICLE -> rows.add(new Row("jump bar", CONTEXTUAL_BAR));
			case LOCATOR -> rows.add(new Row("locator bar", LOCATOR_ARROWS));
			case EMPTY -> {
			}
		}
		if (survival && s.experienceLevel > 0) {
			rows.add(new Row("experience level", EXPERIENCE_LEVEL));
		}
		if (!spectator) {
			if (s.heldItemName) {
				rows.add(new Row("held item name", (survival ? HELD_ITEM_NAME : HELD_ITEM_NAME_NO_HEALTH) + backdrop));
			}
		} else if (s.spectatorMenu) {
			rows.add(new Row("spectator text", SPECTATOR_ACTION + backdrop));
		}
		if (s.overlayMessage) {
			rows.add(new Row("action bar", OVERLAY_MESSAGE + backdrop));
		}
		return rows;
	}

	private static void health(State s, List<Row> rows) {
		int currentHealth = Mth.ceil(s.health);
		float maxHealth = Math.max(s.maxHealth, Math.max(s.displayHealth, currentHealth));
		int totalAbsorption = Mth.ceil(s.absorption);
		int numHealthRows = Mth.ceil((maxHealth + totalAbsorption) / 2.0F / 10.0F);
		int healthRowHeight = Math.max(10 - (numHealthRows - 2), 3);
		int healthContainers = Mth.ceil(maxHealth / 2.0);
		int absorptionContainers = Mth.ceil(totalAbsorption / 2.0);
		int topRow = Math.max(0, healthContainers + absorptionContainers - 1) / 10;
		boolean hop = s.regeneration && (healthContainers - 1) / 10 == topRow;
		rows.add(new Row("hearts", HEALTH_LINE + topRow * healthRowHeight + (hop ? 2 : 0)));
		if (s.armor > 0) {
			rows.add(new Row("armor", HEALTH_LINE + (numHealthRows - 1) * healthRowHeight + ROW));
		}
		if (s.vehicleHearts == 0) {
			rows.add(new Row("food", HEALTH_LINE + (s.saturation <= 0.0F ? 1 : 0)));
		}
		if (s.air) {
			int vehicleRows = vehicleRows(s.vehicleHearts);
			rows.add(new Row("air", HEALTH_LINE + ROW + Math.max(0, vehicleRows - 1) * ROW));
		}
	}

	private static int vehicleRows(int hearts) {
		return (int) Math.ceil(hearts / 10.0);
	}

	public static int vehicleHearts(float maxVehicleHealth, boolean showsHealth) {
		return showsHealth ? Math.min((int) (maxVehicleHealth + 0.5F) / 2, MAX_VEHICLE_HEARTS) : 0;
	}

	public static Bar bar(boolean waypoints, boolean jumpableVehicle, boolean experience, boolean preferJump, boolean preferExperience) {
		if (waypoints) {
			if (jumpableVehicle && preferJump) {
				return Bar.JUMPABLE_VEHICLE;
			}
			return experience && preferExperience ? Bar.EXPERIENCE : Bar.LOCATOR;
		}
		if (jumpableVehicle) {
			return Bar.JUMPABLE_VEHICLE;
		}
		return experience ? Bar.EXPERIENCE : Bar.EMPTY;
	}

	public static State read(Minecraft minecraft) {
		State s = new State();
		s.mode = minecraft.gameMode.getPlayerMode();
		Hud hud = minecraft.gui.hud;
		HudStateAccessor fields = (HudStateAccessor) hud;
		Player camera = minecraft.getCameraEntity() instanceof Player player ? player : null;
		s.cameraPlayer = camera != null;
		if (camera != null) {
			s.health = camera.getHealth();
			s.maxHealth = (float) camera.getAttributeValue(Attributes.MAX_HEALTH);
			s.displayHealth = fields.scribe$displayHealth();
			s.absorption = camera.getAbsorptionAmount();
			s.regeneration = camera.hasEffect(MobEffects.REGENERATION);
			s.armor = camera.getArmorValue();
			s.saturation = camera.getFoodData().getSaturationLevel();
			int maxAir = camera.getMaxAirSupply();
			s.air = camera.isEyeInFluid(FluidTags.WATER) || Math.clamp(camera.getAirSupply(), 0, maxAir) < maxAir;
			if (camera.getVehicle() instanceof LivingEntity vehicle) {
				s.vehicleHearts = vehicleHearts(vehicle.getMaxHealth(), vehicle.showVehicleHealth());
			}
		}
		s.spectatorMenu = hud.getSpectatorGui().isMenuActive();
		LocalPlayer player = minecraft.player;
		PlayerRideableJumping jumpable = player.jumpableVehicle();
		boolean preferJump = player.getJumpRidingScale() > 0.0F || (jumpable != null ? jumpable.getJumpCooldown() : 0) > 0;
		boolean preferExperience = player.experienceDisplayStartTick + EXPERIENCE_BAR_DISPLAY_TICKS > player.tickCount;
		s.bar = bar(player.connection.getWaypointManager().hasWaypoints(), jumpable != null, minecraft.gameMode.hasExperience(), preferJump, preferExperience);
		s.experienceLevel = player.experienceLevel;
		s.heldItemName = fields.scribe$toolHighlightTimer() > 0 && !fields.scribe$lastToolHighlight().isEmpty();
		s.overlayMessage = fields.scribe$overlayMessage() != null && fields.scribe$overlayMessageTime() > 0;
		s.backdrop = minecraft.options.getBackgroundColor(0.0F) != 0;
		return s;
	}
}
