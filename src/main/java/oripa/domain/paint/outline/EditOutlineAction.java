package oripa.domain.paint.outline;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.util.Collection;
import java.util.List;

import javax.vecmath.Vector2d;

import oripa.domain.cptool.OverlappingLineExtractor;
import oripa.domain.paint.EditMode;
import oripa.domain.paint.PaintContextInterface;
import oripa.domain.paint.core.GraphicMouseAction;
import oripa.domain.paint.util.PairLoop;

public class EditOutlineAction extends GraphicMouseAction {

	public EditOutlineAction() {
		setActionState(new SelectingVertexForOutline(new CloseTempOutlineFactory(new IsOnTempOutlineLoop(),
				new IsOutsideOfTempOutlineLoop(), new OverlappingLineExtractor())));
		setEditMode(EditMode.OTHER);
	}

	private void drawTempOutlines(final Graphics2D g2d, final Collection<Vector2d> outlineVertices,
			final double scale) {
		var selector = getElementSelector();
		g2d.setColor(selector.getEditingOutlineColor());
		g2d.setStroke(selector.createEditingOutlineStroke(scale));

		if (outlineVertices.size() > 1) {
			PairLoop.iterateWithCount(
					outlineVertices, outlineVertices.size() - 1, (p0, p1) -> {
						g2d.draw(new Line2D.Double(p0.x, p0.y, p1.x, p1.y));
						return true;
					});
		}
	}

	@Override
	public void onDraw(final Graphics2D g2d, final PaintContextInterface context) {
		super.onDraw(g2d, context);

		this.drawPickCandidateVertex(g2d, context);

		List<Vector2d> outlinevertices = context.getPickedVertices();

		// Shows the outline of the editing
		int outlineVnum = outlinevertices.size();

		if (outlineVnum != 0) {

			drawTempOutlines(g2d, outlinevertices, context.getScale());

			Vector2d cv = (context.getCandidateVertexToPick() == null)
					? new Vector2d(context.getLogicalMousePoint().getX(),
							context.getLogicalMousePoint().getY())
					: context.getCandidateVertexToPick();
			g2d.draw(new Line2D.Double(outlinevertices.get(0).x,
					outlinevertices.get(0).y,
					cv.x, cv.y));
			g2d.draw(new Line2D.Double(outlinevertices.get(outlineVnum - 1).x,
					outlinevertices.get(outlineVnum - 1).y, cv.x, cv.y));
		}

	}

	@Override
	public void onPress(final PaintContextInterface context, final AffineTransform affine,
			final boolean differentAction) {

	}

	@Override
	public void onDrag(final PaintContextInterface context, final AffineTransform affine,
			final boolean differentAction) {

	}

	@Override
	public void onRelease(final PaintContextInterface context, final AffineTransform affine,
			final boolean differentAction) {

	}

}
