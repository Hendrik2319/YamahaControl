package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

import net.schwarzbaer.gui.Canvas;
import net.schwarzbaer.image.BumpMapping;
import net.schwarzbaer.image.BumpMapping.ExtraNormalFunctionPolar;
import net.schwarzbaer.image.BumpMapping.ExtraNormalFunctionPolar.LineOnX;
import net.schwarzbaer.image.BumpMapping.Indexer;
import net.schwarzbaer.image.BumpMapping.Normal;
import net.schwarzbaer.image.BumpMapping.NormalXY;
import net.schwarzbaer.image.BumpMapping.OverSampling;
import net.schwarzbaer.image.BumpMapping.ProfileXY;
import net.schwarzbaer.image.BumpMapping.RotatedProfile;
import net.schwarzbaer.image.BumpMapping.Shading;
import net.schwarzbaer.image.BumpMapping.Shading.MaterialShading;
import net.schwarzbaer.image.BumpMapping.Shading.MixedShading;

public class RotaryCtrl2 extends Canvas {
		private static final Color COLOR_DISABLED_MARKER = new Color(0x8080B0);
		//private static final Color COLOR_DISABLED_BACKGROUND = new Color(0xE8E8E8);
		private static final long serialVersionUID = -5870265710270984615L;
		
		private final int fixedWidth;
		private final int radius;
		private final double zeroAngle;
		private final double zeroAngle_deg;
		
		private double angle;
		private double value;
		private double minValue;
		private double maxValue;
		private double deltaPerFullCircle;
		private double tickInterval;
		private int decimals;
		private String unit;
		
		private final RotaryCtrl2.ValueListener valueListener;
		private final boolean valueIn2Rows;
		private final MouseHandler mouseHandler;
		private final RotatedProfile rotaryCtrlProfile;
		private final BumpMapping bumpMapping;
		
		private BufferedImage backgroundImage;
		
		public interface ValueListener {
			public void valueChanged(double value, boolean isAdjusting);
		}
		
		public RotaryCtrl2(int width, boolean valueIn2Rows, double minValue, double maxValue, double deltaPerFullCircle, double tickInterval, int decimals, double zeroAngle_deg, RotaryCtrl2.ValueListener valueListener) {
			super(width, width);
			this.fixedWidth = width;
			this.minValue = minValue;
			this.maxValue = maxValue;
			this.valueIn2Rows = valueIn2Rows;
			this.deltaPerFullCircle = deltaPerFullCircle;
			this.decimals = decimals;
			this.tickInterval = tickInterval;
			this.zeroAngle_deg = zeroAngle_deg;
			this.valueListener = valueListener;
			
			zeroAngle = this.zeroAngle_deg/180*Math.PI;
			radius = width/2-20;
			angle = 0.0;
			value = 0.0;
			unit = null;
			
			rotaryCtrlProfile = createRotaryCtrlProfile(radius,5,15,3,5);
			setTicks(rotaryCtrlProfile, radius,5,15,3, this.deltaPerFullCircle, this.tickInterval, this.zeroAngle_deg);			
			
			Normal sun = new Normal(1,-1,2).normalize();
			bumpMapping = new BumpMapping(false);
			bumpMapping.setShading(
				new MixedShading(
					(Indexer.Polar)(w,r)->radius/2.0<=r && r<radius ? 1 : 0,
					new Shading.GUISurfaceShading(sun,Color.WHITE,new Color(0xf0f0f0),new Color(0x707070)),
					new MaterialShading(sun, new Color(0xf0f0f0), 0, 40, false, 0)
				)
			);
			
			bumpMapping.setNormalFunction(rotaryCtrlProfile);
			bumpMapping.setOverSampling(OverSampling._9x_Square);
			updateBackgroundImage(false);
			
			mouseHandler = new MouseHandler();
			addMouseListener(mouseHandler);
			addMouseMotionListener(mouseHandler);
		}
		
		private void updateBackgroundImage(boolean inBackgroundThread) {
			if (inBackgroundThread) {
				new Thread(()->{
					backgroundImage = bumpMapping.renderImage(fixedWidth,fixedWidth);
					repaint();
				}).start();
			} else
				backgroundImage = bumpMapping.renderImage(fixedWidth,fixedWidth);
		}
		
		private static RotatedProfile createRotaryCtrlProfile(double radius, double innerRing, double outerRing, double transition, double height) {
			double r1 = radius/2;
			double r2 = radius/2+innerRing;
			double r3 = radius-outerRing;
			double r4 = radius;
			double tr = transition;
			NormalXY vFace  = new NormalXY(0,1);
			NormalXY vInner = ProfileXY.Constant.computeNormal(r1+tr, r2   , 0, height);
			NormalXY vOuter = ProfileXY.Constant.computeNormal(r3   , r4-tr, height, 0);
			NormalXY vHorizOutside = new NormalXY( 1,0);
			NormalXY vHorizInside  = new NormalXY(-1,0);
			
			return new RotatedProfile(
				new ProfileXY.Group(
					new ProfileXY.Constant  (   0.0, r1-tr ),
					new ProfileXY.RoundBlend(r1-tr , r1    , vFace,vHorizOutside),
					new ProfileXY.RoundBlend(r1    , r1+tr , vHorizInside,vInner),
					new ProfileXY.Constant  (r1+tr , r2    , 0, height),
					new ProfileXY.RoundBlend(r2    , r2+tr , vInner, vFace),
					new ProfileXY.Constant  (r2+tr , r3-tr ),
					new ProfileXY.RoundBlend(r3-tr , r3    , vFace, vOuter),
					new ProfileXY.Constant  (r3    , r4-tr , height, 0),
					new ProfileXY.RoundBlend(r4-tr , r4    , vOuter,vHorizOutside),
					new ProfileXY.RoundBlend(r4    , r4+tr , vHorizInside,vFace),
					new ProfileXY.Constant  (r4+tr , Double.POSITIVE_INFINITY)
				)
			);
		}
		
		private static LineOnX getTickLine(double radius, double innerRing, double outerRing, double transition, boolean getBigLine) {
			double ramp = 1;
			double lineHeight = 2;
			
			NormalXY vFace = new NormalXY(0,1);
			NormalXY vRamp = ProfileXY.Constant.computeNormal(0,ramp, 0,lineHeight);
			
			ProfileXY.Group profileBigLine = new ProfileXY.Group(
				new ProfileXY.Constant   (0.0     , 0.5     ),
				new ProfileXY.RoundBlend (0.5     , 1.5     , vFace, vRamp),
				new ProfileXY.Constant   (1.5     , 1.5+ramp, 0,lineHeight),
				new ProfileXY.RoundBlend (1.5+ramp, 2.5+ramp, vRamp, vFace)
			);
			ProfileXY.Group profileSmallLine = new ProfileXY.Group(
					new ProfileXY.Constant   (0.0       , 0.2       ),
					new ProfileXY.RoundBlend (0.2       , 0.5       , vFace, vRamp),
					new ProfileXY.Constant   (0.5       , 0.5+ramp/2, 0,lineHeight/2),
					new ProfileXY.RoundBlend (0.5+ramp/2, 0.8+ramp/2, vRamp, vFace)
				);
			
			double minRB = radius/2+innerRing+transition*2+profileBigLine.maxR;
			double maxRB = radius;
			double minRS = radius-outerRing-2;
			double maxRS = radius+5;
			
			if (getBigLine)
				return new ExtraNormalFunctionPolar.LineOnX(minRB, maxRB, profileBigLine   );
			return new ExtraNormalFunctionPolar.LineOnX(minRS, maxRS, profileSmallLine );
		}
		
		private static void setTicks(RotatedProfile background, double radius, double innerRing, double outerRing, double transition, double deltaPerFullCircle, double tickInterval, double zeroAngle_deg) {
			//LineOnX bigLine   = getTickLine(radius, innerRing, outerRing, transition, true);
			LineOnX smallLine = getTickLine(radius, innerRing, outerRing, transition, false);
			
			double angleTick = 360*tickInterval/deltaPerFullCircle;
			ExtraNormalFunctionPolar.Group outerTicks = new ExtraNormalFunctionPolar.Group();
			outerTicks.add(new ExtraNormalFunctionPolar.Rotated(zeroAngle_deg, smallLine));
			for (double a=angleTick; a<180*0.9; a+=angleTick) {
				outerTicks.add(new ExtraNormalFunctionPolar.Rotated(zeroAngle_deg-a, smallLine));
				outerTicks.add(new ExtraNormalFunctionPolar.Rotated(zeroAngle_deg+a, smallLine));
			}
			
			background.setExtras(new ExtraNormalFunctionPolar.Stencil( (w,r)->r> radius, outerTicks ));
		}
		
		public boolean isAdjusting() {
			return mouseHandler.isAdjusting;
		}
		
		public void setConfig(double minValue, double maxValue, double deltaPerFullCircle, double tickInterval, int decimals) {
			boolean newValues = false;
			if (this.minValue           != minValue          ) { this.minValue           = minValue          ; newValues = true; }
			if (this.maxValue           != maxValue          ) { this.maxValue           = maxValue          ; newValues = true; }
			if (this.deltaPerFullCircle != deltaPerFullCircle) { this.deltaPerFullCircle = deltaPerFullCircle; newValues = true; }
			if (this.tickInterval       != tickInterval      ) { this.tickInterval       = tickInterval      ; newValues = true; }
			this.decimals = decimals;

			if (newValues) {
				setTicks(rotaryCtrlProfile, radius,5,15,3, this.deltaPerFullCircle, this.tickInterval, this.zeroAngle_deg);			
				updateBackgroundImage(true);
				if (mouseHandler.isAdjusting)
					mouseHandler.stopAdjusting();
			}
			
			repaint();
		}
		
		public void setValue(Device.NumberWithUnit numberWithUnit) {
			if (mouseHandler.isAdjusting) return;
			if (numberWithUnit==null) {
				this.value = 0;
				this.unit = null;
			} else {
				this.value = numberWithUnit.getValue();
				this.unit = numberWithUnit.getUnit();
			}
			this.angle = value*2*Math.PI/deltaPerFullCircle;
			repaint();
		}

		private class MouseHandler extends MouseAdapter {
			
			private double pickAngle;
			private boolean isAdjusting;
			MouseHandler() {
				pickAngle = Double.NaN;
				isAdjusting = false;
			}

			public void stopAdjusting() {
				pickAngle = Double.NaN;
				isAdjusting = false;
			}

			@Override
			public void mousePressed(MouseEvent e) {
				if (!isEnabled()) return;
				
				pickAngle = getMouseAngle(e.getX(), e.getY(), true)-angle;
				isAdjusting = true;
//				System.out.printf("pickAngle: %f%n",pickAngle);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (!isEnabled()) return;
				
				isAdjusting = false;
				if (!Double.isNaN(pickAngle))
					changeValue(e.getX(), e.getY());
				pickAngle = Double.NaN;
//				System.out.printf("pickAngle: %f%n",pickAngle);
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (!isEnabled()) return;
				
				if (!Double.isNaN(pickAngle))
					changeValue(e.getX(), e.getY());
//				System.out.printf("angle: %f%n",angle);
			}
			

			private void changeValue(int mouseX, int mouseY) {
				double mouseAngle = getMouseAngle(mouseX, mouseY, false);
				double diff = mouseAngle-pickAngle-angle;
				if      (Math.abs(diff) > Math.abs(diff+2*Math.PI)) pickAngle -= 2*Math.PI;
				else if (Math.abs(diff) > Math.abs(diff-2*Math.PI)) pickAngle += 2*Math.PI;
				angle = mouseAngle-pickAngle;
				value = angle/2/Math.PI*deltaPerFullCircle;
				value = Math.max(minValue, Math.min(value, maxValue));
				angle = value*2*Math.PI/deltaPerFullCircle;
				
				valueListener.valueChanged(value,isAdjusting);
				repaint();
			}

			private double getMouseAngle(int mouseX, int mouseY, boolean checkIfInsideCircle) {
				int x = mouseX-width/2;
				int y = mouseY-height/2;
				if (checkIfInsideCircle && Math.sqrt(x*x+y*y)>radius) return Double.NaN;
				double mouseAngle = Math.atan2(y,x);
				return mouseAngle;
			}
		}

		@Override
		protected void paintCanvas(Graphics g, int x, int y, int width, int height) {
			Graphics2D g2 = (g instanceof Graphics2D)?(Graphics2D)g:null;
			if (g2!=null) g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
//			double angleTick = 2*Math.PI*tickInterval/deltaPerFullCircle;
			
//			Color ctrlTicks  = Color.BLACK;
			Color ctrlMarker = Color.BLACK; //Color.BLUE;
			Color ctrlText   = Color.BLACK;
			if (!isEnabled()) {
//				ctrlTicks  = Color.GRAY;
				ctrlMarker = COLOR_DISABLED_MARKER;
				ctrlText   = Color.GRAY;
			}
			
			int imWidth  = backgroundImage.getWidth();
			int imHeight = backgroundImage.getHeight();
			int imX = x + (width -imWidth )/2;
			int imY = y + (height-imHeight)/2;
			g.drawImage(backgroundImage, imX, imY, null);
			
//			g.setColor(ctrlTicks);
//			double ri = 1.0; //0.95;
//			double ra = 1.15;
//			drawRadiusLine(g, x,y, width, height, ri, ra, zeroAngle);
//			for (double a=angleTick; a<Math.PI*0.9; a+=angleTick) {
//				drawRadiusLine(g, x,y, width, height, ri, ra, zeroAngle+a);
//				drawRadiusLine(g, x,y, width, height, ri, ra, zeroAngle-a);
//			}
			
			g.setColor(ctrlMarker);
			if (g2!=null) g2.setStroke( new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND) );
			drawRadiusLine(g, x,y, width, height, 0.9, 0.7, angle+zeroAngle);
			if (g2!=null) g2.setStroke(new BasicStroke(1));
			
			drawValue(g, x, y, width, height, ctrlText);
		}

		private void drawValue(Graphics g, int x, int y, int width, int height, Color ctrlText) {
			if (unit!=null) {
				if (valueIn2Rows) {
					String str1 = String.format(Locale.ENGLISH, "%1."+decimals+"f", value);
					String str2 = unit;
					Rectangle2D stringBounds1 = g.getFontMetrics().getStringBounds(str1,g);
					Rectangle2D stringBounds2 = g.getFontMetrics().getStringBounds(str2,g);
					int strX1 = width /2-(int)Math.round(stringBounds1.getWidth ()/2+stringBounds1.getX());
					int strY1 = height/2-(int)Math.round(stringBounds1.getHeight()/2+stringBounds1.getY()) - 6;
					int strX2 = width /2-(int)Math.round(stringBounds2.getWidth ()/2+stringBounds2.getX());
					int strY2 = height/2-(int)Math.round(stringBounds2.getHeight()/2+stringBounds2.getY()) + 6;
					
					g.setColor(ctrlText);
					g.drawString(str1, x+strX1, y+strY1);
					g.drawString(str2, x+strX2, y+strY2);
				} else {
					String str = String.format(Locale.ENGLISH, "%1."+decimals+"f %s", value, unit);
					Rectangle2D stringBounds = g.getFontMetrics().getStringBounds(str,g);
					int strX = width /2-(int)Math.round(stringBounds.getWidth ()/2+stringBounds.getX());
					int strY = height/2-(int)Math.round(stringBounds.getHeight()/2+stringBounds.getY());
					
					g.setColor(ctrlText);
					g.drawString(str, x+strX, y+strY);
				}
			}
		}

		private void drawRadiusLine(Graphics g, int x, int y, int width, int height, double r1, double r2, double angle) {
			double cos = radius*Math.cos(angle);
			double sin = radius*Math.sin(angle);
			int x1 = width /2 + (int)Math.round(cos*r1);
			int y1 = height/2 + (int)Math.round(sin*r1);
			int x2 = width /2 + (int)Math.round(cos*r2);
			int y2 = height/2 + (int)Math.round(sin*r2);
			g.drawLine(x1, y1, x2, y2);
		}
	
	}