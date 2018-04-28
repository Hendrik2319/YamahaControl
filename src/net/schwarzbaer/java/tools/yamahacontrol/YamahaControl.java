package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionListener;
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
		new Responder().createGUI();
		
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
		mainGui.volumeControl.setBorder(BorderFactory.createTitledBorder("Volume"));
		
		JPanel mainControlPanel = new JPanel(new BorderLayout(3,3));
		mainControlPanel.add(devicePanel,BorderLayout.NORTH);
		mainControlPanel.add(scenesInputsPanel,BorderLayout.CENTER);
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
			case IconOff: smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GREEN.darker())); break;
			case IconOn : smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GREEN)); break;
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
		
		VolumeControl(int width) {
			super(width, width);
			
		}
		
		@Override
		protected void paintCanvas(Graphics g, int width, int height) {
			// TODO Auto-generated method stub
	
		}
	
	}

	private static final class Device {

		private String address;
		private boolean isOn;
		private DeviceSceneInput[] scenes;
		private DeviceSceneInput[] inputs;
		private DeviceSceneInput currentScene;
		private DeviceSceneInput currentInput;
		
		Device(String address) {
			this.address = address;
			this.isOn = false;
			this.scenes = null;
			this.inputs = null;
			this.currentScene = null;
			this.currentInput = null;
		}

		public void updateConfig() {
			String value = Ctrl.sendGetCommand_String(address,new TagList("System,Power_Control,Power"));
			isOn = "On".equals(value);
			
			scenes = getSceneInput(new TagList("Main_Zone,Scene,Scene_Sel_Item")); // G4: Main_Zone,Scene,Scene_Sel_Item
			inputs = getSceneInput(new TagList("Main_Zone,Input,Input_Sel_Item")); // G2: Main_Zone,Input,Input_Sel_Item
			
			// TODO Auto-generated method stub
		}

		public boolean isOn() { return isOn; }

		public void setOn(boolean isOn) {
			// System,Power_Control,Power = On
			// System,Power_Control,Power = Standby
			int rc = Ctrl.sendPutCommand(address,new TagList("System,Power_Control,Power"),isOn?"On":"Standby");
			if (rc==Ctrl.RC_OK) this.isOn = isOn;
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
			int rc = Ctrl.sendPutCommand(address,new TagList("Main_Zone,Scene,Scene_Sel"),dsi.ID);
			if (rc==Ctrl.RC_OK) currentScene = dsi;
		}

		public void setInput(DeviceSceneInput dsi) {
			// PUT[P4]:    Main_Zone,Input,Input_Sel   =   Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
			int rc = Ctrl.sendPutCommand(address,new TagList("Main_Zone,Input,Input_Sel"),dsi.ID);
			if (rc==Ctrl.RC_OK) currentInput = dsi;
		}

		public DeviceSceneInput[] getInputs() { return inputs; }
		public DeviceSceneInput[] getScenes() { return scenes; }

		private DeviceSceneInput[] getSceneInput(TagList tagList) {
			Node node = Ctrl.sendGetCommand_Node(address,tagList);
			if (node == null) return null;
			
			NodeList itemNodes = node.getChildNodes();
			DeviceSceneInput[] dsi = new DeviceSceneInput[itemNodes.getLength()];
			for (int i=0; i<itemNodes.getLength(); ++i) {
				Node item = itemNodes.item(i);
				dsi[i] = new DeviceSceneInput();
				NodeList valueNodes = item.getChildNodes();
				for (int v=0; v<valueNodes.getLength(); ++v) {
					Node value = valueNodes.item(v);
					String valueStr = XML.getContentOfSingleChildTextNode(value);
					switch (value.getNodeName()) {
					case "Param"     : dsi[i].ID        = valueStr; break;
					case "RW"        : dsi[i].rw        = valueStr; break;
					case "Title"     : dsi[i].title     = valueStr; break;
					case "Src_Name"  : dsi[i].srcName   = valueStr; break;
					case "Src_Number": dsi[i].srcNumber = valueStr; break;
					}
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
