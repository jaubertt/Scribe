package scribe.mixin;

import scribe.LibrarianLecterns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorkAtPoi.class)
public abstract class WorkAtPoiMixin {
	@Inject(method = "useWorkstation", at = @At("HEAD"))
	private void scribe$librarianOpensBook(ServerLevel level, Villager villager, CallbackInfo ci) {
		LibrarianLecterns.startsWork(level, villager, (WorkAtPoi) (Object) this);
	}
}
