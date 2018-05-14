package net.schwarzbaer.java.tools.yamahacontrol;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Vector;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.schwarzbaer.java.tools.yamahacontrol.XML.TagList;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.Log;

public final class Device {
	
	String address;
	
	Volume     volume;
	MainZone   mainZone;
	SystemOpts system;
	Inputs     inputs;
	RemoteCtrl remoteCtrl;

	NetRadio netRadio;
	USB      usb;
	DLNA     dlna;
	IPodUSB  iPodUSB;
	Tuner    tuner;
	AirPlay  airPlay;
	Spotify  spotify;
	
	Device(String address) {
		this.address = address;
		
		this.volume     = new Volume    (this);
		this.inputs     = new Inputs    (this);
		this.remoteCtrl = new RemoteCtrl(this.address);
		this.mainZone   = new MainZone  (this.address);
		this.system     = new SystemOpts(this.address);
		
		this.netRadio = new NetRadio(this.address);
		this.usb      = new USB     (this.address);
		this.dlna     = new DLNA    (this.address);
		this.iPodUSB  = new IPodUSB (this.address);
		this.airPlay  = new AirPlay (this.address);
		this.spotify  = new Spotify (this.address);
		this.tuner    = new Tuner   (this.address);
	}
	
	enum UpdateWish {
		BasicStatus, Scenes, Inputs, System, SystemPower,
		NetRadioConfig, NetRadioPlayInfo, NetRadioListInfo,
		DLNAConfig, DLNAPlayInfo, DLNAListInfo,
		USBConfig, USBListInfo, USBPlayInfo,
		IPodUSBConfig, IPodUSBListInfo, IPodUSBPlayInfo, IPodUSBMode,
		AirPlayConfig, AirPlayPlayInfo, SpotifyConfig, SpotifyPlayInfo,
		TunerConfig, TunerPlayInfo, TunerPresets
	}
	
	public void update(EnumSet<UpdateWish> updateWishes) {
		//System.out.println("Device.update("+updateWishes+")");
		//long start = System.currentTimeMillis();
		for (UpdateWish uw: updateWishes) {
			update(uw);
			//Log.info(getClass(), "update -> %s", Ctrl.getRCcode(Ctrl.lastRC));
			if (Ctrl.lastRC==Ctrl.RC_CONNECT_TIMEOUT) break;
		}
		//long duration = System.currentTimeMillis()-start;
		//Log.info(getClass(), "%1.3fs elapsed for update( %s )", duration/1000.0f, updateWishes.toString());
	}

	private void update(UpdateWish uw) {
		switch (uw) {
		case Inputs          : inputs.askInputs(); break;
		case Scenes          : inputs.askScenes(); break;
		
		case BasicStatus     : mainZone.basicStatus.update(); break;
		case System          : system.update(); break;
		case SystemPower     : system.updatePowerState(); break;
		
		case NetRadioConfig  : netRadio.updateConfig(); break;
		case NetRadioListInfo: netRadio.listInfo.update(); break;
		case NetRadioPlayInfo: netRadio.playInfo.update(); break;
		
		case USBConfig       : usb     .updateConfig(); break;
		case USBListInfo     : usb     .listInfo.update(); break;
		case USBPlayInfo     : usb     .playInfo.update(); break;
		
		case DLNAConfig      : dlna    .updateConfig(); break;
		case DLNAListInfo    : dlna    .listInfo.update(); break;
		case DLNAPlayInfo    : dlna    .playInfo.update(); break;
		
		case IPodUSBConfig   : iPodUSB .updateConfig(); break;
		case IPodUSBListInfo : iPodUSB .listInfo.update(); break;
		case IPodUSBPlayInfo : iPodUSB .playInfo.update(); break;
		case IPodUSBMode     : iPodUSB.updateMode(); break;
		
		case TunerConfig     : tuner   .config  .update(); break;
		case TunerPlayInfo   : tuner   .playInfo.update(); break;
		case TunerPresets    : tuner   .playInfo.updatePresets(); break;
		
		case AirPlayConfig   : airPlay .config  .update(); break;
		case AirPlayPlayInfo : airPlay .playInfo.update(); break;
		
		case SpotifyConfig   : spotify .updateConfig(); break;
		case SpotifyPlayInfo : spotify .playInfo.update(); break;
		}
	}
	
	public <T extends Value> T askValue(KnownCommand knownCommand, T[] values, String... tagList) {
		return askValue(address,knownCommand, values, tagList);
	}
	
	public static <T extends Value> T askValue(String address, KnownCommand knownCommand, T[] values, String... tagList) {
		Node node = Ctrl.sendGetCommand_Node(address,knownCommand);
		if (node==null) return null;
		return XML.getSubValue(node,values,tagList);
	}
	
	public static String askValue(String address, KnownCommand knownCommand, String... tagList) {
		Node node = Ctrl.sendGetCommand_Node(address,knownCommand);
		if (node==null) return null;
		return XML.getSubValue(node,tagList);
	}

	static class Volume {

		private Device device;
		
		public Volume(Device device) {
			this.device = device;
		}

		public NumberWithUnit getVolume() {
			return device.mainZone.basicStatus.volume;
		}

		public void setVolume(double value) {
			if (device.mainZone.basicStatus.volume==null) return;
			
			int currentValue = (int)Math.round(device.mainZone.basicStatus.volume.getValue()*2);
			int newValue     = (int)Math.round(value*2);
			if (newValue!=currentValue) {
				// Value 0:   Val = Number: -805..(5)..165
				// Value 1:   Exp = "1"
				// Value 2:   Unit = "dB"
				//String xmlStr = NumberWithUnit.createXML(newValue/2.0,1,"dB");
				device.mainZone.basicStatus.volume.setValue(newValue/2.0f);
				String xmlStr = device.mainZone.basicStatus.volume.createXML();
				int rc = Ctrl.sendPutCommand(device.address,KnownCommand.MainZone.SetVolume,xmlStr);
				if (rc!=Ctrl.RC_OK && rc!=Ctrl.RC_DEVICE_IN_STANDBY)
					Log.error(getClass(), "setVolume(%f)-> %s %s -> RC: %s", value, KnownCommand.MainZone.SetVolume.tagList, xmlStr, Ctrl.getRCcode(rc));
			}
		}
		// Value 0:   Val = Number: -805..(5)..165
		public static final double MinVolume = -80.5;
		public static final double MaxVolume =  16.5;

		public Value.OnOff getMute() {
			return device.mainZone.basicStatus.volMute;
		}
		public void setMute(Value.OnOff volMute) {
			// [Vol_Mute_On]    PUT[P3]     Main_Zone,Volume,Mute = On
			// [Vol_Mute_Off]   PUT[P3]     Main_Zone,Volume,Mute = Off
			Device.sendCommand(getClass(), device.address, "setMute", KnownCommand.MainZone.SetVolumeMute, volMute);
		}
	}
	
	static class SystemOpts {

		private String address;
		
		Value.OnOff         eventNotice;
		Value.AvailableUnavailable yamahaNetworkSiteStatus;
		String              networkName;
		Value.PowerState    power;
		Value.OnOff         networkStandby;
		Value.EnableDisable dmcControl;

		public SystemOpts(String address) {
			this.address = address;
			clearValues();
		}
		
		private void clearValues() {
			eventNotice = null;
			yamahaNetworkSiteStatus = null;
			networkName = null;
			power = null;
			networkStandby = null;
			dmcControl = null;
		}
		
		void update() {
			clearValues();
			updateEventNotice();
			if (Ctrl.lastRC!=Ctrl.RC_CONNECT_TIMEOUT) updateYamahaNetworkSiteStatus();
			if (Ctrl.lastRC!=Ctrl.RC_CONNECT_TIMEOUT) updateNetworkName();
			if (Ctrl.lastRC!=Ctrl.RC_CONNECT_TIMEOUT) updatePowerState();
			if (Ctrl.lastRC!=Ctrl.RC_CONNECT_TIMEOUT) updateNetworkStandby();
			if (Ctrl.lastRC!=Ctrl.RC_CONNECT_TIMEOUT) updateDmcControl();
		}
		
		void setEventNotice(Value.OnOff value) {
			// [Event_On]   PUT[P1]     System,Misc,Event,Notice = On
			// [Event_Off]  PUT[P1]     System,Misc,Event,Notice = Off
			Device.sendCommand(getClass(), address, "setEventNotice", KnownCommand.SystemOptions.GetNSetEventNotice, value);
		}
		void updateEventNotice() {
			// GET[G1]:                 System,Misc,Event,Notice   ->   "On" | "Off"
			eventNotice = askValue(address, KnownCommand.SystemOptions.GetNSetEventNotice, Value.OnOff.values());
		}
		
		void updateYamahaNetworkSiteStatus() {
			// Network Update   [Network_Update]   Status   [Update_Status]   
			// GET[G4]:    System,Misc,Update,Yamaha_Network_Site,Status   ->   "Available" | "Unavailable"
			yamahaNetworkSiteStatus = askValue(address, KnownCommand.SystemOptions.GetYamahaNetworkSiteStatus, Value.AvailableUnavailable.values());
		}
		
		void setNetworkName(String value) {
			// PUT[P3]:    System,Misc,Network,Network_Name   =   Text: 1..15 (UTF-8)
			Device.sendCommand(getClass(), address, "setNetworkName", KnownCommand.SystemOptions.GetNSetNetworkName, value);
		}
		void updateNetworkName() {
			// GET[G2]:    System,Misc,Network,Network_Name   ->   Text: 1..15 (UTF-8)
			networkName = askValue(address, KnownCommand.SystemOptions.GetNSetNetworkName);
		}

		void setPowerState(Value.PowerState value) {
			// [Power_On]        PUT[P2]     System,Power_Control,Power = On
			// [Power_Standby]   PUT[P2]     System,Power_Control,Power = Standby
			Device.sendCommand(getClass(), address, "setPower", KnownCommand.SystemOptions.GetNSetPower, value);
		}
		void updatePowerState() {
			power = askValue(address, KnownCommand.SystemOptions.GetNSetPower, Value.PowerState.values());
		}

		void setNetworkStandby(Value.OnOff value) {
			// [Net_Standby_On]   PUT[P4]     System,Misc,Network,Network_Standby = On
			// [Net_Standby_Off]  PUT[P4]     System,Misc,Network,Network_Standby = Off
			Device.sendCommand(getClass(), address, "setNetworkStandby", KnownCommand.SystemOptions.GetNSetNetworkStandby, value);
		}
		void updateNetworkStandby() {
			// GET[G3]:                       System,Misc,Network,Network_Standby   ->   "On" | "Off"
			networkStandby = askValue(address, KnownCommand.SystemOptions.GetNSetNetworkStandby, Value.OnOff.values());
		}

		void setDmcControl(Value.EnableDisable value) {
			// [DMR_Off]  PUT[P5]     System,Misc,Network,DMC_Control = Disable
			// [DMR_On]   PUT[P5]     System,Misc,Network,DMC_Control = Enable
			Device.sendCommand(getClass(), address, "setNetworkStandby", KnownCommand.SystemOptions.GetNSetDmcControl, value);
		}
		void updateDmcControl() {
			// GET[G5]:               System,Misc,Network,DMC_Control   ->   "Disable" | "Enable"
			dmcControl = askValue(address, KnownCommand.SystemOptions.GetNSetDmcControl, Value.EnableDisable.values());
		}
	}
	
	static class MainZone {

		private String address;
		BasicStatus basicStatus;
		
		public MainZone(String address) {
			this.address = address;
			this.basicStatus = new BasicStatus(address);
		}
		
		public void setPowerState(Value.PowerState value) {
			// [Power_On]        PUT[P1]     Main_Zone,Power_Control,Power = On
			// [Power_Standby]   PUT[P1]     Main_Zone,Power_Control,Power = Standby
			if (value!=null)
				Device.sendCommand(getClass(), address, "setPowerState", KnownCommand.MainZone.SetPower, value);
		}

		public void setSleep(Value.SleepState value) {
			// [Sleep_Last]      PUT[P23]     Main_Zone,Power_Control,Sleep = Last
			// [Sleep_1]         PUT[P23]     Main_Zone,Power_Control,Sleep = 120 min
			// [Sleep_2]         PUT[P23]     Main_Zone,Power_Control,Sleep = 90 min
			// [Sleep_3]         PUT[P23]     Main_Zone,Power_Control,Sleep = 60 min
			// [Sleep_4]         PUT[P23]     Main_Zone,Power_Control,Sleep = 30 min
			// [Sleep_Off]       PUT[P23]     Main_Zone,Power_Control,Sleep = Off
			if (value!=null)
				Device.sendCommand(getClass(), address, "setSleep", KnownCommand.MainZone.SetSleep, value);
		}

		public void setSurroundProgram(Value.SurroundProgram value) {
			// PUT[P9]:    Main_Zone,Surround,Program_Sel,Current,Sound_Program   =   "Hall in Munich" | "Hall in Vienna" | "Chamber" | "Cellar Club" | "The Roxy Theatre" | "The Bottom Line" | "Sports" | "Action Game" | "Roleplaying Game" | "Music Video" | "Standard" | "Spectacle" | "Sci-Fi" | "Adventure" | "Drama" | "Mono Movie" | "Surround Decoder" | "2ch Stereo" | "5ch Stereo"
			if (value!=null)
				Device.sendCommand(getClass(), address, "setSurroundProgram", KnownCommand.MainZone.SetSurroundProgram, value);
		}

		public void setSurroundStraight(Value.OnOff value) {
			// [Straight_On]    PUT[P10]     Main_Zone,Surround,Program_Sel,Current,Straight = On
			// [Straight_Off]   PUT[P10]     Main_Zone,Surround,Program_Sel,Current,Straight = Off
			if (value!=null)
				Device.sendCommand(getClass(), address, "setSurroundStraight", KnownCommand.MainZone.SetSurroundStraight, value);
		}

		public void setSurroundEnhancer(Value.OnOff value) {
			// [Enhancer_On]    PUT[P11]     Main_Zone,Surround,Program_Sel,Current,Enhancer = On
			// [Enhancer_Off]   PUT[P11]     Main_Zone,Surround,Program_Sel,Current,Enhancer = Off
			if (value!=null)
				Device.sendCommand(getClass(), address, "setSurroundEnhancer", KnownCommand.MainZone.SetSurroundEnhancer, value);
		}

		public void setBass(float value) {
			// PUT[P7]:    Main_Zone,Sound_Video,Tone,Bass                  =   Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
			int number = Math.round(value*2)*5;
			number = Math.max(-60, Math.min(number, 60));
			NumberWithUnit nwu = new NumberWithUnit(number, 1, "dB");
			if (basicStatus.bass!=null && number==basicStatus.bass.number) return;
			basicStatus.bass = nwu;
			Device.sendCommand(getClass(), address, "setBass", KnownCommand.MainZone.SetBass, String.format(Locale.ENGLISH, "%1.2f->%s", value, nwu), nwu.createXML());
		}

		public void setTreble(float value) {
			// PUT[P8]:    Main_Zone,Sound_Video,Tone,Treble                  =   Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
			int number = Math.round(value*2)*5;
			number = Math.max(-60, Math.min(number, 60));
			NumberWithUnit nwu = new NumberWithUnit(number, 1, "dB");
			if (basicStatus.treble!=null && number==basicStatus.treble.number) return;
			basicStatus.treble = nwu;
			Device.sendCommand(getClass(), address, "setTreble", KnownCommand.MainZone.SetTreble, String.format(Locale.ENGLISH, "%1.2f->%s", value, nwu), nwu.createXML());
		}

		public void setAdaptiveDRC(Value.AutoOff value) {
			// [Adaptive_DRC_Auto]   PUT[P12]     Main_Zone,Sound_Video,Adaptive_DRC = Auto
			// [Adaptive_DRC_Off]    PUT[P12]     Main_Zone,Sound_Video,Adaptive_DRC = Off
			if (value!=null)
				Device.sendCommand(getClass(), address, "setAdaptiveDRC", KnownCommand.MainZone.SetAdaptiveDRC, value);
		}

		public void set3DCinemaDSP(Value.AutoOff value) {
			// [CINEMA_DSP_3D_Auto]  PUT[P13]     Main_Zone,Surround,_3D_Cinema_DSP = Auto
			// [CINEMA_DSP_3D_Off]   PUT[P13]     Main_Zone,Surround,_3D_Cinema_DSP = Off
			if (value!=null)
				Device.sendCommand(getClass(), address, "set3DCinemaDSP", KnownCommand.MainZone.Set3DCinemaDSP, value);
		}

		public void setDirectMode(Value.OnOff value) {
			// [Direct_On]           PUT[P17]     Main_Zone,Sound_Video,Direct,Mode = On
			// [Direct_Off]          PUT[P17]     Main_Zone,Sound_Video,Direct,Mode = Off
			if (value!=null)
				Device.sendCommand(getClass(), address, "setDirectMode", KnownCommand.MainZone.SetDirectMode, value);
		}

		static class BasicStatus {
			
			private String address;
		
			Value.PowerState power;
			Value.SleepState sleep;
			NumberWithUnit volume;
			Value.OnOff volMute;
			String currentInput;
			Inputs.DeviceSceneInput inputInfo;
			Value.SurroundProgram surroundProgram;
			Value.OnOff surroundStraight;
			Value.OnOff surroundEnhancer;
			Value.AutoOff cinemaDSP;
			Value.OnOff hdmiStandbyThrough;
			Value.OnOff directMode;
			Value.AutoOff adaptiveDRC;
			NumberWithUnit bass;
			NumberWithUnit treble;
		
			public BasicStatus(String address) {
				this.address = address;
				clearValues();
			}

			private void clearValues() {
				power = null;
				sleep = null;
				volume = null;
				volMute = null;
				currentInput = null;
				inputInfo = null;
				surroundProgram = null;
				surroundStraight = null;
				surroundEnhancer = null;
				cinemaDSP = null;
				hdmiStandbyThrough = null;
				directMode = null;
				adaptiveDRC = null;
				bass = null;
				treble = null;
			}
			
			public void update() {
				parse(Ctrl.sendGetCommand_Node(address,KnownCommand.MainZone.GetBasicStatus));
			}
			private void parse(Node node) {
				clearValues();
				XML.forEachChild(node, value->{
					switch (value.getNodeName()) {
					case "Power_Control":
						// GET[G1]:    Main_Zone,Basic_Status   ->   Power_Control,Power -> "On" | "Standby"
						// GET[G1]:    Main_Zone,Basic_Status   ->   Power_Control,Sleep -> "120 min" | "90 min" | "60 min" | "30 min" | "Off"
						power = XML.getSubValue(value,Value.PowerState.values(),"Power");
						sleep = XML.getSubValue(value,Value.SleepState.values(),"Sleep");
						break;
					case "Volume":
						volume = NumberWithUnit.parse(value,"Lvl");
						volMute = XML.getSubValue(value,Value.OnOff.values(),"Mute");
						break;
					case "Input":
						currentInput = XML.getSubValue(value,"Input_Sel");
						Node inputInfoNode = XML.getSubNode(value, "Input_Sel_Item_Info");
						if (inputInfoNode==null) inputInfo = null;
						else inputInfo = Inputs.DeviceSceneInput.parse(inputInfoNode);
						break;
						
					case "Surround":
						XML.forEachChild(value, surroundChild->{
							switch (surroundChild.getNodeName()) {
							case "Program_Sel":
								XML.forEachChild(surroundChild, programSelChild->{
									switch (programSelChild.getNodeName()) {
									case "Current":
										XML.forEachChild(programSelChild, currentChild->{
											switch (currentChild.getNodeName()) {
											case "Sound_Program":
												// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,Program_Sel,Current,Sound_Program -> "Hall in Munich" | "Hall in Vienna" | "Chamber" | "Cellar Club" | "The Roxy Theatre" | "The Bottom Line" | "Sports" | "Action Game" | "Roleplaying Game" | "Music Video" | "Standard" | "Spectacle" | "Sci-Fi" | "Adventure" | "Drama" | "Mono Movie" | "Surround Decoder" | "2ch Stereo" | "5ch Stereo"
												surroundProgram = XML.getSubValue(currentChild,Value.SurroundProgram.values());
												break;
											case "Straight":
												// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,Program_Sel,Current,Straight -> "On" | "Off"
												surroundStraight = XML.getSubValue(currentChild,Value.OnOff.values());
												break;
											case "Enhancer":
												// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,Program_Sel,Current,Enhancer -> "On" | "Off"
												surroundEnhancer = XML.getSubValue(currentChild,Value.OnOff.values());
												break;
											}
										});
										break;
									}
								});
								break;
								
							case "_3D_Cinema_DSP":
								// GET[G1]:    Main_Zone,Basic_Status   ->   Surround,_3D_Cinema_DSP -> "Auto" | "Off"
								cinemaDSP = XML.getSubValue(surroundChild,Value.AutoOff.values());
								break;
							}
						});
						break;
						
					case "Sound_Video":
						XML.forEachChild(value, soundVideoChild->{
							switch (soundVideoChild.getNodeName()) {
							case "Tone":
								XML.forEachChild(soundVideoChild, toneChild->{
									switch (toneChild.getNodeName()) {
									case "Bass":
										// GET[G1]:    Main_Zone,Basic_Status -> Sound_Video,Tone,Bass  ->  Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
										bass = NumberWithUnit.parse(toneChild);
										break;
										
									case "Treble":
										// GET[G1]:    Main_Zone,Basic_Status -> Sound_Video,Tone,Treble  ->  Number:-60..(5)..60 / Exp:"1" / Unit:"dB"
										treble = NumberWithUnit.parse(toneChild);
										break;
									}
								});
								break;
								
							case "Direct":
								XML.forEachChild(soundVideoChild, directChild->{
									switch (directChild.getNodeName()) {
									case "Mode":
										// GET[G1]:    Main_Zone,Basic_Status   ->   Sound_Video,Direct,Mode -> "On" | "Off"
										directMode = XML.getSubValue(directChild,Value.OnOff.values());
										break;
									}
								});
								break;
								
							case "HDMI":
								XML.forEachChild(soundVideoChild, hdmiChild->{
									switch (hdmiChild.getNodeName()) {
									case "Standby_Through_Info":
										// GET[G1]:    Main_Zone,Basic_Status   ->   Sound_Video,HDMI,Standby_Through_Info -> "On" | "Off"
										hdmiStandbyThrough = XML.getSubValue(hdmiChild,Value.OnOff.values());
										break;
										
									case "Output":
										XML.forEachChild(hdmiChild, outputChild->{
											switch (outputChild.getNodeName()) {
											case "OUT_1":
												break;
											}
										});
										break;
									}
								});
								break;
							case "Adaptive_DRC":
								// GET[G1]:    Main_Zone,Basic_Status   ->   Sound_Video,Adaptive_DRC -> "Auto" | "Off"
								adaptiveDRC = XML.getSubValue(soundVideoChild,Value.AutoOff.values());
								break;
							}
						});
						break;
						
					}
				});
			}
		}
	}
	
	
	static class RemoteCtrl {
	
		private String address;
	
		public RemoteCtrl(String address) {
			this.address = address;
		}

		public void setPlayback(Value.PlayPauseStop play) {
			Device.sendPlayback(getClass(), address, KnownCommand.SetPlayback.MainZone, play);
		}

		public void setPlayback(Value.SkipFwdRev skip) {
			Device.sendPlayback(getClass(), address, KnownCommand.SetPlayback.MainZone, skip);
		}

		public void setCursorSelect(Value.CursorSelectExt cursor) {
			Device.sendCursorSelect(getClass(), address, KnownCommand.SetCursorSelExt.MainZone, cursor);
		}

		public void setMenuControl(Value.MainZoneMenuControl menuCtrl) {
			Device.sendMenuControl(getClass(), address, KnownCommand.MenuControl.MainZone, menuCtrl);
		}
	
	}

	static class Inputs {

		private Device device;
		private DeviceSceneInput[] scenes;
		private DeviceSceneInput[] inputs;
		
		public Inputs(Device device) {
			this.device = device;
			this.scenes = null;
			this.inputs = null;
		}

		public boolean hasScenes() { return scenes!=null; }
		public boolean hasInputs() { return inputs!=null; }
		public void askScenes() { scenes = getSceneInput(KnownCommand.MainZone.GetSceneItems); }
		public void askInputs() { inputs = getSceneInput(KnownCommand.MainZone.GetInputItems); }
		public DeviceSceneInput[] getScenes() { return scenes; }
		public DeviceSceneInput[] getInputs() { return inputs; }
		public void setScene(DeviceSceneInput dsi) {
			// PUT[P6]:    Main_Zone,Scene,Scene_Sel   =   Values [GET[G4]:Main_Zone,Scene,Scene_Sel_Item]
			Device.sendCommand(getClass(), device.address, "setScene", KnownCommand.MainZone.SetCurrentScene, dsi.ID);
		}
		public void setInput(DeviceSceneInput dsi) {
			// PUT[P4]:    Main_Zone,Input,Input_Sel   =   Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
			Device.sendCommand(getClass(), device.address, "setInput", KnownCommand.MainZone.SetCurrentInput, dsi.ID);
		}

		public DeviceSceneInput findInput(String inputID) {
			if (inputID==null) return null;
			if (inputs==null) return null;
			for (DeviceSceneInput dsi:inputs)
				if (inputID.equals(dsi.ID))
					return dsi;
			return null;
		}

		public DeviceSceneInput getCurrentInput() {
			// GET[G1]:    Main_Zone,Basic_Status   ->   Input,Input_Sel -> Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
			if (inputs==null) return null;
			if (device.mainZone.basicStatus.currentInput==null) return null;
			for (DeviceSceneInput dsi:inputs)
				if (device.mainZone.basicStatus.currentInput.equals(dsi.ID))
					return dsi;
			return null;
		}

		private DeviceSceneInput[] getSceneInput(KnownCommand knownCommand) {
			Node node = Ctrl.sendGetCommand_Node(device.address,knownCommand);
			if (node == null) return null;
			
			NodeList itemNodes = node.getChildNodes();
			DeviceSceneInput[] dsiArr = new DeviceSceneInput[itemNodes.getLength()];
			for (int i=0; i<itemNodes.getLength(); ++i)
				dsiArr[i] = DeviceSceneInput.parse(itemNodes.item(i));
			return dsiArr;
		}
		
		static class DeviceSceneInput {
			public String ID;
			public String rw;
			public String title;
			public String srcName;
			public String srcNumber;
			public DeviceSceneInput() {
				this.ID = null;
				this.rw = null;
				this.title = null;
				this.srcName = null;
				this.srcNumber = null;
			}

			static DeviceSceneInput parse(Node item) {
				DeviceSceneInput dsi = new DeviceSceneInput();
				XML.forEachChild(item, value->{
					switch (value.getNodeName()) {
					case "Param"     : dsi.ID        = XML.getSubValue(value); break;
					case "RW"        : dsi.rw        = XML.getSubValue(value); break;
					case "Title"     : dsi.title     = XML.getSubValue(value); break;
					case "Src_Name"  : dsi.srcName   = XML.getSubValue(value); break;
					case "Src_Number": dsi.srcNumber = XML.getSubValue(value); break;
					}
				});
				return dsi;
			}
		}
	}

	static interface KnownCommand {
		public TagList getTagList();
		public String toFullString();
		
		enum MainZone implements KnownCommand {
			SetPower       ("Main_Zone,Power_Control,Power"), // P1:  Main_Zone,Power_Control,Power 
			SetVolume      ("Main_Zone,Volume,Lvl"         ), // P2:  Main_Zone,Volume,Lvl
			SetVolumeMute  ("Main_Zone,Volume,Mute"        ), // P3:  Main_Zone,Volume,Mute
			SetCurrentInput("Main_Zone,Input,Input_Sel"    ), // P4:  Main_Zone,Input,Input_Sel
			SetCurrentScene("Main_Zone,Scene,Scene_Sel"    ), // P6:  Main_Zone,Scene,Scene_Sel
			SetBass        ("Main_Zone,Sound_Video,Tone,Bass"      ), // P7:  Main_Zone,Sound_Video,Tone,Bass
			SetTreble      ("Main_Zone,Sound_Video,Tone,Treble"    ), // P8:  Main_Zone,Sound_Video,Tone,Treble
			SetSurroundProgram ("Main_Zone,Surround,Program_Sel,Current,Sound_Program"), // P9:  Main_Zone,Surround,Program_Sel,Current,Sound_Program
			SetSurroundStraight("Main_Zone,Surround,Program_Sel,Current,Straight"     ), // P10: Main_Zone,Surround,Program_Sel,Current,Straight
			SetSurroundEnhancer("Main_Zone,Surround,Program_Sel,Current,Enhancer"     ), // P11: Main_Zone,Surround,Program_Sel,Current,Enhancer
			SetAdaptiveDRC ("Main_Zone,Sound_Video,Adaptive_DRC"   ), // P12: Main_Zone,Sound_Video,Adaptive_DRC
			Set3DCinemaDSP ("Main_Zone,Surround,_3D_Cinema_DSP"    ), // P13: Main_Zone,Surround,_3D_Cinema_DSP
			SetDirectMode  ("Main_Zone,Sound_Video,Direct,Mode"    ), // P17: Main_Zone,Sound_Video,Direct,Mode
//			-> SetCursorSelExt("Main_Zone,Cursor_Control,Cursor"   ), // P18: Main_Zone,Cursor_Control,Cursor
//			-> MenuControl ("Main_Zone,Cursor_Control,Menu_Control"), // P19: Main_Zone,Cursor_Control,Menu_Control
			SetSleep       ("Main_Zone,Power_Control,Sleep"        ), // P23: Main_Zone,Power_Control,Sleep
//			-> SetPlayback ("Main_Zone,Play_Control,Playback"      ), // P24: Main_Zone,Play_Control,Playback
			
			GetBasicStatus ("Main_Zone,Basic_Status"        ), // G1: Main_Zone,Basic_Status
			GetInputItems  ("Main_Zone,Input,Input_Sel_Item"), // G2: Main_Zone,Input,Input_Sel_Item
			GetSceneItems  ("Main_Zone,Scene,Scene_Sel_Item"), // G4: Main_Zone,Scene,Scene_Sel_Item
			;
			
			final private TagList tagList;
			MainZone(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum SystemOptions implements KnownCommand {
			GetNSetEventNotice         ("System,Misc,Event,Notice"                     ), // P1: System,Misc,Event,Notice             // G1: System,Misc,Event,Notice
			GetNSetPower               ("System,Power_Control,Power"                   ), // P2: System,Power_Control,Power
			GetNSetNetworkName         ("System,Misc,Network,Network_Name"             ), // P3: System,Misc,Network,Network_Name     // G2: System,Misc,Network,Network_Name
			GetNSetNetworkStandby      ("System,Misc,Network,Network_Standby"          ), // P4: System,Misc,Network,Network_Standby  // G3: System,Misc,Network,Network_Standby
			GetNSetDmcControl          ("System,Misc,Network,DMC_Control"              ), // P5: System,Misc,Network,DMC_Control      // G5: System,Misc,Network,DMC_Control
			GetYamahaNetworkSiteStatus ("System,Misc,Update,Yamaha_Network_Site,Status"), // G4: System,Misc,Update,Yamaha_Network_Site,Status
			;
			final private TagList tagList;
			SystemOptions(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}

		enum MenuControl implements KnownCommand {
			MainZone("Main_Zone,Cursor_Control,Menu_Control"), // P19: Main_Zone,Cursor_Control,Menu_Control
			;
			final private TagList tagList;
			MenuControl(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum Config implements KnownCommand {
			MainZone("Main_Zone,Config"),
			NetRadio("NET_RADIO,Config"),
			USB     (      "USB,Config"),
			DLNA    (   "SERVER,Config"),
			IPodUSB ( "iPod_USB,Config"),
			Spotify (  "Spotify,Config"), 
			AirPlay (  "AirPlay,Config"),
			Tuner   (    "Tuner,Config"),
			;
			final private TagList tagList;
			Config (String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum GetListInfo implements KnownCommand {
			NetRadio("NET_RADIO,List_Info"), // G2: NET_RADIO,List_Info
			USB     (      "USB,List_Info"), // G2: USB,List_Info
			DLNA    (   "SERVER,List_Info"), // G2: SERVER,List_Info
			IPodUSB ( "iPod_USB,List_Info"), // G2: iPod_USB,List_Info
			;
			final private TagList tagList;
			GetListInfo (String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum GetPlayInfo implements KnownCommand {
			NetRadio("NET_RADIO,Play_Info"), // G1: NET_RADIO,Play_Info
			USB     (      "USB,Play_Info"), // G1: USB,Play_Info
			DLNA    (   "SERVER,Play_Info"), // G1: SERVER,Play_Info
			IPodUSB ( "iPod_USB,Play_Info"), // G1: iPod_USB,Play_Info
			Spotify (  "Spotify,Play_Info"), // G1: Spotify,Play_Info
			AirPlay (  "AirPlay,Play_Info"), // G1: AirPlay,Play_Info
			Tuner   (    "Tuner,Play_Info"), // G1: Tuner,Play_Info
			;
			final private TagList tagList;
			GetPlayInfo (String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum SetDirectSel implements KnownCommand {
			NetRadio("NET_RADIO,List_Control,Direct_Sel"),
			USB     (      "USB,List_Control,Direct_Sel"), // P5: USB,List_Control,Direct_Sel
			DLNA    (   "SERVER,List_Control,Direct_Sel"), // P5: SERVER,List_Control,Direct_Sel
			IPodUSB ( "iPod_USB,List_Control,Direct_Sel"), // P2: iPod_USB,List_Control,Direct_Sel
			;
			final private TagList tagList;
			SetDirectSel (String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum SetCursorSelExt implements KnownCommand {
			MainZone  ("Main_Zone,Cursor_Control,Cursor"), // P18: Main_Zone,Cursor_Control,Cursor
			;
			final private TagList tagList;
			SetCursorSelExt(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum SetCursorSel implements KnownCommand {
			NetRadio("NET_RADIO,List_Control,Cursor"),
			USB     (      "USB,List_Control,Cursor"), // P7: USB,List_Control,Cursor
			DLNA    (   "SERVER,List_Control,Cursor"), // P7: SERVER,List_Control,Cursor
			IPodUSB ( "iPod_USB,List_Control,Cursor"), // P5: iPod_USB,List_Control,Cursor
			;
			final private TagList tagList;
			SetCursorSel(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum SetPageSel implements KnownCommand {
			NetRadio("NET_RADIO,List_Control,Page"),
			USB     (      "USB,List_Control,Page"), // P8: USB,List_Control,Page
			DLNA    (   "SERVER,List_Control,Page"), // P8: SERVER,List_Control,Page
			IPodUSB ( "iPod_USB,List_Control,Page"), // P6: iPod_USB,List_Control,Page
			;
			final private TagList tagList;
			SetPageSel(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum JumpToLine implements KnownCommand {
			NetRadio("NET_RADIO,List_Control,Jump_Line"), // P3: NET_RADIO,List_Control,Jump_Line 
			USB     (      "USB,List_Control,Jump_Line"), // P6: USB,List_Control,Jump_Line
			DLNA    (   "SERVER,List_Control,Jump_Line"), // P6: SERVER,List_Control,Jump_Line
			IPodUSB ( "iPod_USB,List_Control,Jump_Line"), // P4: iPod_USB,List_Control,Jump_Line
			;
			final private TagList tagList;
			JumpToLine(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum SetPlayback implements KnownCommand {
			MainZone("Main_Zone,Play_Control,Playback"), // P24: Main_Zone,Play_Control,Playback
			NetRadio("NET_RADIO,Play_Control,Playback"),
			USB     (      "USB,Play_Control,Playback"), // P3: USB,Play_Control,Playback
			DLNA    (   "SERVER,Play_Control,Playback"), // P3: SERVER,Play_Control,Playback
			IPodUSB ( "iPod_USB,Play_Control,Playback"), // P1: iPod_USB,Play_Control,Playback
			Spotify (  "Spotify,Play_Control,Playback"), // P1: Spotify,Play_Control,Playback
			AirPlay (  "AirPlay,Play_Control,Playback"), // P1: AirPlay,Play_Control,Playback
			;
			final private TagList tagList;
			SetPlayback(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum SetRepeat implements KnownCommand {
			USB    (     "USB,Play_Control,Play_Mode,Repeat"), // P1: USB,Play_Control,Play_Mode,Repeat
			DLNA   (  "SERVER,Play_Control,Play_Mode,Repeat"), // P1: SERVER,Play_Control,Play_Mode,Repeat
			IPodUSB("iPod_USB,Play_Control,Play_Mode,Repeat"), // P8: iPod_USB,Play_Control,Play_Mode,Repeat
			;
			final private TagList tagList;
			SetRepeat(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum SetShuffle implements KnownCommand {
			USB    (     "USB,Play_Control,Play_Mode,Shuffle"), // P2: USB,Play_Control,Play_Mode,Shuffle
			DLNA   (  "SERVER,Play_Control,Play_Mode,Shuffle"), // P2: SERVER,Play_Control,Play_Mode,Shuffle
			IPodUSB("iPod_USB,Play_Control,Play_Mode,Shuffle"), // P9: iPod_USB,Play_Control,Play_Mode,Shuffle
			;
			final private TagList tagList;
			SetShuffle(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum SetTuner implements KnownCommand {
			SetSearchmode       ("Tuner,Play_Control,Search_Mode"       ), // P1: Tuner,Play_Control,Search_Mode
			SetBand             ("Tuner,Play_Control,Tuning,Band"       ), // P3: Tuner,Play_Control,Tuning,Band
			SetFreqFM           ("Tuner,Play_Control,Tuning,Freq,FM"    ), // P8: Tuner,Play_Control,Tuning,Freq,FM
			SetFreqAM           ("Tuner,Play_Control,Tuning,Freq,AM"    ), // P9: Tuner,Play_Control,Tuning,Freq,AM
			SetScanFM           ("Tuner,Play_Control,Tuning,Freq,FM,Val"), // P10: Tuner,Play_Control,Tuning,Freq,FM,Val
			SetScanAM           ("Tuner,Play_Control,Tuning,Freq,AM,Val"), // P11: Tuner,Play_Control,Tuning,Freq,AM,Val
			;
			final private TagList tagList;
			SetTuner(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum Presets implements KnownCommand {
			GetUSBPresets       (     "USB,Play_Control,Preset,Preset_Sel_Item"), // G4: USB,Play_Control,Preset,Preset_Sel_Item
			SetUSBSelectPreset  (     "USB,Play_Control,Preset,Preset_Sel"     ), // P4: USB,Play_Control,Preset,Preset_Sel
			GetDLNAPresets      (  "SERVER,Play_Control,Preset,Preset_Sel_Item"), // G4: SERVER,Play_Control,Preset,Preset_Sel_Item
			SetDLNASelectPreset (  "SERVER,Play_Control,Preset,Preset_Sel"     ), // P4: SERVER,Play_Control,Preset,Preset_Sel
			GetTunerPresets     (   "Tuner,Play_Control,Preset,Preset_Sel_Item"), // G3: Tuner,Play_Control,Preset,Preset_Sel_Item
			SetTunerSelectPreset(   "Tuner,Play_Control,Preset,Preset_Sel"     ), // P2: Tuner,Play_Control,Preset,Preset_Sel
			;
			final private TagList tagList;
			Presets(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
		enum Special implements KnownCommand {
			SetIPodUSBMode      ("iPod_USB,Play_Control,iPod_Mode"             ), // P10: iPod_USB,Play_Control,iPod_Mode
			;
			final private TagList tagList;
			Special(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
			@Override public String toFullString() { return "KnownCommand."+getClass().getSimpleName()+"."+toString(); }
		}
		
	}

	static interface Value {

		public String getLabel();
		
		public enum EnableDisable        implements Value { Enable, Disable       ; @Override public String getLabel() { return toString(); }  } 
		public enum AutoOff              implements Value { Auto, Off             ; @Override public String getLabel() { return toString(); }  } 
		public enum OnOff                implements Value { On, Off               ; @Override public String getLabel() { return toString(); }  } 
		public enum PowerState           implements Value { On, Standby           ; @Override public String getLabel() { return toString(); }  }
		public enum PlayStop             implements Value { Play, Stop            ; @Override public String getLabel() { return toString(); }  }
		public enum PlayPauseStop        implements Value { Play, Pause, Stop     ; @Override public String getLabel() { return toString(); }  }
		public enum AlbumCoverFormat     implements Value { BMP, YMF              ; @Override public String getLabel() { return toString(); }  }
		public enum ReadyOrBusy          implements Value { Ready, Busy           ; @Override public String getLabel() { return toString(); }  }
		public enum OffOneAll            implements Value { Off, One, All         ; @Override public String getLabel() { return toString(); }  } 
		public enum ShuffleIPod          implements Value { Off, Songs, Albums    ; @Override public String getLabel() { return toString(); }  } 
		public enum IPodMode             implements Value { Normal, Extended      ; @Override public String getLabel() { return toString(); }  }
		public enum NegateAssert         implements Value { Negate, Assert        ; @Override public String getLabel() { return toString(); }  }
		public enum AmFm                 implements Value { AM, FM                ; @Override public String getLabel() { return toString(); }  }
		public enum UpDown               implements Value { Up, Down              ; @Override public String getLabel() { return toString(); }  }
		public enum AvailableUnavailable implements Value { Available, Unavailable; @Override public String getLabel() { return toString(); }  }
		
		public static class SurroundProgram implements Value {
			
			// PUT[P9]:    Main_Zone,Surround,Program_Sel,Current,Sound_Program   =   "Hall in Munich" | "Hall in Vienna" | "Chamber" | "Cellar Club" | "The Roxy Theatre" | "The Bottom Line" | "Sports" | "Action Game" | "Roleplaying Game" | "Music Video" | "Standard" | "Spectacle" | "Sci-Fi" | "Adventure" | "Drama" | "Mono Movie" | "Surround Decoder" | "2ch Stereo" | "5ch Stereo"
			private static String[] labels = new String[] {"Hall in Munich","Hall in Vienna","Chamber","Cellar Club","The Roxy Theatre","The Bottom Line","Sports","Action Game","Roleplaying Game","Music Video","Standard","Spectacle","Sci-Fi","Adventure","Drama","Mono Movie","Surround Decoder","2ch Stereo","5ch Stereo"};
			private static SurroundProgram[] values = Arrays.stream(labels).map(str->new SurroundProgram(str)).toArray(n->new SurroundProgram[n]);
			public static SurroundProgram[] values() { return values; }
			
			private String label;
			private SurroundProgram(String label) { this.label = label; }
			@Override public String getLabel() { return label; }
			@Override public String toString() { return label; }
			@Override public boolean equals(Object obj) {
				if (!(obj instanceof SurroundProgram)) return false;
				return label.equals(((SurroundProgram)obj).label);
			}
		}
		
		public enum FreqAutoTP implements Value {
			AutoUp("Auto Up"),AutoDown("Auto Down"),TPUp("TP Up"),TPDown("TP Down");
			private String label;
			FreqAutoTP(String label) { this.label = label; }
			@Override public String getLabel() { return label; }
		}
		
		public enum ScanFreq implements Value {
			AutoUp("Auto Up"),AutoDown("Auto Down"), Cancel("Cancel");
			private String label;
			ScanFreq(String label) { this.label = label; }
			@Override public String getLabel() { return label; }
		}
		
		public enum ScanTP implements Value {
			TPUp("TP Up"),TPDown("TP Down"), Cancel("Cancel");
			private String label;
			ScanTP(String label) { this.label = label; }
			@Override public String getLabel() { return label; }
		}
		
		public enum SkipFwdRev implements Value {
			SkipFwd("Skip Fwd"),SkipRev("Skip Rev");
			private String label;
			SkipFwdRev(String label) { this.label = label; }
			@Override public String getLabel() { return label; }
		}
		
		public enum ReadyOrNot implements Value {
			Ready("Ready"),NotReady("Not Ready");
			private String label;
			ReadyOrNot(String label) { this.label = label; }
			@Override public String getLabel() { return label; }
		}
		
		public enum SleepState implements Value {
			Last("Last"),Off("Off"),_30min("30 min"),_60min("60 min"),_90min("90 min"),_120min("120 min");
			private String label;
			SleepState(String label) { this.label = label; }
			@Override public String getLabel() { return label; }
		}
		
		public enum LineAttribute implements Value {
			Container,UnplayableItem("Unplayable Item"),Item,Unselectable;
			private String label;
			LineAttribute(String label) { this.label = label; }
			LineAttribute() { this.label = toString(); }
			@Override public String getLabel() { return label; }
		}
		
		public enum MainZoneMenuControl implements Value {
			Setup("On Screen"),Option,Display;
			private String label;
			MainZoneMenuControl(String label) { this.label = label; }
			MainZoneMenuControl() { this.label = toString(); }
			@Override public String getLabel() { return label; }
		}
		
		public enum CursorSelect implements Value {
			// [Cursor_Up]        PUT[P4]     NET_RADIO,List_Control,Cursor = Up
			// [Cursor_Down]        PUT[P4]     NET_RADIO,List_Control,Cursor = Down
			// [Cursor_Left]        PUT[P4]     NET_RADIO,List_Control,Cursor = Return
			// [Cursor_Sel]        PUT[P4]     NET_RADIO,List_Control,Cursor = Sel
			// [Cursor_Home]        PUT[P4]     NET_RADIO,List_Control,Cursor = Return to Home
			Up,Down,Return,Sel,ReturnToHome("Return to Home");
			private String label;
			CursorSelect(String label) { this.label = label; }
			CursorSelect() { this.label = toString(); }
			@Override public String getLabel() { return label; }
		}
		
		public enum CursorSelectExt implements Value {
			// [Cursor_Up]    Layout:5     PUT[P18]     Main_Zone,Cursor_Control,Cursor = Up
			// [Cursor_Down]    Layout:9     PUT[P18]     Main_Zone,Cursor_Control,Cursor = Down
			// [Cursor_Left]    Layout:6     PUT[P18]     Main_Zone,Cursor_Control,Cursor = Left
			// [Cursor_Right]    Layout:8     PUT[P18]     Main_Zone,Cursor_Control,Cursor = Right
			// [Cursor_Return]    Layout:10     PUT[P18]     Main_Zone,Cursor_Control,Cursor = Return
			// [Cursor_Sel]    Layout:7     PUT[P18]     Main_Zone,Cursor_Control,Cursor = Sel
			// [Cursor_Home]        PUT[P18]     Main_Zone,Cursor_Control,Cursor = Return to Home
			Up,Down,Left,Right,Return,Sel,Home("Return to Home");
			private String label;
			CursorSelectExt(String label) { this.label = label; }
			CursorSelectExt() { this.label = toString(); }
			@Override public String getLabel() { return label; }
		}
		
		// [Page_Up_1]        PUT[P5]     NET_RADIO,List_Control,Page = Up
		// [Page_Down_1]        PUT[P5]     NET_RADIO,List_Control,Page = Down
		public enum PageSelect implements Value { Up,Down; @Override public String getLabel() { return toString(); }  }
	}

	static class NumberWithUnit {
		private float value;
		private int number;
		private final String unit;
		private final int exponent;
		
		private NumberWithUnit(int number, int exponent, String unit) {
			this.number = number;
			this.exponent = exponent;
			this.unit = unit;
			this.value = (float)number;
			for (int i=0; i<exponent; ++i) value/=10;
		}

		String getUnit() { return unit; }
		float getValue() { return value; }
		
		void setValue(float value) {
			this.value = value;
			for (int i=0; i<exponent; ++i) value*=10;
			this.number = Math.round(value);
		}
		
		@Override
		public String toString() {
			return "NumberWithUnit("+number+","+exponent+",\""+unit+"\")";
		}
		
		public String toValueStr() {
			return String.format(Locale.ENGLISH, "%1."+exponent+"f %s", value, unit);
		}

		String createXML() {
			return "<Val>"+number+"</Val><Exp>"+exponent+"</Exp><Unit>"+unit+"</Unit>";
		}

		static String createXML(double val, int exp, String unit) {
			for (int i=0; i<exp; ++i) val*=10;
			int valInt = (int)Math.round(val);
			return "<Val>"+valInt+"</Val><Exp>"+exp+"</Exp><Unit>"+unit+"</Unit>";
		}

		static NumberWithUnit parse(Node node, String... tagList) {
			Node value = XML.getSubNode(node, tagList);
			if (value==null) return null;
			
			String val = XML.getSubValue(value,"Val");
			String exp = XML.getSubValue(value,"Exp");
			String unit = XML.getSubValue(value,"Unit");
			
			int number;
			int exponent;
			try {
				number = Integer.parseInt(val);
				exponent = Integer.parseInt(exp);
			} catch (NumberFormatException e) {
				return null;
			}
			return new NumberWithUnit(number, exponent, unit);
		}
	}
	
	static class SubUnitWithSimpleReadyState {
		
		private KnownCommand.Config configCmd;
		protected String address;
		Value.ReadyOrNot deviceStatus;

		SubUnitWithSimpleReadyState( String address, KnownCommand.Config configCmd ) {
			this.address = address;
			this.configCmd = configCmd;
			this.deviceStatus = null;
		}
		
		public void updateConfig() {
			// GET:    #######,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
			deviceStatus = askValue(address, configCmd, Value.ReadyOrNot.values(), "Feature_Availability");
		}
	}
	
	static class NetRadio extends SubUnitWithSimpleReadyState {
		
		ListInfo          listInfo;
		PlayInfo_NetRadio playInfo;
		
		public NetRadio(String address) {
			super(address, KnownCommand.Config.NetRadio);
			this.listInfo = new ListInfo         (             address, KnownCommand.GetListInfo.NetRadio, KnownCommand.SetDirectSel.NetRadio, KnownCommand.SetCursorSel.NetRadio, KnownCommand.SetPageSel.NetRadio, KnownCommand.JumpToLine.NetRadio);
			this.playInfo = new PlayInfo_NetRadio("Net Radio", address, KnownCommand.GetPlayInfo.NetRadio, KnownCommand.SetPlayback.NetRadio);
		}
	}
	
	static class USB extends SubUnitWithSimpleReadyState {
		
		ListInfo listInfo;
		PlayInfoExt<Value.OnOff> playInfo;
		
		public USB(String address) {
			super(address, KnownCommand.Config.USB);
			this.listInfo = new ListInfo     (       address, KnownCommand.GetListInfo.USB, KnownCommand.SetDirectSel.USB, KnownCommand.SetCursorSel.USB, KnownCommand.SetPageSel.USB, KnownCommand.JumpToLine.USB);
			this.playInfo = new PlayInfoExt<>("USB", address, KnownCommand.GetPlayInfo.USB, KnownCommand.SetPlayback.USB, KnownCommand.SetRepeat.USB, KnownCommand.SetShuffle.USB, Value.OnOff.values());
		}
	}
	
	static class DLNA extends SubUnitWithSimpleReadyState {
		
		ListInfo listInfo;
		PlayInfoExt<Value.OnOff> playInfo;
		
		public DLNA(String address) {
			super(address, KnownCommand.Config.DLNA);
			this.listInfo = new ListInfo     (        address, KnownCommand.GetListInfo.DLNA, KnownCommand.SetDirectSel.DLNA, KnownCommand.SetCursorSel.DLNA, KnownCommand.SetPageSel.DLNA, KnownCommand.JumpToLine.DLNA);
			this.playInfo = new PlayInfoExt<>("DLNA", address, KnownCommand.GetPlayInfo.DLNA, KnownCommand.SetPlayback.DLNA, KnownCommand.SetRepeat.DLNA, KnownCommand.SetShuffle.DLNA, Value.OnOff.values());
		}
	}
	
	static class IPodUSB extends SubUnitWithSimpleReadyState {
		
		Value.IPodMode mode;
		ListInfo listInfo;
		PlayInfoExt<Value.ShuffleIPod> playInfo;
		
		public IPodUSB(String address) {
			super(address, KnownCommand.Config.IPodUSB);
			this.listInfo = new ListInfo     (            address, KnownCommand.GetListInfo.IPodUSB, KnownCommand.SetDirectSel.IPodUSB, KnownCommand.SetCursorSel.IPodUSB, KnownCommand.SetPageSel.IPodUSB, KnownCommand.JumpToLine.IPodUSB);
			this.playInfo = new PlayInfoExt<>("iPod USB", address, KnownCommand.GetPlayInfo.IPodUSB, KnownCommand.SetPlayback.IPodUSB, KnownCommand.SetRepeat.IPodUSB, KnownCommand.SetShuffle.IPodUSB, Value.ShuffleIPod.values());
		}

		public void updateMode() {
			mode = Ctrl.sendGetCommand(address,KnownCommand.Special.SetIPodUSBMode,Value.IPodMode.values());
			//Log.info(getClass(), "update mode: %s", mode.getLabel());
		}

		public void sendSetMode(Value.IPodMode mode) {
			// Browse Mode   []        PUT[P10]     iPod_USB,Play_Control,iPod_Mode = Extended
			Device.sendCommand(getClass(), address, "sendSetMode", KnownCommand.Special.SetIPodUSBMode, mode);
		}
	}
	
	static class AirPlay {
		
		PlayInfo_AirPlaySpotify playInfo;
		Config config;
		
		public AirPlay(String address) {
			this.config   = new Config(address, KnownCommand.Config.AirPlay);
			this.playInfo = new PlayInfo_AirPlaySpotify("AirPlay", address, KnownCommand.GetPlayInfo.AirPlay, KnownCommand.SetPlayback.AirPlay);
		}
		
		static class Config extends AbstractConfig {
			Value.ReadyOrNot deviceStatus;
			String volumeInterlock;
			
			Config(String address, KnownCommand.Config getConfigCmd) {
				super(address, getConfigCmd);
				clearValues();
			}

			private void clearValues() {
				deviceStatus = null;
				volumeInterlock = null;
			}
	
			@Override
			protected void parse(Node node) {
				clearValues();
				XML.forEachChild(node, child->{
					switch (child.getNodeName()) {
					case "Feature_Availability":
						// GET[G3]:    AirPlay,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
						deviceStatus =  XML.getSubValue(child, Value.ReadyOrNot.values());
						break;
						
					case "Volume_Interlock":
						volumeInterlock =  XML.getSubValue(child);
						break;
					}
				});
			}
		}
	}

	static class Spotify extends SubUnitWithSimpleReadyState {
		
		PlayInfo_AirPlaySpotify playInfo;
		
		public Spotify(String address) {
			super(address, KnownCommand.Config.Spotify);
			this.playInfo = new PlayInfo_AirPlaySpotify("Spotify", address, KnownCommand.GetPlayInfo.Spotify, KnownCommand.SetPlayback.Spotify);
		}
	}

	static class Tuner {
		private String address;
		Config config;
		PlayInfo_Tuner playInfo;
	
		public Tuner(String address) {
			this.address = address;
			this.config   = new Config        (         address, KnownCommand.Config.Tuner);
			this.playInfo = new PlayInfo_Tuner("Tuner", address, KnownCommand.GetPlayInfo.Tuner, KnownCommand.Presets.GetTunerPresets);
		}

		public void setScanAM(Value.ScanFreq value) {
			// [Plus_1]             PUT[P11]     Tuner,Play_Control,Tuning,Freq,AM,Val = Auto Up
			// [Minus_1]            PUT[P11]     Tuner,Play_Control,Tuning,Freq,AM,Val = Auto Down
			// [Freq_Auto_Cancel]   PUT[P11]     Tuner,Play_Control,Tuning,Freq,AM,Val = Cancel
			if (value!=null)
				sendCommand(getClass(), address, "setScanAM", KnownCommand.SetTuner.SetScanAM, value.getLabel());
		}

		public void setScanFM(Value.ScanFreq value) {
			// [Plus_1]             PUT[P10]     Tuner,Play_Control,Tuning,Freq,FM,Val = Auto Up
			// [Minus_1]            PUT[P10]     Tuner,Play_Control,Tuning,Freq,FM,Val = Auto Down
			// [Freq_Auto_Cancel]   PUT[P10]     Tuner,Play_Control,Tuning,Freq,FM,Val = Cancel
			if (value!=null)
				sendCommand(getClass(), address, "setScanFM", KnownCommand.SetTuner.SetScanFM, value.getLabel());
		}

		public void setScanFMTP(Value.ScanTP value) {
			// [Plus_1]             PUT[P10]     Tuner,Play_Control,Tuning,Freq,FM,Val = TP Up
			// [Minus_1]            PUT[P10]     Tuner,Play_Control,Tuning,Freq,FM,Val = TP Down
			// [Freq_Auto_Cancel]   PUT[P10]     Tuner,Play_Control,Tuning,Freq,FM,Val = Cancel
			if (value!=null)
				sendCommand(getClass(), address, "setScanFM", KnownCommand.SetTuner.SetScanFM, value.getLabel());
		}

		public void setBand(Value.AmFm band) {
			Device.sendSetBand(getClass(), address, KnownCommand.SetTuner.SetBand, band);
		}

		public void setFreqAM(float value) {
			// PUT[P9]:    Tuner,Play_Control,Tuning,Freq,AM  =  Number: 531..(9)..1611 / Exp:0 / Unit:"kHz"
			int number = Math.round((value-531)/9)*9+531;
			number = Math.max(531, Math.min(number, 1611));
			NumberWithUnit nwu = new NumberWithUnit(number, 0, "kHz");
			if (playInfo.tuningFreqAmValue!=null) {
				if (number==playInfo.tuningFreqAmValue.number) return;
				playInfo.tuningFreqAmValue = nwu;
			}
			sendCommand(getClass(), address, "setFreqAM", KnownCommand.SetTuner.SetFreqAM, String.format(Locale.ENGLISH, "%1.2f->%s", value, nwu), nwu.createXML());
		}

		public void setFreqFM(float value) {
			// PUT[P8]:    Tuner,Play_Control,Tuning,Freq,FM  =  Number: 8750..(5)..10800 / Exp:2 / Unit:"MHz"
			int number = Math.round(value*20)*5;
			number = Math.max(8750, Math.min(number, 10800));
			NumberWithUnit nwu = new NumberWithUnit(number, 2, "MHz");
			if (playInfo.tuningFreqFmValue!=null) {
				if (number==playInfo.tuningFreqFmValue.number) return;
				playInfo.tuningFreqFmValue = nwu;
			}
			sendCommand(getClass(), address, "setFreqFM", KnownCommand.SetTuner.SetFreqFM, String.format(Locale.ENGLISH, "%1.4f->%s", value, nwu), nwu.createXML());
		}

		public void setPreset(Value.UpDown value) {
			// [Plus_1]        PUT[P2]     Tuner,Play_Control,Preset,Preset_Sel = Up
			// [Minus_1]       PUT[P2]     Tuner,Play_Control,Preset,Preset_Sel = Down
			if (value!=null)
				sendCommand(getClass(), address, "setPreset", KnownCommand.Presets.SetTunerSelectPreset, value.getLabel());
		}

		public void setPreset(PlayInfo_Tuner.Preset preset) {
			// PUT[P2]:    Tuner,Play_Control,Preset,Preset_Sel   =   Values [GET[G3]:Tuner,Play_Control,Preset,Preset_Sel_Item]
			if (preset!=null)
				sendCommand(getClass(), address, "setPreset", KnownCommand.Presets.SetTunerSelectPreset, preset.ID);
			//new Throwable().printStackTrace();
		}
	
		static class Config extends AbstractConfig {
	
			Value.ReadyOrNot deviceStatus;
			String RDS;
			BandRange FM;
			BandRange AM;
	
			public Config(String address, KnownCommand.Config getConfigCmd) {
				super(address, getConfigCmd);
				clearValues();
			}

			private void clearValues() {
				deviceStatus = null;
				RDS = null;
				FM = null;
				AM = null;
			}
	
			@Override
			protected void parse(Node node) {
				clearValues();
				XML.forEachChild(node, child->{
					switch (child.getNodeName()) {
					case "Feature_Availability":
						// GET[G2]:    Tuner,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
						deviceStatus =  XML.getSubValue(child, Value.ReadyOrNot.values());
						break;
						
					case "RDS":
						RDS =  XML.getSubValue(child);
						break;
						
					case "Range_and_Step":
						XML.forEachChild(child, band->{
							switch (child.getNodeName()) {
							case "FM": FM = (BandRange)BandRange.parse(child); break;
							case "AM": AM = (BandRange)BandRange.parse(child); break;
							}
						});
						break;
					}
				});
			}
			
			static class BandRange {
	
				NumberWithUnit min;
				NumberWithUnit max;
				NumberWithUnit step;
	
				public BandRange() {
					this.min = null;
					this.max = null;
					this.step = null;
				}
	
				private static BandRange parse(Node node) {
					BandRange bandRange = new BandRange();
					XML.forEachChild(node, child->{
						switch (child.getNodeName()) {
						case "Min" : bandRange.min  = NumberWithUnit.parse(child); break;
						case "Max" : bandRange.max  = NumberWithUnit.parse(child); break;
						case "Step": bandRange.step = NumberWithUnit.parse(child); break;
						}
					});
					return bandRange;
				}
				
			}
		}
	}

	static class ListInfo {
	
		Value.ReadyOrBusy menuStatus;
		Integer menuLayer;
		String  menuName;
		Integer currentLine;
		Integer maxLine;
		Vector<Line> lines;
		
		private String address;
		private KnownCommand.GetListInfo   getListInfoCmd;
		private KnownCommand.SetDirectSel  setDirectSelCmd;
		private KnownCommand.SetCursorSel  setCursorSelCmd;
		private KnownCommand.SetPageSel    setPageSelCmd;
		private KnownCommand.JumpToLine    setJumpToLineCmd;
	
		public ListInfo(String address, KnownCommand.GetListInfo getListInfoCmd, KnownCommand.SetDirectSel setDirectSelCmd, KnownCommand.SetCursorSel setCursorSelCmd, KnownCommand.SetPageSel setPageSelCmd, KnownCommand.JumpToLine setJumpToLineCmd) {
			this.address = address;
			this.getListInfoCmd   = getListInfoCmd;
			this.setDirectSelCmd  = setDirectSelCmd;
			this.setCursorSelCmd  = setCursorSelCmd;
			this.setPageSelCmd    = setPageSelCmd;
			this.setJumpToLineCmd = setJumpToLineCmd;
			clearValues();
		}
		
		private void clearValues() {
			this.menuStatus = null;
			this.menuLayer = null;
			this.menuName = null;
			this.currentLine = null;
			this.maxLine = null;
			this.lines = new Vector<>();
		}
		
		public void sendDirectSelect(ListInfo.Line line) {
			sendDirectSelect(line.index);
		}
		
		public void sendDirectSelect(int lineIndex) {
			if (setDirectSelCmd==null) throw new UnsupportedOperationException("DirectSelect not supported");
			// PUT:    #######,List_Control,Direct_Sel   =   Label: Line_% (1..8)
			if (lineIndex>0) {
				Device.sendDirectSelect(getClass(),address,setDirectSelCmd, "Line_"+lineIndex);
			}
		}
		
		public void sendJumpToLine(int lineNumber) {
			if (setJumpToLineCmd==null) throw new UnsupportedOperationException("JumpToLine not supported");
			// PUT:    #######,List_Control,Jump_Line   =   Number: 1..65536
			Device.sendJumpToLine(getClass(),address,setJumpToLineCmd, lineNumber);
		}
		
		public void sendCursorSelect(Value.CursorSelect cursorSelect) {
			if (setCursorSelCmd==null) throw new UnsupportedOperationException("CursorSelect not supported");
			// [Cursor_Up]      #######,List_Control,Cursor = Up
			// [Cursor_Down]    #######,List_Control,Cursor = Down
			// [Cursor_Left]    #######,List_Control,Cursor = Return
			// [Cursor_Sel]     #######,List_Control,Cursor = Sel
			// [Cursor_Home]    #######,List_Control,Cursor = Return to Home
			Device.sendCursorSelect(getClass(),address,setCursorSelCmd, cursorSelect);
		}
		
		public void sendPageSelect(Value.PageSelect pageSelect) {
			if (setPageSelCmd==null) throw new UnsupportedOperationException("PageSelect not supported");
			// [Page_Up_1]      #######,List_Control,Page = Up
			// [Page_Down_1]    #######,List_Control,Page = Down
			Device.sendPageSelect(getClass(),address,setPageSelCmd, pageSelect);
		}
	
		
		public synchronized void update() {
			parse(Ctrl.sendGetCommand_Node(address,getListInfoCmd));
		}
	
		private synchronized void parse(Node node) {
			clearValues();
			XML.forEachChild(node, child->{
				switch (child.getNodeName()) {
				case "Menu_Status":
					// GET:    #######,List_Info   ->   Menu_Status -> "Ready" | "Busy"
					menuStatus =  XML.getSubValue(child, Value.ReadyOrBusy.values());
					break;
					
				case "Menu_Layer":
					// GET:    #######,List_Info   ->   Menu_Layer -> Number: 1..16
					menuLayer =  XML.getSubValue_Int(child);
					break;
					
				case "Menu_Name":
					// GET:    #######,List_Info   ->   Menu_Name -> Text: 0..128 (UTF-8)
					menuName =  XML.getSubValue(child);
					break;
					
				case "Current_List":
					// GET:    #######,List_Info
					// Value 0:   Current_List,Line_1,Txt -> Text: 0..128 (UTF-8)
					// Value 1:   Current_List,Line_1,Attribute -> "Container" | "Unplayable Item" | "Item" | "Unselectable"
					lines.clear();;
					XML.forEachChild(child, line->{
						String nodeName = line.getNodeName();
						if (nodeName==null || !nodeName.startsWith("Line_")) return;
						
						int index;
						try { index = Integer.parseInt(nodeName.substring("Line_".length())); }
						catch (NumberFormatException e) { index=-1; }
						String txt = XML.getSubValue(line,"Txt");
						Value.LineAttribute attr = XML.getSubValue(line,Value.LineAttribute.values(), "Attribute");
						lines.add(new Line(index,txt,attr));
					});
					lines.sort(Comparator.nullsLast(Comparator.comparing(line->line.index)));
					break;
					
				case "Cursor_Position":
					// GET:    #######,List_Info   ->   Cursor_Position,Current_Line -> Number: 1..65536
					// GET:    #######,List_Info   ->   Cursor_Position,Max_Line -> Number: 0..65536
					currentLine =  XML.getSubValue_Int(child,"Current_Line");
					maxLine =  XML.getSubValue_Int(child,"Max_Line");
					break;
					
				default:
					System.out.println(child.getNodeName());
					break;
				}
			});
		}
		
		static class Line {
	
			int index;
			String txt;
			Value.LineAttribute attr;
	
			public Line(int index, String txt, Value.LineAttribute attr) {
				this.index = index;
				this.txt = txt;
				this.attr = attr;
			}}
	}

	static abstract class AbstractConfig {
		
		private String address;
		private KnownCommand.Config getConfigCmd;
		
		protected AbstractConfig(String address, KnownCommand.Config getConfigCmd) {
			this.address = address;
			this.getConfigCmd = getConfigCmd;
		}
		public void update() {
			parse(Ctrl.sendGetCommand_Node(address,getConfigCmd));
		}
		protected abstract void parse(Node node);
	}

	static abstract class PlayInfo {
		protected String name;
		protected String address;
		private KnownCommand.GetPlayInfo getPlayInfoCmd;
		
		protected PlayInfo(String name, String address, KnownCommand.GetPlayInfo getPlayInfoCmd) {
			this.name = name;
			this.address = address;
			this.getPlayInfoCmd = getPlayInfoCmd;
		}
		public void update() {
			parse(Ctrl.sendGetCommand_Node(address,getPlayInfoCmd));
		}
		protected abstract void parse(Node node);
		
		protected String convertUTF8(String str, boolean active) {
			return convert(str, active, StandardCharsets.UTF_8);
		}
		protected String convertLatin1(String str, boolean active) {
			return convert(str, active, StandardCharsets.ISO_8859_1);
		}
		protected String convert(String str, boolean active, Charset charset) {
			if (!active) return str;
			return charset.decode(ByteBuffer.wrap(str.getBytes())).toString();
		}
		
		public abstract String toString(boolean withExtraCharsetConversion);
		@Override public String toString() {
			return toString(false);
		}
	}

	static class PlayInfo_Tuner extends PlayInfo {

		Value.ReadyOrNot deviceStatus;
		
		String searchMode;
		Value.NegateAssert signalTuned;
		Value.NegateAssert signalStereo;
		String fmMode;
		String currentPreset;
		
		Value.AmFm tuningBand;
		Value.FreqAutoTP tuningFreqCurrentAutomatic;
		Value.FreqAutoTP tuningFreqFmAutomatic;
		Value.ScanFreq   tuningFreqAmAutomatic;
		NumberWithUnit tuningFreqCurrentValue;
		NumberWithUnit tuningFreqFmValue;
		NumberWithUnit tuningFreqAmValue;
		
		String programType;
		String programService;
		String radioTextA;
		String radioTextB;
		String clockTime;

		private KnownCommand getPresetsCmd;
		final Vector<Preset> presets;

		protected PlayInfo_Tuner(String name, String address, KnownCommand.GetPlayInfo getPlayInfoCmd, KnownCommand getPresetsCmd) {
			super(name, address, getPlayInfoCmd);
			this.getPresetsCmd = getPresetsCmd;
			this.presets = new Vector<>();
			clearValues();
		}

		private void clearValues() {
			this.deviceStatus = null;
			this.searchMode = null;
			this.signalTuned = null;
			this.signalStereo = null;
			this.fmMode = null;
			this.currentPreset = null;
			this.tuningBand = null;
			this.tuningFreqCurrentAutomatic = null;
			this.tuningFreqFmAutomatic = null;
			this.tuningFreqAmAutomatic = null;
			this.tuningFreqCurrentValue = null;
			this.tuningFreqFmValue = null;
			this.tuningFreqAmValue = null;
			this.programType = null;
			this.programService = null;
			this.radioTextA = null;
			this.radioTextB = null;
			this.clockTime = null;
		}

		@Override
		public String toString(boolean withExtraCharsetConversion) {
			StringBuilder sb = new StringBuilder();
			sb.append(name+": ").append(deviceStatus==null?"???":deviceStatus.getLabel()).append("\r\n");
			sb.append("   SearchMode: ").append(searchMode   ==null?"":("\""+searchMode   +"\"")).append("\r\n");
			sb.append("   FM Mode   : ").append(fmMode       ==null?"":("\""+fmMode       +"\"")).append("\r\n");
			sb.append("   Preset    : ").append(currentPreset==null?"":("\""+currentPreset+"\""));
			Preset preset = getPreset(currentPreset);
			if (preset!=null) sb.append("  (").append(preset.rw).append(",\"").append(preset.title==null?"":preset.title).append("\")");
			sb.append("\r\n");
			sb.append("   Signal    : ");
			if (signalTuned !=null) sb.append((signalTuned ==Value.NegateAssert.Negate?"not":"")+" tuned");
			if (signalStereo!=null && signalTuned !=null) sb.append(" & "); 
			if (signalStereo!=null) sb.append( signalStereo==Value.NegateAssert.Negate?"mono":"stereo");
			sb.append("\r\n");
			sb.append("\r\n");
			
			sb.append("Program Info\r\n");
			sb.append("   Genre  : ").append(programType   ==null?"":(" \""+programType   +"\"")).append("\r\n");
			sb.append("   Service: ").append(programService==null?"":(" \""+convertLatin1(programService,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append("   Text   : ").append(radioTextA    ==null?"":(" \""+convertLatin1(radioTextA    ,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append("          : ").append(radioTextB    ==null?"":(" \""+convertLatin1(radioTextB    ,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append("   Time   : ").append(clockTime     ==null?"":(" \""+clockTime     +"\"")).append("\r\n");
			sb.append("\r\n");
			
			sb.append("Tuning\r\n");
			sb.append("   Band: ").append(tuningBand==null?"":tuningBand).append("\r\n");
			sb.append("   Frequency\r\n");
			sb.append("      Current: ").append(tuningFreqCurrentAutomatic!=null?tuningFreqCurrentAutomatic:tuningFreqCurrentValue==null?"":tuningFreqCurrentValue.toValueStr()).append("\r\n");
			sb.append("      FM     : ").append(tuningFreqFmAutomatic     !=null?tuningFreqFmAutomatic     :tuningFreqFmValue     ==null?"":tuningFreqFmValue     .toValueStr()).append("\r\n");
			sb.append("      AM     : ").append(tuningFreqAmAutomatic     !=null?tuningFreqAmAutomatic     :tuningFreqAmValue     ==null?"":tuningFreqAmValue     .toValueStr()).append("\r\n");
			sb.append("\r\n");
			
			return sb.toString();
		}

		@Override
		protected void parse(Node node) {
			clearValues();
			XML.forEachChild(node, child->{
				switch (child.getNodeName()) {
				case "Feature_Availability":
					deviceStatus = XML.getSubValue(child, Value.ReadyOrNot.values());
					break;
					
				case "Search_Mode":
					searchMode =  XML.getSubValue(child);
					break;
					
				case "Preset":
					// GET[G1]:    Tuner,Play_Info   ->   Preset,Preset_Sel -> Values [GET[G3]:Tuner,Play_Control,Preset,Preset_Sel_Item]
					currentPreset = XML.getSubValue(child, "Preset_Sel");
					break;
					
				case "Tuning":
					XML.forEachChild(child, tuning->{
						switch (tuning.getNodeName()) {
						case "Band":
							// GET[G1]:    Tuner,Play_Info   ->   Tuning,Band -> "AM" | "FM"
							tuningBand = XML.getSubValue(tuning, Value.AmFm.values());
							break;
							
						case "Freq":
							XML.forEachChild(tuning, freq->{
								switch (freq.getNodeName()) {
								case "Current":
									// GET[G1]:    Tuner,Play_Info
									// Value 0:   Tuning,Freq,Current,Val -> Number: 531..(9)..1611 | Number: 8750..(5)..10800 | "Auto Up" | "Auto Down" | "TP Up" | "TP Down"
									// Value 1:   Tuning,Freq,Current,Exp -> "0" | "2" | <no value> | <no value> | <no value> | <no value>
									// Value 2:   Tuning,Freq,Current,Unit -> "kHz" | "MHz" | <no value> | <no value> | <no value> | <no value>
									tuningFreqCurrentAutomatic = XML.getSubValue(freq, Value.FreqAutoTP.values(), "Val");
									if (tuningFreqCurrentAutomatic==null)
										tuningFreqCurrentValue = NumberWithUnit.parse(freq);
									break;
									
								case "FM":
									// GET[G1]:    Tuner,Play_Info
									// Value 0:   Tuning,Freq,FM,Val -> Number: 8750..(5)..10800 | "Auto Up" | "Auto Down" | "TP Up" | "TP Down"
									// Value 1:   Tuning,Freq,FM,Exp -> "2" | <no value> | <no value> | <no value> | <no value>
									// Value 2:   Tuning,Freq,FM,Unit -> "MHz" | <no value> | <no value> | <no value> | <no value>
									tuningFreqFmAutomatic = XML.getSubValue(freq, Value.FreqAutoTP.values(), "Val");
									if (tuningFreqFmAutomatic==null)
										tuningFreqFmValue = NumberWithUnit.parse(freq);
									break;
									
								case "AM":
									// GET[G1]:    Tuner,Play_Info
									// Value 0:   Tuning,Freq,AM,Val -> Number: 531..(9)..1611 | "Auto Up" | "Auto Down"
									// Value 1:   Tuning,Freq,AM,Exp -> "0" | <no value> | <no value>
									// Value 2:   Tuning,Freq,AM,Unit -> "kHz" | <no value> | <no value>
									tuningFreqAmAutomatic = XML.getSubValue(freq, Value.ScanFreq.values(), "Val");
									if (tuningFreqAmAutomatic==null)
										tuningFreqAmValue = NumberWithUnit.parse(freq);
									break;
								}
							});
							break;
						}
					});
					break;
					
				case "FM_Mode":
					fmMode =  XML.getSubValue(child);
					break;
					
				case "Signal_Info":
					// GET[G1]:    Tuner,Play_Info   ->   Signal_Info,Tuned -> "Negate" | "Assert"
					// GET[G1]:    Tuner,Play_Info   ->   Signal_Info,Stereo -> "Negate" | "Assert"
					signalTuned  =  XML.getSubValue(child, Value.NegateAssert.values(), "Tuned"); 
					signalStereo =  XML.getSubValue(child, Value.NegateAssert.values(), "Stereo"); 
					break;
					
				case "Meta_Info":
					// GET[G1]:    Tuner,Play_Info   ->   Meta_Info,Program_Type -> Text: 0..8 (Ascii)
					// GET[G1]:    Tuner,Play_Info   ->   Meta_Info,Program_Service -> Text: 0..8 (Latin-1)
					// GET[G1]:    Tuner,Play_Info   ->   Meta_Info,Radio_Text_A -> Text: 0..64 (Latin-1)
					// GET[G1]:    Tuner,Play_Info   ->   Meta_Info,Radio_Text_B -> Text: 0..64 (Latin-1)
					// GET[G1]:    Tuner,Play_Info   ->   Meta_Info,Clock_Time -> Text: 0..5 (Ascii)
					programType    =  XML.getSubValue(child, "Program_Type"   ); // Program Type   [Genre]   
					programService =  XML.getSubValue(child, "Program_Service");
					radioTextA     =  XML.getSubValue(child, "Radio_Text_A"   ); 
					radioTextB     =  XML.getSubValue(child, "Radio_Text_B"   ); 
					clockTime      =  XML.getSubValue(child, "Clock_Time"     ); 
					break;
				}
			});
		}

		void updatePresets() {
			presets.clear();
			Node node = Ctrl.sendGetCommand_Node(address,getPresetsCmd);
			if (node==null) return;
			XML.forEachChild(node, child->{
				String nodeName = child.getNodeName();
				if (nodeName==null) return;
				if (!nodeName.startsWith("Item_")) return;
				int index;
				try { index = Integer.parseInt(nodeName.substring("Item_".length())); }
				catch (NumberFormatException e) { return; }
				Preset preset = Preset.parse(child,index);
				if (preset!=null) presets.add(preset);
			});
		}
		
		Preset getCurrentPreset() {
			return getPreset(currentPreset);
		}
		
		private Preset getPreset(String ID) {
			if (ID==null) return null;
			for (Preset p:presets)
				if (ID.equals(p.ID))
					return p;
			return null;
		}
		
		static class Preset {

			String ID;
			String rw;
			String title;

			public Preset() {
				this.ID = null;
				this.rw = null;
				this.title = null;
			}

			public static Preset parse(Node node, int index) {
				Preset preset = new Preset();
				XML.forEachChild(node, child->{
					switch (child.getNodeName()) {
					case "Param": preset.ID    = XML.getSubValue(child); break;
					case "RW"   : preset.rw    = XML.getSubValue(child); break;
					case "Title": preset.title = XML.getSubValue(child); break;
					}
					
				});
				return preset;
			}

			@Override
			public String toString() {
				return title==null?("["+ID+"]"):title;
			}
		}
	}

	static class PlayInfo_NetRadio extends PlayInfo implements SubUnits.PlayInfo_PlayStop {
	
		Value.ReadyOrNot deviceStatus;
		Value.PlayStop playState;
		String currentStation;
		String currentAlbum;
		String currentSong;
		Integer albumCoverID;
		String albumCoverURL;
		Value.AlbumCoverFormat albumCoverFormat;
		private KnownCommand.SetPlayback setPlayback;
	
		public PlayInfo_NetRadio(String name, String address, KnownCommand.GetPlayInfo getPlayInfoCmd, KnownCommand.SetPlayback setPlayback) {
			super(name, address, getPlayInfoCmd);
			this.setPlayback = setPlayback;
			clearValues();
		}
	
		private void clearValues() {
			this.deviceStatus = null;
			this.playState = null;
			this.currentStation = null;
			this.currentAlbum = null;
			this.currentSong = null;
			this.albumCoverID = null;
			this.albumCoverURL = null;
			this.albumCoverFormat = null;
		}
		
		@Override public Value.PlayStop getPlayState() { return playState; }

		@Override public void sendPlayback(Value.PlayStop playState) {
			// [Play]    Visible:No     PUT[P1]     NET_RADIO,Play_Control,Playback = Play
			// [Stop]    Playable:No     PUT[P1]     NET_RADIO,Play_Control,Playback = Stop
			Device.sendPlayback(getClass(), address,setPlayback, playState);
		}
	
		@Override
		public String toString(boolean withExtraCharsetConversion) {
			StringBuilder sb = new StringBuilder();
			sb.append(name+": ").append(deviceStatus==null?"???":deviceStatus.getLabel());
			if (playState==null)
				sb.append("\r\n");
			else
				switch (playState) {
				case Play: sb.append(" & is playing\r\n"); break;
				case Stop: sb.append(" & has stopped\r\n"); break;
				}
			sb.append("\r\n");
			
			sb.append("Station: ").append(currentStation==null?"":("\""+convertUTF8(currentStation,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append("  Album: ").append(currentAlbum  ==null?"":("\""+convertUTF8(currentAlbum  ,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append("   Song: ").append(currentSong   ==null?"":("\""+convertUTF8(currentSong   ,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append("\r\n");
			
			sb.append("AlbumCover:\r\n");
			sb.append("   ");
			sb.append(albumCoverID==null?"":(" "+albumCoverID));
			sb.append(albumCoverFormat==null?"":(" "+albumCoverFormat.getLabel()));
			sb.append("\r\n");
			sb.append("   ");
			sb.append(albumCoverURL==null?"":(" \""+convertUTF8(albumCoverURL,withExtraCharsetConversion)+"\""));
			sb.append("\r\n");
			
			return sb.toString();
		}
		
		@Override
		protected void parse(Node node) {
			clearValues();
			XML.forEachChild(node, child->{
				switch (child.getNodeName()) {
				case "Feature_Availability":
					// GET[G1]:    NET_RADIO,Play_Info   ->   Feature_Availability -> "Ready" | "Not Ready"
					deviceStatus =  XML.getSubValue(child, Value.ReadyOrNot.values());
					break;
					
				case "Playback_Info":
					// GET[G1]:    NET_RADIO,Play_Info   ->   Playback_Info -> "Play" | "Stop"
					playState =  XML.getSubValue(child, Value.PlayStop.values());
					break;
					
				case "Meta_Info":
					// GET[G1]:    NET_RADIO,Play_Info   ->   Meta_Info,Station -> Text: 0..128 (UTF-8)
					// GET[G1]:    NET_RADIO,Play_Info   ->   Meta_Info,Album -> Text: 0..128 (UTF-8)
					// GET[G1]:    NET_RADIO,Play_Info   ->   Meta_Info,Song -> Text: 0..128 (UTF-8)
					currentStation =  XML.getSubValue(child, "Station"); 
					currentAlbum   =  XML.getSubValue(child, "Album"); 
					currentSong    =  XML.getSubValue(child, "Song"); 
					break;
					
				case "Album_ART":
					// GET[G1]:    NET_RADIO,Play_Info   ->   Album_ART,URL -> Text: 0..128 (UTF-8)
					// GET[G1]:    NET_RADIO,Play_Info   ->   Album_ART,ID -> Number: 0..255
					// GET[G1]:    NET_RADIO,Play_Info   ->   Album_ART,Format -> "BMP" | "YMF"
					albumCoverID     =  XML.getSubValue_Int(child, "ID"); 
					albumCoverURL    =  XML.getSubValue(child, "URL"); 
					albumCoverFormat =  XML.getSubValue(child, Value.AlbumCoverFormat.values(), "Format"); 
					break;
				}
			});
		}
		
	}

	static class PlayInfo_AirPlaySpotify extends PlayInfo implements SubUnits.PlayInfo_PlayPauseStopSkip {
		
		Value.ReadyOrNot deviceStatus;
		Value.PlayPauseStop playState;
		String currentArtist;
		String currentAlbum;
		String currentSong;
		String inputLogoURL_S;
		String inputLogoURL_M;
		String inputLogoURL_L;
		
		private KnownCommand.SetPlayback setPlaybackCmd;
		public PlayInfo_AirPlaySpotify(String name, String address, KnownCommand.GetPlayInfo getPlayInfoCmd, KnownCommand.SetPlayback setPlaybackCmd) {
			super(name, address, getPlayInfoCmd);
			this.setPlaybackCmd = setPlaybackCmd;
			clearValues();
		}
		
		private void clearValues() {
			this.deviceStatus = null;
			this.playState = null;
			this.currentArtist = null;
			this.currentAlbum = null;
			this.currentSong = null;
			this.inputLogoURL_S = null;
			this.inputLogoURL_M = null;
			this.inputLogoURL_L = null;
		}
		
		@Override public Value.PlayPauseStop getPlayState() { return playState; }
		
		@Override public void sendPlayback(Value.PlayPauseStop playState) {
			// [Play]      #######,Play_Control,Playback = Play
			// [Pause]     #######,Play_Control,Playback = Pause
			// [Stop]      #######,Play_Control,Playback = Stop
			Device.sendPlayback(getClass(),address,setPlaybackCmd,playState);
		}
		
		@Override public void sendPlayback(Value.SkipFwdRev skip) {
			// [Plus_1]    #######,Play_Control,Playback = Skip Fwd
			// [Minus_1]   #######,Play_Control,Playback = Skip Rev
			Device.sendPlayback(getClass(),address,setPlaybackCmd,skip);
		}

		@Override
		public String toString(boolean withExtraCharsetConversion) {
			StringBuilder sb = new StringBuilder();
			sb.append(name+": ").append(deviceStatus==null?"???":deviceStatus.getLabel());
			if (playState==null)
				sb.append("\r\n");
			else
				switch (playState) {
				case Play : sb.append(" & is playing\r\n"); break;
				case Pause: sb.append(" & was paused\r\n"); break;
				case Stop : sb.append(" & was stopped\r\n"); break;
				}
			sb.append("\r\n");
			
			sb.append("Artist: ").append(currentArtist==null?"":("\""+convertUTF8(currentArtist,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append(" Album: ").append(currentAlbum ==null?"":("\""+convertUTF8(currentAlbum ,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append("  Song: ").append(currentSong  ==null?"":("\""+convertUTF8(currentSong  ,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append("\r\n");
			
			sb.append("Input Logo:\r\n");
			if (inputLogoURL_S!=null) sb.append("    [S] "+convertUTF8(inputLogoURL_S,withExtraCharsetConversion)+"\r\n");
			if (inputLogoURL_M!=null) sb.append("    [M] "+convertUTF8(inputLogoURL_M,withExtraCharsetConversion)+"\r\n");
			if (inputLogoURL_L!=null) sb.append("    [L] "+convertUTF8(inputLogoURL_L,withExtraCharsetConversion)+"\r\n");
			
			return sb.toString();
		}
	
		@Override
		protected void parse(Node node) {
			clearValues();
			XML.forEachChild(node, child->{
				switch (child.getNodeName()) {
				case "Feature_Availability":
					// GET:    #######,Play_Info   ->   Feature_Availability -> "Ready" | "Not Ready"
					deviceStatus = XML.getSubValue(child, Value.ReadyOrNot.values());
					break;
					
				case "Playback_Info":
					// GET:    #######,Play_Info   ->   Playback_Info -> "Play" | "Pause" | "Stop"
					playState = XML.getSubValue(child, Value.PlayPauseStop.values());
					break;
					
				case "Meta_Info":
					// GET[G1]:    AirPlay,Play_Info   ->   Meta_Info,Artist -> Text: 0..128 (UTF-8)
					// GET[G1]:    Spotify,Play_Info   ->   Meta_Info,Artist -> Text: 0..128 (UTF-8)
					// GET[G1]:    AirPlay,Play_Info   ->   Meta_Info,Album -> Text: 0..128 (UTF-8)
					// GET[G1]:    Spotify,Play_Info   ->   Meta_Info,Album -> Text: 0..128 (UTF-8)
					// GET[G1]:    AirPlay,Play_Info   ->   Meta_Info,Song -> Text: 0..128 (UTF-8)
					// GET[G1]:    Spotify,Play_Info   ->   Meta_Info,Track -> Text: 0..128 (UTF-8)
					currentArtist = XML.getSubValue(child, "Artist"); 
					currentAlbum  = XML.getSubValue(child, "Album");
					if (XML.hasChild(child,"Song" )) currentSong = XML.getSubValue(child,"Song" );
					if (XML.hasChild(child,"Track")) currentSong = XML.getSubValue(child,"Track"); 
					break;
					
				case "Input_Logo":
					// GET:    #######,Play_Info   ->   Input_Logo,URL_S -> Text: 0..128 (UTF-8)
					// GET:    #######,Play_Info   ->   Input_Logo,URL_M -> Text: 0..128 (UTF-8)
					// GET:    #######,Play_Info   ->   Input_Logo,URL_L -> Text: 0..128 (UTF-8)
					inputLogoURL_S = XML.getSubValue(child, "URL_S"); 
					inputLogoURL_M = XML.getSubValue(child, "URL_M"); 
					inputLogoURL_L = XML.getSubValue(child, "URL_L"); 
					break;
				}
			});
		}
	}

	static class PlayInfoExt<Shuffle extends Enum<Shuffle>&Value> extends PlayInfo implements SubUnits.PlayInfo_PlayPauseStopSkip, SubUnits.PlayInfo_RepeatShuffle<Shuffle> {
	
		Value.ReadyOrNot deviceStatus;
		Value.PlayPauseStop playState;
		Value.OffOneAll repeat;
		Shuffle shuffle;
		String currentArtist;
		String currentAlbum;
		String currentSong;
		String albumCoverURL;
		Integer albumCoverID;
		Value.AlbumCoverFormat albumCoverFormat;
		
		private KnownCommand.SetPlayback setPlaybackCmd;
		private KnownCommand.SetRepeat   setRepeatCmd;
		private KnownCommand.SetShuffle  setShuffleCmd;
		private Shuffle[] shuffleValues;
	
		public PlayInfoExt(String name, String address, KnownCommand.GetPlayInfo getPlayInfoCmd, KnownCommand.SetPlayback setPlaybackCmd, KnownCommand.SetRepeat setRepeatCmd, KnownCommand.SetShuffle setShuffleCmd, Shuffle[] shuffleValues) {
			super(name, address, getPlayInfoCmd);
			this.setPlaybackCmd = setPlaybackCmd;
			this.setRepeatCmd   = setRepeatCmd;
			this.setShuffleCmd  = setShuffleCmd;
			this.shuffleValues  = shuffleValues;
			clearValues();
		}
	
		private void clearValues() {
			this.deviceStatus = null;
			this.playState = null;
			this.repeat = null;
			this.shuffle = null;
			this.currentArtist = null;
			this.currentAlbum = null;
			this.currentSong = null;
			this.albumCoverID = null;
			this.albumCoverURL = null;
			this.albumCoverFormat = null;
		}
	
		@Override public Value.PlayPauseStop getPlayState() { return playState; }
		@Override public Value.OffOneAll     getRepeat   () { return repeat; }
		@Override public Shuffle             getShuffle  () { return shuffle; }
		
		@Override public void sendPlayback(Value.PlayPauseStop playState) {
			// [Play]      #######,Play_Control,Playback = Play
			// [Pause]     #######,Play_Control,Playback = Pause
			// [Stop]      #######,Play_Control,Playback = Stop
			Device.sendPlayback(getClass(),address,setPlaybackCmd,playState);
		}
		
		@Override public void sendPlayback(Value.SkipFwdRev skip) {
			// [Plus_1]    #######,Play_Control,Playback = Skip Fwd
			// [Minus_1]   #######,Play_Control,Playback = Skip Rev
			Device.sendPlayback(getClass(),address,setPlaybackCmd,skip);
		}
		
		@Override public void sendRepeat(Value.OffOneAll repeatState) {
			// [Rep_Off]   #######,Play_Control,Play_Mode,Repeat = Off
			// [Rep_1]     #######,Play_Control,Play_Mode,Repeat = One
			// [Rep_2]     #######,Play_Control,Play_Mode,Repeat = All
			Device.sendRepeat(getClass(),address,setRepeatCmd,repeatState);
		}
		
		@Override public void sendShuffle(Shuffle shuffleState) {
			Device.sendShuffle(getClass(),address,setShuffleCmd,shuffleState);
		}

		@Override
		public String toString(boolean withExtraCharsetConversion) {
			StringBuilder sb = new StringBuilder();
			sb.append(name+": ").append(deviceStatus==null?"???":deviceStatus.getLabel());
			if (playState==null)
				sb.append("\r\n");
			else
				switch (playState) {
				case Play : sb.append(" & is playing\r\n"); break;
				case Pause: sb.append(" & was paused\r\n"); break;
				case Stop : sb.append(" & was stopped\r\n"); break;
				}
			sb.append("   Repeat : ").append(repeat ==null?"???":repeat .getLabel()).append("\r\n");;
			sb.append("   Shuffle: ").append(shuffle==null?"???":shuffle.getLabel()).append("\r\n");;
			sb.append("\r\n");
			
			sb.append("Artist: ").append(currentArtist==null?"":("\""+convertUTF8(currentArtist,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append(" Album: ").append(currentAlbum ==null?"":("\""+convertUTF8(currentAlbum ,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append("  Song: ").append(currentSong  ==null?"":("\""+convertUTF8(currentSong  ,withExtraCharsetConversion)+"\"")).append("\r\n");
			sb.append("\r\n");
			
			sb.append("AlbumCover:\r\n");
			sb.append("   ");
			sb.append(albumCoverID==null?"":(" "+albumCoverID));
			sb.append(albumCoverFormat==null?"":(" "+albumCoverFormat.getLabel()));
			sb.append("\r\n");
			sb.append("   ");
			sb.append(albumCoverURL==null?"":(" \""+convertUTF8(albumCoverURL,withExtraCharsetConversion)+"\""));
			sb.append("\r\n");
			
			return sb.toString();
		}
	
		@Override
		protected void parse(Node node) {
			clearValues();
			XML.forEachChild(node, child->{
				switch (child.getNodeName()) {
				case "Feature_Availability":
					// GET:    #######,Play_Info   ->   Feature_Availability -> "Ready" | "Not Ready"
					deviceStatus =  XML.getSubValue(child, Value.ReadyOrNot.values());
					break;
					
				case "Playback_Info":
					// GET:    #######,Play_Info   ->   Playback_Info -> "Play" | "Pause" | "Stop"
					playState =  XML.getSubValue(child, Value.PlayPauseStop.values());
					break;
					
				case "Play_Mode":
					// GET:    #######,Play_Info   ->   Play_Mode,Repeat -> "Off" | "One" | "All"
					// GET:    #######,Play_Info   ->   Play_Mode,Shuffle -> "Off" | "On"
					repeat  =  XML.getSubValue(child, Value.OffOneAll.values(), "Repeat");
					shuffle =  XML.getSubValue(child, shuffleValues, "Shuffle");
					break;
					
				case "Meta_Info":
					// GET:    #######,Play_Info   ->   Meta_Info,Artist -> Text: 0..64 (UTF-8)
					// GET:    #######,Play_Info   ->   Meta_Info,Album -> Text: 0..64 (UTF-8)
					// GET:    #######,Play_Info   ->   Meta_Info,Song -> Text: 0..64 (UTF-8)
					currentArtist =  XML.getSubValue(child, "Artist"); 
					currentAlbum  =  XML.getSubValue(child, "Album"); 
					currentSong   =  XML.getSubValue(child, "Song"); 
					break;
					
				case "Album_ART":
					// GET:    #######,Play_Info   ->   Album_ART,URL -> Text: 0..128 (UTF-8)
					// GET:    #######,Play_Info   ->   Album_ART,ID -> Number: 0..255
					// GET:    #######,Play_Info   ->   Album_ART,Format -> "BMP" | "YMF"
					albumCoverURL    =  XML.getSubValue(child, "URL"); 
					albumCoverID     =  XML.getSubValue_Int(child, "ID"); 
					albumCoverFormat =  XML.getSubValue(child, Value.AlbumCoverFormat.values(), "Format"); 
					break;
				}
			});
		}
		
	}
	
//	private static boolean debug_verbose = true;
//	private static Log.Type logType = Log.Type.INFO;
	private static boolean debug_verbose = false;
	private static Log.Type logType = Log.Type.ERROR;
	
	private static void sendSetBand(Class<?> callerClass, String address, KnownCommand cmd, Value.AmFm value) {
		// [Band_AM]        PUT[P3]     Tuner,Play_Control,Tuning,Band = AM
		// [Band_FM]        PUT[P3]     Tuner,Play_Control,Tuning,Band = FM
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendSetBand( %s ): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendCommand(Class<?> callerClass, String address, String function, KnownCommand cmd, String valueSourceStr, String xmlStr) {
		int rc = Ctrl.sendPutCommand(address, cmd, xmlStr);
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "%s( %s->%s ): %s -> RC: %s", function, valueSourceStr, xmlStr, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendCommand(Class<?> callerClass, String address, String function, KnownCommand cmd, String value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value);
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "%s( %s ): %s -> RC: %s", function, value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendCommand(Class<?> callerClass, String address, String function, KnownCommand cmd, Value value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "%s( %s ): %s -> RC: %s", function, value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendMenuControl(Class<?> callerClass, String address, KnownCommand.MenuControl cmd, Value.MainZoneMenuControl value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendMenuControl( %s ): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendDirectSelect(Class<?> callerClass, String address, KnownCommand.SetDirectSel cmd, String value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value);
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendDirectSelect( %s ): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendJumpToLine(Class<?> callerClass, String address, KnownCommand.JumpToLine cmd, int value) {
		int rc = Ctrl.sendPutCommand(address, cmd, ""+value);
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendJumpToLine( %d ): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendCursorSelect(Class<?> callerClass, String address, KnownCommand.SetCursorSelExt cmd, Value.CursorSelectExt value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendCursorSelect( %s ): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendCursorSelect(Class<?> callerClass, String address, KnownCommand.SetCursorSel cmd, Value.CursorSelect value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendCursorSelect( %s ): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendPageSelect(Class<?> callerClass, String address, KnownCommand.SetPageSel cmd, Value.PageSelect value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendPageSelect( %s ): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendPlayback(Class<?> callerClass, String address, KnownCommand.SetPlayback cmd, Value.PlayStop value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendPlayback(%s): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendPlayback(Class<?> callerClass, String address, KnownCommand.SetPlayback cmd, Value.PlayPauseStop value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendPlayback(%s): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendPlayback(Class<?> callerClass, String address, KnownCommand.SetPlayback cmd, Value.SkipFwdRev value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendPlayback(%s): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static void sendRepeat(Class<?> callerClass, String address, KnownCommand.SetRepeat cmd, Value.OffOneAll value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendRepeat(%s): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}

	private static <Shuffle extends Enum<Shuffle>&Value> void sendShuffle(Class<?> callerClass, String address, KnownCommand.SetShuffle cmd, Shuffle value) {
		int rc = Ctrl.sendPutCommand(address, cmd, value.getLabel());
		if (rc!=Ctrl.RC_OK || debug_verbose) Log.log( logType, callerClass, "sendShuffle(%s): %s -> RC: %s", value, cmd.toFullString(), Ctrl.getRCcode(rc));
	}
}