package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
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
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.yamahacontrol.Device.NetRadio.ListInfo;
import net.schwarzbaer.java.tools.yamahacontrol.Device.UpdateWish;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.CursorSelect;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.PageSelect;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.PlayState;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.ReadyOrNot;
import net.schwarzbaer.java.tools.yamahacontrol.gui.VolumeControl;

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
		
		createSmallImages();
		
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

	public enum SmallImages { IconOn, IconOff, IconUnknown }
	public static EnumMap<SmallImages,Icon> smallImages = null;
	private static void createSmallImages() {
		smallImages = new EnumMap<>(SmallImages.class);
		for (SmallImages id:SmallImages.values()) {
			switch (id) {
			case IconOff    : smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GREEN.darker())); break;
			case IconOn     : smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GREEN)); break;
			case IconUnknown: smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GRAY)); break;
			}
		}
	}

	private StandardMainWindow mainWindow;
	private Device device;
	private MainGui mainGui;
	private Vector<GuiRegion> guiRegions;
	private FrequentlyUpdater frequentlyUpdater;
	
	YamahaControl() {
		mainWindow = null;
		device = null;
		mainGui = null;
		guiRegions = new Vector<>();
		frequentlyUpdater = new FrequentlyUpdater(2000);
	}
	
	private void createGUI() {
		
		mainGui = new MainGui();
		guiRegions.add(mainGui);
		
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
		
		new SubUnitNetRadio().addTo(guiRegions,subUnitPanel);
		new SubUnitTuner   ().addTo(guiRegions,subUnitPanel);
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		contentPane.add(mainControlPanel,BorderLayout.WEST);
		contentPane.add(subUnitPanel,BorderLayout.CENTER);
		
		mainWindow = new StandardMainWindow("YamahaControl");
		mainWindow.startGUI(contentPane);
		
		guiRegions.forEach(gr->gr.setEnabled(false));
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

	private void updateDevice(UpdateReason reason) {
		EnumSet<UpdateWish> updateWishes = EnumSet.noneOf(UpdateWish.class);
		guiRegions.forEach((GuiRegion gr)->updateWishes.addAll(gr.getUpdateWishes(reason)));
		device.update(updateWishes);
	}

	private void connectToReciever() {
		String addr = Config.selectAddress(mainWindow);
		if (addr!=null) {
			device = new Device(addr);
			updateDevice(UpdateReason.Initial);
			guiRegions.forEach(gr->gr.initGUIafterConnect(device));
			frequentlyUpdater.start();
		}
	}

	public class FrequentlyUpdater implements Runnable {

		private int interval_ms;
		private boolean stop;

		public FrequentlyUpdater(int interval_ms) {
			this.interval_ms = interval_ms;
			this.stop = false;
		}

		public void start() {
			stop = false;
			new Thread(this).start();
		}

		public void stop() {
			stop = true;
			notify();
		}

		@Override
		public void run() {
			synchronized (this) {
				while (!stop) {
					long startTime = System.currentTimeMillis();
					while (!stop && System.currentTimeMillis()-startTime<interval_ms)
						try { wait(interval_ms-(System.currentTimeMillis()-startTime)); }
						catch (InterruptedException e) {}
					
					updateDevice(UpdateReason.Frequently);
					SwingUtilities.invokeLater(()->guiRegions.forEach(gr->gr.frequentlyUpdate()));
				}
			}
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
	
	static float getScrollPos(JScrollBar scrollBar) {
		int min = scrollBar.getMinimum();
		int max = scrollBar.getMaximum();
		int ext = scrollBar.getVisibleAmount();
		int val = scrollBar.getValue();
		return (val-min)/((float)max-ext-min);
	}

	static void setScrollPos(JScrollBar scrollBar, float pos) {
		int min = scrollBar.getMinimum();
		int max = scrollBar.getMaximum();
		int ext = scrollBar.getVisibleAmount();
		SwingUtilities.invokeLater(()->scrollBar.setValue(Math.round(pos*(max-ext-min)+min)));
	}

	static void copyToClipBoard(String str) {
		if (str==null) return;
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Clipboard clipboard = toolkit.getSystemClipboard();
		DataHandler content = new DataHandler(str,"text/plain");
		try { clipboard.setContents(content,null); }
		catch (IllegalStateException e1) { e1.printStackTrace(); }
	}

	static String pasteFromClipBoard() {
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
	
	enum UpdateReason { Initial, Frequently }

	public static interface GuiRegion {
		void setEnabled(boolean enabled);
		void initGUIafterConnect(Device device);
		void frequentlyUpdate();
		Collection<Device.UpdateWish> getUpdateWishes(UpdateReason reason);
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

		@Override public void setEnabled(boolean enabled) {
			onoffBtn.setEnabled(enabled);
			volumeControl.setEnabled(enabled);
			scenesPanel.setEnabled(enabled);
			inputsPanel.setEnabled(enabled);
		}

		@Override
		public Collection<UpdateWish> getUpdateWishes(UpdateReason reason) {
			switch (reason) {
			case Initial   : return Arrays.asList( new UpdateWish[] { UpdateWish.Power, UpdateWish.BasicStatus, UpdateWish.Scenes, UpdateWish.Inputs } );
			case Frequently: return Arrays.asList( new UpdateWish[] { UpdateWish.Power, UpdateWish.BasicStatus } );
			}
			return null;
		}

		@Override
		public void initGUIafterConnect(Device _device) {
			setEnabled(device!=null);
			
			if (device!=null) {
				scenesPanel.createButtons(device.getScenes(),this::setScene,dsi->dsi!=null && "W".equals(dsi.rw));
				inputsPanel.createButtons(device.getInputs(),this::setInput,null);
				selectCurrentScene();
				selectCurrentInput();
			}
			
			frequentlyUpdate();
		}

		@Override
		public void frequentlyUpdate() {
			setOnOffButton(device==null?false:device.isOn());
			volumeControl.setValue(device==null?null:device.getVolume());
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
			volumeControl = new VolumeControl(width, 3.0, -90, (value, isAdjusting) -> {
				if (device==null) return;
				device.setVolume(value);
				if (!isAdjusting) 
					volumeControl.setValue(device.getVolume());
			});
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
	
	private static abstract class SubUnit extends JPanel implements GuiRegion {
		private static final long serialVersionUID = 7368584160348326790L;
		
		protected Device device;
		private JLabel readyStateLabel;
		private JLabel tabHeaderComp;

		protected SubUnit(String tabTitle) {
			super(new BorderLayout(3,3));
			tabHeaderComp = new JLabel(tabTitle);
			readyStateLabel = new JLabel("???",smallImages.get(SmallImages.IconUnknown),JLabel.LEFT);
			add(readyStateLabel,BorderLayout.NORTH);
			add(createContentPanel(),BorderLayout.CENTER);
		}

		public void addTo(Vector<GuiRegion> guiRegions, JTabbedPane subUnitPanel) {
			guiRegions.add(this);
			int index = subUnitPanel.getTabCount();
			subUnitPanel.insertTab("tabTitle",null,this,null, index);
			subUnitPanel.setTabComponentAt(index, tabHeaderComp);
		}

		@Override
		public void initGUIafterConnect(Device device) {
			this.device = device;
			frequentlyUpdate();
		}

		@Override
		public void frequentlyUpdate() {
			boolean isReady = getReadyState();
			tabHeaderComp.setOpaque(isReady);
			tabHeaderComp.setBackground(isReady?Color.GREEN:null);
			readyStateLabel.setText(isReady?"Ready":"Not Ready");
			readyStateLabel.setIcon(isReady?smallImages.get(SmallImages.IconOn):smallImages.get(SmallImages.IconOff));
		}

		@Override
		public Collection<UpdateWish> getUpdateWishes(UpdateReason reason) {
			return new Vector<UpdateWish>();
		}

		protected abstract JPanel createContentPanel();
		protected abstract boolean getReadyState();
		
	}
	
	private static class SubUnitNetRadio extends SubUnit {
		private static final long serialVersionUID = -8583320100311806933L;
		private JTextArea playinfoOutput;
		private JList<Line> lineList;
		private JTextField lineListLabel;
		private boolean ignoreListSelection;
		private ListInfo listInfo;
		private Vector<JButton> buttons;
		private JScrollPane playinfoScrollPane;

		public SubUnitNetRadio() {
			super("Net Radio");
			listInfo = null;
		}

		@Override
		public void setEnabled(boolean enabled) {
			lineList.setEnabled(enabled);
			buttons.forEach(b->b.setEnabled(enabled));
		}

		@Override
		protected JPanel createContentPanel() {
			GridBagLayout layout;
			GridBagConstraints c = new GridBagConstraints();
			c.insets = new Insets(3, 3, 3, 3);
			
			lineListLabel = new JTextField("???");
			lineListLabel.setEditable(false);
			
			ignoreListSelection = false;
			lineList = new JList<Line>();
			lineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			lineList.addListSelectionListener(e -> {
				if (e.getValueIsAdjusting()) return;
				if (ignoreListSelection) return;
				if (listInfo.menuStatus==Device.Value.ReadyOrBusy.Busy) return;
				
				Line line = lineList.getSelectedValue();
				if (line==null) return;
				if (line.line.attr==Device.Value.LineAttribute.Unselectable) return;
				
				device.netRadio.sendDirectSelect(line.line);
				device.update(EnumSet.of(UpdateWish.NetRadioListInfo,UpdateWish.NetRadioPlayInfo));
				updateLineList();
				updatePlayInfo();
			});
			
			playinfoOutput = new JTextArea("Play Info");
			
			JScrollPane lineListScrollPane = new JScrollPane(lineList);
			playinfoScrollPane = new JScrollPane(playinfoOutput);
			lineListScrollPane.setPreferredSize(new Dimension(400, 200));
			playinfoScrollPane.setPreferredSize(new Dimension(400, 400));
			
			buttons = new Vector<>();
			JPanel buttonsPanel = new JPanel();
			buttonsPanel.setLayout(layout = new GridBagLayout());
//			addComp(buttonsPanel,layout,c,new   JLabel(                 ), 0,0, 1,1, 1,1, GridBagConstraints.BOTH);
			addComp(buttonsPanel,layout,c,createButton(ButtonID.Up      ), 1,0, 1,1, 1,1, GridBagConstraints.BOTH);
//			addComp(buttonsPanel,layout,c,new   JLabel(                 ), 2,0, 1,1, 1,1, GridBagConstraints.BOTH);
			addComp(buttonsPanel,layout,c,createButton(ButtonID.Home    ), 3,0, 1,1, GridBagConstraints.REMAINDER,1, GridBagConstraints.BOTH);
			addComp(buttonsPanel,layout,c,createButton(ButtonID.Return  ), 0,1, 1,1, 1,1, GridBagConstraints.BOTH);
//			addComp(buttonsPanel,layout,c,new   JLabel(                 ), 1,1, 1,1, 1,1, GridBagConstraints.BOTH);
			addComp(buttonsPanel,layout,c,createButton(ButtonID.Select  ), 2,1, 1,1, 1,1, GridBagConstraints.BOTH);
			addComp(buttonsPanel,layout,c,createButton(ButtonID.PageUp  ), 3,1, 1,1, GridBagConstraints.REMAINDER,1, GridBagConstraints.BOTH);
//			addComp(buttonsPanel,layout,c,new   JLabel(                 ), 0,2, 1,1, 1,1, GridBagConstraints.BOTH);
			addComp(buttonsPanel,layout,c,createButton(ButtonID.Down    ), 1,2, 1,1, 1,1, GridBagConstraints.BOTH);
//			addComp(buttonsPanel,layout,c,new   JLabel(                 ), 2,2, 1,1, 1,1, GridBagConstraints.BOTH);
			addComp(buttonsPanel,layout,c,createButton(ButtonID.PageDown), 3,2, 1,1, GridBagConstraints.REMAINDER,1, GridBagConstraints.BOTH);
			addComp(buttonsPanel,layout,c,createButton(ButtonID.Play    ), 0,3, 1,1, 2,1, GridBagConstraints.BOTH);
			addComp(buttonsPanel,layout,c,createButton(ButtonID.Stop    ), 2,3, 1,1, GridBagConstraints.REMAINDER,1, GridBagConstraints.BOTH);
			
			JPanel contentPanel = new JPanel();
			contentPanel.setLayout(layout = new GridBagLayout());
			addComp(contentPanel,layout,c,lineListLabel      ,0,0,1,0,1,1,GridBagConstraints.BOTH);
			addComp(contentPanel,layout,c,lineListScrollPane ,0,1,1,0,1,1,GridBagConstraints.BOTH);
			addComp(contentPanel,layout,c,buttonsPanel       ,0,2,1,0,1,1,GridBagConstraints.VERTICAL);
			addComp(contentPanel,layout,c,playinfoScrollPane ,0,3,1,1,1,1,GridBagConstraints.BOTH);
			addComp(contentPanel,layout,c,new JLabel("dummy"),0,4,1,0,1,1,GridBagConstraints.BOTH);
			
			
			return contentPanel;
		}
		
		private void addComp(JPanel panel, GridBagLayout layout, GridBagConstraints c, Component comp, int gridx, int gridy, double weightx, double weighty, int gridwidth, int gridheight, int fill) {
			c.gridx=gridx;
			c.gridy=gridy;
			c.weighty=weighty;
			c.weightx=weightx;
			c.gridwidth=gridwidth;
			c.gridheight=gridheight;
			c.fill = fill;
			layout.setConstraints(comp, c);
			panel.add(comp);
		}

		enum ButtonID {
			Up, Home, Return, Select, PageUp("Page Up"), Down, PageDown("Page Down"), Play, Stop;

			String label;
			ButtonID() { label = toString(); }
			ButtonID(String label) { this.label = label; }
			public String getLabel() { return label; }
		}
		private JButton createButton(ButtonID buttonID) {
			JButton button = YamahaControl.createButton(buttonID.getLabel(), createListener(buttonID), true);
			buttons.add(button);
			return button;
		}

		private ActionListener createListener(ButtonID buttonID) {
			switch (buttonID) {
			case Up    : return createCursorSelectListener(Device.Value.CursorSelect.Up);
			case Down  : return createCursorSelectListener(Device.Value.CursorSelect.Down);
			case Return: return createCursorSelectListener(Device.Value.CursorSelect.Return);
			case Select: return createCursorSelectListener(Device.Value.CursorSelect.Sel);
			case Home  : return createCursorSelectListener(Device.Value.CursorSelect.ReturnToHome);
				
			case PageUp  : return createPageSelectListener(Device.Value.PageSelect.Up);
			case PageDown: return createPageSelectListener(Device.Value.PageSelect.Down);
			
			case Play: return createPlaybackListener(Device.Value.PlayState.Play);
			case Stop: return createPlaybackListener(Device.Value.PlayState.Stop);
			}
			return e->{};
		}

		private ActionListener createPlaybackListener(PlayState playState) {
			return e->{
				device.netRadio.sendPlayback(playState);
				device.update(EnumSet.of(UpdateWish.NetRadioListInfo,UpdateWish.NetRadioPlayInfo));
				updateLineList();
				updatePlayInfo();
			};
		}

		private ActionListener createPageSelectListener(PageSelect pageSelect) {
			return e->{
				device.netRadio.sendPageSelect(pageSelect);
				device.update(EnumSet.of(UpdateWish.NetRadioListInfo));
				updateLineList();
			};
		}

		private ActionListener createCursorSelectListener(CursorSelect cursorSelect) {
			return e->{
				device.netRadio.sendCursorSelect(cursorSelect);
				device.update(EnumSet.of(UpdateWish.NetRadioListInfo,UpdateWish.NetRadioPlayInfo));
				updateLineList();
				updatePlayInfo();
			};
		}

		@Override
		public void initGUIafterConnect(Device device) {
			super.initGUIafterConnect(device);
			setEnabled(device!=null);
		}

		@Override
		public void frequentlyUpdate() {
			super.frequentlyUpdate();
			updateLineList();
			updatePlayInfo();
		}

		private void updatePlayInfo() {
			float hPos = YamahaControl.getScrollPos(playinfoScrollPane.getHorizontalScrollBar());
			float vPos = YamahaControl.getScrollPos(playinfoScrollPane.getVerticalScrollBar());
			playinfoOutput.setText(device.netRadio.getPlayInfo().toString());
			YamahaControl.setScrollPos(playinfoScrollPane.getHorizontalScrollBar(),hPos);
			YamahaControl.setScrollPos(playinfoScrollPane.getVerticalScrollBar(),vPos);
		}

		private void updateLineList() {
			listInfo = device.netRadio.getListInfo();
			lineList.setEnabled(listInfo.menuStatus==Device.Value.ReadyOrBusy.Ready);
			
			lineListLabel.setText(String.format("[%s] %s (is %s) %s/%s", listInfo.menuLayer, listInfo.menuName, listInfo.menuStatus, listInfo.currentLine, listInfo.maxLine));
			Line[] lines = listInfo.lines.stream().map((ListInfo.Line line)->new Line(line)).toArray(n->new Line[n]);
			lineList.setListData(lines);
			
			if (listInfo.currentLine!=null) {
				int lineIndex = ((listInfo.currentLine-1)&0x7);
				ignoreListSelection=true;
				lineList.setSelectedIndex(lineIndex);
				ignoreListSelection=false;
			}
		}
		
		@Override
		public Collection<UpdateWish> getUpdateWishes(UpdateReason reason) {
			Vector<UpdateWish> vector = new Vector<>(super.getUpdateWishes(reason));
			switch (reason) {
			case Frequently: vector.addAll(Arrays.asList(new UpdateWish[] { UpdateWish.NetRadioPlayInfo, UpdateWish.NetRadioListInfo })); break;
			case Initial   : vector.addAll(Arrays.asList(new UpdateWish[] { UpdateWish.NetRadioPlayInfo, UpdateWish.NetRadioListInfo })); break;
			}
			return vector;
		}

		@Override
		protected boolean getReadyState() {
			if (device==null) return false;
			// GET[G3]:    NET_RADIO,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			ReadyOrNot readyState = device.askValue(Device.KnownCommand.GetNetRadioConfig, ReadyOrNot.values(), "Feature_Availability");
			return readyState==ReadyOrNot.Ready;
		}

		private class Line {

			private ListInfo.Line line;

			public Line(ListInfo.Line line) {
				this.line = line;
			}

			@Override
			public String toString() {
				return String.format("[%s] %s%s", line.index, line.txt==null?"":line.txt, line.attr==Device.Value.LineAttribute.Unselectable?"":(" ("+line.attr+")"));
			}
		}
	}
	
	private static class SubUnitTuner extends SubUnit {
		private static final long serialVersionUID = -8583320100311806933L;

		public SubUnitTuner() {
			super("Tuner");
		}

		@Override
		protected boolean getReadyState() {
			if (device==null) return false;
			// GET[G2]:    Tuner,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			ReadyOrNot readyState = device.askValue(Device.KnownCommand.GetTunerConfig, ReadyOrNot.values(), "Feature_Availability");
			return readyState==ReadyOrNot.Ready;
		}

		@Override
		public void setEnabled(boolean enabled) {}

		@Override
		protected JPanel createContentPanel() {
			return new JPanel();
		}
	}
	
	
	
	static class Log {
		public static void info   (Class<?> callerClass,                String format, Object... values) { out(System.out, callerClass, "INFO"   ,         format, values); }
		public static void warning(Class<?> callerClass,                String format, Object... values) { out(System.err, callerClass, "WARNING",         format, values); }
		public static void error  (Class<?> callerClass,                String format, Object... values) { out(System.err, callerClass, "ERROR"  ,         format, values); }
		public static void info   (Class<?> callerClass, Locale locale, String format, Object... values) { out(System.out, callerClass, "INFO"   , locale, format, values); }
		public static void warning(Class<?> callerClass, Locale locale, String format, Object... values) { out(System.err, callerClass, "WARNING", locale, format, values); }
		public static void error  (Class<?> callerClass, Locale locale, String format, Object... values) { out(System.err, callerClass, "ERROR"  , locale, format, values); }
		
		private static void out(PrintStream out, Class<?> callerClass, String label,                String format, Object... values) { out(out, callerClass, label, Locale.ENGLISH, format, values); }
		private static void out(PrintStream out, Class<?> callerClass, String label, Locale locale, String format, Object... values) {
			out.printf(locale, "[%s] %s: %s%n", callerClass==null?"???":callerClass.getSimpleName(), label, String.format(format, values));
		}
	}

}
