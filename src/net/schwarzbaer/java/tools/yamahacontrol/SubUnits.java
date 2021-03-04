package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;

import net.schwarzbaer.gui.ContextMenu;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.tools.yamahacontrol.Device.PlayInfo;
import net.schwarzbaer.java.tools.yamahacontrol.Device.UpdateWish;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value;

final class SubUnits {

	private static abstract class AbstractSubUnit extends JPanel implements YamahaControl.GuiRegion {
		private static final long serialVersionUID = 7368584160348326790L;
		
		protected boolean disableSubUnitIfNotReady = true;
		
		protected final Frame window;
		protected Device device;
		protected Boolean isReady;
		private JLabel readyStateLabel;
		private TabHeaderComp tabHeaderComp;
		
		private final String inputID;
		private final String tabTitle;
	
		private JButton activateBtn;
		private Device.Inputs.DeviceSceneInput activateInput;

		private final UpdateWish readyStateUpdateWish;

	
		protected AbstractSubUnit(Frame window, String inputID, String tabTitle, UpdateWish readyStateUpdateWish) {
			super(new BorderLayout(3,3));
			this.window = window;
			this.inputID = inputID;
			this.tabTitle = tabTitle;
			this.readyStateUpdateWish = readyStateUpdateWish;
			this.activateInput = null;
		}
	
		private void createPanel() {
			tabHeaderComp = new TabHeaderComp(tabTitle);
			
			JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,3,3));
			northPanel.add(activateBtn = YamahaControl.createButton("Activate", false, e->{ if (activateInput!=null) device.inputs.setInput(activateInput); }));
			northPanel.add(readyStateLabel = new JLabel("???",YamahaControl.smallImages.get(YamahaControl.SmallImages.IconUnknown),JLabel.LEFT));
			
			add(northPanel,BorderLayout.NORTH);
			add(createContentPanel(),BorderLayout.CENTER);
			setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		}
	
		public void addTo(Vector<YamahaControl.GuiRegion> guiRegions, JTabbedPane subUnitPanel) {
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
			
			boolean wasReady = isReady();
			isReady = getReadyState();
			if (wasReady && !isReady()) window.setTitle("YamahaControl");
			
			if (disableSubUnitIfNotReady) setEnabledGUI(isReady());
			tabHeaderComp.setReady(isReady());
			readyStateLabel.setText(tabTitle+" is "+(isReady==null?"not answering":(isReady?"Ready":"Not Ready")));
			readyStateLabel.setIcon(YamahaControl.smallImages.get(isReady==null?YamahaControl.SmallImages.IconUnknown:(isReady?YamahaControl.SmallImages.IconOn:YamahaControl.SmallImages.IconOff)));
		}
		
		public boolean isReady() { return isReady!=null && isReady.booleanValue(); }
	
		@Override
		public EnumSet<UpdateWish> getUpdateWishes(YamahaControl.UpdateReason reason) {
			if (readyStateUpdateWish!=null) return EnumSet.of(readyStateUpdateWish);
			return EnumSet.noneOf(UpdateWish.class);
		}
	
		protected JPanel createContentPanel() { return new JPanel(); }
	
		protected abstract Boolean getReadyState();
//		protected Boolean askReadyState(Device.KnownCommand.Config cmd) {
//			if (device==null) return null;
//			// GET:    #######,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
//			Value.ReadyOrNot readyState = device.askValue(cmd, Value.ReadyOrNot.values(), "Feature_Availability");
//			return readyState==null?null:(readyState==Value.ReadyOrNot.Ready);
//		}
	
		@Override public void setEnabledGUI(boolean enabled) { /*setEnabled(enabled);*/ }

		public void setWindowTitle(String info) {
			if (isReady()) {
				String title = "YamahaControl";
				if (info!=null && !info.isEmpty()) title += " - "+info;
				if (!title.equals(window.getTitle())) {
					window.setTitle(title);
					System.out.printf("setWindowTitle[%s] %s%n", tabTitle, info);
				}
			}
		}
		
		private static class TabHeaderComp extends JLabel {
			
			private static final long serialVersionUID = 694848205055774285L;
			private static final Color READY_TEXT_COLOR = new Color(0x00d000);

			private final Font defaultFont;
			private final Font readyFont;
			private Color defaultTextColor;
			
			TabHeaderComp(String tabTitle) {
				super("  "+tabTitle+"  ");
				defaultFont = getFont();
				defaultTextColor = getForeground();
				if (!defaultFont.isBold())
					readyFont = defaultFont.deriveFont(Font.BOLD);
				else
					readyFont = defaultFont.deriveFont(Font.ITALIC);
			}

			public void setReady(boolean isReady) {
//				setOpaque(isReady);
//				setBackground(isReady?Color.GREEN:null);
				setFont(isReady ? readyFont : defaultFont);
				setForeground(isReady ? READY_TEXT_COLOR : defaultTextColor);
			}
		}
	}

	static class SubUnitNetRadio extends AbstractSubUnit_ListPlay implements PlayButtonModule.Caller, ButtonModule.ExtraButtons {
		private static final long serialVersionUID = -8583320100311806933L;
		
		private JFileChooser fileChooser;
	
		public SubUnitNetRadio(Frame window) {
			super(window, "NET RADIO", "Net Radio", UpdateWish.NetRadioConfig, UpdateWish.NetRadioListInfo, UpdateWish.NetRadioPlayInfo);
			modules.add(new PlayButtonModule(this, this));
			withExtraCharsetConversion = true;
			fileChooser = new JFileChooser("./");
			fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			fileChooser.setMultiSelectionEnabled(false);
		}
	
		@Override
		protected Boolean getReadyState() {
			if (device==null) return null;
			return device.netRadio.deviceStatus==null?null:(device.netRadio.deviceStatus==Value.ReadyOrNot.Ready);
		}
	
		@Override public    Device.PlayInfo_NetRadio getPlayInfo()              { return device==null?null:device.netRadio.playInfo; }
		@Override protected Device.ListInfo          getListInfo(Device device) { return device==null?null:device.netRadio.listInfo; }
		
		@Override public void updateExtraButtons() {}
		@Override public void addExtraButtons(Vector<AbstractButton> buttons) {
			buttons.add(YamahaControl.createButton("Add Song to PreferredSongs",true,e->addSongToPreferredSongs()));
			buttons.add(YamahaControl.createButton("Show List",true,e->showPreferredSongs()));
			buttons.add(YamahaControl.createButton("Save AlbumCover",true,e->saveAlbumCover(true)));
		}
		
		private void saveAlbumCover(boolean verbose) {
			if (device == null) return;
			if (device.netRadio.playInfo.albumCoverURL == null) return;
			if (verbose) System.out.printf("%nRead AlbumCover ...%n");
			byte[] content = Ctrl.http.getBinaryContentFromURL("http://"+device.address+device.netRadio.playInfo.albumCoverURL, verbose );
			
			int pos = device.netRadio.playInfo.albumCoverURL.lastIndexOf('/');
			String filename;
			if (pos<0) filename = device.netRadio.playInfo.albumCoverURL;
			else filename = device.netRadio.playInfo.albumCoverURL.substring(pos+1);
			if (!filename.isEmpty() && (device.netRadio.playInfo.albumCoverID!=null || device.netRadio.playInfo.currentStation!=null)) {
				pos = filename.lastIndexOf('.');
				String extra = "";
				if (device.netRadio.playInfo.albumCoverID  !=null) extra += " - "+device.netRadio.playInfo.albumCoverID  .toString();
				if (device.netRadio.playInfo.currentStation!=null) extra += " - "+device.netRadio.playInfo.currentStation.toString();
				if (pos<0) filename += extra;
				else filename = filename.substring(0,pos)+extra+filename.substring(pos);
				
				File folder = fileChooser.getCurrentDirectory();
				fileChooser.setSelectedFile(new File(folder, filename));
			}
			
			if (fileChooser.showSaveDialog(window)!=JFileChooser.APPROVE_OPTION) return;
			File file = fileChooser.getSelectedFile();
			
			if (verbose) System.out.printf("Write data to file \"%s\"%n",file.getAbsolutePath());
			try { Files.write(file.toPath(), content, StandardOpenOption.CREATE_NEW); }
			catch (FileAlreadyExistsException e) { if (verbose) System.err.printf("Can't write file. File \"%s\" already exists.%n",file.getAbsolutePath()); }
			catch (IOException e) { e.printStackTrace(); }
		}

		private void showPreferredSongs() {
			new SubUnits.PreferredSongsViewDialog( window ).showDlg();
		}

		private void addSongToPreferredSongs() {
			if (device!=null) {
				if (device.netRadio.playInfo.currentSong!=null) {
					YamahaControl.preferredSongs.add(device.netRadio.playInfo.currentSong);
					YamahaControl.preferredSongs.writeToFile();
				}
			}
		}
	}

	static class SubUnitUSB extends AbstractSubUnit_PlayInfoExt<Value.OnOff> {
		private static final long serialVersionUID = 2909543552931897755L;
	
		public SubUnitUSB(Frame window) {
			super(window, "USB", "USB Device", UpdateWish.USBConfig, UpdateWish.USBListInfo, UpdateWish.USBPlayInfo, Value.OnOff.values());
		}
		
		@Override
		protected Boolean getReadyState() {
			if (device==null) return null;
			return device.usb.deviceStatus==null?null:(device.usb.deviceStatus==Value.ReadyOrNot.Ready);
		}
		
		@Override public    Device.PlayInfoExt<Value.OnOff> getPlayInfo()              { return device==null?null:device.usb.playInfo; }
		@Override protected Device.ListInfo                 getListInfo(Device device) { return device==null?null:device.usb.listInfo; }
	}

	static class SubUnitDLNA extends AbstractSubUnit_PlayInfoExt<Value.OnOff> {
		private static final long serialVersionUID = -4585259335586086032L;
	
		public SubUnitDLNA(Frame window) {
			super(window, "SERVER", "DLNA Server", UpdateWish.DLNAConfig, UpdateWish.DLNAListInfo, UpdateWish.DLNAPlayInfo, Value.OnOff.values());
		}
		
		@Override
		protected Boolean getReadyState() {
			if (device==null) return null;
			return device.dlna.deviceStatus==null?null:(device.dlna.deviceStatus==Value.ReadyOrNot.Ready);
		}
		
		@Override public    Device.PlayInfoExt<Value.OnOff> getPlayInfo()              { return device==null?null:device.dlna.playInfo; }
		@Override protected Device.ListInfo                 getListInfo(Device device) { return device==null?null:device.dlna.listInfo; }
	}

	static class SubUnitIPodUSB extends AbstractSubUnit_PlayInfoExt<Value.ShuffleIPod> implements ButtonModule.ExtraButtons {
		private static final long serialVersionUID = -4180795479139795928L;
		private JButton modeBtn;
	
		public SubUnitIPodUSB(Frame window) {
			super(window, "iPod (USB)", "iPod (USB) [untested]", UpdateWish.IPodUSBConfig, UpdateWish.IPodUSBListInfo, UpdateWish.IPodUSBPlayInfo, Value.ShuffleIPod.values());
			setExtraButtons(this);
		}
	
		@Override
		protected Boolean getReadyState() {
			if (device==null) return null;
			return device.iPodUSB.deviceStatus==null?null:(device.iPodUSB.deviceStatus==Value.ReadyOrNot.Ready);
		}
		
		@Override
		public EnumSet<UpdateWish> getUpdateWishes(YamahaControl.UpdateReason reason) {
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
			buttons.add(modeBtn = YamahaControl.createButton("iPod Mode",true));
			modeBtn.addActionListener(e->{
				if (device.iPodUSB.mode==null) return;
				device.iPodUSB.sendSetMode(YamahaControl.getNext(device.iPodUSB.mode,Value.IPodMode.values()));
				updateDeviceNGui();
			});
		}
	}

	static class SubUnitTuner extends AbstractSubUnit_Play {
		private static final long serialVersionUID = -8583320100311806933L;
		
		private YamahaControl.ToggleButtonGroup<Value.AmFm> bgBand;
		private YamahaControl.ToggleButtonGroup<Value.ScanFreq> bgScanAM;
		private YamahaControl.ToggleButtonGroup<Value.ScanFreq> bgScanFM;
		private YamahaControl.ToggleButtonGroup<Value.ScanTP> bgScanFMTP;
		private RotaryCtrl2 tuneCtrl;
		private YamahaControl.ButtonFactory<Value.UpDown> presetButtons;
		private JComboBox<Device.PlayInfo_Tuner.Preset> presetCmbBx;
		private boolean presetCmbBx_ignoreSelectionEvent;
		private JLabel presetLabel;
		private JButton updatePresetsButton;
		private JTextField freqAmTxtFld;
		private JTextField freqFmTxtFld;
		
		private boolean isEnabled;
		private YamahaControl.ValueSetter freqAmSetter;
		private YamahaControl.ValueSetter freqFmSetter;
		private Value.AmFm selectedBand;
	
	
	
		public SubUnitTuner(Frame window) {
			super(window, "TUNER","Tuner", UpdateWish.TunerConfig, UpdateWish.TunerPlayInfo);
			isEnabled = true;
			selectedBand = null;
			presetCmbBx_ignoreSelectionEvent = false;
		}
	
		@Override
		public EnumSet<UpdateWish> getUpdateWishes(YamahaControl.UpdateReason reason) {
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
		protected Device.PlayInfo getPlayInfo() { return device==null?null:device.tuner.playInfo; }
	
		private void updateDeviceNGui() {
			device.update(getUpdateWishes(YamahaControl.UpdateReason.Frequently));
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
			YamahaControl.GridBagPanel buttonPanel = new YamahaControl.GridBagPanel();
			buttonPanel.setInsets(new Insets(0,3,0,3));
			
			bgBand = new YamahaControl.ToggleButtonGroup<>(Value.AmFm.class,e->{
				if (device==null) return;
				device.tuner.setBand(e);
				updateDeviceNGui();
			});
			buttonPanel.add(bgBand.createLabel("Band: "), 0,0, 0,0, 1,1, GridBagConstraints.BOTH);
			buttonPanel.add(bgBand.createButton("AM",Value.AmFm.AM), 1,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgBand.createButton("FM",Value.AmFm.FM), 2,0, 0,0, 2,1, GridBagConstraints.HORIZONTAL);
			
			freqAmTxtFld = new JTextField();
			freqFmTxtFld = new JTextField();
			freqAmTxtFld.setEditable(false);
			freqFmTxtFld.setEditable(false);
			
			bgScanAM = new YamahaControl.ToggleButtonGroup<>(Value.ScanFreq.class,e->{
				if (device==null) return;
				device.tuner.setScanAM(e);
				updateDeviceNGui();
			});
			buttonPanel.add(bgScanAM.createLabel("Frequency",JLabel.CENTER), 1,1, 0,0, 1,1, GridBagConstraints.BOTH);
			buttonPanel.add(freqAmTxtFld, 1,2, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanAM.createButton("Scan Up"  ,Value.ScanFreq.AutoUp  ), 1,3, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanAM.createButton("Scan Down",Value.ScanFreq.AutoDown), 1,4, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanAM.createButton("Cancel"   ,Value.ScanFreq.Cancel  ), 1,5, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			bgScanFM = new YamahaControl.ToggleButtonGroup<>(Value.ScanFreq.class,e->{
				if (device==null) return;
				device.tuner.setScanFM(e);
				updateDeviceNGui();
			});
			buttonPanel.add(bgScanFM.createLabel("Frequency",JLabel.CENTER), 2,1, 0,0, 1,1, GridBagConstraints.BOTH);
			buttonPanel.add(freqFmTxtFld, 2,2, 0,0, 2,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanFM.createButton("Scan Up"  ,Value.ScanFreq.AutoUp  ), 2,3, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanFM.createButton("Scan Down",Value.ScanFreq.AutoDown), 2,4, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanFM.createButton("Cancel"   ,Value.ScanFreq.Cancel  ), 2,5, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			bgScanFMTP = new YamahaControl.ToggleButtonGroup<>(Value.ScanTP.class,e->{
				if (device==null) return;
				device.tuner.setScanFMTP(e);
				updateDeviceNGui();
			});
			buttonPanel.add(bgScanFMTP.createLabel("Traffic",JLabel.CENTER), 3,1, 0,0, 1,1, GridBagConstraints.BOTH);
			buttonPanel.add(bgScanFMTP.createButton("Scan Up"  ,Value.ScanTP.TPUp  ), 3,3, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanFMTP.createButton("Scan Down",Value.ScanTP.TPDown), 3,4, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			buttonPanel.add(bgScanFMTP.createButton("Cancel"   ,Value.ScanTP.Cancel), 3,5, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			presetButtons = new YamahaControl.ButtonFactory<Value.UpDown>(Value.UpDown.class, e->{
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
			updatePresetsButton = YamahaControl.createButton("Update", true, e->{
				if (device==null) return;
				device.update(EnumSet.of(UpdateWish.TunerPresets));
				presetCmbBx_ignoreSelectionEvent = true;
				presetCmbBx.setModel(new DefaultComboBoxModel<>(device.tuner.playInfo.presets));
				presetCmbBx.setSelectedItem(device.tuner.playInfo.getCurrentPreset());
				presetCmbBx_ignoreSelectionEvent = false;
			});
			
			YamahaControl.GridBagPanel presetPanel = new YamahaControl.GridBagPanel();
			presetPanel.add(presetButtons.createButton("<<",Value.UpDown.Down), 0,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			presetPanel.add(presetCmbBx, 1,0, 1,0, 1,1, GridBagConstraints.BOTH);
			presetPanel.add(presetButtons.createButton(">>",Value.UpDown.Up  ), 2,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			presetPanel.add(updatePresetsButton, 3,0, 0,0, 1,1, GridBagConstraints.HORIZONTAL);
			
			buttonPanel.add(new JLabel(" "), 0,6, 0,0, 4,1, GridBagConstraints.BOTH);
			buttonPanel.add(presetLabel = new JLabel("Preset:"), 0,7, 0,0, 1,1, GridBagConstraints.BOTH);
			buttonPanel.add(presetPanel, 1,7, 0,0, 3,1, GridBagConstraints.BOTH);
			
			tuneCtrl = new RotaryCtrl2("Tuner",150,true, -1.0, +1.0, 1.0, 1.0, 1, -90, (value, isAdjusting) -> {
				if (device==null || device.tuner.playInfo.tuningBand==null) return;
				switch(device.tuner.playInfo.tuningBand) {
				case AM: freqAmSetter.set(value, isAdjusting); break;
				case FM: freqFmSetter.set(value, isAdjusting); break;
				}
			});
			addComp(tuneCtrl);
			freqAmSetter = new YamahaControl.ValueSetter(10,(value, isAdjusting) -> {
				device.tuner.setFreqAM((float)value);
				if (isAdjusting)
					SwingUtilities.invokeLater(this::updateFreqAmTxtFld);
				else {
					device.update(getUpdateWishes(YamahaControl.UpdateReason.Frequently));
					SwingUtilities.invokeLater(this::updateGui);
				}
			});
			freqFmSetter = new YamahaControl.ValueSetter(10,(value, isAdjusting) -> {
				device.tuner.setFreqFM((float)value);
				if (isAdjusting)
					SwingUtilities.invokeLater(this::updateFreqFmTxtFld);
				else {
					device.update(getUpdateWishes(YamahaControl.UpdateReason.Frequently));
					SwingUtilities.invokeLater(this::updateGui);
				}
			});
			
			JPanel panel = new JPanel(new BorderLayout(3,3));
			panel.setBorder(BorderFactory.createTitledBorder("Tuner"));
			panel.add(tuneCtrl,BorderLayout.WEST);
			panel.add(buttonPanel,BorderLayout.CENTER);
			
			return panel;
		}
	}

	static class SubUnitAirPlay extends AbstractSubUnit_AirPlaySpotify {
		private static final long serialVersionUID = 8375036678437177239L;
	
		// [Source_Device | SD_AirPlay | AirPlay]   
		public SubUnitAirPlay(Frame window) {
			super(window, "AirPlay", "AirPlay [untested]", UpdateWish.AirPlayConfig, UpdateWish.AirPlayPlayInfo);
		}
		
		@Override public Device.PlayInfo_AirPlaySpotify getPlayInfo() { return device==null?null:device.airPlay.playInfo; }
	
		@Override
		protected Boolean getReadyState() {
			if (device==null) return null;
			if (device.airPlay==null) return null;
			if (device.airPlay.config==null) return null;
			Value.ReadyOrNot readyState = device.airPlay.config.deviceStatus;
			return readyState==null?null:(readyState==Value.ReadyOrNot.Ready);
		}
	}

	static class SubUnitSpotify extends AbstractSubUnit_AirPlaySpotify {
		private static final long serialVersionUID = -869960569061323838L;
	
		public SubUnitSpotify(Frame window) {
			super(window, "Spotify", "Spotify [untested]", UpdateWish.SpotifyConfig, UpdateWish.SpotifyPlayInfo);
		}
		
		@Override public Device.PlayInfo_AirPlaySpotify getPlayInfo() { return device==null?null:device.spotify.playInfo; }
	
		@Override
		protected Boolean getReadyState() {
			if (device==null) return null;
			return device.spotify.deviceStatus==null?null:(device.spotify.deviceStatus==Value.ReadyOrNot.Ready);
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
		
		public YamahaControl.GridBagPanel createButtonPanel(Vector<JComponent> comps) {
			YamahaControl.GridBagPanel  buttonPanel = new YamahaControl.GridBagPanel();
			
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
			return YamahaControl.createToggleButton(playState.getLabel(), true, playButtons, e->{
				PlayInfo_PlayStop playInfo = caller.getPlayInfo();
				if (playInfo!=null) {
					playInfo.sendPlayback(playState);
					caller.updateDeviceNGui();
				}
			});
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
			return YamahaControl.createToggleButton(playState.getLabel(), true, playButtons, e->{
				PlayInfo_PlayPauseStopSkip playInfo = caller.getPlayInfo();
				if (playInfo!=null) {
					playInfo.sendPlayback(playState);
					caller.updateDeviceNGui();
				}
			});
		}
		
		private JButton createButton(String title, Value.SkipFwdRev skip) {
			return YamahaControl.createButton(title, true, e->{
				PlayInfo_PlayPauseStopSkip playInfo = caller.getPlayInfo();
				if (playInfo!=null) {
					playInfo.sendPlayback(skip);
					caller.updateDeviceNGui();
				}
			});
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
		
		protected AbstractSubUnit_Play(Frame window, String inputID, String tabTitle, UpdateWish readyStateUpdateWish, UpdateWish playInfoUpdateWish) {
			super(window, inputID, tabTitle, readyStateUpdateWish);
			this.playInfoUpdateWish = playInfoUpdateWish;
			comps = new Vector<>();
			modules = new Vector<>();
			withExtraCharsetConversion = false;
		}
	
		@Override
		public EnumSet<UpdateWish> getUpdateWishes(YamahaControl.UpdateReason reason) {
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
			
			YamahaControl.GridBagPanel buttonsPanel = new YamahaControl.GridBagPanel();
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
			PlayInfo playInfo = updatePlayInfoOutput();
			if (playInfo!=null) setWindowTitle(playInfo.getWindowTitleInfo(withExtraCharsetConversion));
			modules.forEach(m->m.updateButtons());
		}
	
		private PlayInfo updatePlayInfoOutput() {
			float hPos = YamahaControl.getScrollPos(playinfoScrollPane.getHorizontalScrollBar());
			float vPos = YamahaControl.getScrollPos(playinfoScrollPane.getVerticalScrollBar());
			PlayInfo playInfo = null;
			if (device!=null) playinfoOutput.setText((playInfo = getPlayInfo()).toString(withExtraCharsetConversion));
			else              playinfoOutput.setText("<no data>");
			YamahaControl.setScrollPos(playinfoScrollPane.getHorizontalScrollBar(),hPos);
			YamahaControl.setScrollPos(playinfoScrollPane.getVerticalScrollBar(),vPos);
			return playInfo;
		}
	}

	private static abstract class AbstractSubUnit_ListPlay extends AbstractSubUnit_Play implements LineList.LineListUser, LineList2.LineList2User  {
			private static final long serialVersionUID = 3773609643258015474L;
			
			private   LineList lineList1;
			private   LineList2 lineList2;
			
			protected UpdateWish listInfoUpdateWish;
	
		
			public AbstractSubUnit_ListPlay(Frame window, String inputID, String tabTitle, UpdateWish readyStateUpdateWish, UpdateWish listInfoUpdateWish, UpdateWish playInfoUpdateWish) {
				super(window, inputID, tabTitle, readyStateUpdateWish, playInfoUpdateWish);
				this.listInfoUpdateWish = listInfoUpdateWish;
			}
	
			protected abstract Device.ListInfo getListInfo(Device device);
	
			@Override
			public EnumSet<UpdateWish> getUpdateWishes(YamahaControl.UpdateReason reason) {
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
				device.update(getUpdateWishes(YamahaControl.UpdateReason.Frequently));
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
		
		public AbstractSubUnit_PlayInfoExt(Frame window, String inputID, String tabTitle, UpdateWish readyStateUpdateWish, UpdateWish listInfoUpdateWish, UpdateWish playInfoUpdateWish, Shuffle[] shuffleValues) {
			super(window, inputID, tabTitle, readyStateUpdateWish, listInfoUpdateWish, playInfoUpdateWish);
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
	
		public AbstractSubUnit_AirPlaySpotify(Frame window, String inputID, String tabTitle, UpdateWish readyStateUpdateWish, UpdateWish playInfoUpdateWish) {
			super(window, inputID, tabTitle, readyStateUpdateWish, playInfoUpdateWish);
			modules.add( lastModule = new PlayButtonModuleExt(this, null));
		}
		
		protected void setExtraButtons(ButtonModule.ExtraButtons extraButtons) {
			lastModule.extraButtons = extraButtons;
		}
		
		@Override public void updateDeviceNGui() {
			device.update(getUpdateWishes(YamahaControl.UpdateReason.Frequently));
			updatePlayInfo();
		}
	
		@Override protected JPanel createUpperPanel() { return null; }
	
		@Override public abstract Device.PlayInfo_AirPlaySpotify getPlayInfo();
	}

	private static class DateInputDialog extends JDialog {
		private static final long serialVersionUID = -7064575341358904239L;
		private final Calendar cal;
		private Long result;

		DateInputDialog(Window owner, String title, Long oldValue) {
			super(owner, title, ModalityType.APPLICATION_MODAL);
			result = null;
			cal = Calendar.getInstance(TimeZone.getTimeZone("CET"), Locale.GERMANY);
			if (oldValue!=null) cal.setTimeInMillis(oldValue);
			
			JPanel contentPane = new JPanel(new GridBagLayout());
			contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			
			contentPane.add(createCalFieldComboBox(Calendar.DAY_OF_MONTH, createValues(1,31)     , 0),c);
			contentPane.add(createCalFieldComboBox(Calendar.MONTH       , createValues(1,12)     ,-1),c);
			contentPane.add(createCalFieldComboBox(Calendar.YEAR        , createValues(1970,2030), 0),c);
			contentPane.add(createCalFieldComboBox(Calendar.HOUR_OF_DAY , createValues(0,23)     , 0),c);
			contentPane.add(createCalFieldComboBox(Calendar.MINUTE      , createValues(0,59)     , 0),c);
			contentPane.add(createCalFieldComboBox(Calendar.SECOND      , createValues(0,59)     , 0),c);
			contentPane.add(YamahaControl.createButton("Ok",true,e->{ result = cal.getTimeInMillis(); setVisible(false); }),c);
			
			setContentPane(contentPane);
			pack();
			setLocationRelativeTo(owner);
		}
		
		private JComboBox<Integer> createCalFieldComboBox(int fieldID, Integer[] values, int valueOffset) {
			return YamahaControl.createComboBox(values, cal.get(fieldID)-valueOffset, i->cal.set(fieldID, i+valueOffset));
		}
		
		private Integer[] createValues(int first, int last) {
			if (last<first) throw new IllegalArgumentException();
			Integer[] values = new Integer[last-first+1];
			for (int i=0; i<values.length; i++) values[i] = first+i;
			return values;
		}

		static Long showDialog(Window owner, String title, Long oldValue) {
			DateInputDialog dlg = new DateInputDialog(owner, title, oldValue);
			dlg.setVisible(true);
			return dlg.result;
		}
		
	}

	private static class PreferredSongsViewDialog extends JDialog {
		private static final long serialVersionUID = 7939477522501437501L;
		private String clickedSong;
	
		public PreferredSongsViewDialog(Window owner) {
			super(owner, String.format("Preferred Songs (%s)", YamahaControl.preferredSongs.getFile().getPath()), ModalityType.APPLICATION_MODAL);
			
			JFileChooser fileChooser = new JFileChooser("./");
			fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			fileChooser.setMultiSelectionEnabled(false);
			
			SongTableModel tableModel = new SongTableModel();
			JTable table = new JTable(tableModel);
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			table.setDefaultRenderer(Long.class, new Renderer());
			
			tableModel.setColumnWidths(table);
			Dimension size = table.getPreferredSize();
			size.height = Math.min(size.height, 600);
			table.setPreferredScrollableViewportSize(size);
			
			JMenuItem miSetDate, miClearDate;
			ContextMenu tableContextMenu = new ContextMenu();
			tableContextMenu.addTo(table);
			tableContextMenu.add(miSetDate = YamahaControl.createMenuItem("Set Date", e->{
				if (clickedSong==null) return;
				Long oldValue = YamahaControl.preferredSongs.getTimeStamp(clickedSong);
				Long newValue = DateInputDialog.showDialog(this, oldValue==null ? "Set Date" : "Change Date", oldValue);
				if (newValue==null) return;
				boolean successful = YamahaControl.preferredSongs.setTimeStamp(clickedSong,newValue);
				if (successful) {
					YamahaControl.preferredSongs.writeToFile();
					tableModel.fireTableColumnUpdate(ColumnID.TimeStamp);
				}
			}));
			tableContextMenu.add(miClearDate = YamahaControl.createMenuItem("Clear Date", e->{
				if (clickedSong==null) return;
				boolean successful = YamahaControl.preferredSongs.setTimeStamp(clickedSong,null);
				if (successful) {
					YamahaControl.preferredSongs.writeToFile();
					tableModel.fireTableColumnUpdate(ColumnID.TimeStamp);
				}
			}));
			tableContextMenu.add(YamahaControl.createMenuItem("Set Dates of Unset Songs", e->{
				if (fileChooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION) return;
				File file = fileChooser.getSelectedFile();
				boolean successful = YamahaControl.preferredSongs.setTimeStampsOfUnsetSongs(file,oldValue->DateInputDialog.showDialog(this, "Select Date", oldValue));
				if (successful) {
					YamahaControl.preferredSongs.writeToFile();
					tableModel.fireTableColumnUpdate(ColumnID.TimeStamp);
				}
			}));
			
			clickedSong=null;
			tableContextMenu.addContextMenuInvokeListener((comp, x, y) -> {
				int rowV = table.rowAtPoint(new Point(x, y));
				int rowM = rowV<0 ? -1 : table.convertRowIndexToModel(rowV);
				clickedSong = rowM<0 || rowM>=tableModel.songs.size() ? null : tableModel.songs.get(rowM);
				
				miSetDate  .setEnabled(clickedSong!=null);
				miClearDate.setEnabled(clickedSong!=null);
				if (clickedSong!=null) {
					Long timeStamp = YamahaControl.preferredSongs.getTimeStamp(clickedSong);
					miSetDate  .setText(String.format("%s Date for \"%s\"", timeStamp==null ? "Set" : "Change", clickedSong));
					miClearDate.setText(String.format("%s Date for \"%s\"", "Clear", clickedSong));
				} else {
					miSetDate  .setText("Set Date");
					miClearDate.setText("Clear Date");
				}
			});
			
			JPanel contentPane = new JPanel(new BorderLayout(3,3));
			contentPane.add(new JScrollPane(table),BorderLayout.CENTER);
			
			setContentPane(contentPane);
			pack();
			setLocationRelativeTo(owner);
		}
		
		public void showDlg() {
			setVisible(true);
		}
		
		private static class Renderer extends DefaultTableCellRenderer {
			private static final long serialVersionUID = -8040402180068866311L;
			private static final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("CET"), Locale.GERMANY);

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				
				if (value instanceof Long) {
					long time = (Long) value;
					cal.setTimeInMillis(time);
					//setValue(String.format(Locale.ENGLISH, "%1$td.%1$tm.%1$tY %1$tT", cal));
					setValue(String.format(Locale.ENGLISH, "%1$tA, %1$te. %1$tb %1$tY, %1$tT [%1$tZ:%1$tz]", cal));
				}
				return component;
			}
			
		}
		
		private enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			TimeStamp("Added",   Long.class, 20, -1, 250, 250),
			Song     ("Song" , String.class, 20, -1, 600, 600),
			;
			private final SimplifiedColumnConfig config;
			ColumnID(String name, Class<?> columnClass, int minWidth, int maxWidth, int prefWidth, int currentWidth) {
				config = new SimplifiedColumnConfig(name, columnClass, minWidth, maxWidth, prefWidth, currentWidth);
			}
			@Override public SimplifiedColumnConfig getColumnConfig() { return config; }
		}
		
		private static class SongTableModel extends Tables.SimplifiedTableModel<ColumnID> {
			
			private Vector<String> songs;

			SongTableModel() {
				super(ColumnID.values());
				songs = YamahaControl.preferredSongs.getAsSortedList();
			}

			void fireTableColumnUpdate(ColumnID columnID) {
				super.fireTableColumnUpdate(getColumn(columnID));
			}

			@Override public int getRowCount() { return songs.size(); }

			@Override
			public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
				if (rowIndex<0 || rowIndex>=songs.size()) return null;
				String song = songs.get(rowIndex);
				switch (columnID) {
				case TimeStamp: return YamahaControl.preferredSongs.getTimeStamp(song);
				case Song     : return song;
				}
				return null;
			}
		}
	}
}
