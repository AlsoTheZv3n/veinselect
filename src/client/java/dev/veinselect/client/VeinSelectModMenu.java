package dev.veinselect.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Einstellungs-Button in Mod Menu. Wird nur geladen, wenn Mod Menu installiert ist. */
public class VeinSelectModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return VeinSelectConfigScreen::new;
	}
}
