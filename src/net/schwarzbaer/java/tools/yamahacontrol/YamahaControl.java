package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.activation.DataHandler;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
		Ctrl.readCommProtocolFromFile();
//		new Responder().openWindow();
		
		YamahaControl yamahaControl = new YamahaControl();
		yamahaControl.createGUI();
		
		
//		testByteBuffers();
		
//		try {
//			test();
//		} catch (MalformedURLException e) {
//			e.printStackTrace();
//		}
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

	private enum SmallImages { IconOn, IconOff, IconUnknown }
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
		
		JPanel devicePanel = new JPanel(/*new GridLayout(1,0,3,3)*/);
		devicePanel.setLayout(new BoxLayout(devicePanel, BoxLayout.X_AXIS));
		devicePanel.setBorder(BorderFactory.createTitledBorder("Device"));
		devicePanel.add(createButton("Connect",e->connectToReciever(),true));
		devicePanel.add(mainGui.onoffBtn);
		devicePanel.add(createButton("Open Command List",e->CommandList.openWindow(),true));
		
		JTabbedPane scenesInputsPanel = mainGui.createScenesInputsPanel();
		scenesInputsPanel.setBorder(BorderFactory.createTitledBorder("Scenes/Inputs"));
		scenesInputsPanel.setPreferredSize(new Dimension(250, 400));
		
		mainGui.createVolumeControl(200);
		JPanel volumePanel = new JPanel(new BorderLayout());
		volumePanel.setBorder(BorderFactory.createTitledBorder("Volume"));
		volumePanel.add( mainGui.volumeControl, BorderLayout.CENTER );
		
		JPanel mainControlPanel = new JPanel(new BorderLayout(3,3));
		mainControlPanel.add(devicePanel,BorderLayout.NORTH);
		mainControlPanel.add(scenesInputsPanel,BorderLayout.CENTER);
		mainControlPanel.add(volumePanel,BorderLayout.SOUTH);
		
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
			case IconOff    : smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GREEN.darker())); break;
			case IconOn     : smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GREEN)); break;
			case IconUnknown: smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GRAY)); break;
			}
		}
	}

	private void connectToReciever() {
		String addr = Config.selectAddress(mainWindow);
		if (addr!=null) {
			device = new Device(addr);
			device.updateConfig();
			guiRegion.forEach(gr->gr.initGUIafterConnect());
		}
	}

	// ///////////////////////////////////////////////////////////////////////////////////
	// 
	//    Toolbox methods
	//
	static JButton createButton(String title, ActionListener l, boolean enabled) {
		JButton button = new JButton(title);
		button.setEnabled(enabled);
		if (l!=null) button.addActionListener(l);
		return button;
	}

	static JToggleButton createToggleButton(String title, ActionListener l, boolean enabled, ButtonGroup bg) {
		JToggleButton button = new JToggleButton(title);
		button.setEnabled(enabled);
		if (l!=null) button.addActionListener(l);
		bg.add(button);
		return button;
	}

	static <A> JComboBox<A> createComboBox(A[] values, ActionListener l) {
		JComboBox<A> comboBox = new JComboBox<A>(values);
		if (l!=null) comboBox.addActionListener(l);
		return comboBox;
	}

	public static void copyToClipBoard(String str) {
		if (str==null) return;
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Clipboard clipboard = toolkit.getSystemClipboard();
		DataHandler content = new DataHandler(str,"text/plain");
		try { clipboard.setContents(content,null); }
		catch (IllegalStateException e1) { e1.printStackTrace(); }
	}

	public static String pasteFromClipBoard() {
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Clipboard clipboard = toolkit.getSystemClipboard();
		Transferable transferable = clipboard.getContents(null);
		if (transferable==null) return null;
		
		DataFlavor textFlavor = new DataFlavor(String.class, "text/plain; class=<java.lang.String>");
		
		if (!transferable.isDataFlavorSupported(textFlavor)) {
			DataFlavor[] transferDataFlavors = transferable.getTransferDataFlavors();
			if (transferDataFlavors==null || transferDataFlavors.length==0) return null;
			
			System.out.println("transferDataFlavors: "+toString(transferDataFlavors));
			textFlavor = DataFlavor.selectBestTextFlavor(transferDataFlavors);
		}
		
		if (textFlavor==null) return null;
		
		Reader reader;
		try { reader = textFlavor.getReaderForText(transferable); }
		catch (UnsupportedFlavorException | IOException e) { return null; }
		StringWriter sw = new StringWriter();
		
		int n; char[] cbuf = new char[100000];
		try { while ((n=reader.read(cbuf))>=0) if (n>0) sw.write(cbuf, 0, n); }
		catch (IOException e) {}
		
		try { reader.close(); } catch (IOException e) {}
		return sw.toString();
	}

	private static String toString(DataFlavor[] dataFlavors) {
		if (dataFlavors==null) return "<null>";
		String str = "";
		for (DataFlavor df:dataFlavors) {
			if (!str.isEmpty()) str+=",\r\n";
			str+=""+df;
		}
		return "[\r\n"+str+"\r\n]";
	}
	// ///////////////////////////////////////////////////////////////////////////////////
	
	public static interface GuiRegion {
		void setEnabled(boolean enabled);
		void initGUIafterConnect();
	}
	
	private class MainGui implements GuiRegion {

		public JButton onoffBtn;
		private VolumeControl volumeControl;
		private DsiPanel scenesPanel;
		private DsiPanel inputsPanel;
		
		MainGui() {
			onoffBtn = null;
			volumeControl = null;
			scenesPanel = null;
			inputsPanel = null;
		}

		@Override
		public void setEnabled(boolean enabled) {
			onoffBtn.setEnabled(enabled);
			volumeControl.setEnabled(enabled);
		}

		@Override
		public void initGUIafterConnect() {
			setEnabled(device!=null);
			setOnOffButton(device==null?false:device.isOn);
			//volumeControl.setValue(device==null?0:device.volume);
			
			if (device!=null) {
				scenesPanel.createButtons(device.getScenes(),this::setScene,dsi->dsi!=null && "W".equals(dsi.rw));
				inputsPanel.createButtons(device.getInputs(),this::setInput,null);
				selectCurrentScene();
				selectCurrentInput();
			}
		}

		private void setScene(Device.DeviceSceneInput dsi) {
			if (device==null) return;
			device.setScene(dsi);
			selectCurrentScene();
		}

		private void setInput(Device.DeviceSceneInput dsi) {
			if (device==null) return;
			device.setInput(dsi);
			selectCurrentInput();
		}

		private void selectCurrentScene() {
			if (scenesPanel!=null) scenesPanel.setSelected(device.getScene());
		}

		private void selectCurrentInput() {
			if (inputsPanel!=null) inputsPanel.setSelected(device.getInput());
		}

		public JTabbedPane createScenesInputsPanel() {
			scenesPanel = new DsiPanel();
			inputsPanel = new DsiPanel();
			
			JTabbedPane scenesInputsPanel = new JTabbedPane();
			scenesInputsPanel.add("Scenes", new JScrollPane(scenesPanel));
			scenesInputsPanel.add("Inputs", new JScrollPane(inputsPanel));
			
			return scenesInputsPanel;
		}

		public void createVolumeControl(int width) {
			volumeControl = new VolumeControl(width, 3.0, -90);
		}

		public void createOnOffBtn() {
			onoffBtn = createButton("", e->toggleOnOff(), false);
			setOnOffButton(false);
		}

		private void setOnOffButton(Boolean isOn) {
			onoffBtn.setIcon(smallImages.get(isOn==null?SmallImages.IconUnknown:isOn?SmallImages.IconOn:SmallImages.IconOff));
			onoffBtn.setText(isOn==null?"??":isOn?"On":"Off");
		}

		private void toggleOnOff() {
			if (device!=null) device.setOn(!device.isOn());
			setOnOffButton(device==null?false:device.isOn());
		}
		
		private class DsiPanel extends JPanel {
			private static final long serialVersionUID = -3330564101527546450L;
			
			private ButtonGroup bg;
			private HashMap<Device.DeviceSceneInput,JToggleButton> buttons;

			private GridBagLayout layout;
			DsiPanel() {
				super();
				bg = null;
				buttons = null;
			}
			
			public void setSelected(Device.DeviceSceneInput dsi) {
				JToggleButton button = buttons.get(dsi);
				if (button!=null) bg.setSelected(button.getModel(), true);
			}

			public void createButtons(Device.DeviceSceneInput[] dsiArr, Consumer<Device.DeviceSceneInput> setFunction, Predicate<Device.DeviceSceneInput> filter) {
				removeAll();
				layout = new GridBagLayout();
				setLayout(layout);
				GridBagConstraints c = new GridBagConstraints();
				c.weighty=0;
				
				bg = new ButtonGroup();
				buttons = new HashMap<>();
				for (Device.DeviceSceneInput dsi:dsiArr) {
					if (filter!=null && !filter.test(dsi)) continue;
					String title = dsi.title==null?"<???>":dsi.title.trim();
					JToggleButton button = createToggleButton(title, e->setFunction.accept(dsi), true, bg);
					buttons.put(dsi,button);
					addComp(c,button,0,1,GridBagConstraints.HORIZONTAL);
					addComp(c,new JLabel("["+dsi.ID+"]",JLabel.CENTER),1,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
					//addComp(c,new JLabel(dsi.rw),0,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
				}
				c.weighty=1;
				addComp(c,new JLabel(""),1,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
			}
			
			private void addComp(GridBagConstraints c, Component comp, double weightx, int gridwidth, int fill) {
				c.weightx=weightx;
				c.gridwidth=gridwidth;
				c.fill = fill;
				layout.setConstraints(comp, c);
				add(comp);
			}
		}
	}
	
	private static class VolumeControl extends Canvas {
		private static final long serialVersionUID = -5870265710270984615L;
		private double angle;
		private int radius;
		private double mouseAngle;
		private double zeroAngle;
		private double value;
		private double deltaPerFullCircle;
		protected boolean isAdjusting;
		
		VolumeControl(int width, double deltaPerFullCircle, double zeroAngle_deg) {
			super(width, width);
			this.deltaPerFullCircle = deltaPerFullCircle;
			zeroAngle = zeroAngle_deg/180*Math.PI;
			
			radius = width/2-20;
			angle = 0.0;
			mouseAngle = 0.0;
			
			MouseAdapter mouseAdapter = new MouseAdapter() {

				private double pickAngle = Double.NaN;

				@Override
				public void mousePressed(MouseEvent e) {
					int x = e.getX()-VolumeControl.this.width/2;
					int y = e.getY()-VolumeControl.this.height/2;
					mouseAngle = Math.atan2(y,x);
					pickAngle = mouseAngle-angle;
					isAdjusting = true;
//					System.out.printf("pickAngle: %f%n",pickAngle);
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					pickAngle = Double.NaN;
					isAdjusting = false;
//					System.out.printf("pickAngle: %f%n",pickAngle);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					int x = e.getX()-VolumeControl.this.width/2;
					int y = e.getY()-VolumeControl.this.height/2;
					mouseAngle = Math.atan2(y,x);
					double diff = mouseAngle-pickAngle-angle;
					if      (Math.abs(diff) > Math.abs(diff+2*Math.PI)) pickAngle -= 2*Math.PI;
					else if (Math.abs(diff) > Math.abs(diff-2*Math.PI)) pickAngle += 2*Math.PI;
					angle = mouseAngle-pickAngle;
					value = angle/2/Math.PI*deltaPerFullCircle;
//					System.out.printf("angle: %f%n",angle);
					VolumeControl.this.repaint();
				}
				
			};
			addMouseListener(mouseAdapter);
			addMouseMotionListener(mouseAdapter);
		}
		
		public void setValue(double value) {
			this.value = value;
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
			
			
			g.drawString(String.format(Locale.ENGLISH, "%1.1f", value), width/2, height/2);
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
	
	enum KnownCommand {
		GetSceneItems("Main_Zone,Scene,Scene_Sel_Item"),
		GetInputItems("Main_Zone,Input,Input_Sel_Item"),
		GetNSetSystemPower("System,Power_Control,Power"),
		SetCurrentScene("Main_Zone,Scene,Scene_Sel"),
		SetCurrentInput("Main_Zone,Input,Input_Sel"),
		GetBasicStatus("Main_Zone,Basic_Status")
		;
		
		final TagList tagList;
		KnownCommand(String tagListStr) { tagList = new TagList(tagListStr); }
	}
	
	private static interface Value {
		public String getLabel();
		
		public enum OnOff implements Value { On, Off; @Override public String getLabel() { return toString(); }  } 
		public enum PowerState implements Value { On, Standby; @Override public String getLabel() { return toString(); }  } 
		public enum SleepState implements Value {
			_120min("120 min"),_90min("90 min"),_60min("60 min"),_30min("30 min"),Off("Off");
			private String label;
			SleepState(String label) { this.label = label; }
			@Override public String getLabel() { return label; }  } 
	}
	
	private static class NumberWithUnit {
		final Float number;
		final String unit;
		
		public NumberWithUnit(String val, String exp, String unit) {
			this.unit = unit;
			float value;
			int exponent;
			try {
				value = Integer.parseInt(val);
				exponent = Integer.parseInt(exp);
			} catch (NumberFormatException e) {
				this.number = null;
				return;
			}
			for (int i=0; i<exponent; ++i) value/=10;
			this.number = value;
		}
	}

	private static final class Device {
		
		private String address;
		private BasicStatus basicStatus;
		private Boolean isOn;
		private DeviceSceneInput[] scenes;
		private DeviceSceneInput[] inputs;
		private DeviceSceneInput currentScene;
		private DeviceSceneInput currentInput;
		
		Device(String address) {
			this.address = address;
			this.basicStatus = null;
			this.isOn = null;
			this.scenes = null;
			this.inputs = null;
			this.currentScene = null;
			this.currentInput = null;
		}

		public void updateConfig() {
			isOn = askOn();
			scenes = getSceneInput(KnownCommand.GetSceneItems); // G4: Main_Zone,Scene,Scene_Sel_Item
			inputs = getSceneInput(KnownCommand.GetInputItems); // G2: Main_Zone,Input,Input_Sel_Item
			updateBasicStatus();
		}
		
		public void updateBasicStatus() {
			Node node = Ctrl.sendGetCommand_Node(address,KnownCommand.GetBasicStatus);
			this.basicStatus = BasicStatus.parseNode(node);
		}
		
		private static String getSubValue(Node node, String... tagList) {
			Vector<Node> nodes = XML.getSubNodes(node, tagList);
			if (nodes.isEmpty()) return null;
			if (nodes.size()>1) Log.warning(Device.class, "getSubValue found more than one value node: Node=%s TagList=%s", XML.getPath(node), Arrays.toString(tagList));
			
			return XML.getContentOfSingleChildTextNode(nodes.get(0));
		}
		
		private static <T extends Value> T getSubValue(Node node, T[] values, String... tagList) {
			Vector<Node> nodes = XML.getSubNodes(node, tagList);
			if (nodes.isEmpty()) return null;
			
			String str = XML.getContentOfSingleChildTextNode(nodes.get(0));
			if (str==null) return null;
			
			for (T val:values)
				if (str.equals(val.getLabel()))
					return val;
			
			return null;
		}

		public static NumberWithUnit getNumberWithUnit(Node value, String string) {
			return new NumberWithUnit(
					getSubValue(value,"Val"),
					getSubValue(value,"Exp"),
					getSubValue(value,"Unit")
				);
		}

		private static class BasicStatus {

			private Value.PowerState power;
			private Value.SleepState sleep;
			private NumberWithUnit volume;
			private Value.OnOff volMute;
			private String currentInput;
			private DeviceSceneInput inputInfo;

			public BasicStatus() {
				this.power = null;
				this.sleep = null;
				this.volume = null;
				this.volMute = null;
				this.currentInput = null;
				this.inputInfo = null;
			}

			public static BasicStatus parseNode(Node node) {
				BasicStatus status = new BasicStatus();
				NodeList valueNodes = node.getChildNodes();
				for (int i=0; i<valueNodes.getLength(); ++i) {
					Node value = valueNodes.item(i);
					switch (value.getNodeName()) {
					case "Power_Control":
						status.power = getSubValue(value,Value.PowerState.values(),"Power");
						status.sleep = getSubValue(value,Value.SleepState.values(),"Sleep");
						break;
					case "Volume":
						status.volume = getNumberWithUnit(value,"Lvl");
						status.volMute = getSubValue(value,Value.OnOff.values(),"Mute");
						break;
					case "Input":
						status.currentInput = getSubValue(value,"Input_Sel");
						Vector<Node> nodes = XML.getChildNodesByNodeName(value, "Input_Sel_Item_Info");
						if (nodes.isEmpty()) Log.error(Device.class, "Can't find value node: Node=%s TagName=[%s]", XML.getPath(value), "Input_Sel_Item_Info");
						else {
							if (nodes.size()>1) Log.warning(Device.class, "Found more than one value node: Node=%s TagName=[%s]", XML.getPath(value), "Input_Sel_Item_Info");
							status.inputInfo = parseDeviceSceneInput(nodes.get(0));
						}
						break;
					}
					
				}
					
					//dsiArr[i] = parseDeviceSceneInput(itemNodes.item(i));
				// TODO Auto-generated method stub
				return null;
			}
		}

		public Boolean isOn() { return isOn; }

		public void setOn(boolean isOn) {
			// System,Power_Control,Power = On
			// System,Power_Control,Power = Standby
			int rc = Ctrl.sendPutCommand(address,KnownCommand.GetNSetSystemPower,isOn?"On":"Standby");
			if (rc!=Ctrl.RC_OK) return;
			this.isOn = askOn();
		}

		private Boolean askOn() {
			String value = Ctrl.sendGetCommand_String(address,KnownCommand.GetNSetSystemPower);
			if ("On".equals(value)) return true;
			if ("Standby".equals(value)) return false;
			return null;
		}

		public DeviceSceneInput getScene() {
			// GET[G1]:    Main_Zone,Basic_Status   ->   Input,Input_Sel -> Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
			return currentScene;
		}

		public DeviceSceneInput getInput() {
			// GET[G1]:    Main_Zone,Basic_Status   ->   Input,Input_Sel -> Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
			return currentInput;
		}

		public void setScene(DeviceSceneInput dsi) {
			// PUT[P6]:    Main_Zone,Scene,Scene_Sel   =   Values [GET[G4]:Main_Zone,Scene,Scene_Sel_Item]
			int rc = Ctrl.sendPutCommand(address,KnownCommand.SetCurrentScene,dsi.ID);
			if (rc==Ctrl.RC_OK) currentScene = dsi;
		}

		public void setInput(DeviceSceneInput dsi) {
			// PUT[P4]:    Main_Zone,Input,Input_Sel   =   Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
			int rc = Ctrl.sendPutCommand(address,KnownCommand.SetCurrentInput,dsi.ID);
			if (rc==Ctrl.RC_OK) currentInput = dsi;
		}

		public DeviceSceneInput[] getInputs() { return inputs; }
		public DeviceSceneInput[] getScenes() { return scenes; }

		private DeviceSceneInput[] getSceneInput(KnownCommand knownCommand) {
			Node node = Ctrl.sendGetCommand_Node(address,knownCommand);
			if (node == null) return null;
			
			NodeList itemNodes = node.getChildNodes();
			DeviceSceneInput[] dsiArr = new DeviceSceneInput[itemNodes.getLength()];
			for (int i=0; i<itemNodes.getLength(); ++i)
				dsiArr[i] = parseDeviceSceneInput(itemNodes.item(i));
			return dsiArr;
		}

		private static DeviceSceneInput parseDeviceSceneInput(Node item) {
			DeviceSceneInput dsi = new DeviceSceneInput();
			NodeList valueNodes = item.getChildNodes();
			for (int v=0; v<valueNodes.getLength(); ++v) {
				Node value = valueNodes.item(v);
				switch (value.getNodeName()) {
				case "Param"     : dsi.ID        = XML.getContentOfSingleChildTextNode(value); break;
				case "RW"        : dsi.rw        = XML.getContentOfSingleChildTextNode(value); break;
				case "Title"     : dsi.title     = XML.getContentOfSingleChildTextNode(value); break;
				case "Src_Name"  : dsi.srcName   = XML.getContentOfSingleChildTextNode(value); break;
				case "Src_Number": dsi.srcNumber = XML.getContentOfSingleChildTextNode(value); break;
				}
			}
			return dsi;
		}
		
		private static class DeviceSceneInput {
			public String ID;
			public String rw;
			public String title;
			@SuppressWarnings("unused") public String srcName;
			@SuppressWarnings("unused") public String srcNumber;
			public DeviceSceneInput() {
				this.ID = null;
				this.rw = null;
				this.title = null;
				this.srcName = null;
				this.srcNumber = null;
			}
			
		}
	}
	
	static class Log {
		public static void info   (Class<?> callerClass,                String format, Object... values) { out(System.out, callerClass, "INFO",         format, values); }
		public static void warning(Class<?> callerClass,                String format, Object... values) { out(System.out, callerClass, "INFO",         format, values); }
		public static void error  (Class<?> callerClass,                String format, Object... values) { out(System.out, callerClass, "INFO",         format, values); }
		public static void info   (Class<?> callerClass, Locale locale, String format, Object... values) { out(System.out, callerClass, "INFO", locale, format, values); }
		public static void warning(Class<?> callerClass, Locale locale, String format, Object... values) { out(System.out, callerClass, "INFO", locale, format, values); }
		public static void error  (Class<?> callerClass, Locale locale, String format, Object... values) { out(System.out, callerClass, "INFO", locale, format, values); }
		
		private static void out(PrintStream out, Class<?> callerClass, String label,                String format, Object... values) { out(out, callerClass, label, Locale.ENGLISH, format, values); }
		private static void out(PrintStream out, Class<?> callerClass, String label, Locale locale, String format, Object... values) {
			out.printf(locale, "[%s] %s: %s%n", callerClass==null?"???":callerClass.getSimpleName(), label, String.format(format, values));
		}
	}

}
