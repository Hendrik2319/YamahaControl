package net.schwarzbaer.java.tools.yamahacontrol;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Vector;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.PlayState;
import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.ReadyOrNot;
import net.schwarzbaer.java.tools.yamahacontrol.XML.TagList;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.Log;

public final class Device {
	
	private String address;
	public Power power;
	public Inputs inputs;
	private BasicStatus basicStatus;
	public NetRadio netRadio;
	public Tuner tuner;
	public AirPlay airPlay;
	
	Device(String address) {
		this.address = address;
		this.basicStatus = null;
		this.power    = new Power   (this.address);
		this.inputs   = new Inputs  (this);
		this.netRadio = new NetRadio(this.address);
		this.tuner    = new Tuner   (this.address);
		this.airPlay  = new AirPlay (this.address);
	}
	
	enum UpdateWish { Power, BasicStatus, Scenes, Inputs, NetRadioPlayInfo, NetRadioListInfo, TunerConfig, AirPlayConfig }
	
	public void update(EnumSet<UpdateWish> updateWishes) {
		//System.out.println("Device.update("+updateWishes+")");
		updateWishes.forEach(uw->{
			switch (uw) {
			case Power           : power.askOn(); break;
			case Inputs          : inputs.inputs     = inputs.getSceneInput(KnownCommand.GetInputItems); break;
			case Scenes          : inputs.scenes     = inputs.getSceneInput(KnownCommand.GetSceneItems); break;
			case BasicStatus     : basicStatus       = BasicStatus      .parse(Ctrl.sendGetCommand_Node(address,KnownCommand.GetBasicStatus     )); break;
			case NetRadioListInfo: netRadio.listInfo = NetRadio.ListInfo.parse(Ctrl.sendGetCommand_Node(address,KnownCommand.GetNetRadioListInfo)); break;
			case NetRadioPlayInfo: netRadio.playInfo = NetRadio.PlayInfo.parse(Ctrl.sendGetCommand_Node(address,KnownCommand.GetNetRadioPlayInfo)); break;
			case TunerConfig     : tuner.config      = Tuner   .Config  .parse(Ctrl.sendGetCommand_Node(address,KnownCommand.GetTunerConfig     )); break;
			case AirPlayConfig   : airPlay.config    = AirPlay .Config  .parse(Ctrl.sendGetCommand_Node(address,KnownCommand.GetAirPlayConfig   )); break;
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

		@SuppressWarnings("unused") private Value.PowerState power;
		@SuppressWarnings("unused") private Value.SleepState sleep;
		private NumberWithUnit volume;
		@SuppressWarnings("unused") private Value.OnOff volMute;
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
			int rc = Ctrl.sendPutCommand(address,KnownCommand.SetVolume,xmlStr);
			if (rc!=Ctrl.RC_OK) {
				Log.error(getClass(), "setVolume(%f)-> %s %s -> RC:%d", value, KnownCommand.SetVolume.tagList, xmlStr, rc);
			}
			
		}
	}
	
	static class Power {
		
		private Boolean isOn;
		private String address;
		
		Power(String address) {
			this.address = address;
			this.isOn = null;
		}
		
		public Boolean isOn() { return isOn; }

		public void setOn(boolean isOn) {
			// System,Power_Control,Power = On
			// System,Power_Control,Power = Standby
			int rc = Ctrl.sendPutCommand(address,KnownCommand.GetNSetSystemPower,isOn?"On":"Standby");
			if (rc!=Ctrl.RC_OK) return;
			askOn();
		}

		private void askOn() {
			String value = Ctrl.sendGetCommand_String(address,KnownCommand.GetNSetSystemPower);
			if ("On".equals(value)) { isOn = true; return; }
			if ("Standby".equals(value)) { isOn = false; return; }
			isOn = null;
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
			Ctrl.sendPutCommand(device.address,KnownCommand.SetCurrentScene,dsi.ID);
		}

		public void setInput(DeviceSceneInput dsi) {
			// PUT[P4]:    Main_Zone,Input,Input_Sel   =   Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
			Ctrl.sendPutCommand(device.address,KnownCommand.SetCurrentInput,dsi.ID);
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

	enum KnownCommand {
		GetSceneItems("Main_Zone,Scene,Scene_Sel_Item"), // G4: Main_Zone,Scene,Scene_Sel_Item
		GetInputItems("Main_Zone,Input,Input_Sel_Item"), // G2: Main_Zone,Input,Input_Sel_Item
		GetNSetSystemPower("System,Power_Control,Power"),
		SetCurrentScene("Main_Zone,Scene,Scene_Sel"),
		SetCurrentInput("Main_Zone,Input,Input_Sel"),
		GetBasicStatus("Main_Zone,Basic_Status"),
		SetVolume("Main_Zone,Volume,Lvl"),
		
		GetTunerConfig   (    "Tuner,Config"),
		GetAirPlayConfig (  "AirPlay,Config"),
		GetSpotifyConfig (  "Spotify,Config"), 
		GetIPodUSBConfig ( "iPod_USB,Config"),
		GetUSBConfig     (      "USB,Config"),
		GetNetRadioConfig("NET_RADIO,Config"),
		GetServerConfig  (   "SERVER,Config"),
		GetNetRadioListInfo("NET_RADIO,List_Info"), // G2: NET_RADIO,List_Info
		GetNetRadioPlayInfo("NET_RADIO,Play_Info"), // G1: NET_RADIO,Play_Info
		SetNetRadioDirectListSel("NET_RADIO,List_Control,Direct_Sel"),
		SetNetRadioCursorListSel("NET_RADIO,List_Control,Cursor"),
		SetNetRadioPageListSel  ("NET_RADIO,List_Control,Page"),
		SetNetRadioPlayback     ("NET_RADIO,Play_Control,Playback")
		;
		
		final TagList tagList;
		KnownCommand(String tagListStr) { tagList = new TagList(tagListStr); }
	}

	static interface Value {
		public String getLabel();
		
		public enum ReadyOrNot implements Value {
			Ready("Ready"),NotReady("Not Ready");
			private String label;
			ReadyOrNot(String label) { this.label = label; }
			@Override public String getLabel() { return label; }
		}
		
		public enum OnOff implements Value { On, Off; @Override public String getLabel() { return toString(); }  } 
		public enum PowerState implements Value { On, Standby; @Override public String getLabel() { return toString(); }  } 
		public enum SleepState implements Value {
			_120min("120 min"),_90min("90 min"),_60min("60 min"),_30min("30 min"),Off("Off");
			private String label;
			SleepState(String label) { this.label = label; }
			@Override public String getLabel() { return label; }
		}
		
		public enum PlayState implements Value { Play, Stop; @Override public String getLabel() { return toString(); }  }
		public enum AlbumCoverFormat implements Value { BMP, YMF; @Override public String getLabel() { return toString(); }  }
		public enum ReadyOrBusy implements Value { Ready, Busy; @Override public String getLabel() { return toString(); }  }
		
		public enum LineAttribute implements Value {
			Container,UnplayableItem("Unplayable Item"),Item,Unselectable;
			private String label;
			LineAttribute(String label) { this.label = label; }
			LineAttribute() { this.label = toString(); }
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
		
		// [Page_Up_1]        PUT[P5]     NET_RADIO,List_Control,Page = Up
		// [Page_Down_1]        PUT[P5]     NET_RADIO,List_Control,Page = Down
		public enum PageSelect implements Value { Up,Down; @Override public String getLabel() { return toString(); }  }
	}

	public static class NumberWithUnit {
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
		
		ListInfo listInfo;
		PlayInfo playInfo;
		private String address;
		
		public NetRadio(String address) {
			this.address = address;
			this.listInfo = null;
			this.playInfo = null;
		}
		
		public void sendDirectSelect(ListInfo.Line line) {
			// PUT[P2]:    NET_RADIO,List_Control,Direct_Sel   =   Label: Line_% (1..8)
			Ctrl.sendPutCommand(address,KnownCommand.SetNetRadioDirectListSel, "Line_"+line.index);
		}
		
		public void sendCursorSelect(Value.CursorSelect cursorSelect) {
			// [Cursor_Up]        PUT[P4]     NET_RADIO,List_Control,Cursor = Up
			// [Cursor_Down]        PUT[P4]     NET_RADIO,List_Control,Cursor = Down
			// [Cursor_Left]        PUT[P4]     NET_RADIO,List_Control,Cursor = Return
			// [Cursor_Sel]        PUT[P4]     NET_RADIO,List_Control,Cursor = Sel
			// [Cursor_Home]        PUT[P4]     NET_RADIO,List_Control,Cursor = Return to Home
			Ctrl.sendPutCommand(address,KnownCommand.SetNetRadioCursorListSel, cursorSelect.getLabel());
		}
		
		public void sendPageSelect(Value.PageSelect pageSelect) {
			// [Page_Up_1]        PUT[P5]     NET_RADIO,List_Control,Page = Up
			// [Page_Down_1]        PUT[P5]     NET_RADIO,List_Control,Page = Down
			Ctrl.sendPutCommand(address,KnownCommand.SetNetRadioPageListSel, pageSelect.getLabel());
		}
		
		public void sendPlayback(Value.PlayState playState) {
			//[Play]    Visible:No     PUT[P1]     NET_RADIO,Play_Control,Playback = Play
			// [Stop]    Playable:No     PUT[P1]     NET_RADIO,Play_Control,Playback = Stop
			Ctrl.sendPutCommand(address,KnownCommand.SetNetRadioPlayback, playState.getLabel());
		}

		public ListInfo getListInfo() { return listInfo; }
		public PlayInfo getPlayInfo() { return playInfo; }

		static class ListInfo {

			Value.ReadyOrBusy menuStatus;
			Integer menuLayer;
			String  menuName;
			Integer currentLine;
			Integer maxLine;
			Vector<Line> lines;

			public ListInfo() {
				this.menuStatus = null;
				this.menuLayer = null;
				this.menuName = null;
				this.currentLine = null;
				this.maxLine = null;
				this.lines = new Vector<>();
			}

			public static ListInfo parse(Node node) {
				ListInfo listInfo = new ListInfo();
				XML.forEachChild(node, child->{
					switch (child.getNodeName()) {
					case "Menu_Status":
						// GET[G2]:    NET_RADIO,List_Info   ->   Menu_Status -> "Ready" | "Busy"
						listInfo.menuStatus =  XML.getSubValue(child, Value.ReadyOrBusy.values());
						break;
						
					case "Menu_Layer":
						// GET[G2]:    NET_RADIO,List_Info   ->   Menu_Layer -> Number: 1..16
						listInfo.menuLayer =  XML.getSubValue_Int(child);
						break;
						
					case "Menu_Name":
						// GET[G2]:    NET_RADIO,List_Info   ->   Menu_Name -> Text: 0..128 (UTF-8)
						listInfo.menuName =  XML.getSubValue(child);
						break;
						
					case "Current_List":
						// GET[G2]:    NET_RADIO,List_Info
						// Value 0:   Current_List,Line_1,Txt -> Text: 0..128 (UTF-8)
						// Value 1:   Current_List,Line_1,Attribute -> "Container" | "Unplayable Item" | "Item" | "Unselectable"
						listInfo.lines.clear();;
						XML.forEachChild(child, line->{
							String nodeName = line.getNodeName();
							if (nodeName==null || !nodeName.startsWith("Line_")) return;
							
							int index = Integer.parseInt(nodeName.substring("Line_".length()));
							String txt = XML.getSubValue(line,"Txt");
							Value.LineAttribute attr = XML.getSubValue(line,Value.LineAttribute.values(), "Attribute");
							listInfo.lines.add(new Line(index,txt,attr));
						});
						listInfo.lines.sort(Comparator.nullsLast(Comparator.comparing(line->line.index)));
						break;
						
					case "Cursor_Position":
						// GET[G2]:    NET_RADIO,List_Info   ->   Cursor_Position,Current_Line -> Number: 1..65536
						// GET[G2]:    NET_RADIO,List_Info   ->   Cursor_Position,Max_Line -> Number: 0..65536
						listInfo.currentLine =  XML.getSubValue_Int(child,"Current_Line");
						listInfo.maxLine =  XML.getSubValue_Int(child,"Max_Line");
						break;
					}
				});
				return listInfo;
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
		static class PlayInfo {

			Value.ReadyOrNot deviceStatus;
			Value.PlayState playState;
			String currentStation;
			String currentAlbum;
			String currentSong;
			Integer albumCoverID;
			String albumCoverURL;
			Value.AlbumCoverFormat albumCoverFormat;

			public PlayInfo() {
				this.deviceStatus = null;
				this.playState = null;
				this.currentStation = null;
				this.currentAlbum = null;
				this.currentSong = null;
				this.albumCoverID = null;
				this.albumCoverURL = null;
				this.albumCoverFormat = null;
			}

			@Override
			public String toString() {
				StringBuilder sb = new StringBuilder();
				sb.append("Net Radio: ").append(deviceStatus==null?"???":deviceStatus.getLabel());
				if (playState==PlayState.Play) sb.append(" & is playing\r\n"); else
				if (playState==PlayState.Stop) sb.append(" & has stopped\r\n"); else sb.append("\r\n");
				
				sb.append("Station: ").append(currentStation==null?"":("\""+currentStation+"\"")).append("\r\n");
				sb.append("  Album: ").append(currentAlbum==null?"":("\""+currentAlbum+"\"")).append("\r\n");
				sb.append("   Song: ").append(currentSong==null?"":("\""+currentSong+"\"")).append("\r\n");
				
				sb.append("AlbumCover:");
				sb.append(albumCoverID==null?"":(" "+albumCoverID));
				sb.append(albumCoverFormat==null?"":(" "+albumCoverFormat.getLabel()));
				sb.append(albumCoverURL==null?"":(" \""+albumCoverURL+"\""));
				sb.append("\r\n");
				
				return sb.toString();
			}
			
			public static PlayInfo parse(Node node) {
				PlayInfo playInfo = new PlayInfo();
				XML.forEachChild(node, child->{
					switch (child.getNodeName()) {
					case "Feature_Availability":
						// GET[G1]:    NET_RADIO,Play_Info   ->   Feature_Availability -> "Ready" | "Not Ready"
						playInfo.deviceStatus =  XML.getSubValue(child, Value.ReadyOrNot.values());
						break;
						
					case "Playback_Info":
						// GET[G1]:    NET_RADIO,Play_Info   ->   Playback_Info -> "Play" | "Stop"
						playInfo.playState =  XML.getSubValue(child, Value.PlayState.values());
						break;
						
					case "Meta_Info":
						// GET[G1]:    NET_RADIO,Play_Info   ->   Meta_Info,Station -> Text: 0..128 (UTF-8)
						// GET[G1]:    NET_RADIO,Play_Info   ->   Meta_Info,Album -> Text: 0..128 (UTF-8)
						// GET[G1]:    NET_RADIO,Play_Info   ->   Meta_Info,Song -> Text: 0..128 (UTF-8)
						playInfo.currentStation =  XML.getSubValue(child, "Station"); 
						playInfo.currentAlbum   =  XML.getSubValue(child, "Album"); 
						playInfo.currentSong    =  XML.getSubValue(child, "Song"); 
						break;
						
					case "Album_ART":
						// GET[G1]:    NET_RADIO,Play_Info   ->   Album_ART,URL -> Text: 0..128 (UTF-8)
						// GET[G1]:    NET_RADIO,Play_Info   ->   Album_ART,ID -> Number: 0..255
						// GET[G1]:    NET_RADIO,Play_Info   ->   Album_ART,Format -> "BMP" | "YMF"
						playInfo.albumCoverID     =  XML.getSubValue_Int(child, "ID"); 
						playInfo.albumCoverURL    =  XML.getSubValue(child, "URL"); 
						playInfo.albumCoverFormat =  XML.getSubValue(child, Value.AlbumCoverFormat.values(), "Format"); 
						break;
					}
				});
				return playInfo;
			}
			
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
	
			ReadyOrNot deviceStatus;
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

			ReadyOrNot deviceStatus;
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