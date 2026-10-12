package scribe.mixin;

import scribe.LibrarianReading;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorkAtPoi.class)
public abstract class WorkAtPoiReadingMixin {
	@Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;J)V", at = @At("HEAD"))
	private void scribe$librarianStopsReading(ServerLevel level, Villager villager, long timestamp, CallbackInfo ci) {
		LibrarianReading.startsWork(villager, (WorkAtPoi) (Object) this);
	}
}
