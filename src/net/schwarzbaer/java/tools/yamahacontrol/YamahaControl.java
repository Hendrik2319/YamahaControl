package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileSystemView;

import net.schwarzbaer.java.lib.gui.IconSource;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.system.ClipboardTools;
import net.schwarzbaer.java.lib.system.Settings;
import net.schwarzbaer.java.tools.yamahacontrol.Device.UpdateWish;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value;

public class YamahaControl {
	
	private static IconSource.CachedIcons<ToolbarIcons> ToolbarIconsIS = IconSource.createCachedIcons(16, 16, "/Toolbar.png", ToolbarIcons.values());
	public enum ToolbarIcons {
		EmptyDoc, OpenFolder, Save, SaveAs, ReloadDoc, CloseDoc, ZZZ, Cut, Copy, Paste, Delete, Colors;
		public Icon getIcon() { return ToolbarIconsIS.getCachedIcon(this); }
	}
	
	static final AppSettings settings = new AppSettings();
	
	static final PreferredSongs preferredSongs = new PreferredSongs();
	static class PreferredSongs {
		private static final String PREFERREDSONGS_FILENAME = "YamahaControl.PreferredSongs.txt";
		
		final HashMap<String,Long> songs = new HashMap<>();
		
		File getFile() {
			return new File(PREFERREDSONGS_FILENAME);
		}

		public boolean rename(String currentSongName, String newSongName) {
			if (!songs.containsKey(currentSongName)) return false;
			if ( songs.containsKey(    newSongName)) return false;
			
			Long value = songs.remove(currentSongName);
			songs.put(newSongName, value);
			
			return true;
		}

		void add(String currentSong) {
			songs.put(currentSong,System.currentTimeMillis());
		}

		boolean setTimeStamp(String song, Long timeStamp) {
			songs.put(song,timeStamp);
			return true;
		}

		Long getTimeStamp(String song) {
			return songs.get(song);
		}

		Vector<String> getAsSortedList() {
			Vector<String> list = new Vector<>(songs.keySet());
			list.sort(Comparator.nullsLast(Comparator.<String,String>comparing(String::toLowerCase).thenComparing(Comparator.naturalOrder())));
			return list;
		}

		void writeToFile() {
			File file = new File(PREFERREDSONGS_FILENAME);
			System.out.printf("Write list of preferred songs to file \"%s\"%n", file.getAbsolutePath());
			Vector<String> list = getAsSortedList();
			try (PrintWriter out = new PrintWriter( new OutputStreamWriter( new FileOutputStream(file), StandardCharsets.UTF_8) )) {
				list.forEach(str->{
					Long timestamp = songs.get(str);
					if (timestamp==null) out.printf("%s%n", str);
					else                 out.printf("%016X=%s%n", timestamp, str);
				});
			}
			catch (FileNotFoundException e) {}
		}

		void readFromFile() {
			readFromFile(new File(PREFERREDSONGS_FILENAME), songs);
		}

		private HashMap<String, Long> readFromFile(File file, HashMap<String, Long> songs)
		{
			System.out.printf("Read list of preferred songs from file \"%s\"%n", file.getAbsolutePath());
			songs.clear();
			
			try (BufferedReader in = new BufferedReader( new InputStreamReader( new FileInputStream(file), StandardCharsets.UTF_8) ))
			{
				String line;
				while ( (line=in.readLine())!=null )
					if (line.length()>16 && line.charAt(16)=='=')
					{
						String timeStr = line.substring(0, 16);
						String name = line.substring(17);
						try { songs.put(name,Long.parseUnsignedLong(timeStr, 16)); }
						catch (NumberFormatException e) { songs.put(line,null); }
						
					}
					else if (!line.isEmpty())
						songs.put(line,null);
			}
			catch (FileNotFoundException e) {}
			catch (IOException e) {
				e.printStackTrace();
			}
			
			return songs; 
		}

		public void addFromFile(File file)
		{
			readFromFile(file, new HashMap<>()).forEach(songs::putIfAbsent);
		}

		boolean setTimeStampsOfUnsetSongs(File file, Function<Long,Long> getData) {
			if (file==null || getData==null) return false;
			
			String wrongLine = new String(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF }, StandardCharsets.UTF_8);
			Vector<String> unsetSongs = new Vector<>();
			try(BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
				
				System.out.printf("Read songs from text file \"%s\" ...%n", file.getAbsolutePath());
				for (String line = in.readLine(); line!=null; line = in.readLine()) {
					if (line.equals(wrongLine)) continue;
					
					if (!songs.containsKey(line)) {
						System.err.printf("Found unknown song: \"%s\"%n", line);
						System.err.printf("It could be a wrong file.%n");
						return false;
						
					} else if (songs.get(line)==null)
						unsetSongs.add(line);
				}
				System.out.printf("done%n");
				 
			}
			catch (FileNotFoundException e) {
				System.err.printf("FileNotFoundException: %s%n", e.getMessage());
				return false;
			} catch (IOException e) {
				System.err.printf("IOException: %s%n", e.getMessage());
				return false;
			}
			
			Long newValue = getData.apply(file.lastModified());
			if (newValue==null) return false;
			
			for (String song:unsetSongs)
				songs.put(song,newValue);
			
			return true;
		}
	}

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		Config.readConfig();
		Ctrl.readCommProtocolFromFile();
//		new Responder().openWindow();
		
		preferredSongs.readFromFile();
		createSmallImages();
//		BufferedImage mergedIcons = mergeIcons(SmallImages.values(), smallImages::get);
//		try {
//			ImageIO.write(mergedIcons, "png", new File("./MergedSmallImages.png"));
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		
		
		System.out.println("YamahaControl");
		System.out.println("created by Hendrik Scholtz");
		System.out.println();
		
		YamahaControl yamahaControl = new YamahaControl();
		if (args.length <= 0) {
			yamahaControl.showCommands();
			return;
		}
		
		String address = null;
		GUI createdGUI = yamahaControl.parseArgs(args);
		if (createdGUI!=null)
			switch (createdGUI) {
				case CommandList:
					if (yamahaControl.device!=null)
						address = yamahaControl.device.address;
					CommandList.openWindow(address,true);
					break;
				case Generic:
					if (yamahaControl.device!=null)
						address = yamahaControl.device.address;
					GenericYamahaControl.openWindow(address);
					break;
				case YamahaControl:
					yamahaControl.createGUI();
					if (yamahaControl.device!=null)
						yamahaControl.initGUIafterConnect();
					break;
			}
	}

	public enum SmallImages { IconOn, IconOff, IconUnknown, FolderIcon, IconPlay, IconNoPlay }
	public static EnumMap<SmallImages,Icon> smallImages = null;
	private static void createSmallImages() {
		smallImages = new EnumMap<>(SmallImages.class);
		for (SmallImages id:SmallImages.values()) {
			switch (id) {
			case IconOff    : smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,11,Color.BLACK,Color.GREEN.darker())); break;
			case IconOn     : smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,11,Color.BLACK,Color.GREEN)); break;
			case IconUnknown: smallImages.put(id, ImageToolbox.createIcon_Circle(16,12,11,Color.BLACK,Color.GRAY)); break;
			case IconPlay   : smallImages.put(id, ImageToolbox.createIcon_TriangleToRight(16, 13, 11, 11, Color.BLACK,new Color(0x00ddff))); break;
			case IconNoPlay : smallImages.put(id, ImageToolbox.createIcon_TriangleToRight(16, 13, 11, 11, Color.BLACK,Color.GRAY)); break;
			case FolderIcon : smallImages.put(id, FileSystemView.getFileSystemView().getSystemIcon(new File("./"))); break;
			}
		}
	}
	@SuppressWarnings("unused")
	private static <A extends Enum<A>> BufferedImage mergeIcons(A[] keys, Function<A,Icon> getIcon) {
		Icon[] extractedIcons = new Icon[keys.length];
		int maxHeight = 0;
		int sumOfWidths = 0;
		for (int i=0; i<keys.length; i++) {
			extractedIcons[i] = getIcon.apply(keys[i]);
			if (extractedIcons[i]!=null) {
				maxHeight = Math.max(maxHeight, extractedIcons[i].getIconHeight());
				sumOfWidths += extractedIcons[i].getIconWidth();
			}
		}
		
		BufferedImage image = new BufferedImage(sumOfWidths+2*extractedIcons.length, maxHeight+2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = image.createGraphics();
		int x=0;
		for (int i=0; i<extractedIcons.length; i++)
			if (extractedIcons[i]!=null) {
				g2.setColor(Color.MAGENTA);
				int width  = extractedIcons[i].getIconWidth();
				int height = extractedIcons[i].getIconHeight();
				g2.drawRect(x, 0, width+1, height+1);
				extractedIcons[i].paintIcon(null, g2, x, 1);
				if (height<maxHeight)
					g2.fillRect(x, height+2, width+2, maxHeight-height);
				x += width+2;
			}
		
		return image;
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
	
	private enum GUI { YamahaControl, CommandList, Generic }
	private GUI parseArgs(String[] args) {
		GUI createdGUI = null;
		
		Vector<String> commands = new Vector<>();
		for (int i=0; i<args.length; ++i) {
			switch (args[i].toLowerCase()) {
			case "-addr": if (i+1<args.length) { device = new Device(args[++i]); } break;
			case "-gui"        : createdGUI = GUI.YamahaControl; break;
			case "-commandlist": createdGUI = GUI.CommandList; break;
			case "-generic"    : createdGUI = GUI.Generic; break;
			default: commands.add(args[i]); break;
			}
		}
		
		if (device!=null) {
			for (String cmd:commands) {
				switch (cmd.toLowerCase()) {
				case "switchoff" : device.mainZone.setPowerState(Value.PowerState.Standby); break;
				case "switchon"  : device.mainZone.setPowerState(Value.PowerState.On     ); break;
				case "togglemute":
					device.update(EnumSet.of( UpdateWish.BasicStatus ));
					Value.OnOff value = device.volume.getMute();
					value = value==null ? Value.OnOff.On : getNext(value, Value.OnOff.values());
					device.volume.setMute( value );
					break;
				}
			}
		}
		
		return createdGUI;
	}

	private void showCommands() {
		System.out.println("Usage:");
		System.out.println("   ... YamahaControl [PARAMETERS] [DEVICE COMMANDS]");
		System.out.println();
		System.out.println("Example:");
		System.out.println("   ... YamahaControl -addr 192.168.1.42 -gui SwitchON");
		System.out.println();
		System.out.println("Parameters:");
		System.out.println("   -addr [IPAddress or Name]");
		System.out.println("       sets device address");
		System.out.println("   -gui");
		System.out.println("       starts GUI after processing commands");
		System.out.println("   -commandlist");
		System.out.println("       starts CommandList after processing commands");
		System.out.println("   -generic");
		System.out.println("       starts generic GUI after processing commands");
		System.out.println();
		System.out.println("Device Commands:");
		System.out.println("   SwitchOFF");
		System.out.println("       switches device off");
		System.out.println("   SwitchON");
		System.out.println("       switches device on");
		System.out.println("   ToggleMute");
		System.out.println("       toggles volume mute");
	}

	private void createGUI() {
		mainWindow = new StandardMainWindow("YamahaControl");
		
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
		devicePanel.add(createButton("Connect",true,e->connectToReciever()),0,1,GridBagConstraints.BOTH);
		devicePanel.add(onOffBtn.button,0,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
		devicePanel.add(createButton("Open Command List",true,e->CommandList.openWindow(device==null?null:device.address,false)),0,GridBagConstraints.REMAINDER,GridBagConstraints.BOTH);
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
		new SubUnits.SubUnitNetRadio(mainWindow).addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitDLNA    (mainWindow).addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitUSB     (mainWindow).addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitTuner   (mainWindow).addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitIPodUSB (mainWindow).addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitSpotify (mainWindow).addTo(guiRegions,subUnitPanel);
		new SubUnits.SubUnitAirPlay (mainWindow).addTo(guiRegions,subUnitPanel);
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		contentPane.add(mainControlPanel,BorderLayout.WEST);
		contentPane.add(subUnitPanel,BorderLayout.CENTER);
		
		mainWindow.startGUI(contentPane);
		mainWindow.setIconImagesFromResource("/Yamaha_Logo_%d.png",16,24,32,48,64,96,128,256);
		
		guiRegions.forEach(gr->gr.setEnabledGUI(false));
		
		settings.registerAppWindow(mainWindow);
	}
	
	private static class ImageToolbox {

		public static Icon createIcon_Circle(int imgWidth, int imgHeight, int diameter, Color border, Color fill) {
			BufferedImage image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = image.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			int x = (imgWidth -diameter)/2;
			int y = (imgHeight-diameter)/2;
			
			Color fill2 = fill;
			Color fill1 = fill2.darker();
			g2.setPaint(fill1 ); g2.fillOval(x+1, y+1, diameter-2, diameter-2);
			g2.setPaint(fill2 ); g2.fillOval(x+2, y+2, diameter-4, diameter-4);
			g2.setPaint(border); g2.drawOval(x  , y  , diameter-1, diameter-1);
			
			return new ImageIcon(image);
		}

		public static Icon createIcon_TriangleToRight(int imgWidth, int imgHeight, int triWidth, int triHeight, Color border, Color fill) {
			BufferedImage image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = image.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			int x = (imgWidth -triWidth)/2;
			int y = (imgHeight-triHeight)/2;
			
			Color fill2 = fill;
			Color fill1 = fill2.darker();
			
			Polygon p;
			
			g2.setPaint(fill1);
			p = new Polygon();
			p.addPoint(x+1, y+1);
			p.addPoint(x+triWidth-1, y+triHeight/2);
			p.addPoint(x+1, y+triHeight-1);
			g2.fillPolygon(p );
			
			g2.setPaint(fill2);
			p = new Polygon();
			p.addPoint(x+2, y+2);
			p.addPoint(x+triWidth-2, y+triHeight/2);
			p.addPoint(x+2, y+triHeight-2);
			g2.fillPolygon(p );
			
			g2.setPaint(border);
			p = new Polygon();
			p.addPoint(x, y);
			p.addPoint(x+triWidth-1, y+triHeight/2);
			p.addPoint(x, y+triHeight-1);
			g2.drawPolygon(p );
			
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
			initGUIafterConnect();
		}
	}

	private void initGUIafterConnect() {
		updateDevice(UpdateReason.Initial);
		guiRegions.forEach(gr->gr.initGUIafterConnect(device));
		startUpdater();
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
	static JButton createButton(String title, boolean enabled, ActionListener al) {
		JButton button = createButton(title,enabled);
		if (al!=null) button.addActionListener(al);
		return button;
	}
	static JButton createButton(String title, Icon icon, boolean enabled, ActionListener al) {
		JButton button = new JButton(title,icon);
		button.setEnabled(enabled);
		if (al!=null) button.addActionListener(al);
		return button;
	}

	static JToggleButton createToggleButton(String title, boolean enabled, ButtonGroup bg, ActionListener l) {
		JToggleButton button = createToggleButton(title, enabled, l);
		if (bg!=null) bg.add(button);
		return button;
	}

	static JToggleButton createToggleButton(String title, boolean enabled, ActionListener l) {
		JToggleButton button = new JToggleButton(title);
		button.setEnabled(enabled);
		if (l!=null) button.addActionListener(l);
		return button;
	}

	static JMenuItem createMenuItem(String title, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	static JCheckBoxMenuItem createCheckBoxMenuItem(String title, ButtonGroup bg, ActionListener l) {
		JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(title);
		if (l!=null) menuItem.addActionListener(l);
		if (bg!=null) bg.add(menuItem);
		return menuItem;
	}

	static <A> JComboBox<A> createComboBox(A[] values) {
		return createComboBox(values, null);
	}
	static <A> JComboBox<A> createComboBox(A[] values, Consumer<A> setValue) {
		return createComboBox(values, null, null);
	}
	static <A> JComboBox<A> createComboBox(A[] values, A selected, Consumer<A> setValue) {
		JComboBox<A> comboBox = new JComboBox<A>(values);
		comboBox.setSelectedItem(selected);
		if (setValue!=null) comboBox.addActionListener(e->{
			int i = comboBox.getSelectedIndex();
			setValue.accept(i<0 ? null : values[i]);
		});
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
		ClipboardTools.copyToClipBoard(str);
	}

	static String pasteFromClipBoard() {
		return ClipboardTools.getStringFromClipBoard(true);
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
			JButton button = YamahaControl.createButton(title, true, l);
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
			JToggleButton button = YamahaControl.createToggleButton(title, true, bg, l);
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
		static final Function<Value.AutoOff, SmallImages> IconSourceAutoOff = v->{
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

	static class ValueSetter {

		static interface Setter {
			public void setValue(double value, boolean isAdjusting);
		}

		private final Setter setter;
		private final ExecutorService executor;
		private Double nextValue;
		private Boolean nextIsAdjusting;
		private Future<?> runningTask;
		
		ValueSetter(Setter setter) {
			this.setter = setter;
			executor = Executors.newSingleThreadExecutor();
			nextValue = null;
			nextIsAdjusting = null;
			runningTask = null;
		}

		synchronized void set(double value, boolean isAdjusting) {
			//System.out.printf(Locale.ENGLISH, "ValueSetter: set( %1.5f, %s )%n", value, isAdjusting);
			nextValue = value;
			nextIsAdjusting = isAdjusting;
			
			if (runningTask!=null)
				return;
			
			runningTask = executor.submit(()->{
				
				boolean isActive = true;
				while (isActive) {
					
					double value_ = 0;
					boolean isAdjusting_ = false;
					
					synchronized (this) {
						if (nextValue==null || nextIsAdjusting==null) {
							runningTask = null;
							isActive = false;
						} else {
							value_ = nextValue;
							isAdjusting_ = nextIsAdjusting;
						}
						nextValue = null;
						nextIsAdjusting = null;
					}
					
					if (isActive) {
						//System.out.printf(Locale.ENGLISH, "ValueSetter: ( %1.5f, %s ) -> start%n", value_, isAdjusting_);
						setter.setValue(value_, isAdjusting_);
						//System.out.printf(Locale.ENGLISH, "ValueSetter: ( %1.5f, %s ) -> done%n", value_, isAdjusting_);
					}
					
				}
			});
		}

		synchronized void clear() {
			nextValue = null;
			nextIsAdjusting = null;
			if (runningTask!=null) {
				runningTask.cancel(false);
				runningTask = null;
			}
		}
	}
	
	static class QueuedValueSetter {
		private ExecutorService executor;
		private int counter;
		private int queueLength;
		private Stack<Future<?>> runningTasks;
		private Setter setter;

		QueuedValueSetter(int queueLength, Setter setter) {
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
			button = createButton("",false,e->toggleOnOff());
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
		private Device device = null;
		private RotaryCtrl2 rotaryCtrl = null;
		private ValueSetter volumeSetter;
		private JToggleButton muteBtn = null;
		private JButton decBtn = null;
		private JButton incBtn = null;
		private JProgressBar volumeBar = null;

		VolumeCtrl() {
			volumeSetter = new ValueSetter((value, isAdjusting) -> {
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
			rotaryCtrl = new RotaryCtrl2("Volume",width,false, -80.5, +16.5, 8.0, 1.0, 1, -90, (value, isAdjusting) -> {
				if (device==null) return;
				volumeSetter.set(value,isAdjusting);
			});
			volumeBar = new JProgressBar(JProgressBar.HORIZONTAL,BAR_MIN,BAR_MAX);
			volumeBar.setStringPainted(true);
			volumeBar.setString("");
			
			GridBagPanel volumePanel = new GridBagPanel();
			volumePanel.add(rotaryCtrl , 0,0, 1,1, 3,1, GridBagConstraints.BOTH);
			volumePanel.add(volumeBar  , 0,1, 1,1, 3,1, GridBagConstraints.BOTH);
			volumePanel.add(decBtn  = createButton      ("Vol -",true,e-> decVol()), 0,2, 1,1, 1,1, GridBagConstraints.BOTH);
			volumePanel.add(muteBtn = createToggleButton("Mute" ,true,e->muteVol()), 1,2, 1,1, 1,1, GridBagConstraints.BOTH);
			volumePanel.add(incBtn  = createButton      ("Vol +",true,e-> incVol()), 2,2, 1,1, 1,1, GridBagConstraints.BOTH);
			
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
		protected JScrollPane createPanel() {
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
			
			JScrollPane scrollPane = new JScrollPane(panel);
			scrollPane.setBorder(null);
			//scrollPane.setPreferredSize(new Dimension(150,200));
			return scrollPane;
		}
	
		private JButton createButton(Value.CursorSelectExt cursor) {
			JButton button = YamahaControl.createButton(cursor.toString(), true, e->{
				if (device!=null) device.remoteCtrl.setCursorSelect(cursor);
			});
			comps.add(button);
			return button;
		}
	
		private JButton createButton(Value.MainZoneMenuControl menuCtrl) {
			JButton button = YamahaControl.createButton(menuCtrl.toString(), true, e->{
				if (device!=null) device.remoteCtrl.setMenuControl(menuCtrl);
			});
			comps.add(button);
			return button;
		}
	
		private JButton createButton(Value.PlayPauseStop play) {
			JButton button = YamahaControl.createButton(play.toString(), true, e->{
				if (device!=null) device.remoteCtrl.setPlayback(play);
			});
			comps.add(button);
			return button;
		}
	
		private JButton createButton(Value.SkipFwdRev skip, String title) {
			JButton button = YamahaControl.createButton(title, true, e->{
				if (device!=null) device.remoteCtrl.setPlayback(skip);
			});
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
							button = createButton(title, true, e->setFunction.accept(dsi));
						} else {
							JToggleButton tButton = createToggleButton(title, true, bg, e->setFunction.accept(dsi));
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
				networkUpdateSiteField = new JTextField("");
				networkUpdateSiteField.setEditable(false);
				
				// PUT[P3]:    System,Misc,Network,Network_Name   =   Text: 1..15 (UTF-8)
				// GET[G2]:    System,Misc,Network,Network_Name   ->   Text: 1..15 (UTF-8)
				networkNameField = new JTextField("",8);
				JButton networkNameSetButton = createButton("Set",true,e->{
					String networkName = networkNameField.getText();
					if (!networkName.isEmpty()) {
						device.system.setNetworkName(networkName.substring(0, 15));
						device.system.updateNetworkName();
					}
					updateNetworkNameField();
				});
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
				int row = 0;
//				systemOptionsPanel.add(updateAllButton, 0,row++, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
				addField(systemOptionsPanel,row++,"System Power"       ,2,systemPowerButton);
				addField(systemOptionsPanel,row++,"Network Standby"    ,2,networkStandbyButton);
				addField(systemOptionsPanel,row++,"Network Name"       ,networkNameField,networkNameSetButton);
				addField(systemOptionsPanel,row++,"Network Update Site",2,networkUpdateSiteField);
				addField(systemOptionsPanel,row++,"Event Notice"       ,2,eventNoticeButton);
				addField(systemOptionsPanel,row++,"DMC Control"        ,2,dmcControlButton);
				
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
				if (networkNameField.isFocusOwner()) return;
				networkNameField.setText(device.system.networkName==null?"???":device.system.networkName);
			}
		}
		
		private class MainZoneSetup {
			
			private ValueButton<Value.PowerState> powerButton;
			private ValueComboBox<Value.SleepState> sleepSelect;
			private ValueComboBox<Value.SurroundProgram> surroundProgramSelect;
			private ValueButton<Value.OnOff> surroundStraightButton;
			private ValueButton<Value.OnOff> surroundEnhancerButton;
			private ValueButton<Value.AutoOff> adaptiveDRCButton;
			private ValueButton<Value.AutoOff> cinemaDSPButton;
			private ValueButton<Value.OnOff> directModeButton;
			private JTextField hdmiStandbyThroughField;
			private JSlider bassSlider;
			private JSlider trebleSlider;
			private ValueSetter bassSetter;
			private ValueSetter trebleSetter;

			private GridBagPanel createPanel() {
				
				//panel.add(systemOptionsPanel, gridx, gridy, weightx, weighty, gridwidth, gridheight, fill);
				
				// [Power_On]        PUT[P1]     Main_Zone,Power_Control,Power = On
				// [Power_Standby]   PUT[P1]     Main_Zone,Power_Control,Power = Standby
				// GET[G1]:    Main_Zone,Basic_Status   ->   Power_Control,Power -> "On" | "Standby"
				powerButton = new ValueButton<>(ValueButton.IconSourcePowerState,v->{
					Value.PowerState newValue = v==null?Value.PowerState.On:getNext(v, Value.PowerState.values());
					device.mainZone.setPowerState(newValue);
					updateBasicStatusAndPanel();
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
					updateBasicStatusAndPanel();
				});
				sleepSelect.setSelected(null);
				
				// PUT[P9]:    Main_Zone,Surround,Program_Sel,Current,Sound_Program   =   "Hall in Munich" | "Hall in Vienna" | "Chamber" | "Cellar Club" | "The Roxy Theatre" | "The Bottom Line" | "Sports" | "Action Game" | "Roleplaying Game" | "Music Video" | "Standard" | "Spectacle" | "Sci-Fi" | "Adventure" | "Drama" | "Mono Movie" | "Surround Decoder" | "2ch Stereo" | "5ch Stereo"
				// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,Program_Sel,Current,Sound_Program -> "Hall in Munich" | "Hall in Vienna" | "Chamber" | "Cellar Club" | "The Roxy Theatre" | "The Bottom Line" | "Sports" | "Action Game" | "Roleplaying Game" | "Music Video" | "Standard" | "Spectacle" | "Sci-Fi" | "Adventure" | "Drama" | "Mono Movie" | "Surround Decoder" | "2ch Stereo" | "5ch Stereo"
				surroundProgramSelect = new ValueComboBox<Value.SurroundProgram>(Value.SurroundProgram.values(), v->{
					device.mainZone.setSurroundProgram(v);
					updateBasicStatusAndPanel();
				});
				surroundProgramSelect.setSelected(null);
				
				// [Straight_On]    PUT[P10]     Main_Zone,Surround,Program_Sel,Current,Straight = On
				// [Straight_Off]   PUT[P10]     Main_Zone,Surround,Program_Sel,Current,Straight = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,Program_Sel,Current,Straight -> "On" | "Off"
				surroundStraightButton = new ValueButton<Value.OnOff>(ValueButton.IconSourceOnOff,v->{
					Value.OnOff newValue = v==null?Value.OnOff.On:getNext(v, Value.OnOff.values());
					device.mainZone.setSurroundStraight(newValue);
					updateBasicStatusAndPanel();
				});
				surroundStraightButton.setMargin(defaultButtonInsets);
				surroundStraightButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// [Enhancer_On]    PUT[P11]     Main_Zone,Surround,Program_Sel,Current,Enhancer = On
				// [Enhancer_Off]   PUT[P11]     Main_Zone,Surround,Program_Sel,Current,Enhancer = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,Program_Sel,Current,Enhancer -> "On" | "Off"
				surroundEnhancerButton = new ValueButton<Value.OnOff>(ValueButton.IconSourceOnOff,v->{
					Value.OnOff newValue = v==null?Value.OnOff.On:getNext(v, Value.OnOff.values());
					device.mainZone.setSurroundEnhancer(newValue);
					updateBasicStatusAndPanel();
				});
				surroundEnhancerButton.setMargin(defaultButtonInsets);
				surroundEnhancerButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// PUT[P7]:    Main_Zone,Sound_Video,Tone,Bass                  =   Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
				// GET[G1]:    Main_Zone,Basic_Status -> Sound_Video,Tone,Bass  ->  Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
				bassSlider = new JSlider(JSlider.HORIZONTAL, -12, 12, 0);
				bassSlider.setMajorTickSpacing(4);
				bassSlider.setMinorTickSpacing(1);
				bassSlider.setPaintTicks(true);
				bassSlider.setPreferredSize(new Dimension(20,30));
				bassSlider.addChangeListener(e -> bassSetter.set(bassSlider.getValue()/2.0, bassSlider.getValueIsAdjusting()));
				bassSetter = new ValueSetter((value, isAdjusting) -> {
					if (device==null) return;
					device.mainZone.setBass((float) value);
					if (!isAdjusting) {
						device.update(EnumSet.of(UpdateWish.BasicStatus));
						SwingUtilities.invokeLater(this::updatePanel);
					}
				});
				
				// PUT[P8]:    Main_Zone,Sound_Video,Tone,Treble                  =   Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
				// GET[G1]:    Main_Zone,Basic_Status -> Sound_Video,Tone,Treble  ->  Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
				trebleSlider = new JSlider(JSlider.HORIZONTAL, -12, 12, 0);
				trebleSlider.setMajorTickSpacing(4);
				trebleSlider.setMinorTickSpacing(1);
				trebleSlider.setPaintTicks(true);
				trebleSlider.setPreferredSize(new Dimension(20,30));
				trebleSlider.addChangeListener(e -> trebleSetter.set(trebleSlider.getValue()/2.0, trebleSlider.getValueIsAdjusting()));
				trebleSetter = new ValueSetter((value, isAdjusting) -> {
					if (device==null) return;
					device.mainZone.setTreble((float) value);
					if (!isAdjusting) {
						device.update(EnumSet.of(UpdateWish.BasicStatus));
						SwingUtilities.invokeLater(this::updatePanel);
					}
				});
				
				// [Adaptive_DRC_Auto]   PUT[P12]     Main_Zone,Sound_Video,Adaptive_DRC = Auto
				// [Adaptive_DRC_Off]    PUT[P12]     Main_Zone,Sound_Video,Adaptive_DRC = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Sound_Video,Adaptive_DRC -> "Auto" | "Off"
				adaptiveDRCButton = new ValueButton<Value.AutoOff>(ValueButton.IconSourceAutoOff,v->{
					Value.AutoOff newValue = v==null?Value.AutoOff.Auto:getNext(v, Value.AutoOff.values());
					device.mainZone.setAdaptiveDRC(newValue);
					updateBasicStatusAndPanel();
				});
				adaptiveDRCButton.setMargin(defaultButtonInsets);
				adaptiveDRCButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// [CINEMA_DSP_3D_Auto]  PUT[P13]     Main_Zone,Surround,_3D_Cinema_DSP = Auto
				// [CINEMA_DSP_3D_Off]   PUT[P13]     Main_Zone,Surround,_3D_Cinema_DSP = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,_3D_Cinema_DSP -> "Auto" | "Off"
				cinemaDSPButton = new ValueButton<Value.AutoOff>(ValueButton.IconSourceAutoOff,v->{
					Value.AutoOff newValue = v==null?Value.AutoOff.Auto:getNext(v, Value.AutoOff.values());
					device.mainZone.set3DCinemaDSP(newValue);
					updateBasicStatusAndPanel();
				});
				cinemaDSPButton.setMargin(defaultButtonInsets);
				cinemaDSPButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// [Direct_On]           PUT[P17]     Main_Zone,Sound_Video,Direct,Mode = On
				// [Direct_Off]          PUT[P17]     Main_Zone,Sound_Video,Direct,Mode = Off
				// GET[G1]:    Main_Zone,Basic_Status   ->   Sound_Video,Direct,Mode -> "On" | "Off"
				directModeButton = new ValueButton<Value.OnOff>(ValueButton.IconSourceOnOff,v->{
					Value.OnOff newValue = v==null?Value.OnOff.On:getNext(v, Value.OnOff.values());
					device.mainZone.setDirectMode(newValue);
					updateBasicStatusAndPanel();
				});
				directModeButton.setMargin(defaultButtonInsets);
				directModeButton.setHorizontalAlignment(SwingConstants.LEFT);
				
				// GET[G1]:    Main_Zone,Basic_Status   ->   Sound_Video,HDMI,Standby_Through_Info -> "On" | "Off"
				hdmiStandbyThroughField = new JTextField("");
				hdmiStandbyThroughField.setEditable(false);
				
				GridBagPanel mainZoneSetupPanel = new GridBagPanel();
				mainZoneSetupPanel.setBorder(BorderFactory.createTitledBorder("MainZone Setup"));
				int row = 0;
				addField(mainZoneSetupPanel,row++,"MainZone Power"      ,powerButton            );
				addField(mainZoneSetupPanel,row++,"Sleep"               ,sleepSelect            );
				addField(mainZoneSetupPanel,row++,"Bass"                ,bassSlider             );
				addField(mainZoneSetupPanel,row++,"Treble"              ,trebleSlider           );
				addField(mainZoneSetupPanel,row++,"Surround Program"    ,surroundProgramSelect  );
				addField(mainZoneSetupPanel,row++,"Straight"            ,surroundStraightButton );
				addField(mainZoneSetupPanel,row++,"Surround Enhancer"   ,surroundEnhancerButton );
				addField(mainZoneSetupPanel,row++,"Adaptive DRC"        ,adaptiveDRCButton      );
				addField(mainZoneSetupPanel,row++,"3D Cinema DSP"       ,cinemaDSPButton        );
				addField(mainZoneSetupPanel,row++,"Direct Mode"         ,directModeButton       );
				addField(mainZoneSetupPanel,row++,"HDMI Standby Through",hdmiStandbyThroughField);
				
				return mainZoneSetupPanel;
			}

			private void updateBasicStatusAndPanel() {
				device.update(EnumSet.of(UpdateWish.BasicStatus));
				updatePanel();
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
				hdmiStandbyThroughField.setText(device.mainZone.basicStatus.hdmiStandbyThrough==null?"???":device.mainZone.basicStatus.hdmiStandbyThrough.getLabel());
				if (device.mainZone.basicStatus.bass==null) {
					bassSlider.setValue(0);
					bassSlider.setEnabled(false);
				} else {
					bassSlider  .setValue(Math.round(device.mainZone.basicStatus.bass.getValue()*2));
					bassSlider.setEnabled(true);
				}
				if (device.mainZone.basicStatus.treble==null) {
					trebleSlider.setValue(0);
					trebleSlider.setEnabled(false);
				} else {
					trebleSlider  .setValue(Math.round(device.mainZone.basicStatus.treble.getValue()*2));
					trebleSlider.setEnabled(true);
				}
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

		private void addField(GridBagPanel panel, int rowIndex, String label, int gridWith, JComponent comp) {
			JLabel jLabel = new JLabel(label+" : ",JLabel.RIGHT);
			this.comps.add(jLabel);
			this.comps.add(comp);
			panel.add(jLabel, 0,rowIndex, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			panel.add(comp  , 1,rowIndex, 0,0, gridWith,1, GridBagConstraints.HORIZONTAL);
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

	
	static class AppSettings extends Settings.DefaultAppSettings<AppSettings.ValueGroup, AppSettings.ValueKey> {
		enum ValueKey {
		}
	
		enum ValueGroup implements Settings.GroupKeys<ValueKey> {
			;
			ValueKey[] keys;
			ValueGroup(ValueKey...keys) { this.keys = keys;}
			@Override public ValueKey[] getKeys() { return keys; }
		}
		
		public AppSettings() { super(YamahaControl.class, ValueKey.values()); }
	}
}
