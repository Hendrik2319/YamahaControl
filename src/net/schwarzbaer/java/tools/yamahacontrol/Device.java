package net.schwarzbaer.java.tools.yamahacontrol;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Vector;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.PowerState;
import net.schwarzbaer.java.tools.yamahacontrol.XML.TagList;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.Log;

public final class Device {
	
	String address;
	Inputs inputs;
	MainPlayCtrl mainPlayCtrl;
	private BasicStatus basicStatus;
	
	NetRadio netRadio;
	Tuner    tuner;
	AirPlay  airPlay;
	USB      usb;
	DLNA     dlna;
	IPodUSB  iPodUSB;
	
	Device(String address) {
		this.address = address;
		this.basicStatus = null;
		this.inputs   = new Inputs  (this);
		this.mainPlayCtrl = new MainPlayCtrl(this.address);
		
		this.netRadio = new NetRadio(this.address);
		this.tuner    = new Tuner   (this.address);
		this.airPlay  = new AirPlay (this.address);
		this.usb      = new USB     (this.address);
		this.dlna     = new DLNA    (this.address);
		this.iPodUSB  = new IPodUSB (this.address);
	}
	
	enum UpdateWish {
		BasicStatus, Scenes, Inputs, TunerConfig, AirPlayConfig,
		NetRadioPlayInfo, NetRadioListInfo, USBListInfo, USBPlayInfo,
		DLNAPlayInfo, DLNAListInfo, IPodUSBListInfo, IPodUSBPlayInfo, IPodUSBMode
	}
	
	public void update(EnumSet<UpdateWish> updateWishes) {
		//System.out.println("Device.update("+updateWishes+")");
		updateWishes.forEach(uw->{
			switch (uw) {
			case Inputs          : inputs.inputs     = inputs.getSceneInput(KnownCommand.General.GetInputItems); break;
			case Scenes          : inputs.scenes     = inputs.getSceneInput(KnownCommand.General.GetSceneItems); break;
			case BasicStatus     : basicStatus       = BasicStatus   .parse(Ctrl.sendGetCommand_Node(address,KnownCommand.General.GetBasicStatus  )); break;
			case TunerConfig     : tuner  .config    = Tuner  .Config.parse(Ctrl.sendGetCommand_Node(address,KnownCommand.Config.Tuner  )); break;
			case AirPlayConfig   : airPlay.config    = AirPlay.Config.parse(Ctrl.sendGetCommand_Node(address,KnownCommand.Config.AirPlay)); break;
			case NetRadioListInfo: netRadio.listInfo.update(); break;
			case NetRadioPlayInfo: netRadio.playInfo.update(); break;
			case USBListInfo     : usb     .listInfo.update(); break;
			case USBPlayInfo     : usb     .playInfo.update(); break;
			case DLNAListInfo    : dlna    .listInfo.update(); break;
			case DLNAPlayInfo    : dlna    .playInfo.update(); break;
			case IPodUSBListInfo : iPodUSB .listInfo.update(); break;
			case IPodUSBPlayInfo : iPodUSB .playInfo.update(); break;
			case IPodUSBMode     : iPodUSB.updateMode(); break;
			}
		});
	}
	
	public <T extends Value> T askValue(KnownCommand knownCommand, T[] values, String... tagList) {
		Node node = Ctrl.sendGetCommand_Node(address,knownCommand);
		if (node==null) return null;
		return XML.getSubValue(node,values,tagList);
	}
	
	public int sendPutCommand(Device.KnownCommand knownCommand, String value) {
		return Ctrl.sendPutCommand(address, knownCommand, value);
	}

	private static class BasicStatus {

		private Value.PowerState power;
		@SuppressWarnings("unused") private Value.SleepState sleep;
		private NumberWithUnit volume;
		private Value.OnOff volMute;
		private String currentInput;
		@SuppressWarnings("unused") private Inputs.DeviceSceneInput inputInfo;

		public BasicStatus() {
			this.power = null;
			this.sleep = null;
			this.volume = null;
			this.volMute = null;
			this.currentInput = null;
			this.inputInfo = null;
		}

		public static BasicStatus parse(Node node) {
			BasicStatus status = new BasicStatus();
			XML.forEachChild(node, value->{
				switch (value.getNodeName()) {
				case "Power_Control":
					status.power = XML.getSubValue(value,Value.PowerState.values(),"Power");
					status.sleep = XML.getSubValue(value,Value.SleepState.values(),"Sleep");
					break;
				case "Volume":
					status.volume = NumberWithUnit.parse(value,"Lvl");
					status.volMute = XML.getSubValue(value,Value.OnOff.values(),"Mute");
					break;
				case "Input":
					status.currentInput = XML.getSubValue(value,"Input_Sel");
					Node inputInfoNode = XML.getSubNode(value, "Input_Sel_Item_Info");
					if (inputInfoNode==null) status.inputInfo = null;
					else status.inputInfo = Inputs.DeviceSceneInput.parse(inputInfoNode);
					break;
				}
			});
			return status;
		}
	}

	public NumberWithUnit getVolume() {
		if (basicStatus==null) return null;
		return basicStatus.volume;
	}

	public void setVolume(double value) {
		if (basicStatus==null) return;
		if (basicStatus.volume==null) return;
		if (basicStatus.volume.number==null) return;
		
		int currentValue = (int)Math.round(basicStatus.volume.number*2);
		int newValue     = (int)Math.round(value*2);
		if (newValue!=currentValue) {
			// Value 0:   Val = Number: -805..(5)..165
			// Value 1:   Exp = "1"
			// Value 2:   Unit = "dB"
			String xmlStr = NumberWithUnit.createXML(newValue/2.0,1,"dB");
			basicStatus.volume.number = newValue/2.0f;
			int rc = Ctrl.sendPutCommand(address,KnownCommand.General.SetVolume,xmlStr);
			if (rc!=Ctrl.RC_OK)
				Log.error(getClass(), "setVolume(%f)-> %s %s -> RC:%d", value, KnownCommand.General.SetVolume.tagList, xmlStr, rc);
		}
	}
	// Value 0:   Val = Number: -805..(5)..165
	public static double getMinVolume() { return -80.5; }
	public static double getMaxVolume() { return  16.5; }

	public Value.OnOff getMute() {
		if (basicStatus==null) return null;
		return basicStatus.volMute;
	}
	public void setMute(Value.OnOff volMute) {
		// [Vol_Mute_On]        PUT[P3]     Main_Zone,Volume,Mute = On
		// [Vol_Mute_Off]        PUT[P3]     Main_Zone,Volume,Mute = Off
		Ctrl.sendPutCommand(address,KnownCommand.General.SetMute,volMute.getLabel());
	}
	
	public PowerState getPowerState() {
		if (basicStatus==null) return null;
		return basicStatus.power;
	}
	
	public void setPowerState(Value.PowerState power) {
		// System,Power_Control,Power = On
		// System,Power_Control,Power = Standby
		Ctrl.sendPutCommand(address,KnownCommand.General.GetNSetSystemPower,power.getLabel());
	}
	
	static class MainPlayCtrl {
	
		private String address;
	
		public MainPlayCtrl(String address) {
			this.address = address;
		}

		public void setPlayback(Value.PlayPauseStop play) {
			int rc = Ctrl.sendPutCommand(address,KnownCommand.MainZone.Playback,play.getLabel());
			if (rc!=Ctrl.RC_OK) Log.warning(getClass(), "setPlayback( %s ) -> RC: %d", play.getLabel(), rc);
		}

		public void setPlayback(Value.SkipFwdRev skip) {
			int rc = Ctrl.sendPutCommand(address,KnownCommand.MainZone.Playback,skip.getLabel());
			if (rc!=Ctrl.RC_OK) Log.warning(getClass(), "setPlayback( %s ) -> RC: %d", skip.getLabel(), rc);
		}

		public void setCursorSelect(Value.CursorSelectExt cursor) {
			int rc = Ctrl.sendPutCommand(address,KnownCommand.MainZone.Cursor,cursor.getLabel());
			if (rc!=Ctrl.RC_OK) Log.warning(getClass(), "setCursorSelect( %s ) -> RC: %d", cursor.getLabel(), rc);
		}

		public void setMenuControl(Value.MainZoneMenuControl menuCtrl) {
			int rc = Ctrl.sendPutCommand(address,KnownCommand.MainZone.MenuControl,menuCtrl.getLabel());
			if (rc!=Ctrl.RC_OK) Log.warning(getClass(), "setMenuControl( %s ) -> RC: %d", menuCtrl.getLabel(), rc);
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
			if (device.basicStatus==null) return null;
			if (device.basicStatus.currentInput==null) return null;
			for (DeviceSceneInput dsi:inputs)
				if (device.basicStatus.currentInput.equals(dsi.ID))
					return dsi;
			return null;
		}

		public void setScene(DeviceSceneInput dsi) {
			// PUT[P6]:    Main_Zone,Scene,Scene_Sel   =   Values [GET[G4]:Main_Zone,Scene,Scene_Sel_Item]
			Ctrl.sendPutCommand(device.address,KnownCommand.General.SetCurrentScene,dsi.ID);
		}

		public void setInput(DeviceSceneInput dsi) {
			// PUT[P4]:    Main_Zone,Input,Input_Sel   =   Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
			Ctrl.sendPutCommand(device.address,KnownCommand.General.SetCurrentInput,dsi.ID);
		}

		public DeviceSceneInput[] getInputs() { return inputs; }
		public DeviceSceneInput[] getScenes() { return scenes; }

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
		
		enum General implements KnownCommand {
			GetSceneItems("Main_Zone,Scene,Scene_Sel_Item"), // G4: Main_Zone,Scene,Scene_Sel_Item
			GetInputItems("Main_Zone,Input,Input_Sel_Item"), // G2: Main_Zone,Input,Input_Sel_Item
			GetNSetSystemPower("System,Power_Control,Power"),
			SetCurrentScene("Main_Zone,Scene,Scene_Sel"),
			SetCurrentInput("Main_Zone,Input,Input_Sel"),
			GetBasicStatus("Main_Zone,Basic_Status"),
			SetVolume("Main_Zone,Volume,Lvl"),
			SetMute("Main_Zone,Volume,Mute"), // P3: Main_Zone,Volume,Mute
			;
			
			final private TagList tagList;
			General(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
		}
		
		enum MainZone implements KnownCommand {
			Playback   ("Main_Zone,Play_Control,Playback"      ), // P24: Main_Zone,Play_Control,Playback
			Cursor     ("Main_Zone,Cursor_Control,Cursor"      ), // P18: Main_Zone,Cursor_Control,Cursor
			MenuControl("Main_Zone,Cursor_Control,Menu_Control"), // P19: Main_Zone,Cursor_Control,Menu_Control
			;
			
			final private TagList tagList;
			MainZone(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
		}
		
		enum Config implements KnownCommand {
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
		}
		
		enum GetPlayInfo implements KnownCommand {
			NetRadio("NET_RADIO,Play_Info"), // G1: NET_RADIO,Play_Info
			USB     (      "USB,Play_Info"), // G1: USB,Play_Info
			DLNA    (   "SERVER,Play_Info"), // G1: SERVER,Play_Info
			IPodUSB ( "iPod_USB,Play_Info"), // G1: iPod_USB,Play_Info
			Spotify (  "Spotify,Play_Info"), // G1: Spotify,Play_Info
			AirPlay (  "AirPlay,Play_Info"), // G1: AirPlay,Play_Info
			;
			
			final private TagList tagList;
			GetPlayInfo (String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
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
		}
		
		enum SetPlayback implements KnownCommand {
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
		}
		
		enum SetRepeat implements KnownCommand {
			USB    (     "USB,Play_Control,Play_Mode,Repeat"), // P1: USB,Play_Control,Play_Mode,Repeat
			DLNA   (  "SERVER,Play_Control,Play_Mode,Repeat"), // P1: SERVER,Play_Control,Play_Mode,Repeat
			IPodUSB("iPod_USB,Play_Control,Play_Mode,Repeat"), // P8: iPod_USB,Play_Control,Play_Mode,Repeat
			;
			
			final private TagList tagList;
			SetRepeat(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
		}
		
		enum SetShuffle implements KnownCommand {
			USB    (     "USB,Play_Control,Play_Mode,Shuffle"), // P2: USB,Play_Control,Play_Mode,Shuffle
			DLNA   (  "SERVER,Play_Control,Play_Mode,Shuffle"), // P2: SERVER,Play_Control,Play_Mode,Shuffle
			IPodUSB("iPod_USB,Play_Control,Play_Mode,Shuffle"), // P9: iPod_USB,Play_Control,Play_Mode,Shuffle
			;
			
			final private TagList tagList;
			SetShuffle(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
		}
		
		enum Special implements KnownCommand {
			GetUSBPresets ("USB,Play_Control,Preset,Preset_Sel_Item"), // G4: USB,Play_Control,Preset,Preset_Sel_Item
			SetUSBSelectPreset ("USB,Play_Control,Preset,Preset_Sel"), // P4: USB,Play_Control,Preset,Preset_Sel
			GetDLNAPresets ("SERVER,Play_Control,Preset,Preset_Sel_Item"), // G4: SERVER,Play_Control,Preset,Preset_Sel_Item
			SetDLNASelectPreset ("SERVER,Play_Control,Preset,Preset_Sel"), // P4: SERVER,Play_Control,Preset,Preset_Sel
			SetIPodUSBMode("iPod_USB,Play_Control,iPod_Mode"), // P10: iPod_USB,Play_Control,iPod_Mode
			;
			
			final private TagList tagList;
			Special(String tagListStr) { tagList = new TagList(tagListStr); }
			@Override public TagList getTagList() { return tagList; }
		}
		
	}

	static interface Value {
		public String getLabel();
		
		public enum OnOff            implements Value { On, Off           ; @Override public String getLabel() { return toString(); }  } 
		public enum PowerState       implements Value { On, Standby       ; @Override public String getLabel() { return toString(); }  }
		public enum PlayStop         implements Value { Play, Stop        ; @Override public String getLabel() { return toString(); }  }
		public enum PlayPauseStop    implements Value { Play, Pause, Stop ; @Override public String getLabel() { return toString(); }  }
		public enum AlbumCoverFormat implements Value { BMP, YMF          ; @Override public String getLabel() { return toString(); }  }
		public enum ReadyOrBusy      implements Value { Ready, Busy       ; @Override public String getLabel() { return toString(); }  }
		public enum OffOneAll        implements Value { Off, One, All     ; @Override public String getLabel() { return toString(); }  } 
		public enum ShuffleIPod      implements Value { Off, Songs, Albums; @Override public String getLabel() { return toString(); }  } 
		public enum IPodMode         implements Value { Normal, Extended  ; @Override public String getLabel() { return toString(); }  }
		
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
			_120min("120 min"),_90min("90 min"),_60min("60 min"),_30min("30 min"),Off("Off");
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
		public Float number;
		public final String unit;
		
		private NumberWithUnit(Float number, String unit) {
			this.number = number;
			this.unit = unit;
		}

		public static String createXML(double val, int exp, String unit) {
			for (int i=0; i<exp; ++i) val*=10;
			int valInt = (int)Math.round(val);
			return "<Val>"+valInt+"</Val><Exp>"+exp+"</Exp><Unit>"+unit+"</Unit>";
		}

		public static NumberWithUnit parse(Node node, String... tagList) {
			Node value = XML.getSubNode(node, tagList);
			if (value==null) return null;
			
			String val = XML.getSubValue(value,"Val");
			String exp = XML.getSubValue(value,"Exp");
			String unit = XML.getSubValue(value,"Unit");
			
			float number;
			int exponent;
			try {
				number = Integer.parseInt(val);
				exponent = Integer.parseInt(exp);
			} catch (NumberFormatException e) {
				return new NumberWithUnit(null, unit);
			}
			for (int i=0; i<exponent; ++i) number/=10;
			return new NumberWithUnit(number, unit);
		}
	}
	
	static class NetRadio {
		
		ListInfo          listInfo;
		PlayInfo_NetRadio playInfo;
		
		public NetRadio(String address) {
			this.listInfo = new ListInfo         (             address, KnownCommand.GetListInfo.NetRadio, KnownCommand.SetDirectSel.NetRadio, KnownCommand.SetCursorSel.NetRadio, KnownCommand.SetPageSel.NetRadio, KnownCommand.JumpToLine.NetRadio);
			this.playInfo = new PlayInfo_NetRadio("Net Radio", address, KnownCommand.GetPlayInfo.NetRadio, KnownCommand.SetPlayback.NetRadio);
		}
	}
	
	static class USB {
		
		ListInfo listInfo;
		PlayInfoExt<Value.OnOff> playInfo;
		
		public USB(String address) {
			this.listInfo = new ListInfo     (       address, KnownCommand.GetListInfo.USB, KnownCommand.SetDirectSel.USB, KnownCommand.SetCursorSel.USB, KnownCommand.SetPageSel.USB, KnownCommand.JumpToLine.USB);
			this.playInfo = new PlayInfoExt<>("USB", address, KnownCommand.GetPlayInfo.USB, KnownCommand.SetPlayback.USB, KnownCommand.SetRepeat.USB, KnownCommand.SetShuffle.USB, Value.OnOff.values());
		}
	}
	
	static class DLNA {
		
		ListInfo listInfo;
		PlayInfoExt<Value.OnOff> playInfo;
		
		public DLNA(String address) {
			this.listInfo = new ListInfo     (        address, KnownCommand.GetListInfo.DLNA, KnownCommand.SetDirectSel.DLNA, KnownCommand.SetCursorSel.DLNA, KnownCommand.SetPageSel.DLNA, KnownCommand.JumpToLine.DLNA);
			this.playInfo = new PlayInfoExt<>("DLNA", address, KnownCommand.GetPlayInfo.DLNA, KnownCommand.SetPlayback.DLNA, KnownCommand.SetRepeat.DLNA, KnownCommand.SetShuffle.DLNA, Value.OnOff.values());
		}
	}
	
	static class IPodUSB {
		
		Value.IPodMode mode;
		ListInfo listInfo;
		PlayInfoExt<Value.ShuffleIPod> playInfo;
		private String address;
		
		public IPodUSB(String address) {
			this.address = address;
			this.listInfo = new ListInfo     (            address, KnownCommand.GetListInfo.IPodUSB, KnownCommand.SetDirectSel.IPodUSB, KnownCommand.SetCursorSel.IPodUSB, KnownCommand.SetPageSel.IPodUSB, KnownCommand.JumpToLine.IPodUSB);
			this.playInfo = new PlayInfoExt<>("iPod USB", address, KnownCommand.GetPlayInfo.IPodUSB, KnownCommand.SetPlayback.IPodUSB, KnownCommand.SetRepeat.IPodUSB, KnownCommand.SetShuffle.IPodUSB, Value.ShuffleIPod.values());
		}

		public void updateMode() {
			mode = Ctrl.sendGetCommand(address,KnownCommand.Special.SetIPodUSBMode,Value.IPodMode.values());
			//Log.info(getClass(), "update mode: %s", mode.getLabel());
		}

		public void sendSetMode(Value.IPodMode mode) {
			// Browse Mode   []        PUT[P10]     iPod_USB,Play_Control,iPod_Mode = Extended
			/*int rc = */Ctrl.sendPutCommand(address,KnownCommand.Special.SetIPodUSBMode, mode.getLabel());
			//Log.info(getClass(), "set mode: %s -> RC: %d", mode.getLabel(), rc);
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
			if (setDirectSelCmd==null) throw new UnsupportedOperationException("DirectSelect not supported");
			// PUT:    #######,List_Control,Direct_Sel   =   Label: Line_% (1..8)
			Ctrl.sendPutCommand(address,setDirectSelCmd, "Line_"+line.index);
		}
		
		public void sendJumpToLine(int lineNumber) {
			if (setJumpToLineCmd==null) throw new UnsupportedOperationException("JumpToLine not supported");
			// PUT:    #######,List_Control,Jump_Line   =   Number: 1..65536
			Ctrl.sendPutCommand(address,setJumpToLineCmd, ""+lineNumber);
		}
		
		public void sendCursorSelect(Value.CursorSelect cursorSelect) {
			if (setCursorSelCmd==null) throw new UnsupportedOperationException("CursorSelect not supported");
			// [Cursor_Up]      #######,List_Control,Cursor = Up
			// [Cursor_Down]    #######,List_Control,Cursor = Down
			// [Cursor_Left]    #######,List_Control,Cursor = Return
			// [Cursor_Sel]     #######,List_Control,Cursor = Sel
			// [Cursor_Home]    #######,List_Control,Cursor = Return to Home
			Ctrl.sendPutCommand(address,setCursorSelCmd, cursorSelect.getLabel());
		}
		
		public void sendPageSelect(Value.PageSelect pageSelect) {
			if (setPageSelCmd==null) throw new UnsupportedOperationException("PageSelect not supported");
			// [Page_Up_1]      #######,List_Control,Page = Up
			// [Page_Down_1]    #######,List_Control,Page = Down
			Ctrl.sendPutCommand(address,setPageSelCmd, pageSelect.getLabel());
		}
	
		
		public void update() {
			parse(Ctrl.sendGetCommand_Node(address,getListInfoCmd));
		}
	
		private void parse(Node node) {
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
						
						int index = Integer.parseInt(nodeName.substring("Line_".length()));
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
	}

	static class PlayInfo_NetRadio extends PlayInfo {
	
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
		
		public void sendPlayback(Value.PlayStop playState) {
			// [Play]    Visible:No     PUT[P1]     NET_RADIO,Play_Control,Playback = Play
			// [Stop]    Playable:No     PUT[P1]     NET_RADIO,Play_Control,Playback = Stop
			Ctrl.sendPutCommand(address,setPlayback, playState.getLabel());
		}
	
		@Override
		public String toString() {
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
			
			sb.append("Station: ").append(currentStation==null?"":("\""+currentStation+"\"")).append("\r\n");
			sb.append("  Album: ").append(currentAlbum==null?"":("\""+currentAlbum+"\"")).append("\r\n");
			sb.append("   Song: ").append(currentSong==null?"":("\""+currentSong+"\"")).append("\r\n");
			sb.append("\r\n");
			
			sb.append("AlbumCover:\r\n");
			sb.append("   ");
			sb.append(albumCoverID==null?"":(" "+albumCoverID));
			sb.append(albumCoverFormat==null?"":(" "+albumCoverFormat.getLabel()));
			sb.append("\r\n");
			sb.append("   ");
			sb.append(albumCoverURL==null?"":(" \""+albumCoverURL+"\""));
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

	static class PlayInfoExt<Shuffle extends Enum<Shuffle>&Value> extends PlayInfo {
	
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
		
		private KnownCommand setPlaybackCmd;
		private KnownCommand setRepeatCmd;
		private KnownCommand setShuffleCmd;
		private Shuffle[] shuffleValues;
	
		public PlayInfoExt(String name, String address, KnownCommand.GetPlayInfo getPlayInfoCmd, KnownCommand setPlaybackCmd, KnownCommand setRepeatCmd, KnownCommand setShuffleCmd, Shuffle[] shuffleValues) {
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
	
		@Override
		public String toString() {
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
			
			sb.append("Artist: ").append(currentArtist==null?"":("\""+currentArtist+"\"")).append("\r\n");
			sb.append(" Album: ").append(currentAlbum==null?"":("\""+currentAlbum+"\"")).append("\r\n");
			sb.append("  Song: ").append(currentSong==null?"":("\""+currentSong+"\"")).append("\r\n");
			sb.append("\r\n");
			
			sb.append("AlbumCover:\r\n");
			sb.append("   ");
			sb.append(albumCoverID==null?"":(" "+albumCoverID));
			sb.append(albumCoverFormat==null?"":(" "+albumCoverFormat.getLabel()));
			sb.append("\r\n");
			sb.append("   ");
			sb.append(albumCoverURL==null?"":(" \""+albumCoverURL+"\""));
			sb.append("\r\n");
			
			return sb.toString();
		}
		
		public void sendPlayback(Value.PlayPauseStop playState) {
			// [Play]      #######,Play_Control,Playback = Play
			// [Pause]     #######,Play_Control,Playback = Pause
			// [Stop]      #######,Play_Control,Playback = Stop
			Ctrl.sendPutCommand(address,setPlaybackCmd, playState.getLabel());
		}
		
		public void sendPlayback(Value.SkipFwdRev skip) {
			// [Plus_1]    #######,Play_Control,Playback = Skip Fwd
			// [Minus_1]   #######,Play_Control,Playback = Skip Rev
			Ctrl.sendPutCommand(address,setPlaybackCmd, skip.getLabel());
		}
		
		public void sendRepeat(Value.OffOneAll repeatState) {
			// [Rep_Off]   #######,Play_Control,Play_Mode,Repeat = Off
			// [Rep_1]     #######,Play_Control,Play_Mode,Repeat = One
			// [Rep_2]     #######,Play_Control,Play_Mode,Repeat = All
			Ctrl.sendPutCommand(address,setRepeatCmd, repeatState.getLabel());
		}
		
		public void sendShuffle(Shuffle shuffleState) {
			Ctrl.sendPutCommand(address,setShuffleCmd, shuffleState.getLabel());
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

	static class Tuner {
		@SuppressWarnings("unused")
		private String address;
		Config config;
	
		public Tuner(String address) {
			this.address = address;
			this.config = null;
		}
	
		static class Config {
	
			Value.ReadyOrNot deviceStatus;
			String RDS;
			BandRange FM;
			BandRange AM;

			public Config() {
				deviceStatus = null;
				RDS = null;
				FM = null;
				AM = null;
			}

			static Config parse(Node node) {
				Config config = new Config();
				XML.forEachChild(node, child->{
					switch (child.getNodeName()) {
					case "Feature_Availability":
						// GET[G2]:    Tuner,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
						config.deviceStatus =  XML.getSubValue(child, Value.ReadyOrNot.values());
						break;
						
					case "RDS":
						config.RDS =  XML.getSubValue(child);
						break;
					case "Range_and_Step":
						XML.forEachChild(child, band->{
							switch (child.getNodeName()) {
							case "FM": config.FM = (BandRange)BandRange.parse(child); break;
							case "AM": config.AM = (BandRange)BandRange.parse(child); break;
							}
						});
						break;
					}
				});
				return config;
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
	
	static class AirPlay {
		
		@SuppressWarnings("unused")
		private String address;

		public AirPlay(String address) {
			this.address = address;
		}

		public Config config;

		static class Config {

			Value.ReadyOrNot deviceStatus;
			String volumeInterlock;

			public static Config parse(Node node) {
				Config config = new Config();
				XML.forEachChild(node, child->{
					switch (child.getNodeName()) {
					case "Feature_Availability":
						// GET[G3]:    AirPlay,Config   ->   Feature_Availability -> "Ready" | "Not Ready"
						config.deviceStatus =  XML.getSubValue(child, Value.ReadyOrNot.values());
						break;
						
					case "Volume_Interlock":
						config.volumeInterlock =  XML.getSubValue(child);
						break;
					}
				});
				return config;
			}
			
		}
	}
}