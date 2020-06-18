package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.Locale;

import net.schwarzbaer.gui.Canvas;
import net.schwarzbaer.image.ImageCache;
import net.schwarzbaer.image.bumpmapping.BumpMapping;
import net.schwarzbaer.image.bumpmapping.BumpMapping.Normal;
import net.schwarzbaer.image.bumpmapping.BumpMapping.NormalXY;
import net.schwarzbaer.image.bumpmapping.BumpMapping.ProfileXY;
import net.schwarzbaer.image.bumpmapping.BumpMapping.Shading;
import net.schwarzbaer.image.bumpmapping.BumpMapping.NormalFunction.Polar.RotatedProfile;

public class RotaryCtrl extends Canvas {
		private static final Color COLOR_DISABLED_MARKER = new Color(0x8080B0);
		//private static final Color COLOR_DISABLED_BACKGROUND = new Color(0xE8E8E8);
		private static final long serialVersionUID = -5870265710270984615L;
		private double angle;
		private final int radius;
		private double zeroAngle;
		private double value;
		private double deltaPerFullCircle;
		private String unit;
		private RotaryCtrl.ValueListener valueListener;
		private int decimals;
		private double tickInterval;
		private boolean valueIn2Rows;
		private double minValue;
		private double maxValue;
		private MouseHandler mouseHandler;
		private ImageCache<Image> backgroundImageCache;
		
		public interface ValueListener {
			public void valueChanged(double value, boolean isAdjusting);
		}
		
		public RotaryCtrl(int width, boolean valueIn2Rows, double minValue, double maxValue, double deltaPerFullCircle, double tickInterval, int decimals, double zeroAngle_deg, RotaryCtrl.ValueListener valueListener) {
			super(width, width);
			this.minValue = minValue;
			this.maxValue = maxValue;
			this.valueIn2Rows = valueIn2Rows;
			this.deltaPerFullCircle = deltaPerFullCircle;
			this.decimals = decimals;
			this.tickInterval = tickInterval;
			this.valueListener = valueListener;
			
			zeroAngle = zeroAngle_deg/180*Math.PI;
			radius = width/2-20;
			angle = 0.0;
			value = 0.0;
			unit = null;
			
			double r1 = radius/2;
			double r2 = radius/2+5;
			double r3 = radius-15;
			double r4 = radius;
			double tr = 3;
			
			if (r2+tr > r3-tr) {
				r3 = (r2+r4)/2;
				//throw new IllegalArgumentException();
			}
			
			NormalXY vFace  = new NormalXY(0,1);
			NormalXY vInner = ProfileXY.Constant.computeNormal(r1+tr, r2   , 0, 5);
			NormalXY vOuter = ProfileXY.Constant.computeNormal(r3   , r4-tr, 5, 0);
			NormalXY vHorizOutside = new NormalXY( 1,0);
			NormalXY vHorizInside  = new NormalXY(-1,0);
			
			BumpMapping bumpMapping = new BumpMapping(false,false);
			bumpMapping.setShading(
				new Shading.GUISurfaceShading(
					new Normal(1,-1,2).normalize(),
					Color.WHITE, new Color(0xf0f0f0), new Color(0x707070)
				)
			);
			bumpMapping.setNormalFunction(
				new RotatedProfile(
					new ProfileXY.Group(
						new ProfileXY.Constant  (   0.0, r1-tr ),
						new ProfileXY.RoundBlend(r1-tr , r1    , vFace,vHorizOutside),
						new ProfileXY.RoundBlend(r1    , r1+tr , vHorizInside,vInner),
						new ProfileXY.Constant  (r1+tr , r2    , 0, 5),
						new ProfileXY.RoundBlend(r2    , r2+tr , vInner, vFace),
						new ProfileXY.Constant  (r2+tr , r3-tr ),
						new ProfileXY.RoundBlend(r3-tr , r3    , vFace, vOuter),
						new ProfileXY.Constant  (r3    , r4-tr , 5, 0),
						new ProfileXY.RoundBlend(r4-tr , r4    , vOuter,vHorizOutside),
						new ProfileXY.RoundBlend(r4    , r4+tr , vHorizInside,vFace),
						new ProfileXY.Constant  (r4+tr , Double.POSITIVE_INFINITY)
					)
				)
			);
			
			backgroundImageCache = new ImageCache<Image>((w,h)->bumpMapping.renderImage(w,h));
			
			mouseHandler = new MouseHandler();
			addMouseListener(mouseHandler);
			addMouseMotionListener(mouseHandler);
		}
		
		public boolean isAdjusting() {
			return mouseHandler.isAdjusting;
		}
		
		public void setConfig(double minValue, double maxValue, double deltaPerFullCircle, double tickInterval, int decimals) {
			boolean stopAdjusting = false;
			if (this.minValue != minValue) { this.minValue = minValue; stopAdjusting = true; }
			if (this.maxValue != maxValue) { this.maxValue = maxValue; stopAdjusting = true; }
			if (this.deltaPerFullCircle != deltaPerFullCircle) { this.deltaPerFullCircle = deltaPerFullCircle; stopAdjusting = true; }
			this.decimals = decimals;
			this.tickInterval = tickInterval;
			if (stopAdjusting && mouseHandler.isAdjusting) {
				mouseHandler.stopAdjusting();
				repaint();
			}
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
			
			double angleTick = 2*Math.PI*tickInterval/deltaPerFullCircle;
			
			//Color ctrlBackground = Color.WHITE;
			Color ctrlTicks  = Color.BLACK;
//			Color ctrlLines  = new Color(0xd0d0d0); //Color.BLACK;
//			Color ctrlLines2 = new Color(0xd0d0d0); //Color.GRAY;
			Color ctrlMarker = Color.BLACK; //Color.BLUE;
			Color ctrlText   = Color.BLACK;
			if (!isEnabled()) {
				//ctrlBackground = COLOR_DISABLED_BACKGROUND;
				ctrlTicks  = Color.GRAY;
//				ctrlLines  = new Color(0xd0d0d0); //Color.GRAY;
//				ctrlLines2 = new Color(0xd0d0d0); //Color.GRAY;
				ctrlMarker = COLOR_DISABLED_MARKER;
				ctrlText   = Color.GRAY;
			}
			
			Image image = backgroundImageCache.getImage(width, height);
			g.drawImage(image, x, y, null);
			
			g.setColor(ctrlTicks);
			double ri = 1.0; //0.95;
			double ra = 1.15;
			drawRadiusLine(g, x,y, width, height, ri, ra, zeroAngle);
			for (double a=angleTick; a<Math.PI*0.9; a+=angleTick) {
				drawRadiusLine(g, x,y, width, height, ri, ra, zeroAngle+a);
				drawRadiusLine(g, x,y, width, height, ri, ra, zeroAngle-a);
			}
			
			//g.setColor(ctrlBackground);
			//g.fillOval(width/2-radius, height/2-radius, radius*2, radius*2);
			
//			g.setColor(ctrlLines);
//			g.drawOval(x+width/2-radius, y+height/2-radius, radius*2, radius*2);
//			if (showInnerCircle) {
//				g.setColor(ctrlLines2);
//				g.drawOval(x+width/2-radius/2, y+height/2-radius/2, radius, radius);
//			}
			
			g.setColor(ctrlMarker);
			if (g2!=null) g2.setStroke( new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND) );
			drawRadiusLine(g, x,y, width, height, 0.9, 0.7, angle+zeroAngle);
			if (g2!=null) g2.setStroke(new BasicStroke(1));
			
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