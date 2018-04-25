package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.schwarzbaer.gui.Canvas;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.yamahacontrol.XML.TagList;

public class YamahaControl {

	@SuppressWarnings("unused")
	private static void testByteBuffers() {
		String command = "<YAMAHA_AV cmd=\"GET\"><System><Power_Control><Power>GetParam</Power></Power_Control></System></YAMAHA_AV>";
		ByteBuffer bytes = StandardCharsets.UTF_8.encode(command);
		System.out.println("capacity: "+bytes.capacity());
		System.out.println("limit   : "+bytes.limit   ());
		System.out.println("position: "+bytes.position());
		
		byte[] array = new byte[bytes.limit()];
		bytes.get(array);		
		System.out.println("array.length: "+array.length);
		System.out.println("array: "+Arrays.toString(array));
	}

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		Config.readConfig();

//		testByteBuffers();
		
//		try {
//			test();
//		} catch (MalformedURLException e) {
//			e.printStackTrace();
//		}
		
		YamahaControl yamahaControl = new YamahaControl();
		yamahaControl.createGUI();
		
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><System><Power_Control><Power>GetParam</Power></Power_Control></System></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"PUT\"><System><Power_Control><Power>On</Power></Power_Control></System></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"PUT\"><System><Power_Control><Power>Standby</Power></Power_Control></System></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Basic_Status><Power_Control><Power>GetParam</Power></Power_Control></Basic_Status></Main_Zone></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Basic_Status><Power_Control><Power>On</Power></Power_Control></Basic_Status></Main_Zone></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Basic_Status><Power_Control><Power>On</Power></Power_Control></Basic_Status></Main_Zone></YAMAHA_AV>");
		
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Input><Input_Sel>GetParam</Input_Sel></Input></Main_Zone></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Input><Input_Sel_Item>GetParam</Input_Sel_Item></Input></Main_Zone></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Scene><Scene_Sel>GetParam</Scene_Sel></Scene></Main_Zone></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Scene><Scene_Sel_Item>GetParam</Scene_Sel_Item></Scene></Main_Zone></YAMAHA_AV>");
		
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Basic_Status><Power_Control><Power></Power></Power_Control></Basic_Status></Main_Zone></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"PUT\"><Main_Zone><Power_Control><Power>On</Power></Power_Control></Main_Zone></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"PUT\"><Main_Zone><Power_Control><Power>Standby</Power></Power_Control></Main_Zone></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Power_Control><Power>GetParam</Power></Power_Control></Main_Zone></YAMAHA_AV>");
		
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Basic_Status>GetParam</Basic_Status></Main_Zone></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Config>GetParam</Config></Main_Zone></YAMAHA_AV>");
		
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><NET_RADIO><List_Info>GetParam</List_Info></NET_RADIO></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><NET_RADIO><Play_Info>GetParam</Play_Info></NET_RADIO></YAMAHA_AV>");
//		Ctrl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><NET_RADIO><Config>GetParam</Config></NET_RADIO></YAMAHA_AV>");
	}

	private enum SmallImages { IconOn, IconOff }
	private EnumMap<SmallImages,Icon> smallImages;
	private StandardMainWindow mainWindow;
	private Device device;
	private MainGui mainGui;
	private Vector<GuiRegion> guiRegion;
	
	YamahaControl() {
		smallImages = new EnumMap<>(SmallImages.class);
		mainWindow = null;
		device = null;
		mainGui = null;
		guiRegion = new Vector<>();
	}
	
	private void createGUI() {
		createSmallImages();
		
		mainGui = new MainGui();
		guiRegion.add(mainGui);
		
		mainGui.createOnOffBtn();
		
		JPanel devicePanel = new JPanel(new GridLayout(1,0,3,3));
		devicePanel.setBorder(BorderFactory.createTitledBorder("Device"));
		devicePanel.add(createButton("Connect",e->connectToReciever(),true));
		devicePanel.add(mainGui.onoffBtn);
		
		mainGui.createScenePanel();
		JScrollPane scenePanel = new JScrollPane(mainGui.scenePanel);
		scenePanel.setBorder(BorderFactory.createTitledBorder("Scene/Input"));
		
		mainGui.createVolumeControl(200);
		mainGui.volumeControl.setBorder(BorderFactory.createTitledBorder("Volume"));
		
		JPanel mainControlPanel = new JPanel(new BorderLayout(3,3));
		mainControlPanel.add(devicePanel,BorderLayout.NORTH);
		mainControlPanel.add(scenePanel,BorderLayout.CENTER);
		mainControlPanel.add(mainGui.volumeControl,BorderLayout.SOUTH);
		
		JTabbedPane subUnitPanel = new JTabbedPane();
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		contentPane.add(mainControlPanel,BorderLayout.WEST);
		contentPane.add(subUnitPanel,BorderLayout.CENTER);
		
		// TODO YamahaControl.createGUI
		
		mainWindow = new StandardMainWindow("YamahaControl");
		mainWindow.startGUI(contentPane);
		
		guiRegion.forEach(gr->gr.setEnabled(false));
	}
	
	private static class ImageToolbox {

		public static Icon createIcon_Circle(int imgWidth, int imgHeight, int diameter, Color border, Color fill) {
			BufferedImage image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = image.createGraphics();
			
			g2.setPaint(fill);
			g2.fillOval(imgWidth/2-diameter/2, imgHeight/2-diameter/2, diameter, diameter);
			
			g2.setPaint(border);
			g2.drawOval(imgWidth/2-diameter/2, imgHeight/2-diameter/2, diameter, diameter);
			
			return new ImageIcon(image);
		}
		
	}

	private void createSmallImages() {
		for (SmallImages id:SmallImages.values()) {
			switch (id) {
			case IconOff: smallImages.put(id, ImageToolbox.createIcon_Circle(16,16,10,Color.BLACK,Color.GREEN.darker())); break;
			case IconOn : smallImages.put(id, ImageToolbox.createIcon_Circle(16,16,10,Color.BLACK,Color.GREEN)); break;
			}
		}
	}

	private void connectToReciever() {
		String addr = Config.selectAddress(mainWindow);
		if (addr!=null) {
			device = new Device(addr);
			device.updateConfig();
			guiRegion.forEach(gr->gr.updateGUI());
		}
	}

	private JButton createButton(String title, ActionListener l, boolean enabled) {
		JButton button = new JButton(title);
		button.setEnabled(enabled);
		if (l!=null) button.addActionListener(l);
		return button;
	}
	
	public static interface GuiRegion {
		void setEnabled(boolean enabled);
		void updateGUI();
	}
	
	private class MainGui implements GuiRegion {

		public JPanel scenePanel;
		public JButton onoffBtn;
		private VolumeControl volumeControl;

		@Override
		public void setEnabled(boolean enabled) {
			onoffBtn.setEnabled(enabled);
			volumeControl.setEnabled(enabled);
		}

		@Override
		public void updateGUI() {
			setEnabled(device!=null);
			setOnOffButton(device==null?false:device.isOn);
			//volumeControl.setValue(device==null?0:device.volume);
			
			// TODO Auto-generated method stub
		}

		public void createScenePanel() {
			scenePanel = new JPanel();
			// TODO Auto-generated method stub
		}

		public void createVolumeControl(int width) {
			volumeControl = new VolumeControl(width);
		}

		public void createOnOffBtn() {
			onoffBtn = createButton("", e->toggleOnOff(), false);
			setOnOffButton(false);
		}

		private void setOnOffButton(boolean isOn) {
			onoffBtn.setIcon(smallImages.get(isOn?SmallImages.IconOn:SmallImages.IconOff));
			onoffBtn.setText(isOn?"On":"Off");
		}

		private void toggleOnOff() {
			if (device!=null) device.setOn(!device.isOn());
			setOnOffButton(device==null?false:device.isOn());
		}
		
	}
	
	private static class VolumeControl extends Canvas {
		private static final long serialVersionUID = -5870265710270984615L;
		
		VolumeControl(int width) {
			super(width, width);
			
		}
		
		@Override
		protected void paintCanvas(Graphics g, int width, int height) {
			// TODO Auto-generated method stub
	
		}
	
	}

	private static final class Device {

		private boolean isOn;
		private String address;
		
		Device(String address) {
			this.address = address;
		}

		public void updateConfig() {
			String value = Ctrl.sendGetCommand(address,new TagList("System,Power_Control,Power"));
			isOn = "On".equals(value);
			// TODO Auto-generated method stub
		}

		public boolean isOn() { return isOn; }
		public void setOn(boolean isOn) {
			// System,Power_Control,Power = On
			// System,Power_Control,Power = Standby
			int rc = Ctrl.sendPutCommand(address,new TagList("System,Power_Control,Power"),isOn?"On":"Standby");
			if (rc==Ctrl.RC_OK) this.isOn = isOn;
		}
	
	}

}
