package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.activation.DataHandler;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileSystemView;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Inputs.DeviceSceneInput;
import net.schwarzbaer.java.tools.yamahacontrol.Device.UpdateWish;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value;

public class YamahaControl {
	
	private static final String PREFERREDSONGS_FILENAME = "YamahaControl.PreferredSongs.txt";
	private static final HashSet<String> preferredSongs = new HashSet<>();

	static void writePreferredSongsToFile() {
		Vector<String> list = new Vector<>(preferredSongs);
		list.sort(null);
		try (PrintWriter out = new PrintWriter( new OutputStreamWriter( new FileOutputStream(PREFERREDSONGS_FILENAME), StandardCharsets.UTF_8) )) {
			list.forEach(str->out.println(str));
		}
		catch (FileNotFoundException e) {}
	}

	static void readPreferredSongsFromFile() {
		preferredSongs.clear();
		try (BufferedReader in = new BufferedReader( new InputStreamReader( new FileInputStream(PREFERREDSONGS_FILENAME), StandardCharsets.UTF_8) )) {
			String line;
			while ( (line=in.readLine())!=null )
				if (!line.isEmpty())
					preferredSongs.add(line);
		}
		catch (FileNotFoundException e) {}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		Config.readConfig();
		Ctrl.readCommProtocolFromFile();
//		new Responder().openWindow();
		
		readPreferredSongsFromFile();
		createSmallImages();
		
		YamahaControl yamahaControl = new YamahaControl();
		yamahaControl.createGUI();
	}

	public enum SmallImages { IconOn, IconOff, IconUnknown, FolderIcon }
	public static EnumMap<SmallImages,Icon> smallImages = null;
	private static void createSmallImages() {
		smallImages = new EnumMap<>(SmallImages.class);
		for (SmallImages id:SmallImages.values()) {
			switch (id) {
			case IconOff    : smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GREEN.darker())); break;
			case IconOn     : smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GREEN)); break;
			case IconUnknown: smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,10,Color.BLACK,Color.GRAY)); break;
			case FolderIcon : smallImages.put(id, FileSystemView.getFileSystemView().getSystemIcon(new File("./"))); break;
			}
		}
	}

	private StandardMainWindow mainWindow;
	private Device device;
	private MainGui mainGui;
	private Vector<GuiRegion> guiRegions;
	private FrequentlyTask frequentlyUpdater;
	
	YamahaControl() {
		mainWindow = null;
		device = null;
		mainGui = null;
		guiRegions = new Vector<>();
		frequentlyUpdater = new FrequentlyTask(2000,()->{
			updateDevice(UpdateReason.Frequently);
			SwingUtilities.invokeLater(()->guiRegions.forEach(gr->gr.frequentlyUpdate()));
		});
	}
	
	private void createGUI() {
		
		mainGui = new MainGui();
		guiRegions.add(mainGui);
		
		mainGui.createOnOffBtn();
		
		GridBagPanel devicePanel = new GridBagPanel();
		devicePanel.setBorder(BorderFactory.createTitledBorder("Device"));
		devicePanel.add(createButton("Connect",e->connectToReciever(),true),1,1,GridBagConstraints.BOTH);
		devicePanel.add(mainGui.onoffBtn,1,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
		devicePanel.add(createButton("Open Command List",e->CommandList.openWindow(device==null?null:device.address),true),1,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
		
		JTabbedPane scenesInputsPanel = mainGui.createScenesInputsPanel();
		scenesInputsPanel.setBorder(BorderFactory.createTitledBorder("Scenes/Inputs"));
		scenesInputsPanel.setPreferredSize(new Dimension(150,520));
		
		JPanel volumeControlPanel = mainGui.createVolumeControlPanel(200);
		volumeControlPanel.setBorder(BorderFactory.createTitledBorder("Volume"));
		
		JPanel mainControlPanel = new JPanel(new BorderLayout(3,3));
		mainControlPanel.add(devicePanel,BorderLayout.NORTH);
		mainControlPanel.add(scenesInputsPanel,BorderLayout.CENTER);
		mainControlPanel.add(volumeControlPanel,BorderLayout.SOUTH);
		
		JTabbedPane subUnitPanel = new JTabbedPane();
		subUnitPanel.setBorder(BorderFactory.createTitledBorder("Sub Units"));
		new SubUnitNetRadio().addTo(guiRegions,subUnitPanel);
		new SubUnitDLNA    ().addTo(guiRegions,subUnitPanel);
		new SubUnitUSB     ().addTo(guiRegions,subUnitPanel);
		new SubUnitIPodUSB ().addTo(guiRegions,subUnitPanel);
		new SubUnitTuner   ().addTo(guiRegions,subUnitPanel);
		new SubUnitSpotify ().addTo(guiRegions,subUnitPanel);
		new SubUnitAirPlay ().addTo(guiRegions,subUnitPanel);
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		contentPane.add(mainControlPanel,BorderLayout.WEST);
		contentPane.add(subUnitPanel,BorderLayout.CENTER);
		
		mainWindow = new StandardMainWindow("YamahaControl");
		mainWindow.startGUI(contentPane);
		
		guiRegions.forEach(gr->gr.setEnabledGUI(false));
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

	public static class FrequentlyTask implements Runnable {

		private int interval_ms;
		private boolean stop;
		private Runnable task;
		private boolean isRunning;

		public FrequentlyTask(int interval_ms, Runnable task) {
			this.interval_ms = interval_ms;
			this.task = task;
			this.stop = false;
			this.isRunning = false;
		}

		public boolean isRunning() {
			return isRunning;
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
			isRunning = true;
			synchronized (this) {
				while (!stop) {
					long startTime = System.currentTimeMillis();
					while (!stop && System.currentTimeMillis()-startTime<interval_ms)
						try { wait(interval_ms-(System.currentTimeMillis()-startTime)); }
						catch (InterruptedException e) {}
					
					task.run();
				}
			}
			isRunning = false;
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

	public static <E extends Enum<E>> E getNext(E value, E[] allValues) {
		return allValues[(value.ordinal()+1)%allValues.length];
	}
	// ///////////////////////////////////////////////////////////////////////////////////
	
	static class GridBagPanel extends JPanel {
		private static final long serialVersionUID = -344141298780014208L;
		
		private GridBagLayout layout;
		protected GridBagConstraints gbc;
	
		GridBagPanel() {
			super();
			resetLayout();
		}
	
		void resetLayout() {
			setLayout(layout = new GridBagLayout());
			gbc = new GridBagConstraints();
		}
		
		void setInsets(Insets insets) {
			gbc.insets = insets;
		}
		
		void add(Component comp, double weightx, int gridwidth, int fill) {
			gbc.weightx=weightx;
			gbc.gridwidth=gridwidth;
			gbc.fill = fill;
			layout.setConstraints(comp, gbc);
			add(comp);
		}
		
		void add(Component comp, int gridx, int gridy, double weightx, double weighty, int gridwidth, int gridheight, int fill) {
			gbc.gridx=gridx;
			gbc.gridy=gridy;
			gbc.weighty=weighty;
			gbc.weightx=weightx;
			gbc.gridwidth=gridwidth;
			gbc.gridheight=gridheight;
			gbc.fill = fill;
			layout.setConstraints(comp, gbc);
			add(comp);
		}
		
	}

	enum UpdateReason { Initial, Frequently }

	public static interface GuiRegion {
		void setEnabledGUI(boolean enabled);
		void initGUIafterConnect(Device device);
		void frequentlyUpdate();
		Collection<Device.UpdateWish> getUpdateWishes(UpdateReason reason);
	}
	
	private class MainGui implements GuiRegion {

		public JButton onoffBtn;
		private VolumeControl volumeControl;
		private DsiPanel scenesPanel;
		private DsiPanel inputsPanel;
		private VolumeSetter volumeSetter;
		
		MainGui() {
			onoffBtn = null;
			volumeControl = null;
			scenesPanel = null;
			inputsPanel = null;
			volumeSetter = new VolumeSetter(10);
		}

		@Override public void setEnabledGUI(boolean enabled) {
			onoffBtn.setEnabled(enabled);
			volumeControl.setEnabled(enabled);
			scenesPanel.setEnabled(enabled);
			inputsPanel.setEnabled(enabled);
		}

		@Override
		public Collection<UpdateWish> getUpdateWishes(UpdateReason reason) {
			switch (reason) {
			case Initial   : return EnumSet.of( UpdateWish.Power, UpdateWish.BasicStatus, UpdateWish.Scenes, UpdateWish.Inputs );
			case Frequently: return EnumSet.of( UpdateWish.Power, UpdateWish.BasicStatus );
			}
			return null;
		}

		@Override
		public void initGUIafterConnect(Device _device) {
			setEnabledGUI(device!=null);
			
			if (device!=null) {
				scenesPanel.createButtons(device.inputs.getScenes(),this::setScene,dsi->dsi!=null && "W".equals(dsi.rw));
				inputsPanel.createButtons(device.inputs.getInputs(),this::setInput,null);
				selectCurrentInput();
			}
			
			frequentlyUpdate();
		}

		@Override
		public void frequentlyUpdate() {
			setOnOffButton(device==null?(Boolean)false:device.power.isOn());
			volumeControl.setValue(device==null?null:device.getVolume());
			selectCurrentInput();
		}

		private void setScene(Device.Inputs.DeviceSceneInput dsi) {
			if (device==null) return;
			device.inputs.setScene(dsi);
		}

		private void setInput(Device.Inputs.DeviceSceneInput dsi) {
			if (device==null) return;
			device.inputs.setInput(dsi);
		}

		private void selectCurrentInput() {
			if (inputsPanel!=null) inputsPanel.setSelected(device.inputs.getCurrentInput());
		}

		public JTabbedPane createScenesInputsPanel() {
			scenesPanel = new DsiPanel(true);
			inputsPanel = new DsiPanel(false);
			
			JScrollPane scenesScrollPane = new JScrollPane(scenesPanel);
			JScrollPane inputsScrollPane = new JScrollPane(inputsPanel);
			scenesScrollPane.setMinimumSize(new Dimension(150,20));
			inputsScrollPane.setMinimumSize(new Dimension(150,20));
			
			JTabbedPane scenesInputsPanel = new JTabbedPane();
			scenesInputsPanel.add("Scenes", scenesScrollPane);
			scenesInputsPanel.add("Inputs", inputsScrollPane);
			
			return scenesInputsPanel;
		}

		public JPanel createVolumeControlPanel(int width) {
			volumeControl = new VolumeControl(width, 3.0, -90, (value, isAdjusting) -> {
				if (device==null) return;
				volumeSetter.set(value,isAdjusting);
			});
			JPanel volumePanel = new JPanel(new BorderLayout());
			volumePanel.add( volumeControl, BorderLayout.CENTER );
			return volumePanel;
		}
		
		private class VolumeSetter {
			private ExecutorService executor;
			private int counter;
			private int queueLength;
			private Stack<Future<?>> runningTasks;

			VolumeSetter(int queueLength) {
				this.queueLength = queueLength;
				executor = Executors.newSingleThreadExecutor();
				counter = 0;
				runningTasks = new Stack<>();
			}

			public synchronized void set(double value, boolean isAdjusting) {
				if (runningTasks.size()>100) {
//					Log.info(getClass(), "Max. number of running tasks reached");
					removeCompletedTasks();
				}

				if (isAdjusting) {
					
					if (counter>queueLength) return;
					
					runningTasks.add(executor.submit(()->{
						incCounter();
						device.setVolume(value);
						decCounter();
					}));
					
				} else {
//					Log.info(getClass(), "Value stops adjusting: cancel all running tasks");
					runningTasks.forEach(task->task.cancel(false));
					removeCompletedTasks();
					
					runningTasks.add(executor.submit(()->{
						incCounter();
						device.setVolume(value);
						Device.NumberWithUnit volume = device.getVolume();
						SwingUtilities.invokeLater(()->{
							volumeControl.setValue(volume);
						});
						decCounter();
					}));
				}
			}

			private void removeCompletedTasks() {
//				Log.info(getClass(), "remove completed tasks");
				for (int i=0; i<runningTasks.size();) {
					Future<?> task = runningTasks.get(i); 
					if (task.isDone() || task.isCancelled())
						runningTasks.remove(i);
					else
						++i;
				}
			}

			private synchronized void decCounter() { --counter; }
			private synchronized void incCounter() { ++counter; }
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
			if (device!=null) device.power.setOn(!device.power.isOn());
			setOnOffButton(device==null?false:device.power.isOn());
		}
		
		private class DsiPanel extends GridBagPanel {
			private static final long serialVersionUID = -3330564101527546450L;
			
			private ButtonGroup bg;
			private HashMap<Device.Inputs.DeviceSceneInput,JToggleButton> buttons;

			private boolean createNormalButtons;
			
			DsiPanel(boolean createNormalButtons) {
				super();
				this.createNormalButtons = createNormalButtons;
				bg = null;
				buttons = null;
			}
			
			public void setSelected(Device.Inputs.DeviceSceneInput dsi) {
				if (createNormalButtons) throw new UnsupportedOperationException("Can't select a button, if normal buttons are created.");
				JToggleButton button = buttons.get(dsi);
				if (button!=null) bg.setSelected(button.getModel(), true);
			}

			public void createButtons(Device.Inputs.DeviceSceneInput[] dsiArr, Consumer<Device.Inputs.DeviceSceneInput> setFunction, Predicate<Device.Inputs.DeviceSceneInput> filter) {
				removeAll();
				resetLayout();
				gbc.weighty=0;
				
				if (!createNormalButtons) {
					bg = new ButtonGroup();
					buttons = new HashMap<>();
				}
				
				for (Device.Inputs.DeviceSceneInput dsi:dsiArr) {
					if (filter!=null && !filter.test(dsi)) continue;
					
					String title = dsi.title==null?"<???>":dsi.title.trim();
					
					AbstractButton button;
					if (createNormalButtons) {
						button = createButton(title, e->setFunction.accept(dsi), true);
					} else {
						JToggleButton tButton = createToggleButton(title, e->setFunction.accept(dsi), true, bg);
						buttons.put(dsi,tButton);
						button = tButton;
					}
					
					add(button,0,1,GridBagConstraints.HORIZONTAL);
					add(new JLabel("["+dsi.ID+"]",JLabel.CENTER),1,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
					//add(new JLabel(dsi.rw),0,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
				}
				gbc.weighty=1;
				add(new JLabel(""),1,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
			}
		}
	}
	
	private static abstract class AbstractSubUnit extends JPanel implements GuiRegion {
		private static final long serialVersionUID = 7368584160348326790L;
		
		protected Device device;
		protected boolean isReady;
		private JLabel readyStateLabel;
		private JLabel tabHeaderComp;

		private String inputID;
		private String tabTitle;

		protected AbstractSubUnit(String inputID, String tabTitle) {
			super(new BorderLayout(3,3));
			this.inputID = inputID;
			this.tabTitle = tabTitle;
		}

		private void createPanel() {
			tabHeaderComp = new JLabel("  "+tabTitle+"  ");
			
			JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,3,3));
			northPanel.add(createButton("Activate", e->{
				if (device==null) return;
				DeviceSceneInput dsi = device.inputs.findInput(inputID);
				if (dsi!=null) device.inputs.setInput(dsi);
			}, true));
			northPanel.add(readyStateLabel = new JLabel("???",smallImages.get(SmallImages.IconUnknown),JLabel.LEFT));
			
			add(northPanel,BorderLayout.NORTH);
			add(createContentPanel(),BorderLayout.CENTER);
			setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		}

		public void addTo(Vector<GuiRegion> guiRegions, JTabbedPane subUnitPanel) {
			createPanel();
			
			guiRegions.add(this);
			int index = subUnitPanel.getTabCount();
			subUnitPanel.insertTab(tabTitle,null,this,null, index);
			subUnitPanel.setTabComponentAt(index, tabHeaderComp);
		}

		@Override
		public void initGUIafterConnect(Device device) {
			this.device = device;
			setEnabled(this.device!=null);
			frequentlyUpdate();
		}

		@Override
		public void frequentlyUpdate() {
			isReady = getReadyState();
			setEnabled(isReady);
			tabHeaderComp.setOpaque(isReady);
			tabHeaderComp.setBackground(isReady?Color.GREEN:null);
//			setTabBackground.accept(isReady?Color.GREEN:null);
			readyStateLabel.setText(tabTitle+" is "+(isReady?"Ready":"Not Ready"));
			readyStateLabel.setIcon(isReady?smallImages.get(SmallImages.IconOn):smallImages.get(SmallImages.IconOff));
		}

		@Override
		public Collection<UpdateWish> getUpdateWishes(UpdateReason reason) {
			UpdateWish readyStateUpdateWish = getReadyStateUpdateWish();
			if (readyStateUpdateWish!=null) return EnumSet.of(readyStateUpdateWish);
			return EnumSet.noneOf(UpdateWish.class);
		}

		protected JPanel createContentPanel() { return new JPanel(); }
		protected abstract boolean getReadyState();
		protected UpdateWish getReadyStateUpdateWish() { return null; }

		@Override public void setEnabledGUI(boolean enabled) { setEnabled(enabled); }
		
		
	}
	
	private static class SubUnitNetRadio extends AbstractSubUnit_ListPlay {
		private static final long serialVersionUID = -8583320100311806933L;
		
		private JToggleButton playBtn;
		private JToggleButton stopBtn;

		public SubUnitNetRadio() {
			super("NET RADIO","Net Radio",UpdateWish.NetRadioListInfo,UpdateWish.NetRadioPlayInfo);
		}

		@Override
		protected boolean getReadyState() {
			if (device==null) return false;
			// GET[G3]:    NET_RADIO,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			Device.Value.ReadyOrNot readyState = device.askValue(Device.KnownCommand.GetNetRadioConfig, Device.Value.ReadyOrNot.values(), "Feature_Availability");
			return readyState==Device.Value.ReadyOrNot.Ready;
		}

		@Override protected Device.PlayInfo getPlayInfo()              { return device.netRadio.playInfo; }
		@Override protected Device.ListInfo getListInfo(Device device) { return device.netRadio.listInfo; }

		@Override
		protected void updateButtons() {
			if (device!=null) {
				if (device.netRadio.playInfo.playState==null || !isReady)
					playButtons.clearSelection();
				else
					switch (device.netRadio.playInfo.playState) {
					case Play : playBtn .setSelected(true); break;
					case Stop : stopBtn .setSelected(true); break;
					}
			} else {
				playButtons.clearSelection();
			}
		}

		@Override
		protected void createButtons(GridBagPanel buttonsPanel) {
			buttonsPanel.add(playBtn = createButton(Device.Value.PlayStop.Play), 0,0, 0,1, 1,1, GridBagConstraints.BOTH);
			buttonsPanel.add(stopBtn = createButton(Device.Value.PlayStop.Stop), 1,0, 0,1, 1,1, GridBagConstraints.BOTH);
			buttonsPanel.add(createButton("Add Song to PreferredSongs",e->addSongToPreferredSongs()), 2,0, 0,1, 1,1, GridBagConstraints.BOTH);
		}
		
		private JToggleButton createButton(Device.Value.PlayStop playState) {
			JToggleButton button = YamahaControl.createToggleButton(playState.getLabel(), null, true, playButtons);
			button.addActionListener(e->{
				device.netRadio.playInfo.sendPlayback(playState);
				device.update(EnumSet.of(UpdateWish.NetRadioListInfo,UpdateWish.NetRadioPlayInfo));
				lineList.updateLineList();
				updatePlayInfo();
			});
			comps.add(button);
			return button;
		}
		private JButton createButton(String title, ActionListener l) {
			JButton button = YamahaControl.createButton(title, l, true);
			comps.add(button);
			return button;
		}

		private void addSongToPreferredSongs() {
			if (device!=null && device.netRadio.playInfo.currentSong!=null) {
				preferredSongs.add(device.netRadio.playInfo.currentSong);
				writePreferredSongsToFile();
			}
		}
	}
	
	private static class SubUnitUSB extends AbstractSubUnit_PlayInfoExt<Device.Value.OnOff> {
		private static final long serialVersionUID = 2909543552931897755L;

		public SubUnitUSB() {
			super("USB","USB Device",UpdateWish.USBListInfo,UpdateWish.USBPlayInfo,Device.Value.OnOff.values());
		}
		
		@Override
		protected boolean getReadyState() {
			if (device==null) return false;
			// GET[G3]:    USB,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			Device.Value.ReadyOrNot readyState = device.askValue(Device.KnownCommand.GetUSBConfig, Device.Value.ReadyOrNot.values(), "Feature_Availability");
			return readyState==Device.Value.ReadyOrNot.Ready;
		}
		
		@Override protected Device.PlayInfoExt<Device.Value.OnOff> getPlayInfoExt()           { return device.usb.playInfo; }
		@Override protected Device.ListInfo                        getListInfo(Device device) { return device.usb.listInfo; }
	}
	
	private static class SubUnitDLNA extends AbstractSubUnit_PlayInfoExt<Device.Value.OnOff> {
		private static final long serialVersionUID = -4585259335586086032L;

		public SubUnitDLNA() {
			super("SERVER","DLNA Server",UpdateWish.DLNAListInfo,UpdateWish.DLNAPlayInfo,Device.Value.OnOff.values());
		}
		
		@Override
		protected boolean getReadyState() {
			if (device==null) return false;
			// GET[G3]:    SERVER,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			Device.Value.ReadyOrNot readyState = device.askValue(Device.KnownCommand.GetServerConfig, Device.Value.ReadyOrNot.values(), "Feature_Availability");
			return readyState==Device.Value.ReadyOrNot.Ready;
		}
		
		@Override protected Device.PlayInfoExt<Device.Value.OnOff> getPlayInfoExt()           { return device.dlna.playInfo; }
		@Override protected Device.ListInfo                        getListInfo(Device device) { return device.dlna.listInfo; }
	}

	private static class SubUnitIPodUSB extends AbstractSubUnit_PlayInfoExt<Device.Value.ShuffleIPod> {
		private static final long serialVersionUID = -4180795479139795928L;
	
		public SubUnitIPodUSB() {
			super("iPod (USB)","IPod USB",UpdateWish.IPodUSBListInfo,UpdateWish.IPodUSBPlayInfo,Device.Value.ShuffleIPod.values());
		}
	
		@Override
		protected boolean getReadyState() {
			if (device==null) return false;
			// GET[G3]:    iPod_USB,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			Device.Value.ReadyOrNot readyState = device.askValue(Device.KnownCommand.GetIPodUSBConfig, Device.Value.ReadyOrNot.values(), "Feature_Availability");
			return readyState==Device.Value.ReadyOrNot.Ready;
		}
		
		@Override protected Device.PlayInfoExt<Device.Value.ShuffleIPod> getPlayInfoExt()           { return device.iPodUSB.playInfo; }
		@Override protected Device.ListInfo                              getListInfo(Device device) { return device.iPodUSB.listInfo; }
	}

	private static abstract class AbstractSubUnit_PlayInfoExt<Shuffle extends Enum<Shuffle>&Value> extends AbstractSubUnit_ListPlay {
		private static final long serialVersionUID = 8830354607137619068L;
		
		private JToggleButton playBtn;
		private JToggleButton pauseBtn;
		private JToggleButton stopBtn;
		private JButton repeatBtn;
		private JButton shuffleBtn;
		private Shuffle[] shuffleValues;
		
		public AbstractSubUnit_PlayInfoExt(String inputID, String tabTitle, UpdateWish listInfoUpdateWish, UpdateWish playInfoUpdateWish, Shuffle[] shuffleValues) {
			super(inputID, tabTitle, listInfoUpdateWish, playInfoUpdateWish);
			this.shuffleValues = shuffleValues;
		}
		
		protected abstract Device.PlayInfoExt<Shuffle> getPlayInfoExt();
		
		@Override
		protected Device.PlayInfo getPlayInfo() { return getPlayInfoExt(); }

		@Override
		protected void updateButtons() {
			if (device!=null && getPlayInfo()!=null) {
				Device.PlayInfoExt<Shuffle> playInfo = getPlayInfoExt();
				if (playInfo.playState==null || !isReady)
					playButtons.clearSelection();
				else
					switch (playInfo.playState) {
					case Play : playBtn .setSelected(true); break;
					case Pause: pauseBtn.setSelected(true); break;
					case Stop : stopBtn .setSelected(true); break;
					}
				repeatBtn .setText(playInfo.repeat ==null? "Repeat":( "Repeat: "+playInfo.repeat .getLabel()));
				shuffleBtn.setText(playInfo.shuffle==null?"Shuffle":("Shuffle: "+playInfo.shuffle.getLabel()));
				
			} else{
				playButtons.clearSelection();
			}
		}

		@Override
		protected void createButtons(GridBagPanel buttonsPanel) {
			buttonsPanel.add(playBtn    = createButton(     Device.Value.PlayPauseStop.Play ), 0,0, 0,1, 1,1, GridBagConstraints.BOTH);
			buttonsPanel.add(pauseBtn   = createButton(     Device.Value.PlayPauseStop.Pause), 1,0, 0,1, 1,1, GridBagConstraints.BOTH);
			buttonsPanel.add(stopBtn    = createButton(     Device.Value.PlayPauseStop.Stop ), 2,0, 0,1, 1,1, GridBagConstraints.BOTH);
			buttonsPanel.add(             createButton("<<",Device.Value.SkipFwdRev.SkipRev ), 3,0, 0,1, 1,1, GridBagConstraints.BOTH);
			buttonsPanel.add(             createButton(">>",Device.Value.SkipFwdRev.SkipFwd ), 4,0, 0,1, 1,1, GridBagConstraints.BOTH);
			buttonsPanel.add(repeatBtn  = createButton("Repeat"                             ), 5,0, 0,1, 1,1, GridBagConstraints.BOTH);
			buttonsPanel.add(shuffleBtn = createButton("Shuffle"                            ), 6,0, 0,1, 1,1, GridBagConstraints.BOTH);
			
			repeatBtn.addActionListener(e->{
				if (device==null) return;
				Device.PlayInfoExt<Shuffle> playInfo = getPlayInfoExt();
				playInfo.sendRepeat(YamahaControl.getNext(playInfo.repeat,Device.Value.OffOneAll.values()));
				device.update(EnumSet.of(listInfoUpdateWish, playInfoUpdateWish));
				lineList.updateLineList();
				updatePlayInfo();
			});
			shuffleBtn.addActionListener(e->{
				if (device==null) return;
				Device.PlayInfoExt<Shuffle> playInfo = getPlayInfoExt();
				playInfo.sendShuffle(YamahaControl.getNext(playInfo.shuffle,shuffleValues));
				device.update(EnumSet.of(listInfoUpdateWish, playInfoUpdateWish));
				lineList.updateLineList();
				updatePlayInfo();
			});
		}
		
		private JToggleButton createButton(Device.Value.PlayPauseStop playState) {
			ActionListener listener = e->{
				getPlayInfoExt().sendPlayback(playState);
				device.update(EnumSet.of(listInfoUpdateWish, playInfoUpdateWish));
				lineList.updateLineList();
				updatePlayInfo();
			};
			JToggleButton button = YamahaControl.createToggleButton(playState.getLabel(), listener, true, playButtons);
			comps.add(button);
			return button;
		}
		
		private JButton createButton(String title, Device.Value.SkipFwdRev skip) {
			ActionListener listener = e->{
				getPlayInfoExt().sendPlayback(skip);
				device.update(EnumSet.of(listInfoUpdateWish, playInfoUpdateWish));
				lineList.updateLineList();
				updatePlayInfo();
			};
			JButton button = YamahaControl.createButton(title, listener, true);
			comps.add(button);
			return button;
		}
		
		private JButton createButton(String title) {
			JButton button = YamahaControl.createButton(title, null, true);
			comps.add(button);
			return button;
		}
	}

	private static abstract class AbstractSubUnit_ListPlay extends AbstractSubUnit implements LineList.LineListUser {
		private static final long serialVersionUID = 3773609643258015474L;
		
		protected LineList lineList;
		private   JTextArea playinfoOutput;
		private   JScrollPane playinfoScrollPane;
		protected Vector<JComponent> comps;
		protected ButtonGroup playButtons;
		
		protected UpdateWish listInfoUpdateWish;
		protected UpdateWish playInfoUpdateWish;
	
		public AbstractSubUnit_ListPlay(String inputID, String tabTitle, UpdateWish listInfoUpdateWish, UpdateWish playInfoUpdateWish) {
			super(inputID, tabTitle);
			this.listInfoUpdateWish = listInfoUpdateWish;
			this.playInfoUpdateWish = playInfoUpdateWish;
		}

		protected abstract Device.PlayInfo getPlayInfo();
		protected abstract Device.ListInfo getListInfo(Device device);
		protected abstract void createButtons(GridBagPanel buttonsPanel);
		protected abstract void updateButtons();

		@Override
		public Collection<UpdateWish> getUpdateWishes(UpdateReason reason) {
			EnumSet<UpdateWish> enumSet = EnumSet.copyOf(super.getUpdateWishes(reason));
			switch (reason) {
			case Initial:
			case Frequently:
				enumSet.add(listInfoUpdateWish);
				enumSet.add(playInfoUpdateWish);
				break;
			}
			return enumSet;
		}

		@Override
		public void initGUIafterConnect(Device device) {
			lineList.setDeviceAndListInfo(device,device==null?null:getListInfo(device));
			super.initGUIafterConnect(device);
		}

		@Override
		public void frequentlyUpdate() {
			super.frequentlyUpdate();
			lineList.updateLineList();
			updatePlayInfo();
		}

		@Override
		public void setEnabledGuiIfPossible(boolean enabled) {
			setEnabledGUI(isReady && enabled);
		}

		@Override
		public void setEnabledGUI(boolean enabled) {
			lineList.setEnabledGUI(enabled);
			comps.forEach(b->b.setEnabled(enabled));
		}
	
		@Override
		protected JPanel createContentPanel() {
			comps = new Vector<>();
			
			lineList = new LineList(this,listInfoUpdateWish,playInfoUpdateWish);
			JPanel lineListPanel = lineList.createGUIelements();
			lineListPanel.setBorder(BorderFactory.createTitledBorder("Menu"));
			
			playinfoOutput = new JTextArea("<no data>");
			playinfoOutput.setEditable(false);
			comps.add(playinfoOutput);
			
			playinfoScrollPane = new JScrollPane(playinfoOutput);
			playinfoScrollPane.setPreferredSize(new Dimension(500, 400));
			
			playButtons = new ButtonGroup();
			GridBagPanel buttonsPanel = new GridBagPanel();
			buttonsPanel.setInsets(new Insets(3,3,3,3));
			createButtons(buttonsPanel);
			
			JPanel playinfoPanel = new JPanel(new BorderLayout(3,3));
			playinfoPanel.setBorder(BorderFactory.createTitledBorder("Play Info"));
			playinfoPanel.add(buttonsPanel,BorderLayout.NORTH);
			playinfoPanel.add(playinfoScrollPane,BorderLayout.CENTER);
			
			JPanel contentPanel = new JPanel(new BorderLayout(3,3));
			contentPanel.add(lineListPanel, BorderLayout.NORTH);
			contentPanel.add(playinfoPanel, BorderLayout.CENTER);
			
			return contentPanel;
		}

		@Override
		public void updatePlayInfo() {
			float hPos = YamahaControl.getScrollPos(playinfoScrollPane.getHorizontalScrollBar());
			float vPos = YamahaControl.getScrollPos(playinfoScrollPane.getVerticalScrollBar());
			if (device!=null) {
				playinfoOutput.setText(getPlayInfo().toString());
			} else{
				playinfoOutput.setText("<no data>");
			}
			updateButtons();
			YamahaControl.setScrollPos(playinfoScrollPane.getHorizontalScrollBar(),hPos);
			YamahaControl.setScrollPos(playinfoScrollPane.getVerticalScrollBar(),vPos);
		}
	}

	private static class SubUnitTuner extends AbstractSubUnit {
		private static final long serialVersionUID = -8583320100311806933L;

		public SubUnitTuner() {
			super("TUNER","Tuner");
		}

		@Override
		protected UpdateWish getReadyStateUpdateWish() {
			return UpdateWish.TunerConfig;
		}

		@Override
		protected boolean getReadyState() {
			if (device==null) return false;
			if (device.tuner==null) return false;
			if (device.tuner.config==null) return false;
			Device.Value.ReadyOrNot readyState = device.tuner.config.deviceStatus;
			return readyState==Device.Value.ReadyOrNot.Ready;
		}
	}
	
	private static class SubUnitAirPlay extends AbstractSubUnit {
		private static final long serialVersionUID = 8375036678437177239L;

		// [Source_Device | SD_AirPlay | AirPlay]   
		public SubUnitAirPlay() {
			super("AirPlay","AirPlay");
		}

		@Override
		protected UpdateWish getReadyStateUpdateWish() {
			return UpdateWish.AirPlayConfig;
		}

		@Override
		protected boolean getReadyState() {
			if (device==null) return false;
			if (device.airPlay==null) return false;
			if (device.airPlay.config==null) return false;
			Device.Value.ReadyOrNot readyState = device.airPlay.config.deviceStatus;
			return readyState==Device.Value.ReadyOrNot.Ready;
		}
	}
	
	private static class SubUnitSpotify extends AbstractSubUnit {
		private static final long serialVersionUID = -869960569061323838L;

		public SubUnitSpotify() {
			super("Spotify","Spotify");
		}

		@Override
		protected boolean getReadyState() {
			if (device==null) return false;
			// GET[G3]:    Spotify,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			Device.Value.ReadyOrNot readyState = device.askValue(Device.KnownCommand.GetSpotifyConfig, Device.Value.ReadyOrNot.values(), "Feature_Availability");
			return readyState==Device.Value.ReadyOrNot.Ready;
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
