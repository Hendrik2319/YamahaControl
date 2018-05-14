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
import java.util.Arrays;
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
import java.util.function.Function;
import java.util.function.Predicate;

import javax.activation.DataHandler;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileSystemView;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.yamahacontrol.Device.UpdateWish;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.AutoOff;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.OnOff;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.SurroundProgram;

public class YamahaControl {
	
	private static final String PREFERREDSONGS_FILENAME = "YamahaControl.PreferredSongs.txt";
	static final HashSet<String> preferredSongs = new HashSet<>();

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
	private JLabel updateLabel;
	private JCheckBox updateChkBx;
	
	YamahaControl() {
		mainWindow = null;
		device = null;
		guiRegions = new Vector<>();
		frequentlyUpdater = new FrequentlyTask(2000,false,()->{
			updateLabel.setIcon(smallImages.get(SmallImages.IconOn));
			updateDevice(UpdateReason.Frequently);
			SwingUtilities.invokeLater(()->{
				guiRegions.forEach(gr->gr.frequentlyUpdate());
				updateLabel.setIcon(smallImages.get(SmallImages.IconOff));
			});
		});
	}
	
	private void startUpdater() {
		updateChkBx.setSelected(true);
		frequentlyUpdater.start();
	}

	private void stopUpdater() {
		frequentlyUpdater.stop();
		updateChkBx.setSelected(false);
	}

	private void createGUI() {
		
		OnOffBtn onOffBtn = new OnOffBtn();
		guiRegions.add(onOffBtn);
		
		VolumeCtrl volumeCtrl = new VolumeCtrl();
		guiRegions.add(volumeCtrl);
		
//		updateBtn = new ValueButton<Value.OnOff>(ValueButton.IconSourceOnOff, v->{
//			if (v==null) v=Value.OnOff.Off;
//			switch (v) {
//			case Off: startUpdater(); break;
//			case On : stopUpdater (); break;
//			}
//		});
//		updateBtn.setMargin(new Insets(2,3,2,3));
//		updateBtn.setValue(Value.OnOff.Off);
		
		updateChkBx = new JCheckBox("Update GUI frequently",false);
		updateChkBx.addActionListener(e->{
			if (updateChkBx.isSelected()) startUpdater();
			else stopUpdater ();
		});
		updateLabel = new JLabel(smallImages.get(SmallImages.IconOff),JLabel.LEFT);
		
		GridBagPanel updatePanel = new GridBagPanel();
		updatePanel.add(updateChkBx, 0,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
		updatePanel.add(updateLabel, 1,0, 1,0, 1,1, GridBagConstraints.BOTH);
//		updatePanel.add(updateLabel, 0,0, 1,0, 1,1, GridBagConstraints.BOTH);
//		updatePanel.add(updateBtn, 1,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
		
		GridBagPanel devicePanel = new GridBagPanel();
		devicePanel.setBorder(BorderFactory.createTitledBorder("Device"));
		devicePanel.add(createButton("Connect",e->connectToReciever(),true),0,1,GridBagConstraints.BOTH);
		devicePanel.add(onOffBtn.button,0,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
		devicePanel.add(createButton("Open Command List",e->CommandList.openWindow(device==null?null:device.address),true),0,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
		devicePanel.add(updatePanel,0,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
		
		JTabbedPane settingsPanel = new JTabbedPane();
		settingsPanel.setBorder(BorderFactory.createTitledBorder("Settings"));
		//settingsPanel.setPreferredSize(new Dimension(150,520));
		new ScenesAndInputs().addTo(guiRegions, settingsPanel);
		new RemoteCtrl()     .addTo(guiRegions, settingsPanel);
		new Options()        .addTo(guiRegions, settingsPanel);
		
		JPanel volumeControlPanel = volumeCtrl.createVolumeControlPanel(200);
		volumeControlPanel.setBorder(BorderFactory.createTitledBorder("Volume"));
		
		JPanel mainControlPanel = new JPanel(new BorderLayout(3,3));
		mainControlPanel.add(devicePanel,BorderLayout.NORTH);
		mainControlPanel.add(settingsPanel,BorderLayout.CENTER);
		mainControlPanel.add(volumeControlPanel,BorderLayout.SOUTH);
		
		JTabbedPane subUnitPanel = new JTabbedPane();
		subUnitPanel.setBorder(BorderFactory.createTitledBorder("Sub Units"));
		new SubUnits.SubUnitNetRadio().addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitDLNA    ().addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitUSB     ().addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitTuner   ().addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitIPodUSB ().addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitSpotify ().addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitAirPlay ().addTo(guiRegions,subUnitPanel);
		
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
		//Log.info(getClass(), "updateDevice( %s )", reason);
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
			startUpdater();
		}
	}

	public static class FrequentlyTask implements Runnable {

		private int interval_ms;
		private boolean stop;
		private Runnable task;
		private boolean isRunning;
		private boolean skipFirstWait;
		@SuppressWarnings("unused") private boolean isInTask;
		private boolean isInWait;
		private boolean isInLoop;

		public FrequentlyTask(int interval_ms, boolean skipFirstWait, Runnable task) {
			this.interval_ms = interval_ms;
			this.skipFirstWait = skipFirstWait;
			this.task = task;
			this.stop = false;
			this.isRunning = false;
			this.isInTask = false;
			this.isInWait = false;
			this.isInLoop = false;
		}
		
		public boolean isRunning() {
			return isRunning;
		}

		public void start() {
			stop = false;
			if (!isInLoop)
				new Thread(this).start();
		}

		public void stop() {
			stop = true;
			if (isInWait)
				synchronized (this) { notify(); }
		}

		@Override
		public void run() {
			isRunning = true;
			synchronized (this) {
				
				if (skipFirstWait)
					runTask();
				
				while (!stop) {
					isInLoop = true;
					isInWait = true;
					long startTime = System.currentTimeMillis();
					while (!stop && System.currentTimeMillis()-startTime<interval_ms)
						try { wait(interval_ms-(System.currentTimeMillis()-startTime)); }
						catch (InterruptedException e) {}
					isInWait = false;
					
					if (!stop)
						runTask();
					isInLoop = false;
				}
			}
			isRunning = false;
		}

		private void runTask() {
			isInTask = true;
			task.run();
			isInTask = false;
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
	
	static class ValueComboBox<V extends Value> extends JComboBox<String> {
		private static final long serialVersionUID = 8993527380901830503L;
		
		private boolean isSetting;
		private V[] values;
		
		ValueComboBox(V[] values, Consumer<V> actionListener) {
			super(convert(values));
			this.values = values;
			isSetting = false;
			addActionListener(e->{
				if (isSetting) return;
				actionListener.accept(getSelected());
			});
		}

		public V getSelected() {
			int index = getSelectedIndex();
			return index<0?null:values[index];
		}
		
		public void setSelected(V value) {
			isSetting = true;
			if (value==null)
				setSelectedIndex(-1);
			else {
				boolean found = false;
				for (int i=0; i<values.length; ++i)
					if (value.equals(values[i])) {
						setSelectedIndex(i);
						found = true;
						break;
					}
				if (!found)
					setSelectedIndex(-1);
			}
			isSetting = false;
		}

		private static <V extends Value> String[] convert(V[] values) {
			return Arrays.stream(values).map(v->v.getLabel()).toArray(n->new String[n]);
		}
		
	}

	static class ValueButton<V extends Value> extends JButton {
		private static final long serialVersionUID = -7733433597611049422L;

		private V value;
		private Function<V, SmallImages> iconSource;
		
		ValueButton(Function<V,SmallImages> iconSource, Consumer<V> actionListener) {
			this.iconSource = iconSource;
			addActionListener(e->actionListener.accept(value));
			setValue(null);
		}
		
		public void setValue(V value) {
			this.value = value;
			setText(this.value==null?"???":this.value.getLabel());
			setIcon(smallImages.get(iconSource.apply(this.value)));
		}
		
		static final Function<Value.OnOff, SmallImages> IconSourceOnOff = v->{
			if (v!=null)
				switch (v) {
				case Off: return SmallImages.IconOff;
				case On : return SmallImages.IconOn;
				}
			return SmallImages.IconUnknown;
		};
		static final Function<AutoOff, SmallImages> IconSourceAutoOff = v->{
			if (v!=null)
				switch (v) {
				case Off : return SmallImages.IconOff;
				case Auto: return SmallImages.IconOn;
				}
			return SmallImages.IconUnknown;
		};
		static final Function<Value.PowerState, SmallImages> IconSourcePowerState = v->{
			if (v!=null)
				switch (v) {
				case Standby: return SmallImages.IconOff;
				case On     : return SmallImages.IconOn;
				}
			return SmallImages.IconUnknown;
		};
		static final Function<Value.EnableDisable, SmallImages> IconSourceEnableDisable = v->{
			if (v!=null)
				switch (v) {
				case Disable: return SmallImages.IconOff;
				case Enable : return SmallImages.IconOn;
				}
			return SmallImages.IconUnknown;
		};
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
			setOnOffButton(device==null?null:device.mainZone.basicStatus.power);
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
				Value.PowerState powerState = device.mainZone.basicStatus.power;
				powerState = powerState==null ? Value.PowerState.On : getNext(powerState,Value.PowerState.values());
				device.mainZone.setPowerState(powerState);
				device.update(EnumSet.of( UpdateWish.BasicStatus ));
			}
			setOnOffButton(device==null?null:device.mainZone.basicStatus.power);
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
				device.volume.setVolume(value);
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
			rotaryCtrl.setValue(device==null?null:device.volume.getVolume());
			if (device!=null) setMuteBtn(device.volume.getMute());
			updateVolumeBar();
		}
		private void updateVolumeBar() {
			if (device==null) { volumeBar.setValue(BAR_MIN); return; }
			Device.NumberWithUnit volume = device.volume.getVolume();
			if (volume==null) { volumeBar.setValue(BAR_MIN); return; }
			
			double ratio = (volume.getValue()-Device.Volume.MinVolume)/(Device.Volume.MaxVolume-Device.Volume.MinVolume);
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
			Device.NumberWithUnit volume = device.volume.getVolume();
			if (volume==null) return;
			
			if (Device.Volume.MinVolume>volume.getValue()+d || Device.Volume.MaxVolume<volume.getValue()+d) return;
			
			device.volume.setVolume(volume.getValue()+d);
			device.update(EnumSet.of( UpdateWish.BasicStatus ));
			updateValues();
		}

		private void muteVol() {
			if (device==null) return;
			
			Value.OnOff mute = device.volume.getMute();
			if (mute==null) return;
			
			device.volume.setMute(getNext(mute,Value.OnOff.values()));
			device.update(EnumSet.of( UpdateWish.BasicStatus ));
			updateValues();
		}
	}
	
	private static abstract class AbstractSettingSubPanel implements GuiRegion {
		
		private String tabTitle;
		AbstractSettingSubPanel(String tabTitle) {
			this.tabTitle = tabTitle;
		}

		public void addTo(Vector<YamahaControl.GuiRegion> guiRegions, JTabbedPane settingsPanel) {
			guiRegions.add(this);
			settingsPanel.addTab(tabTitle,createPanel());
		}

		protected abstract JComponent createPanel();
	}
	
	private static class RemoteCtrl extends AbstractSettingSubPanel {
		
		private Device device;
		private Vector<JComponent> comps;
		
		RemoteCtrl() {
			super("Remote Control");
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
	
		@Override
		protected GridBagPanel createPanel() {
			GridBagPanel panel = new GridBagPanel();
			
			comps.clear();
			int row=0;
			panel.add(new JLabel(" "), 0,row++, 0,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			GridBagPanel setupPanel = new GridBagPanel();
			setupPanel.add(createButton(Value.MainZoneMenuControl.Setup  ), 0,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			setupPanel.add(createButton(Value.MainZoneMenuControl.Option ), 1,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(setupPanel, 0,row++, 0,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(new JLabel(" "), 0,row++, 0,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(createButton(Value.CursorSelectExt.Up    ), 1,row++, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.CursorSelectExt.Left  ), 0,row  , 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.CursorSelectExt.Sel   ), 1,row  , 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.CursorSelectExt.Right ), 2,row++, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.CursorSelectExt.Down  ), 1,row++, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(new JLabel(" "), 0,row++, 0,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			GridBagPanel row4Panel = new GridBagPanel();
			row4Panel.add(createButton(Value.CursorSelectExt.Return     ), 0,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			row4Panel.add(createButton(Value.MainZoneMenuControl.Display), 1,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			row4Panel.add(createButton(Value.CursorSelectExt.Home       ), 0,1, 1,0, 2,1, GridBagConstraints.HORIZONTAL);
			panel.add(row4Panel, 0,row++, 0,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(new JLabel(" "), 0,row++, 0,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(createButton(Value.PlayPauseStop.Play    ), 0,row  , 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.PlayPauseStop.Pause   ), 1,row  , 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(createButton(Value.PlayPauseStop.Stop    ), 2,row++, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			GridBagPanel skipPanel = new GridBagPanel();
			skipPanel.add(createButton(Value.SkipFwdRev.SkipRev,"<<"), 0,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			skipPanel.add(createButton(Value.SkipFwdRev.SkipFwd,">>"), 1,0, 1,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(skipPanel, 0,row++, 0,0, 3,1, GridBagConstraints.HORIZONTAL);
			
			panel.add(new JLabel(" "), 0,row++, 0,1, 3,1, GridBagConstraints.BOTH);
			
			return panel;
		}
	
		private JButton createButton(Value.CursorSelectExt cursor) {
			JButton button = YamahaControl.createButton(cursor.toString(), e->{
				if (device!=null) device.remoteCtrl.setCursorSelect(cursor);
			}, true);
			comps.add(button);
			return button;
		}
	
		private JButton createButton(Value.MainZoneMenuControl menuCtrl) {
			JButton button = YamahaControl.createButton(menuCtrl.toString(), e->{
				if (device!=null) device.remoteCtrl.setMenuControl(menuCtrl);
			}, true);
			comps.add(button);
			return button;
		}
	
		private JButton createButton(Value.PlayPauseStop play) {
			JButton button = YamahaControl.createButton(play.toString(), e->{
				if (device!=null) device.remoteCtrl.setPlayback(play);
			}, true);
			comps.add(button);
			return button;
		}
	
		private JButton createButton(Value.SkipFwdRev skip, String title) {
			JButton button = YamahaControl.createButton(title, e->{
				if (device!=null) device.remoteCtrl.setPlayback(skip);
			}, true);
			comps.add(button);
			return button;
		}
	
	}

	private static class ScenesAndInputs extends AbstractSettingSubPanel {
		
		private Device device;
		private DsiPanel scenesPanel;
		private DsiPanel inputsPanel;
		
		ScenesAndInputs() {
			super(null);
			device = null;
			scenesPanel = null;
			inputsPanel = null;
		}

		@Override public void addTo(Vector<GuiRegion> guiRegions, JTabbedPane settingsPanel) {
			guiRegions.add(this);
			createPanels(settingsPanel);
		}

		@Override protected JComponent createPanel() {
			throw new UnsupportedOperationException("ScenesAndInputs.createPanel() should not be called.");
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

		private void createPanels(JTabbedPane tabbedPane) {
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
				
				if (dsiArr!=null)
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
	
	private static class Options extends AbstractSettingSubPanel {

		private Vector<JComponent> comps;
		private Device device;
		private SystemOptions systemOptions;
		private MainZoneSetup mainZoneSetup;
		private Insets defaultButtonInsets;
		
		
		Options() {
			super("Options");
			device = null;
			comps = new Vector<>();
			systemOptions = null;
			defaultButtonInsets = new Insets(2,3,2,3);
		}
		
		@Override
		public EnumSet<UpdateWish> getUpdateWishes(UpdateReason reason) {
			EnumSet<UpdateWish> wishes = EnumSet.noneOf(UpdateWish.class);
			switch (reason) {
			case Initial:
				wishes.add(UpdateWish.System);
				break;
			case Frequently:
				wishes.add(UpdateWish.System);
				break;
			}
			return wishes;
		}

		@Override
		public void initGUIafterConnect(Device device) {
			this.device = device;
			setEnabledGUI(this.device!=null);
			systemOptions.updatePanel();
			mainZoneSetup.updatePanel();
		}

		@Override
		public void frequentlyUpdate() {
			systemOptions.updatePanel();
			mainZoneSetup.updatePanel();
		}

		@Override
		public void setEnabledGUI(boolean enabled) {
			comps.forEach(c->c.setEnabled(enabled));
		}

		private class SystemOptions {
			private ValueButton<Value.OnOff> eventNoticeButton;
			private JTextField networkUpdateSiteField;
			private JTextField networkNameField;
			private ValueButton<Value.PowerState> systemPowerButton;
			private ValueButton<Value.OnOff> networkStandbyButton;
			private ValueButton<Value.EnableDisable> dmcControlButton;
			
			
			public SystemOptions() {
				this.eventNoticeButton = null;
				this.networkUpdateSiteField = null;
				this.networkNameField = null;
				this.systemPowerButton = null;
				this.networkStandbyButton = null;
				this.dmcControlButton = null;
			}


			private GridBagPanel createPanel() {
				//systemOptionsPanel.add(systemOptionsPanel, gridx, gridy, weightx, weighty, gridwidth, gridheight, GridBagConstraints.HORIZONTAL);
				
				// [Event_On]   PUT[P1]     System,Misc,Event,Notice = On
				// [Event_Off]  PUT[P1]     System,Misc,Event,Notice = Off
				// GET[G1]:                 System,Misc,Event,Notice   ->   "On" | "Off"
				eventNoticeButton = new ValueButton<>(ValueButton.IconSourceOnOff,v->{
					Value.OnOff newValue = v==null?Value.OnOff.On:getNext(v, Value.OnOff.values());
					device.system.setEventNotice(newValue);
					device.system.updateEventNotice();
					eventNoticeButton.setValue(device.system.eventNotice);
				});
				eventNoticeButton.setMargin(defaultButtonInsets);
				eventNoticeButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// Network Update   [Network_Update]   Status   [Update_Status]   
				// GET[G4]:    System,Misc,Update,Yamaha_Network_Site,Status   ->   "Available" | "Unavailable"
				networkUpdateSiteField = new JTextField("Unavailable");
				networkUpdateSiteField.setEditable(false);
				
				// PUT[P3]:    System,Misc,Network,Network_Name   =   Text: 1..15 (UTF-8)
				// GET[G2]:    System,Misc,Network,Network_Name   ->   Text: 1..15 (UTF-8)
				networkNameField = new JTextField("");
				JButton networkNameSetButton = createButton("Set",e->{
					String networkName = networkNameField.getText();
					if (!networkName.isEmpty()) {
						device.system.setNetworkName(networkName.substring(0, 15));
						device.system.updateNetworkName();
					}
					updateNetworkNameField();
				},true);
				networkNameSetButton.setMargin(defaultButtonInsets);
				
				// [Power_On]        PUT[P2]     System,Power_Control,Power = On
				// [Power_Standby]   PUT[P2]     System,Power_Control,Power = Standby
				systemPowerButton = new ValueButton<>(ValueButton.IconSourcePowerState,v->{
					Value.PowerState newValue = v==null?Value.PowerState.On:getNext(v, Value.PowerState.values());
					device.system.setPowerState(newValue);
					device.system.updatePowerState();
					systemPowerButton.setValue(device.system.power);
				});
				systemPowerButton.setMargin(defaultButtonInsets);
				systemPowerButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// [Net_Standby_On]   PUT[P4]     System,Misc,Network,Network_Standby = On
				// [Net_Standby_Off]  PUT[P4]     System,Misc,Network,Network_Standby = Off
				// GET[G3]:                       System,Misc,Network,Network_Standby   ->   "On" | "Off"
				networkStandbyButton = new ValueButton<>(ValueButton.IconSourceOnOff,v->{
					Value.OnOff newValue = v==null?Value.OnOff.On:getNext(v, Value.OnOff.values());
					device.system.setNetworkStandby(newValue);
					device.system.updateNetworkStandby();
					networkStandbyButton.setValue(device.system.networkStandby);
				});
				networkStandbyButton.setMargin(defaultButtonInsets);
				networkStandbyButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// [DMR_Off]  PUT[P5]     System,Misc,Network,DMC_Control = Disable
				// [DMR_On]   PUT[P5]     System,Misc,Network,DMC_Control = Enable
				// GET[G5]:               System,Misc,Network,DMC_Control   ->   "Disable" | "Enable"
				dmcControlButton = new ValueButton<>(ValueButton.IconSourceEnableDisable,v->{
					Value.EnableDisable newValue = v==null?Value.EnableDisable.Enable:getNext(v, Value.EnableDisable.values());
					device.system.setDmcControl(newValue);
					device.system.updateDmcControl();
					dmcControlButton.setValue(device.system.dmcControl);
				});
				dmcControlButton.setMargin(defaultButtonInsets);
				dmcControlButton.setHorizontalAlignment(SwingConstants.LEFT);
				
//				JButton updateAllButton = createButton("Update All",e->{
//					device.system.update();
//					updatePanel();
//				},true);
//				comps.add(updateAllButton);
				
				GridBagPanel systemOptionsPanel = new GridBagPanel();
				systemOptionsPanel.setBorder(BorderFactory.createTitledBorder("System Options"));
//				systemOptionsPanel.add(updateAllButton, 0,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
				addField(systemOptionsPanel,1,"Event Notice"       ,eventNoticeButton);
				addField(systemOptionsPanel,2,"Network Update Site",networkUpdateSiteField);
				addField(systemOptionsPanel,3,"Network Name"       ,networkNameField,networkNameSetButton);
				addField(systemOptionsPanel,4,"System Power"       ,systemPowerButton);
				addField(systemOptionsPanel,5,"Network Standby"    ,networkStandbyButton);
				addField(systemOptionsPanel,6,"DMC Control"        ,dmcControlButton);
				
				return systemOptionsPanel;
			}

			private void updatePanel() {
				updateNetworkNameField();
				updateNetworkUpdateSiteField();
				eventNoticeButton   .setValue(device.system.eventNotice);
				systemPowerButton   .setValue(device.system.power);
				networkStandbyButton.setValue(device.system.networkStandby);
				dmcControlButton    .setValue(device.system.dmcControl);
			}

			private void updateNetworkUpdateSiteField() {
				networkUpdateSiteField.setText(device.system.yamahaNetworkSiteStatus==null?"???":device.system.yamahaNetworkSiteStatus.getLabel());
			}

			private void updateNetworkNameField() {
				networkNameField.setText(device.system.networkName==null?"???":device.system.networkName);
			}
		}
		
		private class MainZoneSetup {
			
			private ValueButton<Value.PowerState> powerButton;
			private ValueComboBox<Value.SleepState> sleepSelect;
			private ValueComboBox<SurroundProgram> surroundProgramSelect;
			private ValueButton<OnOff> surroundStraightButton;
			private ValueButton<OnOff> surroundEnhancerButton;
			private ValueButton<AutoOff> adaptiveDRCButton;
			private ValueButton<AutoOff> cinemaDSPButton;
			private ValueButton<OnOff> directModeButton;

			private GridBagPanel createPanel() {
				
				//panel.add(systemOptionsPanel, gridx, gridy, weightx, weighty, gridwidth, gridheight, fill);
				
				// [Power_On]        PUT[P1]     Main_Zone,Power_Control,Power = On
				// [Power_Standby]   PUT[P1]     Main_Zone,Power_Control,Power = Standby
				// GET[G1]:    Main_Zone,Basic_Status   ->   Power_Control,Power -> "On" | "Standby"
				powerButton = new ValueButton<>(ValueButton.IconSourcePowerState,v->{
					Value.PowerState newValue = v==null?Value.PowerState.On:getNext(v, Value.PowerState.values());
					device.mainZone.setPowerState(newValue);
					device.update(EnumSet.of(UpdateWish.BasicStatus));
					powerButton.setValue(device.mainZone.basicStatus.power);
				});
				powerButton.setMargin(defaultButtonInsets);
				powerButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// [Sleep_Last]      PUT[P23]     Main_Zone,Power_Control,Sleep = Last
				// [Sleep_1]         PUT[P23]     Main_Zone,Power_Control,Sleep = 120 min
				// [Sleep_2]         PUT[P23]     Main_Zone,Power_Control,Sleep = 90 min
				// [Sleep_3]         PUT[P23]     Main_Zone,Power_Control,Sleep = 60 min
				// [Sleep_4]         PUT[P23]     Main_Zone,Power_Control,Sleep = 30 min
				// [Sleep_Off]       PUT[P23]     Main_Zone,Power_Control,Sleep = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Power_Control,Sleep -> "120 min" | "90 min" | "60 min" | "30 min" | "Off"
				sleepSelect = new ValueComboBox<>(Value.SleepState.values(), v->{
					device.mainZone.setSleep(v);
					device.update(EnumSet.of(UpdateWish.BasicStatus));
					sleepSelect.setSelected(device.mainZone.basicStatus.sleep);
				});
				
				// PUT[P9]:    Main_Zone,Surround,Program_Sel,Current,Sound_Program   =   "Hall in Munich" | "Hall in Vienna" | "Chamber" | "Cellar Club" | "The Roxy Theatre" | "The Bottom Line" | "Sports" | "Action Game" | "Roleplaying Game" | "Music Video" | "Standard" | "Spectacle" | "Sci-Fi" | "Adventure" | "Drama" | "Mono Movie" | "Surround Decoder" | "2ch Stereo" | "5ch Stereo"
				// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,Program_Sel,Current,Sound_Program -> "Hall in Munich" | "Hall in Vienna" | "Chamber" | "Cellar Club" | "The Roxy Theatre" | "The Bottom Line" | "Sports" | "Action Game" | "Roleplaying Game" | "Music Video" | "Standard" | "Spectacle" | "Sci-Fi" | "Adventure" | "Drama" | "Mono Movie" | "Surround Decoder" | "2ch Stereo" | "5ch Stereo"
				surroundProgramSelect = new ValueComboBox<Value.SurroundProgram>(Value.SurroundProgram.values(), v->{
					device.mainZone.setSurroundProgram(v);
					device.update(EnumSet.of(UpdateWish.BasicStatus));
					surroundProgramSelect.setSelected(device.mainZone.basicStatus.surroundProgram);
				});
				
				// [Straight_On]    PUT[P10]     Main_Zone,Surround,Program_Sel,Current,Straight = On
				// [Straight_Off]   PUT[P10]     Main_Zone,Surround,Program_Sel,Current,Straight = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,Program_Sel,Current,Straight -> "On" | "Off"
				surroundStraightButton = new ValueButton<Value.OnOff>(ValueButton.IconSourceOnOff,v->{
					Value.OnOff newValue = v==null?Value.OnOff.On:getNext(v, Value.OnOff.values());
					device.mainZone.setSurroundStraight(newValue);
					device.update(EnumSet.of(UpdateWish.BasicStatus));
					surroundStraightButton.setValue(device.mainZone.basicStatus.surroundStraight);
				});
				surroundStraightButton.setMargin(defaultButtonInsets);
				surroundStraightButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// [Enhancer_On]    PUT[P11]     Main_Zone,Surround,Program_Sel,Current,Enhancer = On
				// [Enhancer_Off]   PUT[P11]     Main_Zone,Surround,Program_Sel,Current,Enhancer = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,Program_Sel,Current,Enhancer -> "On" | "Off"
				surroundEnhancerButton = new ValueButton<Value.OnOff>(ValueButton.IconSourceOnOff,v->{
					Value.OnOff newValue = v==null?Value.OnOff.On:getNext(v, Value.OnOff.values());
					device.mainZone.setSurroundEnhancer(newValue);
					device.update(EnumSet.of(UpdateWish.BasicStatus));
					surroundEnhancerButton.setValue(device.mainZone.basicStatus.surroundEnhancer);
				});
				surroundEnhancerButton.setMargin(defaultButtonInsets);
				surroundEnhancerButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// PUT[P7]:    Main_Zone,Sound_Video,Tone,Bass                  =   Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
				// GET[G1]:    Main_Zone,Basic_Status -> Sound_Video,Tone,Bass  ->  Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
				// TODO
				
				// PUT[P8]:    Main_Zone,Sound_Video,Tone,Treble                  =   Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
				// GET[G1]:    Main_Zone,Basic_Status -> Sound_Video,Tone,Treble  ->  Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
				// TODO
				
				// [Adaptive_DRC_Auto]   PUT[P12]     Main_Zone,Sound_Video,Adaptive_DRC = Auto
				// [Adaptive_DRC_Off]    PUT[P12]     Main_Zone,Sound_Video,Adaptive_DRC = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Sound_Video,Adaptive_DRC -> "Auto" | "Off"
				adaptiveDRCButton = new ValueButton<Value.AutoOff>(ValueButton.IconSourceAutoOff,v->{
					Value.AutoOff newValue = v==null?Value.AutoOff.Auto:getNext(v, Value.AutoOff.values());
					device.mainZone.setAdaptiveDRC(newValue);
					device.update(EnumSet.of(UpdateWish.BasicStatus));
					adaptiveDRCButton.setValue(device.mainZone.basicStatus.adaptiveDRC);
				});
				adaptiveDRCButton.setMargin(defaultButtonInsets);
				adaptiveDRCButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// [CINEMA_DSP_3D_Auto]  PUT[P13]     Main_Zone,Surround,_3D_Cinema_DSP = Auto
				// [CINEMA_DSP_3D_Off]   PUT[P13]     Main_Zone,Surround,_3D_Cinema_DSP = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,_3D_Cinema_DSP -> "Auto" | "Off"
				cinemaDSPButton = new ValueButton<Value.AutoOff>(ValueButton.IconSourceAutoOff,v->{
					Value.AutoOff newValue = v==null?Value.AutoOff.Auto:getNext(v, Value.AutoOff.values());
					device.mainZone.set3DCinemaDSP(newValue);
					device.update(EnumSet.of(UpdateWish.BasicStatus));
					cinemaDSPButton.setValue(device.mainZone.basicStatus.cinemaDSP);
				});
				cinemaDSPButton.setMargin(defaultButtonInsets);
				cinemaDSPButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// [Direct_On]           PUT[P17]     Main_Zone,Sound_Video,Direct,Mode = On
				// [Direct_Off]          PUT[P17]     Main_Zone,Sound_Video,Direct,Mode = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Sound_Video,Direct,Mode -> "On" | "Off"
				directModeButton = new ValueButton<Value.OnOff>(ValueButton.IconSourceOnOff,v->{
					Value.OnOff newValue = v==null?Value.OnOff.On:getNext(v, Value.OnOff.values());
					device.mainZone.setDirectMode(newValue);
					device.update(EnumSet.of(UpdateWish.BasicStatus));
					directModeButton.setValue(device.mainZone.basicStatus.directMode);
				});
				directModeButton.setMargin(defaultButtonInsets);
				directModeButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// GET[G1]:    Main_Zone,Basic_Status   ->   Sound_Video,HDMI,Standby_Through_Info -> "On" | "Off"
				// TODO
				
				GridBagPanel mainZoneSetupPanel = new GridBagPanel();
				mainZoneSetupPanel.setBorder(BorderFactory.createTitledBorder("MainZone Setup"));
				addField(mainZoneSetupPanel,0,"MainZone Power"   ,powerButton           );
				addField(mainZoneSetupPanel,1,"Sleep"            ,sleepSelect           );
				addField(mainZoneSetupPanel,2,"Surround Program" ,surroundProgramSelect );
				addField(mainZoneSetupPanel,3,"Straight"         ,surroundStraightButton);
				addField(mainZoneSetupPanel,4,"Surround Enhancer",surroundEnhancerButton);
				addField(mainZoneSetupPanel,5,"Adaptive DRC"     ,adaptiveDRCButton     );
				addField(mainZoneSetupPanel,6,"3D Cinema DSP"    ,cinemaDSPButton       );
				addField(mainZoneSetupPanel,7,"Direct Mode"      ,directModeButton      );
				
				return mainZoneSetupPanel;
			}

			public void updatePanel() {
				powerButton           .setValue(device.mainZone.basicStatus.power);
				sleepSelect           .setSelected(device.mainZone.basicStatus.sleep);
				surroundProgramSelect .setSelected(device.mainZone.basicStatus.surroundProgram);
				surroundStraightButton.setValue(device.mainZone.basicStatus.surroundStraight);
				surroundEnhancerButton.setValue(device.mainZone.basicStatus.surroundEnhancer);
				adaptiveDRCButton     .setValue(device.mainZone.basicStatus.adaptiveDRC);
				cinemaDSPButton       .setValue(device.mainZone.basicStatus.cinemaDSP);
				directModeButton      .setValue(device.mainZone.basicStatus.directMode);
			}
		}
		
		@Override
		protected JScrollPane createPanel() {
			systemOptions = new SystemOptions();
			GridBagPanel systemOptionsPanel = systemOptions.createPanel();
			
			mainZoneSetup = new MainZoneSetup();
			GridBagPanel mainZoneSetupPanel = mainZoneSetup.createPanel();
			
			GridBagPanel panel = new GridBagPanel();
			panel.add(systemOptionsPanel, 0,0, 1,1, 1,1, GridBagConstraints.BOTH);
			panel.add(mainZoneSetupPanel, 0,1, 1,1, 1,1, GridBagConstraints.BOTH);
			this.comps.add(systemOptionsPanel);
			this.comps.add(mainZoneSetupPanel);
			
			JScrollPane scrollPane = new JScrollPane(panel);
			scrollPane.setBorder(null);
			//scrollPane.setPreferredSize(new Dimension(150,200));
			return scrollPane;
		}

		private void addField(GridBagPanel panel, int rowIndex, String label, JComponent... comps) {
			JLabel jLabel = new JLabel(label+" : ",JLabel.RIGHT);
			this.comps.add(jLabel);
			panel.add(jLabel, 0,rowIndex, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			for (int i=0; i<comps.length; ++i) {
				this.comps.add(comps[i]);
				panel.add(comps[i], i+1,rowIndex, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			}
		}
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
