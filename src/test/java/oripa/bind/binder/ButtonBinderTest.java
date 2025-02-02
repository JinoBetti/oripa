package oripa.bind.binder;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.JRadioButton;

import org.junit.jupiter.api.Test;

import oripa.resource.ResourceHolder;
import oripa.resource.ResourceKey;
import oripa.resource.StringID;
import oripa.viewsetting.ChangeViewSetting;
import oripa.viewsetting.main.uipanel.ChangeOnPaintInputButtonSelected;
import oripa.viewsetting.main.uipanel.UIPanelSetting;

public class ButtonBinderTest {

	@Test
	public void testCreate() {

		BinderInterface<ChangeViewSetting> viewChangeBinder = new ViewChangeBinder();

		// JRadioButton editModeInputLineButton = new JRadioButton("InputLine",
		// true);
		JRadioButton editModeInputLineButton = (JRadioButton) viewChangeBinder.createButton(
				JRadioButton.class, new ChangeOnPaintInputButtonSelected(new UIPanelSetting()),
				StringID.UI.INPUT_LINE_ID, null);

		ResourceHolder resources = ResourceHolder.getInstance();

		assertNotNull(editModeInputLineButton);

		String actualText = editModeInputLineButton.getText();
		assertNotNull(actualText);
		System.out.println(actualText);
		assertEquals(resources.getString(ResourceKey.LABEL, StringID.UI.INPUT_LINE_ID),
				editModeInputLineButton.getText());
	}

	@Test
	public void testCreateWithError() {
	}
}
