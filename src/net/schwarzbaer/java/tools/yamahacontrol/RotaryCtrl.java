package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.Locale;

import net.schwarzbaer.gui.Canvas;

public class RotaryCtrl extends Canvas {
		private static final long serialVersionUID = -5870265710270984615L;
		private double angle;
		private int radius;
		private double zeroAngle;
		private double value;
		private double deltaPerFullCircle;
		protected boolean isAdjusting;
		private String unit;
		private RotaryCtrl.ValueListener valueListener;
		
		public interface ValueListener {
			public void valueChanged(double value, boolean isAdjusting);
		}
		
		public RotaryCtrl(int width, double deltaPerFullCircle, double zeroAngle_deg, RotaryCtrl.ValueListener valueListener) {
			super(width, width);
			this.deltaPerFullCircle = deltaPerFullCircle;
			this.valueListener = valueListener;
			
			zeroAngle = zeroAngle_deg/180*Math.PI;
			radius = width/2-20;
			angle = 0.0;
			value = 0.0;
			isAdjusting = false;
			unit = "##";
			
			setMouseAdapter();
		}
		
		private void setMouseAdapter() {
			MouseAdapter mouseAdapter = new MouseAdapter() {
				
				private RotaryCtrl control = RotaryCtrl.this;
				private double pickAngle = Double.NaN;

				@Override
				public void mousePressed(MouseEvent e) {
					pickAngle = getMouseAngle(e.getX(), e.getY(), true)-angle;
					isAdjusting = true;
//					System.out.printf("pickAngle: %f%n",pickAngle);
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					isAdjusting = false;
					if (!Double.isNaN(pickAngle))
						changeValue(e.getX(), e.getY());
					pickAngle = Double.NaN;
//					System.out.printf("pickAngle: %f%n",pickAngle);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					if (!Double.isNaN(pickAngle))
						changeValue(e.getX(), e.getY());
//					System.out.printf("angle: %f%n",angle);
				}
				

				private void changeValue(int mouseX, int mouseY) {
					double mouseAngle = getMouseAngle(mouseX, mouseY, false);
					double diff = mouseAngle-pickAngle-angle;
					if      (Math.abs(diff) > Math.abs(diff+2*Math.PI)) pickAngle -= 2*Math.PI;
					else if (Math.abs(diff) > Math.abs(diff-2*Math.PI)) pickAngle += 2*Math.PI;
					angle = mouseAngle-pickAngle;
					value = angle/2/Math.PI*deltaPerFullCircle;
					
					valueListener.valueChanged(value,isAdjusting);
					control.repaint();
				}

				private double getMouseAngle(int mouseX, int mouseY, boolean checkIfInsideCircle) {
					int x = mouseX-control.width/2;
					int y = mouseY-control.height/2;
					if (checkIfInsideCircle && Math.sqrt(x*x+y*y)>radius) return Double.NaN;
					double mouseAngle = Math.atan2(y,x);
					return mouseAngle;
				}
			};
			addMouseListener(mouseAdapter);
			addMouseMotionListener(mouseAdapter);
		}
		
		public void setValue(Device.NumberWithUnit numberWithUnit) {
			if (isAdjusting) return;
			if (numberWithUnit==null) {
				this.value = 0;
				this.unit = "##";
			} else {
				this.value = numberWithUnit.number;
				this.unit = numberWithUnit.unit;
			}
			this.angle = value*2*Math.PI/deltaPerFullCircle;
			repaint();
		}
		
		@Override
		protected void paintCanvas(Graphics g, int width, int height) {
			Graphics2D g2 = (g instanceof Graphics2D)?(Graphics2D)g:null;
			if (g2!=null) g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			double angle1 = 2*Math.PI/deltaPerFullCircle;
			
			drawRadiusLine(g, width, height, 0.95, 1.15, zeroAngle);
			for (double a=angle1; a<Math.PI*0.9; a+=angle1) {
				drawRadiusLine(g, width, height, 0.95, 1.15, zeroAngle+a);
				drawRadiusLine(g, width, height, 0.95, 1.15, zeroAngle-a);
			}
			
			g.setColor(Color.WHITE);
			g.fillOval(width/2-radius, height/2-radius, radius*2, radius*2);
			
			g.setColor(Color.BLACK);
			g.drawOval(width/2-radius, height/2-radius, radius*2, radius*2);
			g.setColor(Color.GRAY);
			g.drawOval(width/2-radius/2, height/2-radius/2, radius, radius);
			
			g.setColor(Color.BLUE);
			if (g2!=null) g2.setStroke( new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND) );
			drawRadiusLine(g, width, height, 0.96, 0.7, angle+zeroAngle);
			if (g2!=null) g2.setStroke(new BasicStroke(1));
			
			String str = String.format(Locale.ENGLISH, "%1.1f %s", value, unit);
			Rectangle2D stringBounds = g.getFontMetrics().getStringBounds(str,g);
			int strX = width/2-(int)Math.round(stringBounds.getWidth()/2+stringBounds.getX());
			int strY = height/2-(int)Math.round(stringBounds.getHeight()/2+stringBounds.getY());
			
			g.setColor(Color.BLACK);
			g.drawString(str, strX, strY);
			//g.drawString(String.format(Locale.ENGLISH, "%6.1f", angle/Math.PI*180), width/2, height/2+15);
		}

		private void drawRadiusLine(Graphics g, int width, int height, double r1, double r2, double angle) {
			double cos = radius*Math.cos(angle);
			double sin = radius*Math.sin(angle);
			int x1 = width /2 + (int)Math.round(cos*r1);
			int y1 = height/2 + (int)Math.round(sin*r1);
			int x2 = width /2 + (int)Math.round(cos*r2);
			int y2 = height/2 + (int)Math.round(sin*r2);
			g.drawLine(x1, y1, x2, y2);
		}
	
	}