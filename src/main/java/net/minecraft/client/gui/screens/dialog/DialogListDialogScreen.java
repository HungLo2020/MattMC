package net.minecraft.client.gui.screens.dialog;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent.ShowDialog;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogListDialog;
import net.minecraft.server.dialog.action.StaticAction;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DialogListDialogScreen extends ButtonListDialogScreen<DialogListDialog> {
	public DialogListDialogScreen(@Nullable Screen screen, DialogListDialog dialogListDialog, DialogConnectionAccess dialogConnectionAccess) {
		super(screen, dialogListDialog, dialogConnectionAccess);
	}

	protected Stream<ActionButton> createListActions(DialogListDialog dialogListDialog, DialogConnectionAccess dialogConnectionAccess) {
		return dialogListDialog.dialogs().stream().map(holder -> createDialogClickAction(dialogListDialog, holder));
	}

	private static ActionButton createDialogClickAction(DialogListDialog dialogListDialog, Holder<Dialog> holder) {
		return new ActionButton(
			new CommonButtonData(((Dialog)holder.value()).common().computeExternalTitle(), dialogListDialog.buttonWidth()),
			Optional.of(new StaticAction(new ShowDialog(holder)))
		);
	}
}
