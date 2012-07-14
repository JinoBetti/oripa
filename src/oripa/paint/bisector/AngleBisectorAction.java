package oripa.paint.bisector;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Point2D.Double;

import javax.media.j3d.GraphStructureChangeListener;
import javax.vecmath.Vector2d;

import oripa.Config;
import oripa.Constants;
import oripa.Globals;
import oripa.ORIPA;
import oripa.geom.GeomUtil;
import oripa.geom.OriLine;
import oripa.paint.ElementSelector;
import oripa.paint.GraphicMouseAction;
import oripa.paint.MouseContext;
import oripa.paint.segment.SelectingFirstVertexForSegment;

public class AngleBisectorAction extends GraphicMouseAction {


	public AngleBisectorAction(){
		setActionState(new SelectingVertexForBisector());
	}



//	private OriLine closeLine = null;
//
//	@Override
//	public Vector2d onMove(MouseContext context, AffineTransform affine,
//			MouseEvent event) {
//		Vector2d result = super.onMove(context, affine, event);
//
//		if(context.getVertexCount() == 3){
//			if(closeLine != null){
//				closeLine.selected = false;
//			}
//			
//			closeLine = context.pickCandidateL;
//	
//			if(closeLine != null){
//				closeLine.selected = true;
//			}
//		}		
//		return result;
//	}




	@Override
	public void onDrag(MouseContext context, AffineTransform affine, MouseEvent event) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRelease(MouseContext context, AffineTransform affine,
			MouseEvent event) {
		// TODO Auto-generated method stub

	}


	@Override
	public void onDraw(Graphics2D g2d, MouseContext context) {

		super.onDraw(g2d, context);


		ElementSelector selector = new ElementSelector();

		if(context.getVertexCount() < 3){
			Vector2d closeVertex = context.pickCandidateV;

			if (closeVertex != null) {
				g2d.setColor(selector.selectColorByLineType(Globals.inputLineType));
				drawVertex(g2d, context, closeVertex.x, closeVertex.y);
			}
		}
		else {
			
			OriLine closeLine = context.pickCandidateL;

			if(closeLine != null){
				g2d.setColor(Config.LINE_COLOR_CANDIDATE);
				drawLine(g2d, closeLine);
			}
		}
	}

}
