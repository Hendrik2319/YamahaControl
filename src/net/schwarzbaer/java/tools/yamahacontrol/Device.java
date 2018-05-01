package net.schwarzbaer.java.tools.yamahacontrol;

import java.util.EnumSet;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.schwarzbaer.java.tools.yamahacontrol.XML.TagList;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.Log;

final class Device {
	
	private String address;
	private BasicStatus basicStatus;
	private Boolean isOn;
	private DeviceSceneInput[] scenes;
	private DeviceSceneInput[] inputs;
	private DeviceSceneInput currentScene;
	private DeviceSceneInput currentInput;
	
	Device(String address) {
		this.address = address;
		this.basicStatus = null;
		this.isOn = null;
		this.scenes = null;
		this.inputs = null;
		this.currentScene = null;
		this.currentInput = null;
	}
	
	enum UpdateWish { Power, BasicStatus, Scenes, Inputs }
	
	public void update(EnumSet<UpdateWish> updateWishes) {
		updateWishes.forEach(uw->{
			switch (uw) {
			case Power: isOn = askOn(); break;
			case Inputs: inputs = getSceneInput(KnownCommand.GetInputItems); break; // G2: Main_Zone,Input,Input_Sel_Item
			case Scenes: scenes = getSceneInput(KnownCommand.GetSceneItems); break; // G4: Main_Zone,Scene,Scene_Sel_Item
			case BasicStatus: 
				Node node = Ctrl.sendGetCommand_Node(address,KnownCommand.GetBasicStatus);
				this.basicStatus = BasicStatus.parseNode(node);
				break;
			}
		});
	}
	
	private static String getSubValue(Node node, String... tagList) {
		Node value = XML.getSubNode(node, tagList);
		if (value==null) return null;
		return XML.getContentOfSingleChildTextNode(value);
	}
	
	private static <T extends Value> T getSubValue(Node node, T[] values, String... tagList) {
		String str = getSubValue(node,tagList);
		if (str!=null)
			for (T val:values)
				if (str.equals(val.getLabel()))
					return val;
		return null;
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

		public static BasicStatus parseNode(Node node) {
			BasicStatus status = new BasicStatus();
			NodeList valueNodes = node.getChildNodes();
			for (int i=0; i<valueNodes.getLength(); ++i) {
				Node value = valueNodes.item(i);
				switch (value.getNodeName()) {
				case "Power_Control":
					status.power = getSubValue(value,Value.PowerState.values(),"Power");
					status.sleep = getSubValue(value,Value.SleepState.values(),"Sleep");
					break;
				case "Volume":
					status.volume = NumberWithUnit.parse(value,"Lvl");
					status.volMute = getSubValue(value,Value.OnOff.values(),"Mute");
					break;
				case "Input":
					status.currentInput = getSubValue(value,"Input_Sel");
					Node inputInfoNode = XML.getSubNode(value, "Input_Sel_Item_Info");
					if (inputInfoNode==null) status.inputInfo = null;
					else status.inputInfo = parseDeviceSceneInput(inputInfoNode);
					break;
					// TODO other values cases in BasicStatus
				}
			}
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
		// GET[G1]:    Main_Zone,Basic_Status   ->   Input,Input_Sel -> Values [GET[G2]:Main_Zone,Input,Input_Sel_Item]
		return currentScene;
	}

	public DeviceSceneInput getInput() {
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
		NodeList valueNodes = item.getChildNodes();
		for (int v=0; v<valueNodes.getLength(); ++v) {
			Node value = valueNodes.item(v);
			switch (value.getNodeName()) {
			case "Param"     : dsi.ID        = XML.getContentOfSingleChildTextNode(value); break;
			case "RW"        : dsi.rw        = XML.getContentOfSingleChildTextNode(value); break;
			case "Title"     : dsi.title     = XML.getContentOfSingleChildTextNode(value); break;
			case "Src_Name"  : dsi.srcName   = XML.getContentOfSingleChildTextNode(value); break;
			case "Src_Number": dsi.srcNumber = XML.getContentOfSingleChildTextNode(value); break;
			}
		}
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
		SetVolume("Main_Zone,Volume,Lvl")
		;
		
		final TagList tagList;
		KnownCommand(String tagListStr) { tagList = new TagList(tagListStr); }
	}

	static interface Value {
		public String getLabel();
		
		public enum OnOff implements Value { On, Off; @Override public String getLabel() { return toString(); }  } 
		public enum PowerState implements Value { On, Standby; @Override public String getLabel() { return toString(); }  } 
		public enum SleepState implements Value {
			_120min("120 min"),_90min("90 min"),_60min("60 min"),_30min("30 min"),Off("Off");
			private String label;
			SleepState(String label) { this.label = label; }
			@Override public String getLabel() { return label; }  } 
	}

	static class NumberWithUnit {
		Float number;
		final String unit;
		
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
			
			String val = getSubValue(value,"Val");
			String exp = getSubValue(value,"Exp");
			String unit = getSubValue(value,"Unit");
			
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
}