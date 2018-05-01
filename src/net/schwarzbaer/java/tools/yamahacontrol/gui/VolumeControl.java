package net.schwarzbaer.java.tools.yamahacontrol.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

import net.schwarzbaer.gui.Canvas;
import net.schwarzbaer.java.tools.yamahacontrol.Device.NumberWithUnit;

public class VolumeControl extends Canvas {
		private static final long serialVersionUID = -5870265710270984615L;
		private double angle;
		private int radius;
		private double zeroAngle;
		private double value;
		private double deltaPerFullCircle;
		protected boolean isAdjusting;
		private String unit;
		private VolumeControl.ValueListener valueListener;
		
		public interface ValueListener {
			public void valueChanged(double value, boolean isAdjusting);
		}
		
		public VolumeControl(int width, double deltaPerFullCircle, double zeroAngle_deg, VolumeControl.ValueListener valueListener) {
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
				
				private VolumeControl control = VolumeControl.this;
				private double pickAngle = Double.NaN;

				@Override
				public void mousePressed(MouseEvent e) {
					pickAngle = getMouseAngle(e.getX(), e.getY())-angle;
					isAdjusting = true;
//					System.out.printf("pickAngle: %f%n",pickAngle);
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					isAdjusting = false;
					changeValue(e.getX(), e.getY());
					pickAngle = Double.NaN;
//					System.out.printf("pickAngle: %f%n",pickAngle);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					changeValue(e.getX(), e.getY());
//					System.out.printf("angle: %f%n",angle);
				}
				

				private void changeValue(int mouseX, int mouseY) {
					double mouseAngle = getMouseAngle(mouseX, mouseY);
					double diff = mouseAngle-pickAngle-angle;
					if      (Math.abs(diff) > Math.abs(diff+2*Math.PI)) pickAngle -= 2*Math.PI;
					else if (Math.abs(diff) > Math.abs(diff-2*Math.PI)) pickAngle += 2*Math.PI;
					angle = mouseAngle-pickAngle;
					value = angle/2/Math.PI*deltaPerFullCircle;
					
					valueListener.valueChanged(value,isAdjusting);
					control.repaint();
				}

				private double getMouseAngle(int mouseX, int mouseY) {
					int x = mouseX-control.width/2;
					int y = mouseY-control.height/2;
					double mouseAngle = Math.atan2(y,x);
					return mouseAngle;
				}
			};
			addMouseListener(mouseAdapter);
			addMouseMotionListener(mouseAdapter);
		}
		
		public void setValue(NumberWithUnit numberWithUnit) {
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
			
			double angle01 = 0.1*2*Math.PI/deltaPerFullCircle;
			
			double r1 = 0.95; int i=1;
			drawRadiusLine(g, width, height, r1, 1.3, zeroAngle);
			for (double a=angle01; a<Math.PI*0.9; a+=angle01, ++i) {
				double r2 = (i%10)==0?1.3:(i%5)==0?1.15:1.05;
				drawRadiusLine(g, width, height, r1, r2, zeroAngle+a);
				drawRadiusLine(g, width, height, r1, r2, zeroAngle-a);
			}
			
			g.setColor(Color.WHITE);
			g.fillOval(width/2-radius, height/2-radius, radius*2, radius*2);
			
			g.setColor(Color.BLACK);
//			g.drawOval(0, 0, radius*2, radius*2);
			g.drawOval(width/2-radius, height/2-radius, radius*2, radius*2);
			
			i=1;
			if (g2!=null) g2.setStroke( new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND) );
			drawRadiusLine(g, width, height, 0.96, 0.7, angle+zeroAngle);
			if (g2!=null) g2.setStroke(new BasicStroke(1));
			for (double a=angle01; a<Math.PI*1.05; a+=angle01, ++i) {
				double r2 = (i%10)==0?0.7:(i%5)==0?0.85:0.95;
				drawRadiusLine(g, width, height, 1.0, r2, angle+zeroAngle+a);
				if (a<=Math.PI*0.95) drawRadiusLine(g, width, height, 1.0, r2, angle+zeroAngle-a);
			}
			
			
			g.drawString(String.format(Locale.ENGLISH, "%1.1f %s", value, unit), width/2, height/2);
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