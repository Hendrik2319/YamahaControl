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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import java.util.Comparator;
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
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileSystemView;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.yamahacontrol.Device.PlayInfo;
import net.schwarzbaer.java.tools.yamahacontrol.Device.UpdateWish;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.UpDown;

public class YamahaControl {
	
	private static final String PREFERREDSONGS_FILENAME = "YamahaControl.PreferredSongs.txt";
	private static final HashSet<String> preferredSongs = new HashSet<>();

	static void writePreferredSongsToFile() {
		Vector<String> list = new Vector<>(preferredSongs);
		list.sort(Comparator.nullsLast(Comparator.comparing(str->str.toLowerCase())));
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
	private Vector<GuiRegion> guiRegions;
	private FrequentlyTask frequentlyUpdater;
	
	YamahaControl() {
		mainWindow = null;
		device = null;
		guiRegions = new Vector<>();
		frequentlyUpdater = new FrequentlyTask(2000,false,()->{
			updateDevice(UpdateReason.Frequently);
			SwingUtilities.invokeLater(()->guiRegions.forEach(gr->gr.frequentlyUpdate()));
		});
	}
	
	private void createGUI() {
		
		ScenesAndInputs scenesAndInputs = new ScenesAndInputs();
		guiRegions.add(scenesAndInputs);
		
		OnOffBtn onOffBtn = new OnOffBtn();
		guiRegions.add(onOffBtn);
		
		VolumeCtrl volumeCtrl = new VolumeCtrl();
		guiRegions.add(volumeCtrl);
		
		MainPlayNListCtrl mainPlayNListCtrl = new MainPlayNListCtrl();
		guiRegions.add(mainPlayNListCtrl);
		
		GridBagPanel devicePanel = new GridBagPanel();
		devicePanel.setBorder(BorderFactory.createTitledBorder("Device"));
		devicePanel.add(createButton("Connect",e->connectToReciever(),true),1,1,GridBagConstraints.BOTH);
		devicePanel.add(onOffBtn.button,1,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
		devicePanel.add(createButton("Open Command List",e->CommandList.openWindow(device==null?null:device.address),true),1,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
		
		JTabbedPane settingsPanel = new JTabbedPane();
		settingsPanel.setBorder(BorderFactory.createTitledBorder("Settings"));
		//settingsPanel.setPreferredSize(new Dimension(150,520));
		
		scenesAndInputs.createPanels(settingsPanel);
		mainPlayNListCtrl.createPanel(settingsPanel);
		//settingsPanel.add("Dummy", new JLabel("Dummy"));
		
		JPanel volumeControlPanel = volumeCtrl.createVolumeControlPanel(200);
		volumeControlPanel.setBorder(BorderFactory.createTitledBorder("Volume"));
		
		JPanel mainControlPanel = new JPanel(new BorderLayout(3,3));
		mainControlPanel.add(devicePanel,BorderLayout.NORTH);
		mainControlPanel.add(settingsPanel,BorderLayout.CENTER);
		mainControlPanel.add(volumeControlPanel,BorderLayout.SOUTH);
		
		JTabbedPane subUnitPanel = new JTabbedPane();
		subUnitPanel.setBorder(BorderFactory.createTitledBorder("Sub Units"));
		new SubUnitNetRadio().addTo(guiRegions,subUnitPanel);
		new SubUnitDLNA    ().addTo(guiRegions,subUnitPanel);
		new SubUnitUSB     ().addTo(guiRegions,subUnitPanel);
		new SubUnitTuner   ().addTo(guiRegions,subUnitPanel);
		new SubUnitIPodUSB ().addTo(guiRegions,subUnitPanel);
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
		private boolean skipFirstWait;

		public FrequentlyTask(int interval_ms, boolean skipFirstWait, Runnable task) {
			this.interval_ms = interval_ms;
			this.skipFirstWait = skipFirstWait;
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
				if (skipFirstWait)
					task.run();
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
	static JButton createButton(String title, boolean enabled) {
		JButton button = new JButton(title);
		button.setEnabled(enabled);
		return button;
	}
	static JButton createButton(String title, ActionListener l, boolean enabled) {
		JButton button = createButton(title,enabled);
		button.addActionListener(l);
		return button;
	}

	static JToggleButton createToggleButton(String title, ActionListener l, boolean enabled, ButtonGroup bg) {
		JToggleButton button = createToggleButton(title, l, enabled);
		if (bg!=null) bg.add(button);
		return button;
	}

	static JToggleButton createToggleButton(String title, ActionListener l, boolean enabled) {
		JToggleButton button = new JToggleButton(title);
		button.setEnabled(enabled);
		if (l!=null) button.addActionListener(l);
		return button;
	}

	static JMenuItem createMenuItem(String title, ActionListener l) {
		JMenuItem menuItem = new JMenuItem(title);
		if (l!=null) menuItem.addActionListener(l);
		return menuItem;
	}

	static JCheckBoxMenuItem createCheckBoxMenuItem(String title, ActionListener l, ButtonGroup bg) {
		JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(title);
		if (l!=null) menuItem.addActionListener(l);
		if (bg!=null) bg.add(menuItem);
		return menuItem;
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
		SwingUtilities.invokeLater(()->{
			int min = scrollBar.getMinimum();
			int max = scrollBar.getMaximum();
			int ext = scrollBar.getVisibleAmount();
			scrollBar.setValue(Math.round(pos*(max-ext-min)+min));
		});
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
	
	public static class GridBagPanel extends JPanel {
		private static final long serialVersionUID = -344141298780014208L;
		
		private GridBagLayout layout;
		protected GridBagConstraints gbc;
	
		public GridBagPanel() {
			super();
			resetLayout();
		}
	
		public void resetLayout() {
			setLayout(layout = new GridBagLayout());
			gbc = new GridBagConstraints();
		}
		
		public void setInsets(Insets insets) {
			gbc.insets = insets;
		}
		
		public void add(Component comp, double weightx, int gridwidth, int fill) {
			gbc.weightx=weightx;
			gbc.gridwidth=gridwidth;
			gbc.fill = fill;
			layout.setConstraints(comp, gbc);
			add(comp);
		}
		
		public void add(Component comp, int gridx, int gridy, double weightx, double weighty, int gridwidth, int gridheight, int fill) {
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
	
	public static abstract class AbstractButtonFactory<E extends Enum<E>> {
		private Consumer<E> btnCmd;
		private EnumMap<E, AbstractButton> buttons;

		AbstractButtonFactory(Class<E> enumClass, Consumer<E> btnCmd) {
			this.btnCmd = btnCmd;
			buttons = new EnumMap<E,AbstractButton>(enumClass);
		}
		public AbstractButton createButton(String title, E buttonID) {
			AbstractButton button = createButton(title, buttonID, btnCmd==null?e->{}:e->btnCmd.accept(buttonID));
			buttons.put(buttonID, button);
			return button; 
		}
		protected abstract AbstractButton createButton(String title, E buttonID, ActionListener l);
		
		public void setEnabled(boolean enabled) {
			buttons.forEach((e,b)->b.setEnabled(enabled));
		}
		public AbstractButton getButton(E buttonID) {
			return buttons.get(buttonID);
		}
	}
	
	public static class ButtonFactory<E extends Enum<E>> extends AbstractButtonFactory<E> {

		ButtonFactory(Class<E> enumClass, Consumer<E> btnCmd) {
			super(enumClass,btnCmd);
		}
		@Override
		protected JButton createButton(String title, E buttonID, ActionListener l) {
			JButton button = YamahaControl.createButton(title, l, true);
			return button;
		}
	}
	
	public static class ToggleButtonGroup<E extends Enum<E>> extends AbstractButtonFactory<E> {
		
		private final ButtonGroup bg;
		private JLabel label;

		ToggleButtonGroup(Class<E> enumClass, Consumer<E> btnCmd) {
			super(enumClass,btnCmd);
			bg = new ButtonGroup();
			label = null;
		}
		
		public Component createLabel(String title) {
			return createLabel(title,JLabel.LEFT);
		}
		
		public Component createLabel(String title, int horizontalAlignment) {
			label = new JLabel(title,horizontalAlignment);
			return label;
		}
		
		@Override
		protected JToggleButton createButton(String title, E buttonID, ActionListener l) {
			JToggleButton button = YamahaControl.createToggleButton(title, l, true, bg);
			return button; 
		}
		
		@Override
		public void setEnabled(boolean enabled) {
			super.setEnabled(enabled);
			if (label!=null) label.setEnabled(enabled);
			if (!enabled) clearSelection();
		}
		
		public void clearSelection() {
			bg.clearSelection();
		}
		
		public void setSelected(E buttonID) {
			if (buttonID != null) {
				AbstractButton button = getButton(buttonID);
				if (button != null) { button.setSelected(true); return; }
			}
			clearSelection();
		}
	}

	enum UpdateReason { Initial, Frequently }

	public static class ValueSetter {
		private ExecutorService executor;
		private int counter;
		private int queueLength;
		private Stack<Future<?>> runningTasks;
		private Setter setter;

		ValueSetter(int queueLength, Setter setter) {
			this.queueLength = queueLength;
			this.setter = setter;
			executor = Executors.newSingleThreadExecutor();
			counter = 0;
			runningTasks = new Stack<>();
		}

		static interface Setter {
			public void setValue(double value, boolean isAdjusting);
		}

		public synchronized void set(double value, boolean isAdjusting) {
			if (runningTasks.size() > 100) {
				// Log.info(getClass(), "Max. number of running tasks reached");
				removeCompletedTasks();
			}

			if (isAdjusting) {

				if (counter > queueLength)
					return;

				runningTasks.add(executor.submit(() -> {
					incCounter();
					setter.setValue(value, isAdjusting);
					decCounter();
				}));

			} else {
				// Log.info(getClass(), "Value stops adjusting: cancel all
				// running tasks");
				runningTasks.forEach(task -> task.cancel(false));
				removeCompletedTasks();
				if (!runningTasks.isEmpty())
					Log.warning(getClass(), "stops adjusting -> left %d running tasks", runningTasks.size());

				runningTasks.add(executor.submit(() -> {
					incCounter();
					setter.setValue(value, isAdjusting);
					decCounter();
				}));
			}
		}

		public synchronized void clear() {
			runningTasks.forEach(task -> task.cancel(true));
			removeCompletedTasks();
			if (!runningTasks.isEmpty())
				Log.warning(getClass(), "clear() -> left %d running tasks", runningTasks.size());
		}

		private void removeCompletedTasks() {
			// Log.info(getClass(), "remove completed tasks");
			for (int i = 0; i < runningTasks.size();) {
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

	public static interface GuiRegion {
		void setEnabledGUI(boolean enabled);
		void initGUIafterConnect(Device device);
		void frequentlyUpdate();
		EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason);
	}
	
	private static class MainPlayNListCtrl implements GuiRegion {
		
		private Device device;
		private Vector<JComponent> comps;
		MainPlayNListCtrl() {
			device = null;
			comps = new Vector<JComponent>();
		}
	
		@Override public void setEnabledGUI(boolean enabled) {
			comps.forEach(c->c.setEnabled(enabled));
		}
		@Override public void initGUIafterConnect(Device device) {
			this.device = device;
			setEnabledGUI(device!=null);
			frequentlyUpdate();
		}
		@Override public void frequentlyUpdate() {}
		@Override public EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason) {
			return EnumSet.noneOf(UpdateWish.class);
		}

		public void createPanel(JTabbedPane settingsPanel) {
			GridBagPanel panel = new GridBagPanel();
			
			comps.clear();
			int row=0;
			panel.add(new JLabel(" "), 0,row++, 1,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			GridBagPanel setupPanel = new GridBagPanel();
			setupPanel.add(createButton(Value.MainZoneMenuControl.Setup  ), 0,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			setupPanel.add(createButton(Value.MainZoneMenuControl.Option ), 1,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(setupPanel, 0,row++, 1,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(new JLabel(" "), 0,row++, 1,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(createButton(Value.CursorSelectExt.Up    ), 1,row++, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.CursorSelectExt.Left  ), 0,row  , 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.CursorSelectExt.Sel   ), 1,row  , 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.CursorSelectExt.Right ), 2,row++, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.CursorSelectExt.Down  ), 1,row++, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(new JLabel(" "), 0,row++, 1,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			GridBagPanel row4Panel = new GridBagPanel();
			row4Panel.add(createButton(Value.CursorSelectExt.Return     ), 0,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			row4Panel.add(createButton(Value.MainZoneMenuControl.Display), 1,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			row4Panel.add(createButton(Value.CursorSelectExt.Home       ), 0,1, 1,0, 2,1, GridBagConstraints.HORIZONTAL);
			panel.add(row4Panel, 0,row++, 1,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(new JLabel(" "), 0,row++, 1,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(createButton(Value.PlayPauseStop.Play    ), 0,row  , 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.PlayPauseStop.Pause   ), 1,row  , 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.PlayPauseStop.Stop    ), 2,row++, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			GridBagPanel skipPanel = new GridBagPanel();
			skipPanel.add(createButton(Value.SkipFwdRev.SkipRev,"<<"), 0,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			skipPanel.add(createButton(Value.SkipFwdRev.SkipFwd,">>"), 1,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(skipPanel, 0,row++, 1,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(new JLabel(" "), 0,row++, 1,1, 3,1, GridBagConstraints.BOTH);
			
			settingsPanel.addTab("Play Control", panel);
		}

		private JButton createButton(Value.CursorSelectExt cursor) {
			JButton button = YamahaControl.createButton(cursor.toString(), e->{
				if (device!=null) device.mainPlayCtrl.setCursorSelect(cursor);
			}, true);
			comps.add(button);
			return button;
		}

		private JButton createButton(Value.MainZoneMenuControl menuCtrl) {
			JButton button = YamahaControl.createButton(menuCtrl.toString(), e->{
				if (device!=null) device.mainPlayCtrl.setMenuControl(menuCtrl);
			}, true);
			comps.add(button);
			return button;
		}

		private JButton createButton(Value.PlayPauseStop play) {
			JButton button = YamahaControl.createButton(play.toString(), e->{
				if (device!=null) device.mainPlayCtrl.setPlayback(play);
			}, true);
			comps.add(button);
			return button;
		}

		private JButton createButton(Value.SkipFwdRev skip, String title) {
			JButton button = YamahaControl.createButton(title, e->{
				if (device!=null) device.mainPlayCtrl.setPlayback(skip);
			}, true);
			comps.add(button);
			return button;
		}
	
	}

	private static class OnOffBtn implements GuiRegion {

		private Device device;
		public JButton button;
		
		OnOffBtn() {
			device = null;
			button = createButton("",e->toggleOnOff(),false);
			setOnOffButton(null);
		}
		@Override public void setEnabledGUI(boolean enabled) {
			button.setEnabled(enabled);
		}
		@Override public void initGUIafterConnect(Device device) {
			this.device = device;
			setEnabledGUI(device!=null);
			frequentlyUpdate();
		}
		@Override public void frequentlyUpdate() {
			setOnOffButton(device==null?null:device.getPowerState());
		}
		@Override public EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason) {
			return EnumSet.of( UpdateWish.BasicStatus );
		}

		private void setOnOffButton(Value.PowerState power) {
			SmallImages icon = SmallImages.IconUnknown;
			String title = "???";
			if (power!=null)
				switch (power) {
				case On     : icon = SmallImages.IconOn; title = "On"; break;
				case Standby: icon = SmallImages.IconOff; title = "Off"; break;
				}
			button.setIcon(smallImages.get(icon));
			button.setText(title);
		}

		private void toggleOnOff() {
			if (device!=null) {
				Value.PowerState powerState = device.getPowerState();
				if (powerState==null) device.setPowerState(Value.PowerState.On);
				else device.setPowerState(getNext(powerState,Value.PowerState.values()));
				device.update(EnumSet.of( UpdateWish.BasicStatus ));
			}
			setOnOffButton(device==null?null:device.getPowerState());
		}
	}
	
	private static class VolumeCtrl implements GuiRegion {
		
		private static final int BAR_MAX = 300;
		private static final int BAR_MIN = 0;
		private Device device;
		private RotaryCtrl rotaryCtrl;
		private ValueSetter volumeSetter;
		private JToggleButton muteBtn;
		private JButton decBtn;
		private JButton incBtn;
		private JProgressBar volumeBar;

		VolumeCtrl() {
			device = null;
			rotaryCtrl = null;
			volumeSetter = new ValueSetter(10,(value, isAdjusting) -> {
				device.setVolume(value);
				if (isAdjusting) {
					SwingUtilities.invokeLater(()->{
						updateVolumeBar();
					});
				} else {
					device.update(EnumSet.of( UpdateWish.BasicStatus ));
					SwingUtilities.invokeLater(()->{
						updateValues();
					});
				}
			});
			decBtn = null;
			muteBtn= null;
			incBtn = null;
		}
		
		@Override public void setEnabledGUI(boolean enabled) {
			rotaryCtrl.setEnabled(enabled);
			decBtn .setEnabled(enabled);
			muteBtn.setEnabled(enabled);
			incBtn .setEnabled(enabled);
		}
		@Override public void initGUIafterConnect(Device device) {
			this.device = device;
			setEnabledGUI(device!=null);
			frequentlyUpdate();
		}
		@Override public void frequentlyUpdate() {
			updateValues();
		}

		private void updateValues() {
			rotaryCtrl.setValue(device==null?null:device.getVolume());
			if (device!=null) setMuteBtn(device.getMute());
			updateVolumeBar();
		}
		private void updateVolumeBar() {
			if (device==null) { volumeBar.setValue(BAR_MIN); return; }
			Device.NumberWithUnit volume = device.getVolume();
			if (volume==null) { volumeBar.setValue(BAR_MIN); return; }
			
			double ratio = (volume.getValue()-Device.getMinVolume())/(Device.getMaxVolume()-Device.getMinVolume());
			ratio = Math.max(0.0,Math.min(ratio,1.0));
			
			int barValue = (int)Math.round( ratio*(BAR_MAX-BAR_MIN) + BAR_MIN );
			//Log.info(getClass(), "updateVolumeBar() -> %d", barValue);
			volumeBar.setValue(barValue);
			volumeBar.repaint();
		}

		@Override public EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason) {
			return EnumSet.of( UpdateWish.BasicStatus );
		}

		public JPanel createVolumeControlPanel(int width) {
			// PUT[P2]:    Main_Zone,Volume,Lvl  =  Number: -805..(5)..165 / Exp:"1" / Unit:"dB"
			rotaryCtrl = new RotaryCtrl(width,true, -80.5, +16.5, 3.0, 1.0, 1, -90, (value, isAdjusting) -> {
				if (device==null) return;
				volumeSetter.set(value,isAdjusting);
			});
			volumeBar = new JProgressBar(JProgressBar.HORIZONTAL,BAR_MIN,BAR_MAX);
			volumeBar.setStringPainted(true);
			volumeBar.setString("");
			
			GridBagPanel volumePanel = new GridBagPanel();
			volumePanel.add(rotaryCtrl , 0,0, 1,1, 3,1, GridBagConstraints.BOTH);
			volumePanel.add(volumeBar  , 0,1, 1,1, 3,1, GridBagConstraints.BOTH);
			volumePanel.add(decBtn  = createButton      ("Vol -",e-> decVol(),true), 0,2, 1,1, 1,1, GridBagConstraints.BOTH);
			volumePanel.add(muteBtn = createToggleButton("Mute" ,e->muteVol(),true), 1,2, 1,1, 1,1, GridBagConstraints.BOTH);
			volumePanel.add(incBtn  = createButton      ("Vol +",e-> incVol(),true), 2,2, 1,1, 1,1, GridBagConstraints.BOTH);
			
			setMuteBtn(null);
			return volumePanel;
		}

		private void setMuteBtn(Value.OnOff mute) {
			muteBtn.setSelected(mute==Value.OnOff.On);
		}
		
		private void incVol() { changeVol(+0.5); }
		private void decVol() { changeVol(-0.5); }

		private void changeVol(double d) {
			if (device==null) return;
			Device.NumberWithUnit volume = device.getVolume();
			if (volume==null) return;
			
			if (Device.getMinVolume()>volume.getValue()+d || Device.getMaxVolume()<volume.getValue()+d) return;
			
			device.setVolume(volume.getValue()+d);
			device.update(EnumSet.of( UpdateWish.BasicStatus ));
			updateValues();
		}

		private void muteVol() {
			if (device==null) return;
			
			Value.OnOff mute = device.getMute();
			if (mute==null) return;
			
			device.setMute(getNext(mute,Value.OnOff.values()));
			device.update(EnumSet.of( UpdateWish.BasicStatus ));
			updateValues();
		}
	}
	
	private static class ScenesAndInputs implements GuiRegion {
		
		private Device device;
		private DsiPanel scenesPanel;
		private DsiPanel inputsPanel;
		
		ScenesAndInputs() {
			device = null;
			scenesPanel = null;
			inputsPanel = null;
		}

		@Override public void setEnabledGUI(boolean enabled) {
			scenesPanel.setEnabled(enabled);
			inputsPanel.setEnabled(enabled);
		}

		@Override
		public EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason) {
			switch (reason) {
			case Initial   : return EnumSet.of( UpdateWish.Scenes, UpdateWish.Inputs );
			case Frequently: return EnumSet.noneOf(UpdateWish.class);
			}
			return null;
		}

		@Override
		public void initGUIafterConnect(Device _device) {
			this.device = _device;
			setEnabledGUI(device!=null);
			if (device!=null) {
				scenesPanel.createButtons(device.inputs.getScenes(),this::setScene,dsi->dsi!=null && dsi.rw!=null && dsi.rw.contains("W"));
				inputsPanel.createButtons(device.inputs.getInputs(),this::setInput,null);
			}
			frequentlyUpdate();
		}

		@Override
		public void frequentlyUpdate() {
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

		public void createPanels(JTabbedPane tabbedPane) {
			scenesPanel = new DsiPanel("Scene", true);
			inputsPanel = new DsiPanel("Input", false);
			
			JScrollPane scenesScrollPane = new JScrollPane(scenesPanel);
			JScrollPane inputsScrollPane = new JScrollPane(inputsPanel);
			scenesScrollPane.setMinimumSize(new Dimension(150,20));
			inputsScrollPane.setMinimumSize(new Dimension(150,20));
			scenesScrollPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
			inputsScrollPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
			
			tabbedPane.add("Scenes", scenesScrollPane);
			tabbedPane.add("Inputs", inputsScrollPane);
		}
		
		private class DsiPanel extends GridBagPanel {
			private static final long serialVersionUID = -3330564101527546450L;
			
			private ButtonGroup bg;
			private HashMap<Device.Inputs.DeviceSceneInput,JToggleButton> buttons;

			private boolean createNormalButtons;
			private String itemName;
			
			DsiPanel(String itemName, boolean createNormalButtons) {
				super();
				this.itemName = itemName;
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
				
				add(new JLabel(itemName,JLabel.CENTER),0,1,GridBagConstraints.HORIZONTAL);
				add(new JLabel("ID",JLabel.CENTER),1,GridBagConstraints.REMAINDER,GridBagConstraints.HORIZONTAL);
				
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
					
					JTextField textField = new JTextField("["+dsi.ID+"]");
					textField.setEditable(false);
					
					add(button,0,1,GridBagConstraints.HORIZONTAL);
					add(textField,1,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
					//add(new JLabel(dsi.rw),0,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
				}
				gbc.weighty=1;
				add(new JLabel(""),1,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
			}
		}
	}
	
	private static abstract class AbstractSubUnit extends JPanel implements GuiRegion {
		private static final long serialVersionUID = 7368584160348326790L;
		
		protected boolean disableSubUnitIfNotReady = true;
		
		protected Device device;
		protected Boolean isReady;
		private JLabel readyStateLabel;
		private JLabel tabHeaderComp;

		private String inputID;
		private String tabTitle;

		private JButton activateBtn;
		private Device.Inputs.DeviceSceneInput activateInput;

		protected AbstractSubUnit(String inputID, String tabTitle) {
			super(new BorderLayout(3,3));
			this.inputID = inputID;
			this.tabTitle = tabTitle;
			this.activateInput = null;
		}

		private void createPanel() {
			tabHeaderComp = new JLabel("  "+tabTitle+"  ");
			
			JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,3,3));
			northPanel.add(activateBtn = createButton("Activate", e->{ if (activateInput!=null) device.inputs.setInput(activateInput); }, false));
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
			setEnabledGUI(this.device!=null);
			frequentlyUpdate();
		}

		@Override
		public void frequentlyUpdate() {
			if (activateInput==null && device!=null && device.inputs.hasInputs()) {
				activateInput = device.inputs.findInput(inputID);
				activateBtn.setEnabled(activateInput!=null);
			}
			isReady = getReadyState();
			if (disableSubUnitIfNotReady) setEnabledGUI(isReady());
			tabHeaderComp.setOpaque(isReady());
			tabHeaderComp.setBackground(isReady()?Color.GREEN:null);
			readyStateLabel.setText(tabTitle+" is "+(isReady==null?"not answering":(isReady?"Ready":"Not Ready")));
			readyStateLabel.setIcon(smallImages.get(isReady==null?SmallImages.IconUnknown:(isReady?SmallImages.IconOn:SmallImages.IconOff)));
		}
		
		public boolean isReady() { return isReady!=null && (boolean)isReady; }

		@Override
		public EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason) {
			UpdateWish updateWish = getReadyStateUpdateWish();
			if (updateWish!=null) return EnumSet.of(updateWish);
			return EnumSet.noneOf(UpdateWish.class);
		}

		protected JPanel createContentPanel() { return new JPanel(); }
		protected UpdateWish getReadyStateUpdateWish() { return null; }

		protected abstract Boolean getReadyState();
		protected Boolean askReadyState(Device.KnownCommand.Config cmd) {
			if (device==null) return null;
			// GET:    #######,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			Value.ReadyOrNot readyState = device.askValue(cmd, Value.ReadyOrNot.values(), "Feature_Availability");
			return readyState==null?null:(readyState==Value.ReadyOrNot.Ready);
		}

		@Override public void setEnabledGUI(boolean enabled) { /*setEnabled(enabled);*/ }
	}
	
	private static class SubUnitNetRadio extends AbstractSubUnit_ListPlay implements PlayButtonModule.Caller, ButtonModule.ExtraButtons {
		private static final long serialVersionUID = -8583320100311806933L;

		public SubUnitNetRadio() {
			super("NET RADIO","Net Radio",UpdateWish.NetRadioListInfo,UpdateWish.NetRadioPlayInfo);
			modules.add(new PlayButtonModule(this, this));
			withExtraCharsetConversion = true;
		}

		@Override
		protected Boolean getReadyState() {
			// GET[G3]:    NET_RADIO,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			return askReadyState(Device.KnownCommand.Config.NetRadio);
		}

		@Override public    Device.PlayInfo_NetRadio getPlayInfo()              { return device==null?null:device.netRadio.playInfo; }
		@Override protected Device.ListInfo          getListInfo(Device device) { return device==null?null:device.netRadio.listInfo; }
		
		@Override public void updateExtraButtons() {}
		@Override public void addExtraButtons(Vector<AbstractButton> buttons) {
			buttons.add(createButton("Add Song to PreferredSongs",e->addSongToPreferredSongs(),true));
		}
		private void addSongToPreferredSongs() {
			if (device!=null) {
				if (device.netRadio.playInfo.currentSong!=null)
					preferredSongs.add(device.netRadio.playInfo.currentSong);
				writePreferredSongsToFile();
			}
		}
	}
	
	private static class SubUnitUSB extends AbstractSubUnit_PlayInfoExt<Value.OnOff> {
		private static final long serialVersionUID = 2909543552931897755L;

		public SubUnitUSB() {
			super("USB","USB Device",UpdateWish.USBListInfo,UpdateWish.USBPlayInfo,Value.OnOff.values());
		}
		
		@Override
		protected Boolean getReadyState() {
			// GET[G3]:    USB,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			return askReadyState(Device.KnownCommand.Config.USB);
		}
		
		@Override public    Device.PlayInfoExt<Value.OnOff> getPlayInfo()              { return device==null?null:device.usb.playInfo; }
		@Override protected Device.ListInfo                 getListInfo(Device device) { return device==null?null:device.usb.listInfo; }
	}
	
	private static class SubUnitDLNA extends AbstractSubUnit_PlayInfoExt<Value.OnOff> {
		private static final long serialVersionUID = -4585259335586086032L;

		public SubUnitDLNA() {
			super("SERVER","DLNA Server",UpdateWish.DLNAListInfo,UpdateWish.DLNAPlayInfo,Value.OnOff.values());
		}
		
		@Override
		protected Boolean getReadyState() {
			// GET[G3]:    SERVER,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			return askReadyState(Device.KnownCommand.Config.DLNA);
		}
		
		@Override public    Device.PlayInfoExt<Value.OnOff> getPlayInfo()              { return device==null?null:device.dlna.playInfo; }
		@Override protected Device.ListInfo                 getListInfo(Device device) { return device==null?null:device.dlna.listInfo; }
	}

	private static class SubUnitIPodUSB extends AbstractSubUnit_PlayInfoExt<Value.ShuffleIPod> implements ButtonModule.ExtraButtons {
		private static final long serialVersionUID = -4180795479139795928L;
		private JButton modeBtn;
	
		public SubUnitIPodUSB() {
			super("iPod (USB)","iPod (USB) [untested]",UpdateWish.IPodUSBListInfo,UpdateWish.IPodUSBPlayInfo,Value.ShuffleIPod.values());
			setExtraButtons(this);
		}
	
		@Override
		protected Boolean getReadyState() {
			// GET[G3]:    iPod_USB,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			return askReadyState(Device.KnownCommand.Config.IPodUSB);
		}
		
		@Override
		public EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason) {
			EnumSet<UpdateWish> updateWishes = super.getUpdateWishes(reason);
			updateWishes.add(UpdateWish.IPodUSBMode);
			return updateWishes;
		}

		@Override public    Device.PlayInfoExt<Value.ShuffleIPod> getPlayInfo()              { return device==null?null:device.iPodUSB.playInfo; }
		@Override protected Device.ListInfo                       getListInfo(Device device) { return device==null?null:device.iPodUSB.listInfo; }

		@Override public void updateExtraButtons() {
			modeBtn.setText(device.iPodUSB.mode==null? "iPod Mode":( "iPod Mode: "+device.iPodUSB.mode.getLabel()));
		}
		@Override public void addExtraButtons(Vector<AbstractButton> buttons) {
			buttons.add(modeBtn = createButton("iPod Mode",true));
			modeBtn.addActionListener(e->{
				if (device.iPodUSB.mode==null) return;
				device.iPodUSB.sendSetMode(YamahaControl.getNext(device.iPodUSB.mode,Value.IPodMode.values()));
				updateDeviceNGui();
			});
		}
	}
	
	
	private static class SubUnitTuner extends AbstractSubUnit_Play {
		private static final long serialVersionUID = -8583320100311806933L;
		
		private ToggleButtonGroup<Device.Value.AmFm> bgBand;
		private ToggleButtonGroup<Device.Value.ScanFreq> bgScanAM;
		private ToggleButtonGroup<Device.Value.ScanFreq> bgScanFM;
		private ToggleButtonGroup<Device.Value.ScanTP> bgScanFMTP;
		private RotaryCtrl tuneCtrl;
		private ButtonFactory<UpDown> presetButtons;
		private JComboBox<Device.PlayInfo_Tuner.Preset> presetCmbBx;
		private boolean presetCmbBx_ignoreSelectionEvent;
		private JLabel presetLabel;
		private JButton updatePresetsButton;
		private JTextField freqAmTxtFld;
		private JTextField freqFmTxtFld;
		
		private boolean isEnabled;
		private ValueSetter freqAmSetter;
		private ValueSetter freqFmSetter;
		private Device.Value.AmFm selectedBand;


	
		public SubUnitTuner() {
			super("TUNER","Tuner", UpdateWish.TunerPlayInfo);
			isEnabled = true;
			selectedBand = null;
			presetCmbBx_ignoreSelectionEvent = false;
			freqAmSetter = new ValueSetter(10,(value, isAdjusting) -> {
				device.tuner.setFreqAM((float)value);
				if (isAdjusting)
					SwingUtilities.invokeLater(this::updateFreqAmTxtFld);
				else {
					device.update(getUpdateWishes(UpdateReason.Frequently));
					SwingUtilities.invokeLater(this::updateGui);
				}
			});
			freqFmSetter = new ValueSetter(10,(value, isAdjusting) -> {
				device.tuner.setFreqFM((float)value);
				if (isAdjusting)
					SwingUtilities.invokeLater(this::updateFreqFmTxtFld);
				else {
					device.update(getUpdateWishes(UpdateReason.Frequently));
					SwingUtilities.invokeLater(this::updateGui);
				}
			});
		}

		@Override
		public EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason) {
			EnumSet<UpdateWish> wishes = super.getUpdateWishes(reason);
			switch (reason) {
			case Initial: wishes.add(UpdateWish.TunerPresets); break;
			case Frequently: break;
			}
			return wishes;
		}

		@Override
		public void initGUIafterConnect(Device device) {
			super.initGUIafterConnect(device);
			if (this.device!=null)
				presetCmbBx.setModel(new DefaultComboBoxModel<>(this.device.tuner.playInfo.presets));
		}

		@Override
		public void frequentlyUpdate() {
			super.frequentlyUpdate();
			updateTunerGui();
		}
		
		@Override
		protected UpdateWish getReadyStateUpdateWish() {
			return UpdateWish.TunerConfig;
		}
	
		@Override
		protected Boolean getReadyState() {
			if (device==null) return null;
			if (device.tuner==null) return null;
			if (device.tuner.config==null) return null;
			Value.ReadyOrNot readyState = device.tuner.config.deviceStatus;
			return readyState==null?null:(readyState==Value.ReadyOrNot.Ready);
		}

		@Override
		public void setEnabledGUI(boolean enabled) {
			super.setEnabledGUI(enabled);
			isEnabled = enabled;
			
			bgBand    .setEnabled(enabled);
			bgScanAM  .setEnabled(enabled);
			bgScanFM  .setEnabled(enabled);
			bgScanFMTP.setEnabled(enabled);
			
			tuneCtrl  .setEnabled(enabled);
			freqAmTxtFld.setEnabled(enabled);
			freqFmTxtFld.setEnabled(enabled);
			
			presetLabel  .setEnabled(enabled);
			presetButtons.setEnabled(enabled);
			presetCmbBx  .setEnabled(enabled);
			updatePresetsButton.setEnabled(enabled);
			
			updateTunerGui();
		}

		@Override
		protected PlayInfo getPlayInfo() { return device==null?null:device.tuner.playInfo; }

		private void updateDeviceNGui() {
			device.update(getUpdateWishes(UpdateReason.Frequently));
			updateGui();
		}

		private void updateGui() {
			updatePlayInfo();
			updateTunerGui();
		}

		private void updateTunerGui() {
			if (device==null) return;
			
			if (isEnabled)
				bgBand.setSelected(device.tuner.playInfo.tuningBand);
			
			updateFreqAmTxtFld();
			updateFreqFmTxtFld();
			presetCmbBx_ignoreSelectionEvent = true;
			presetCmbBx.setSelectedItem(device.tuner.playInfo.getCurrentPreset());
			presetCmbBx_ignoreSelectionEvent = false;
				
			if (device.tuner.playInfo.tuningBand==null) {
				bgScanAM  .setEnabled(false);
				bgScanFM  .setEnabled(false);
				bgScanFMTP.setEnabled(false);
			} else
				switch(device.tuner.playInfo.tuningBand) {
				case AM:
					if (isEnabled) {
						bgScanAM  .setEnabled(true);
						bgScanFM  .setEnabled(false);
						bgScanFMTP.setEnabled(false);
						bgScanAM.setSelected(device.tuner.playInfo.tuningFreqAmAutomatic);
						freqAmTxtFld.setEnabled(true);
						freqFmTxtFld.setEnabled(false);
					}
					if (selectedBand!=device.tuner.playInfo.tuningBand) {
						tuneCtrl.setConfig(531,1611, 180.0, 18.0, 0);
						freqFmSetter.clear();
						selectedBand=device.tuner.playInfo.tuningBand;
					}
					if (device.tuner.playInfo.tuningFreqAmValue!=null && !tuneCtrl.isAdjusting()) {
						// PUT[P9]:    Tuner,Play_Control,Tuning,Freq,AM  =  Number: 531..(9)..1611 / Exp:0 / Unit:"kHz"
						tuneCtrl.setValue(device.tuner.playInfo.tuningFreqAmValue);
					}
					break;
					
				case FM:
					if (isEnabled) {
						bgScanAM  .setEnabled(false);
						bgScanFM  .setEnabled(true);
						bgScanFMTP.setEnabled(true);
						freqAmTxtFld.setEnabled(false);
						freqFmTxtFld.setEnabled(true);
						if (device.tuner.playInfo.tuningFreqFmAutomatic==null) {
							bgScanFM  .clearSelection();
							bgScanFMTP.clearSelection();
						} else
							switch (device.tuner.playInfo.tuningFreqFmAutomatic) {
							case AutoDown: bgScanFM.setSelected(Value.ScanFreq.AutoDown); bgScanFMTP.clearSelection(); break;
							case AutoUp  : bgScanFM.setSelected(Value.ScanFreq.AutoUp  ); bgScanFMTP.clearSelection(); break;
							case TPDown  : bgScanFM.clearSelection(); bgScanFMTP.setSelected(Value.ScanTP.TPDown); break;
							case TPUp    : bgScanFM.clearSelection(); bgScanFMTP.setSelected(Value.ScanTP.TPUp  ); break;
							}
					}
					if (selectedBand!=device.tuner.playInfo.tuningBand) {
						tuneCtrl.setConfig(87.5,108.0, 1.0, 0.2, 2);
						freqAmSetter.clear();
						selectedBand=device.tuner.playInfo.tuningBand;
					}
					if (device.tuner.playInfo.tuningFreqFmValue!=null && !tuneCtrl.isAdjusting()) {
						// PUT[P8]:    Tuner,Play_Control,Tuning,Freq,FM  =  Number: 8750..(5)..10800 / Exp:2 / Unit:"MHz"
						tuneCtrl.setValue(device.tuner.playInfo.tuningFreqFmValue);
					}
					break;
				}
		}

		private void updateFreqFmTxtFld() {
			if (device.tuner.playInfo.tuningFreqFmValue!=null)
				freqFmTxtFld.setText(device.tuner.playInfo.tuningFreqFmValue.toValueStr());
		}

		private void updateFreqAmTxtFld() {
			if (device.tuner.playInfo.tuningFreqAmValue!=null)
				freqAmTxtFld.setText(device.tuner.playInfo.tuningFreqAmValue.toValueStr());
		}

		@Override
		protected JPanel createUpperPanel() {
			GridBagPanel buttonPanel = new GridBagPanel();
			buttonPanel.setInsets(new Insets(0,3,0,3));
			
			bgBand = new ToggleButtonGroup<>(Device.Value.AmFm.class,e->{
				if (device==null) return;
				device.tuner.setBand(e);
				updateDeviceNGui();
			});
			buttonPanel.add(bgBand.createLabel("Band: "), 0,0, 0,0, 1,1, GridBagConstraints.BOTH);
			buttonPanel.add(bgBand.createButton("AM",Device.Value.AmFm.AM), 1,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgBand.createButton("FM",Device.Value.AmFm.FM), 2,0, 0,0, 2,1, GridBagConstraints.HORIZONTAL);
			
			freqAmTxtFld = new JTextField();
			freqFmTxtFld = new JTextField();
			freqAmTxtFld.setEditable(false);
			freqFmTxtFld.setEditable(false);
			
			bgScanAM = new ToggleButtonGroup<>(Device.Value.ScanFreq.class,e->{
				if (device==null) return;
				device.tuner.setScanAM(e);
				updateDeviceNGui();
			});
			buttonPanel.add(bgScanAM.createLabel("Frequency",JLabel.CENTER), 1,1, 0,0, 1,1, GridBagConstraints.BOTH);
			buttonPanel.add(freqAmTxtFld, 1,2, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanAM.createButton("Scan Up"  ,Device.Value.ScanFreq.AutoUp  ), 1,3, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanAM.createButton("Scan Down",Device.Value.ScanFreq.AutoDown), 1,4, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanAM.createButton("Cancel"   ,Device.Value.ScanFreq.Cancel  ), 1,5, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			bgScanFM = new ToggleButtonGroup<>(Device.Value.ScanFreq.class,e->{
				if (device==null) return;
				device.tuner.setScanFM(e);
				updateDeviceNGui();
			});
			buttonPanel.add(bgScanFM.createLabel("Frequency",JLabel.CENTER), 2,1, 0,0, 1,1, GridBagConstraints.BOTH);
			buttonPanel.add(freqFmTxtFld, 2,2, 0,0, 2,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanFM.createButton("Scan Up"  ,Device.Value.ScanFreq.AutoUp  ), 2,3, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanFM.createButton("Scan Down",Device.Value.ScanFreq.AutoDown), 2,4, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanFM.createButton("Cancel"   ,Device.Value.ScanFreq.Cancel  ), 2,5, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			bgScanFMTP = new ToggleButtonGroup<>(Device.Value.ScanTP.class,e->{
				if (device==null) return;
				device.tuner.setScanFMTP(e);
				updateDeviceNGui();
			});
			buttonPanel.add(bgScanFMTP.createLabel("Traffic",JLabel.CENTER), 3,1, 0,0, 1,1, GridBagConstraints.BOTH);
			buttonPanel.add(bgScanFMTP.createButton("Scan Up"  ,Device.Value.ScanTP.TPUp  ), 3,3, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanFMTP.createButton("Scan Down",Device.Value.ScanTP.TPDown), 3,4, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanFMTP.createButton("Cancel"   ,Device.Value.ScanTP.Cancel), 3,5, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			presetButtons = new ButtonFactory<Device.Value.UpDown>(Device.Value.UpDown.class, e->{
				if (device==null) return;
				device.tuner.setPreset(e);
				updateDeviceNGui();
			});
			
			presetCmbBx = new JComboBox<>();
			presetCmbBx.addActionListener(e->{
				if (device==null) return;
				if (presetCmbBx_ignoreSelectionEvent) return;
				device.tuner.setPreset((Device.PlayInfo_Tuner.Preset) presetCmbBx.getSelectedItem());
				updateDeviceNGui();
			});
			updatePresetsButton = YamahaControl.createButton("Update", e->{
				if (device==null) return;
				device.update(EnumSet.of(UpdateWish.TunerPresets));
				presetCmbBx_ignoreSelectionEvent = true;
				presetCmbBx.setModel(new DefaultComboBoxModel<>(device.tuner.playInfo.presets));
				presetCmbBx.setSelectedItem(device.tuner.playInfo.getCurrentPreset());
				presetCmbBx_ignoreSelectionEvent = false;
			}, true);
			
			GridBagPanel presetPanel = new GridBagPanel();
			presetPanel.add(presetButtons.createButton("<<",Device.Value.UpDown.Down), 0,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			presetPanel.add(presetCmbBx, 1,0, 1,0, 1,1, GridBagConstraints.BOTH);
			presetPanel.add(presetButtons.createButton(">>",Device.Value.UpDown.Up  ), 2,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			presetPanel.add(updatePresetsButton, 3,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			buttonPanel.add(new JLabel(" "), 0,6, 0,0, 4,1, GridBagConstraints.BOTH);
			buttonPanel.add(presetLabel = new JLabel("Preset:"), 0,7, 0,0, 1,1, GridBagConstraints.BOTH);
			buttonPanel.add(presetPanel, 1,7, 0,0, 3,1, GridBagConstraints.BOTH);
			
			tuneCtrl = new RotaryCtrl(150,false, -1.0, +1.0, 1.0, 1.0, 1, -90, new RotaryCtrl.ValueListener() {
				@Override public void valueChanged(double value, boolean isAdjusting) {
					if (device==null || device.tuner.playInfo.tuningBand==null) return;
					switch(device.tuner.playInfo.tuningBand) {
					case AM: freqAmSetter.set(value, isAdjusting); break;
					case FM: freqFmSetter.set(value, isAdjusting); break;
					}
				}
			});
			addComp(tuneCtrl);
			
			JPanel panel = new JPanel(new BorderLayout(3,3));
			panel.setBorder(BorderFactory.createTitledBorder("Tuner"));
			panel.add(tuneCtrl,BorderLayout.WEST);
			panel.add(buttonPanel,BorderLayout.CENTER);
			
			return panel;
		}
	}

	private static class SubUnitAirPlay extends AbstractSubUnit_AirPlaySpotify {
		private static final long serialVersionUID = 8375036678437177239L;
	
		// [Source_Device | SD_AirPlay | AirPlay]   
		public SubUnitAirPlay() {
			super("AirPlay","AirPlay [untested]",UpdateWish.AirPlayPlayInfo);
		}
		
		@Override public Device.PlayInfo_AirPlaySpotify getPlayInfo() { return device==null?null:device.airPlay.playInfo; }
	
		@Override
		protected UpdateWish getReadyStateUpdateWish() {
			return UpdateWish.AirPlayConfig;
		}
	
		@Override
		protected Boolean getReadyState() {
			if (device==null) return null;
			if (device.airPlay==null) return null;
			if (device.airPlay.config==null) return null;
			Value.ReadyOrNot readyState = device.airPlay.config.deviceStatus;
			return readyState==null?null:(readyState==Value.ReadyOrNot.Ready);
		}
	}

	private static class SubUnitSpotify extends AbstractSubUnit_AirPlaySpotify {
		private static final long serialVersionUID = -869960569061323838L;
	
		public SubUnitSpotify() {
			super("Spotify","Spotify [untested]",UpdateWish.SpotifyPlayInfo);
		}
		
		@Override public Device.PlayInfo_AirPlaySpotify getPlayInfo() { return device==null?null:device.spotify.playInfo; }
	
		@Override
		protected Boolean getReadyState() {
			// GET[G3]:    Spotify,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			return askReadyState(Device.KnownCommand.Config.Spotify);
		}
	}

	public interface PlayInfo_PlayStop {
		public void sendPlayback(Value.PlayStop playState);
		public Value.PlayStop getPlayState();
	}

	public interface PlayInfo_PlayPauseStopSkip {
		public void sendPlayback(Value.PlayPauseStop playState);
		public void sendPlayback(Value.SkipFwdRev skip);
		public Value.PlayPauseStop getPlayState();
	}

	public interface PlayInfo_RepeatShuffle<Shuffle extends Enum<Shuffle>&Value>  {
		public void sendRepeat(Value.OffOneAll repeat);
		public void sendShuffle(Shuffle shuffle);
		public Value.OffOneAll getRepeat();
		public Shuffle getShuffle();
	}

	private static abstract class ButtonModule {
		private ExtraButtons extraButtons;
		
		ButtonModule(ExtraButtons extraButtons) {
			this.extraButtons = extraButtons;
		}
		
		public void updateButtons() {
			updateStdButtons();
			if (extraButtons!=null)
				extraButtons.updateExtraButtons();
		}
		
		public GridBagPanel createButtonPanel(Vector<JComponent> comps) {
			GridBagPanel  buttonPanel = new GridBagPanel();
			
			Vector<AbstractButton> buttons = new Vector<>();
			addStdButtons(buttons);
			if (extraButtons!=null)
				extraButtons.addExtraButtons(buttons);
			
			for (int i=0; i<buttons.size(); ++i) {
				AbstractButton button = buttons.get(i);
				buttonPanel.add(button, i,0, 0,1, 1,1, GridBagConstraints.BOTH);
				comps.add(button);
			}
			return buttonPanel;
		}
		public abstract void updateStdButtons();
		public abstract void addStdButtons(Vector<AbstractButton> buttons);
		
		public static interface ExtraButtons {
			public void updateExtraButtons();
			public void addExtraButtons(Vector<AbstractButton> buttons);
		}
	}

	private static class PlayButtonModule extends ButtonModule {
		private Caller caller;
		private ButtonGroup playButtons;
		private JToggleButton playBtn;
		private JToggleButton stopBtn;
		
		PlayButtonModule(Caller caller, ExtraButtons extraButtons) {
			super(extraButtons);
			this.caller = caller;
		}
		
		public static interface Caller {
			PlayInfo_PlayStop getPlayInfo();
			void updateDeviceNGui();
			boolean isReady();
		}
		
		@Override
		public void updateStdButtons() {
			PlayInfo_PlayStop playInfo = caller.getPlayInfo();
			if (playInfo!=null) {
				Value.PlayStop playState = playInfo.getPlayState();
				if (playState==null || !caller.isReady())
					playButtons.clearSelection();
				else
					switch (playState) {
					case Play : playBtn .setSelected(true); break;
					case Stop : stopBtn .setSelected(true); break;
					}
			} else{
				playButtons.clearSelection();
			}
		}
	
		@Override
		public void addStdButtons(Vector<AbstractButton> buttons) {
			playButtons = new ButtonGroup();
			buttons.add(playBtn = createButton(Value.PlayStop.Play));
			buttons.add(stopBtn = createButton(Value.PlayStop.Stop));
		}
		
		private JToggleButton createButton(Value.PlayStop playState) {
			return YamahaControl.createToggleButton(playState.getLabel(), e->{
				PlayInfo_PlayStop playInfo = caller.getPlayInfo();
				if (playInfo!=null) {
					playInfo.sendPlayback(playState);
					caller.updateDeviceNGui();
				}
			}, true, playButtons);
		}
	}

	private static class PlayButtonModuleExt extends ButtonModule {
		private Caller caller;
		protected ButtonGroup playButtons;
		private JToggleButton playBtn;
		private JToggleButton pauseBtn;
		private JToggleButton stopBtn;
		
		PlayButtonModuleExt(Caller caller, ExtraButtons extraButtons) {
			super(extraButtons);
			this.caller = caller;
		}
		
		public static interface Caller {
			PlayInfo_PlayPauseStopSkip getPlayInfo();
			void updateDeviceNGui();
			boolean isReady();
		}
		
		@Override
		public void updateStdButtons() {
			PlayInfo_PlayPauseStopSkip playInfo = caller.getPlayInfo();
			if (playInfo!=null) {
				Value.PlayPauseStop playState = playInfo.getPlayState();
				if (playState==null || !caller.isReady())
					playButtons.clearSelection();
				else
					switch (playState) {
					case Play : playBtn .setSelected(true); break;
					case Pause: pauseBtn.setSelected(true); break;
					case Stop : stopBtn .setSelected(true); break;
					}
			} else{
				playButtons.clearSelection();
			}
		}

		@Override
		public void addStdButtons(Vector<AbstractButton> buttons) {
			playButtons = new ButtonGroup();
			buttons.add(playBtn    = createButton(     Value.PlayPauseStop.Play ));
			buttons.add(pauseBtn   = createButton(     Value.PlayPauseStop.Pause));
			buttons.add(stopBtn    = createButton(     Value.PlayPauseStop.Stop ));
			buttons.add(             createButton("<<",Value.SkipFwdRev.SkipRev ));
			buttons.add(             createButton(">>",Value.SkipFwdRev.SkipFwd ));
		}
		
		private JToggleButton createButton(Value.PlayPauseStop playState) {
			return YamahaControl.createToggleButton(playState.getLabel(), e->{
				PlayInfo_PlayPauseStopSkip playInfo = caller.getPlayInfo();
				if (playInfo!=null) {
					playInfo.sendPlayback(playState);
					caller.updateDeviceNGui();
				}
			}, true, playButtons);
		}
		
		private JButton createButton(String title, Value.SkipFwdRev skip) {
			return YamahaControl.createButton(title, e->{
				PlayInfo_PlayPauseStopSkip playInfo = caller.getPlayInfo();
				if (playInfo!=null) {
					playInfo.sendPlayback(skip);
					caller.updateDeviceNGui();
				}
			}, true);
		}
		
	}

	private static class ReapeatShuffleButtonModule<Shuffle extends Enum<Shuffle>&Value> extends ButtonModule {
		private Caller<Shuffle> caller;
		private JButton repeatBtn;
		private JButton shuffleBtn;
		private Shuffle[] shuffleValues;
	
		ReapeatShuffleButtonModule(Caller<Shuffle> caller, Shuffle[] shuffleValues, ExtraButtons extraButtons) {
			super(extraButtons);
			this.caller = caller;
			this.shuffleValues = shuffleValues;
		}
		
		public static interface Caller<Sh extends Enum<Sh>&Value> {
			PlayInfo_RepeatShuffle<Sh> getPlayInfo();
			void updateDeviceNGui();
		}
	
		@Override
		public void updateStdButtons() {
			PlayInfo_RepeatShuffle<Shuffle> playInfo = caller.getPlayInfo();
			if (playInfo!=null) {
				Value.OffOneAll repeat = playInfo.getRepeat();
				Shuffle shuffle = playInfo.getShuffle();
				repeatBtn .setText(repeat ==null? "Repeat":( "Repeat: "+repeat .getLabel()));
				shuffleBtn.setText(shuffle==null?"Shuffle":("Shuffle: "+shuffle.getLabel()));
			}
		}
	
		@Override
		public void addStdButtons(Vector<AbstractButton> buttons) {
			buttons.add(repeatBtn  = YamahaControl.createButton("Repeat" ,true));
			buttons.add(shuffleBtn = YamahaControl.createButton("Shuffle",true));
			
			repeatBtn.addActionListener(e->{
				PlayInfo_RepeatShuffle<Shuffle> playInfo = caller.getPlayInfo();
				if (playInfo!=null) {
					Value.OffOneAll repeat = playInfo.getRepeat();
					if (repeat!=null) {
						playInfo.sendRepeat(YamahaControl.getNext(repeat,Value.OffOneAll.values()));
						caller.updateDeviceNGui();
					}
				}
			});
			shuffleBtn.addActionListener(e->{
				PlayInfo_RepeatShuffle<Shuffle> playInfo = caller.getPlayInfo();
				if (playInfo!=null) {
					Shuffle shuffle = playInfo.getShuffle();
					if (shuffle!=null) {
						playInfo.sendShuffle(YamahaControl.getNext(shuffle,shuffleValues));
						caller.updateDeviceNGui();
					}
				}
			});
		}
	}

	private static abstract class AbstractSubUnit_Play extends AbstractSubUnit {
		private static final long serialVersionUID = 7930094230706362373L;

		private   JTextArea   playinfoOutput;
		private   JScrollPane playinfoScrollPane;
		protected UpdateWish  playInfoUpdateWish;
		
		private   Vector<JComponent> comps;
		protected Vector<ButtonModule> modules;

		protected boolean withExtraCharsetConversion;
		
		protected AbstractSubUnit_Play(String inputID, String tabTitle, UpdateWish playInfoUpdateWish) {
			super(inputID, tabTitle);
			this.playInfoUpdateWish = playInfoUpdateWish;
			comps = new Vector<>();
			modules = new Vector<>();
			withExtraCharsetConversion = false;
		}

		@Override
		public EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason) {
			EnumSet<UpdateWish> enumSet = super.getUpdateWishes(reason);
			switch (reason) {
			case Initial:
			case Frequently:
				if (playInfoUpdateWish!=null) enumSet.add(playInfoUpdateWish);
				break;
			}
			return enumSet;
		}

		@Override
		public void frequentlyUpdate() {
			super.frequentlyUpdate();
			updatePlayInfo();
		}

		@Override
		public void setEnabledGUI(boolean enabled) {
			comps.forEach(b->b.setEnabled(enabled));
			playinfoOutput.setEnabled(enabled);
		}
		
		protected abstract JPanel createUpperPanel();
		protected void addComp(JComponent comp) { comps.add(comp); }
		
		@Override
		protected JPanel createContentPanel() {
			comps.clear();
			
			JPanel lineListPanel = createUpperPanel();
			
			playinfoOutput = new JTextArea("<no data>");
			playinfoOutput.setEditable(false);
			playinfoOutput.addMouseListener(createPlayInfoContextMenu());
			
			playinfoScrollPane = new JScrollPane(playinfoOutput);
			playinfoScrollPane.setPreferredSize(new Dimension(500, 200));
			
			GridBagPanel buttonsPanel = new GridBagPanel();
			buttonsPanel.setInsets(new Insets(3,0,3,0));
			for (int i=0; i<modules.size(); ++i) {
				ButtonModule module = modules.get(i);
				buttonsPanel.add(module.createButtonPanel(comps), 0,i, 0,0, 1,1, GridBagConstraints.BOTH);
			}
			
			JPanel playinfoPanel = new JPanel(new BorderLayout(3,3));
			playinfoPanel.setBorder(BorderFactory.createTitledBorder("Play Info"));
			playinfoPanel.add(buttonsPanel,BorderLayout.NORTH);
			playinfoPanel.add(playinfoScrollPane,BorderLayout.CENTER);
			
			if (lineListPanel!=null) {
				JPanel contentPanel = new JPanel(new BorderLayout(3,3));
				contentPanel.add(lineListPanel, BorderLayout.NORTH);
				contentPanel.add(playinfoPanel, BorderLayout.CENTER);
				return contentPanel;
			}
			return playinfoPanel;
		}

		private MouseAdapter createPlayInfoContextMenu() {
			//Font stdFont = playinfoOutput.getFont();
			//Font extFont = new Font("Arial",stdFont.getStyle(),stdFont.getSize());
			
			//ButtonGroup fontBG = new ButtonGroup();
			JCheckBoxMenuItem menuItemExtraConv = YamahaControl.createCheckBoxMenuItem("Additional Charset Conversion",null,null);
			//JCheckBoxMenuItem menuItemStdFont   = YamahaControl.createCheckBoxMenuItem("Use Standard Font",null,fontBG);
			//JCheckBoxMenuItem menuItemExtFont   = YamahaControl.createCheckBoxMenuItem("Use Other Font",null,fontBG);
			
			menuItemExtraConv.addActionListener(e->{
				withExtraCharsetConversion = !withExtraCharsetConversion;
				updatePlayInfoOutput();
			});
			//menuItemStdFont.addActionListener(e->{ menuItemStdFont.setSelected(true); playinfoOutput.setFont(stdFont); updatePlayInfoOutput(); });
			//menuItemExtFont.addActionListener(e->{ menuItemExtFont.setSelected(true); playinfoOutput.setFont(extFont); updatePlayInfoOutput(); });
			
			JPopupMenu playinfoOutputContextmenu = new JPopupMenu();
			playinfoOutputContextmenu.add(menuItemExtraConv);
			//playinfoOutputContextmenu.addSeparator();
			//playinfoOutputContextmenu.add(menuItemStdFont);
			//playinfoOutputContextmenu.add(menuItemExtFont);
			//menuItemStdFont.setSelected(true);
			menuItemExtraConv.setSelected(withExtraCharsetConversion);
			
			MouseAdapter mouseAdapter = new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) {
					if (e.getButton()==MouseEvent.BUTTON3)
						playinfoOutputContextmenu.show(playinfoOutput,e.getX(),e.getY());
				}
			};
			return mouseAdapter;
		}
		
		protected abstract Device.PlayInfo getPlayInfo();

		public void updatePlayInfo() {
			updatePlayInfoOutput();
			modules.forEach(m->m.updateButtons());
		}

		private void updatePlayInfoOutput() {
			float hPos = YamahaControl.getScrollPos(playinfoScrollPane.getHorizontalScrollBar());
			float vPos = YamahaControl.getScrollPos(playinfoScrollPane.getVerticalScrollBar());
			if (device!=null) playinfoOutput.setText(getPlayInfo().toString(withExtraCharsetConversion));
			else              playinfoOutput.setText("<no data>");
			YamahaControl.setScrollPos(playinfoScrollPane.getHorizontalScrollBar(),hPos);
			YamahaControl.setScrollPos(playinfoScrollPane.getVerticalScrollBar(),vPos);
		}
	}

	private static abstract class AbstractSubUnit_ListPlay extends AbstractSubUnit_Play implements LineList.LineListUser, LineList2.LineList2User  {
		private static final long serialVersionUID = 3773609643258015474L;
		
		private   LineList lineList1;
		private   LineList2 lineList2;
		
		protected UpdateWish listInfoUpdateWish;

	
		public AbstractSubUnit_ListPlay(String inputID, String tabTitle, UpdateWish listInfoUpdateWish, UpdateWish playInfoUpdateWish) {
			super(inputID, tabTitle, playInfoUpdateWish);
			this.listInfoUpdateWish = listInfoUpdateWish;
		}

		protected abstract Device.ListInfo getListInfo(Device device);

		@Override
		public EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason) {
			EnumSet<UpdateWish> enumSet = super.getUpdateWishes(reason);
			switch (reason) {
			case Initial:
			case Frequently:
				if (listInfoUpdateWish!=null) enumSet.add(listInfoUpdateWish);
				break;
			}
			return enumSet;
		}

		@Override
		public void initGUIafterConnect(Device device) {
			if (lineList1!=null) lineList1.setDeviceAndListInfo(device,device==null?null:getListInfo(device));
			if (lineList2!=null) lineList2.setDeviceAndListInfo(device,device==null?null:getListInfo(device));
			super.initGUIafterConnect(device);
		}

		@Override
		public void frequentlyUpdate() {
			super.frequentlyUpdate();
			if (lineList1!=null) lineList1.updateLineList();
			if (lineList2!=null) lineList2.updateLineList();
		}

		public void updateDeviceNGui() {
			device.update(getUpdateWishes(UpdateReason.Frequently));
			if (lineList1!=null) lineList1.updateLineList();
			if (lineList2!=null) lineList2.updateLineList();
			updatePlayInfo();
		}

		@Override
		public void setEnabledGuiIfPossible(boolean enabled) {
			setEnabledGUI((isReady || !disableSubUnitIfNotReady) && enabled);
		}

		@Override
		public void setEnabledGUI(boolean enabled) {
			super.setEnabledGUI(enabled);
			if (lineList1!=null) lineList1.setEnabledGUI(enabled);
			if (lineList2!=null) lineList2.setEnabledGUI(enabled);
		}
	
		@Override
		protected JPanel createUpperPanel() {
			
			if (listInfoUpdateWish==null) return null;
			
			lineList1 = null; // new LineList (this,listInfoUpdateWish,playInfoUpdateWish);
			lineList2 = new LineList2(this,listInfoUpdateWish,playInfoUpdateWish);
//			JTabbedPane tabbedPanel = new JTabbedPane();
//			tabbedPanel.add("LineList 1", lineList1.createGUIelements());
//			tabbedPanel.add("LineList 2", lineList2.createGUIelements());
//			tabbedPanel.setSelectedIndex(1);
//			lineListPanel = tabbedPanel;
			JPanel lineListPanel = lineList2.createGUIelements();
			
			lineListPanel.setBorder(BorderFactory.createTitledBorder("Menu"));
			return lineListPanel;
		}
	}

	private static abstract class AbstractSubUnit_PlayInfoExt<Shuffle extends Enum<Shuffle>&Value> extends AbstractSubUnit_ListPlay implements PlayButtonModuleExt.Caller, ReapeatShuffleButtonModule.Caller<Shuffle> {
		private static final long serialVersionUID = 8830354607137619068L;
		private ButtonModule lastModule;
		
		public AbstractSubUnit_PlayInfoExt(String inputID, String tabTitle, UpdateWish listInfoUpdateWish, UpdateWish playInfoUpdateWish, Shuffle[] shuffleValues) {
			super(inputID, tabTitle, listInfoUpdateWish, playInfoUpdateWish);
			modules.add( new PlayButtonModuleExt(this, null));
			modules.add( lastModule = new ReapeatShuffleButtonModule<Shuffle>(this, shuffleValues, null));
		}
		
		protected void setExtraButtons(ButtonModule.ExtraButtons extraButtons) {
			lastModule.extraButtons = extraButtons;
		}
	
		@Override public abstract Device.PlayInfoExt<Shuffle> getPlayInfo();
	}

	private static abstract class AbstractSubUnit_AirPlaySpotify extends AbstractSubUnit_Play implements PlayButtonModuleExt.Caller {
		private static final long serialVersionUID = -1847669703849102028L;
		private ButtonModule lastModule;
	
		public AbstractSubUnit_AirPlaySpotify(String inputID, String tabTitle, UpdateWish playInfoUpdateWish) {
			super(inputID, tabTitle, playInfoUpdateWish);
			modules.add( lastModule = new PlayButtonModuleExt(this, null));
		}
		
		@SuppressWarnings("unused")
		protected void setExtraButtons(ButtonModule.ExtraButtons extraButtons) {
			lastModule.extraButtons = extraButtons;
		}
		
		@Override public void updateDeviceNGui() {
			device.update(getUpdateWishes(UpdateReason.Frequently));
			updatePlayInfo();
		}
	
		@Override protected JPanel createUpperPanel() { return null; }
	
		@Override public abstract Device.PlayInfo_AirPlaySpotify getPlayInfo();
	}

	static class Log {
		enum Type { INFO, WARNING, ERROR }
		public static void log (Type type, Class<?> callerClass, String format, Object... values) {
			switch (type) {
			case INFO   : info   (callerClass, format, values); break;
			case WARNING: warning(callerClass, format, values); break;
			case ERROR  : error  (callerClass, format, values); break;
			}
		}
		
		public static void info   (Class<?> callerClass,                String format, Object... values) { out(System.out, callerClass, "INFO"   ,         format, values); }
		public static void warning(Class<?> callerClass,                String format, Object... values) { out(System.err, callerClass, "WARNING",         format, values); }
		public static void error  (Class<?> callerClass,                String format, Object... values) { out(System.err, callerClass, "ERROR"  ,         format, values); }
		public static void info   (Class<?> callerClass, Locale locale, String format, Object... values) { out(System.out, callerClass, "INFO"   , locale, format, values); }
		public static void warning(Class<?> callerClass, Locale locale, String format, Object... values) { out(System.err, callerClass, "WARNING", locale, format, values); }
		public static void error  (Class<?> callerClass, Locale locale, String format, Object... values) { out(System.err, callerClass, "ERROR"  , locale, format, values); }
		
		private static void out(PrintStream out, Class<?> callerClass, String label,                String format, Object... values) { out(out, callerClass, label, Locale.ENGLISH, format, values); }
		private static void out(PrintStream out, Class<?> callerClass, String label, Locale locale, String format, Object... values) {
			out.printf(locale, "[%s] %s: %s%n", callerClass==null?"???":callerClass.getSimpleName(), label, String.format(locale, format, values));
		}
	}

}
