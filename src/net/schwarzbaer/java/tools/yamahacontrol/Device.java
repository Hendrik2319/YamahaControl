package net.schwarzbaer.java.tools.yamahacontrol;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Vector;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.schwarzbaer.java.tools.yamahacontrol.Device.Value.PlayState;
import net.schwarzbaer.java.tools.yamahacontrol.XML.TagList;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.Log;

public final class Device {
	
	private String address;
	private BasicStatus basicStatus;
	private Boolean isOn;
	private DeviceSceneInput[] scenes;
	private DeviceSceneInput[] inputs;
	private DeviceSceneInput currentScene;
	private DeviceSceneInput currentInput;
	public NetRadio netRadio;
	
	Device(String address) {
		this.address = address;
		this.basicStatus = null;
		this.isOn = null;
		this.scenes = null;
		this.inputs = null;
		this.currentScene = null;
		this.currentInput = null;
		this.netRadio = new NetRadio(this);
	}
	
	enum UpdateWish { Power, BasicStatus, Scenes, Inputs, NetRadioPlayInfo, NetRadioListInfo }
	
	public void update(EnumSet<UpdateWish> updateWishes) {
		updateWishes.forEach(uw->{
			switch (uw) {
			case Power: isOn = askOn(); break;
			case Inputs: inputs = getSceneInput(KnownCommand.GetInputItems); break; // G2: Main_Zone,Input,Input_Sel_Item
			case Scenes: scenes = getSceneInput(KnownCommand.GetSceneItems); break; // G4: Main_Zone,Scene,Scene_Sel_Item
			case BasicStatus     : this.basicStatus       = BasicStatus      .parse(Ctrl.sendGetCommand_Node(address,KnownCommand.GetBasicStatus     )); break;
			case NetRadioListInfo: this.netRadio.listInfo = NetRadio.ListInfo.parse(Ctrl.sendGetCommand_Node(address,KnownCommand.GetNetRadioListInfo)); break;
			case NetRadioPlayInfo: this.netRadio.playInfo = NetRadio.PlayInfo.parse(Ctrl.sendGetCommand_Node(address,KnownCommand.GetNetRadioPlayInfo)); break;
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
		@SuppressWarnings("unused") private String currentInput;
		@SuppressWarnings("unused") private DeviceSceneInput inputInfo;

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
					else status.inputInfo = parseDeviceSceneInput(inputInfoNode);
					break;
					// TODO other values cases in BasicStatus
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

	public Boolean isOn() { return isOn; }

	public void setOn(boolean isOn) {
		// System,Power_Control,Power = On
		// System,Power_Control,Power = Standby
		int rc = Ctrl.sendPutCommand(address,KnownCommand.GetNSetSystemPower,isOn?"On":"Standby");
		if (rc!=Ctrl.RC_OK) return;
		this.isOn = askOn();
	}

	private Boolean askOn() {
		String value = Ctrl.sendGetCommand_String(address,KnownCommand.GetNSetSystemPower);
		if ("On".equals(value)) return true;
		if ("Standby".equals(value)) return false;
		return null;
	}

	public DeviceSceneInput getScene() {
		// TODO
		// GET[G1]:    Main_Zone,Basic_Status   ->   Input,Input_Sel -> Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
		return currentScene;
	}

	public DeviceSceneInput getInput() {
		// TODO
		// GET[G1]:    Main_Zone,Basic_Status   ->   Input,Input_Sel -> Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
		return currentInput;
	}

	public void setScene(DeviceSceneInput dsi) {
		// PUT[P6]:    Main_Zone,Scene,Scene_Sel   =   Values [GET[G4]:Main_Zone,Scene,Scene_Sel_Item]
		int rc = Ctrl.sendPutCommand(address,KnownCommand.SetCurrentScene,dsi.ID);
		if (rc==Ctrl.RC_OK) currentScene = dsi;
	}

	public void setInput(DeviceSceneInput dsi) {
		// PUT[P4]:    Main_Zone,Input,Input_Sel   =   Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
		int rc = Ctrl.sendPutCommand(address,KnownCommand.SetCurrentInput,dsi.ID);
		if (rc==Ctrl.RC_OK) currentInput = dsi;
	}

	public DeviceSceneInput[] getInputs() { return inputs; }
	public DeviceSceneInput[] getScenes() { return scenes; }

	private DeviceSceneInput[] getSceneInput(KnownCommand knownCommand) {
		Node node = Ctrl.sendGetCommand_Node(address,knownCommand);
		if (node == null) return null;
		
		NodeList itemNodes = node.getChildNodes();
		DeviceSceneInput[] dsiArr = new DeviceSceneInput[itemNodes.getLength()];
		for (int i=0; i<itemNodes.getLength(); ++i)
			dsiArr[i] = parseDeviceSceneInput(itemNodes.item(i));
		return dsiArr;
	}

	private static DeviceSceneInput parseDeviceSceneInput(Node item) {
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
		
	}

	enum KnownCommand {
		GetSceneItems("Main_Zone,Scene,Scene_Sel_Item"),
		GetInputItems("Main_Zone,Input,Input_Sel_Item"),
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
		private Device device;
		
		public NetRadio(Device device) {
			this.device = device;
			this.listInfo = null;
			this.playInfo = null;
		}
		
		public void sendDirectSelect(ListInfo.Line line) {
			// PUT[P2]:    NET_RADIO,List_Control,Direct_Sel   =   Label: Line_% (1..8)
			Ctrl.sendPutCommand(device.address,KnownCommand.SetNetRadioDirectListSel, "Line_"+line.index);
		}
		
		public void sendCursorSelect(Value.CursorSelect cursorSelect) {
			// [Cursor_Up]        PUT[P4]     NET_RADIO,List_Control,Cursor = Up
			// [Cursor_Down]        PUT[P4]     NET_RADIO,List_Control,Cursor = Down
			// [Cursor_Left]        PUT[P4]     NET_RADIO,List_Control,Cursor = Return
			// [Cursor_Sel]        PUT[P4]     NET_RADIO,List_Control,Cursor = Sel
			// [Cursor_Home]        PUT[P4]     NET_RADIO,List_Control,Cursor = Return to Home
			Ctrl.sendPutCommand(device.address,KnownCommand.SetNetRadioCursorListSel, cursorSelect.getLabel());
		}
		
		public void sendPageSelect(Value.PageSelect pageSelect) {
			// [Page_Up_1]        PUT[P5]     NET_RADIO,List_Control,Page = Up
			// [Page_Down_1]        PUT[P5]     NET_RADIO,List_Control,Page = Down
			Ctrl.sendPutCommand(device.address,KnownCommand.SetNetRadioPageListSel, pageSelect.getLabel());
		}
		
		public void sendPlayback(Value.PlayState playState) {
			//[Play]    Visible:No     PUT[P1]     NET_RADIO,Play_Control,Playback = Play
			// [Stop]    Playable:No     PUT[P1]     NET_RADIO,Play_Control,Playback = Stop
			Ctrl.sendPutCommand(device.address,KnownCommand.SetNetRadioPlayback, playState.getLabel());
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

			private Value.ReadyOrNot deviceStatus;
			private Value.PlayState playState;
			private String currentStation;
			private String currentAlbum;
			private String currentSong;
			private Integer albumCoverID;
			private String albumCoverURL;
			private Value.AlbumCoverFormat albumCoverFormat;

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
				sb.append("Device Status: ").append(deviceStatus==null?"???":deviceStatus.getLabel());
				if (playState==PlayState.Play) sb.append(" & is playing\r\n"); else
				if (playState==PlayState.Stop) sb.append(" & has stopped\r\n"); else sb.append("\r\n");
				
				sb.append("currently:\r\n");
				sb.append("   Station: ").append(currentStation==null?"???":("\""+currentStation+"\"")).append("\r\n");
				sb.append("     Album: ").append(currentAlbum==null?"???":("\""+currentAlbum+"\"")).append("\r\n");
				sb.append("      Song: ").append(currentSong==null?"???":("\""+currentSong+"\"")).append("\r\n");
				
				sb.append("AlbumCover: ");
				sb.append(albumCoverID==null?"???":albumCoverID).append(", ");
				sb.append(albumCoverFormat==null?"???":albumCoverFormat.getLabel()).append(", ");
				sb.append(albumCoverURL==null?"???":("\""+albumCoverURL+"\"")).append("\r\n");
				
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
}