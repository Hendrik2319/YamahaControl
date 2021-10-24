package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import net.schwarzbaer.gui.Disabler;
import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.ComplexCommandItem;
import net.schwarzbaer.java.tools.yamahacontrol.Ctrl.HttpResponse;
import net.schwarzbaer.java.tools.yamahacontrol.XML.TagList;

public class CommandList {
	
	private static IconSource.CachedIcons<TreeIcon> treeIcons = null;
	enum TreeIcon { Menu, Command, Command_PUT, Command_GET, Language, UnitDescription, Document, XMLTag }
	static {
		IconSource<TreeIcon> treeIconsIS = new IconSource<TreeIcon>(16,16);
		treeIconsIS.readIconsFromResource("/TreeIcons.png");
		treeIcons = treeIconsIS.cacheIcons(TreeIcon.values());
	}
	static Icon getTreeIcon(TreeIcon treeIcon) {
		return treeIcons.getCachedIcon(treeIcon);
	}
	
	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		Config.readConfig();
		Ctrl.readCommProtocolFromFile();
//		new Responder().openWindow();
		
		openWindow(null,true);
		
		//  http://rx-v475/YamahaRemoteControl/desc.xml
		
//		String addr = "192.168.2.34";
//		String addr = "RX_V475";
//		commandList.readCommandList(addr,true);
	}
	
	public static void openWindow(String address, boolean asStandAlone) {
		CommandList commandList = new CommandList(address);
		commandList.createGUI(asStandAlone);
		commandList.readCommandList();
		commandList.showCommandList();
	}
	
	private TreeViewType selectedTreeViewType;
	private String selectedAddress;

	private Document document;
	private JFrame mainWindow;
	private ContextMenuHandler contextMenu;
	private MyTreeModel treeModel;
	private JTree tree;
	private JFileChooser fileChooser;

	CommandList(String selectedAddress) {
		document = null;
		selectedTreeViewType = null;
		this.selectedAddress = selectedAddress;
		contextMenu = null;
		treeModel = null;
		tree = null;
		fileChooser = null;
	}
	
	private enum TreeViewType { DOM, InterpretedDOM, ParsedCommands } 
	
	private enum ContextMenuItemType {
		TreeFunction  (node->true),
		NodeFunction  (node->(node!=null)),
		PutCommand    (node->isCallablePutCommand(node)),
		GetCommand    (node->isCallableGetCommand(node)),
		GetCommandSave(node->isCallableGetCommand(node)),
		MenuWithCommandList(node->isNodeWithCommandList(node)),
		;
		
		private Predicate<Object> checkClickedNode;
		ContextMenuItemType( Predicate<Object> checkClickedNode ) {
			this.checkClickedNode = checkClickedNode;
		}
		private static boolean isNodeWithCommandList(Object node) {
			if (!(node instanceof InterpretedDOMTreeNode.MenuNode)) return false;
			return ((InterpretedDOMTreeNode.MenuNode)node).cmdListNode!=null;
		}
		private static boolean isCallablePutCommand(Object node) {
			if (node instanceof ParsedTreeNode)
				return ((ParsedTreeNode)node).isCallablePutCommand();
			return node instanceof CallablePutCommand;
		}
		private static boolean isCallableGetCommand(Object node) {
			if (node instanceof ParsedTreeNode)
				return ((ParsedTreeNode)node).isCallableGetCommand();
			return node instanceof CallableGetCommand;
		}
	}
	
	private class ContextMenuHandler {
		
		private TreePath clickedTreePath;
		private Object clickedTreeNode;
		private JPopupMenu contextMenu;
		private Disabler<ContextMenuItemType> disabler;

		ContextMenuHandler() {
			contextMenu = new JPopupMenu();
			disabler = new Disabler<>();
			disabler.setCareFor(ContextMenuItemType.values());
			clickedTreePath = null;
			clickedTreeNode = null;
		}
		
		public void activate(int x, int y) {
			clickedTreePath = tree.getPathForLocation(x,y);
			if (clickedTreePath == null) clickedTreeNode = null;
			else clickedTreeNode = clickedTreePath.getLastPathComponent();
			
			for (ContextMenuItemType type:ContextMenuItemType.values())
				disabler.setEnable(type, type.checkClickedNode.test(clickedTreeNode));
			
			contextMenu.show(tree,x,y);
		}

		public void add(ContextMenuItemType type, String title, ActionListener l) {
			JMenuItem menuItem = new JMenuItem(title);
			if (l!=null) menuItem.addActionListener(l);
			disabler.add(type, menuItem);
			contextMenu.add(menuItem);
		}

		public void addSeparator() {
			contextMenu.addSeparator();
		}

		public Object getClickedTreeNode() {
			if (clickedTreePath==null) return null;
			return clickedTreePath.getLastPathComponent();
		}
	}
	
	private void createGUI(boolean asStandAlone) {
		fileChooser = new JFileChooser("./");
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setMultiSelectionEnabled(false);
		
		treeModel = new MyTreeModel();
		tree = new JTree(treeModel);
		tree.setCellRenderer(new TreeNodeRenderer());
		tree.setExpandsSelectedPaths(true); 
		
		JScrollPane treeScrollPane = new JScrollPane(tree);
		treeScrollPane.setPreferredSize(new Dimension(800,800));
		
		contextMenu = new ContextMenuHandler();
		contextMenu.add(ContextMenuItemType.NodeFunction, "Copy Value to Clipboard", e->YamahaControl.copyToClipBoard(getClickedNodeText()));
		contextMenu.add(ContextMenuItemType.NodeFunction, "Copy Path to Clipboard" , e->YamahaControl.copyToClipBoard(getClickedNodePath()));
		contextMenu.addSeparator();
		contextMenu.add(ContextMenuItemType.TreeFunction, "Expand Full Tree", e->expandFullTree());
		contextMenu.add(ContextMenuItemType.NodeFunction, "Expand Branch"   , e->expandBranch());
		contextMenu.addSeparator();
		contextMenu.add(ContextMenuItemType.GetCommand    , "Test Get Command"                , e->testCommand(contextMenu.getClickedTreeNode(),false));
		contextMenu.add(ContextMenuItemType.GetCommandSave, "Test Get Command (Save Response)", e->testCommand(contextMenu.getClickedTreeNode(),true));
		contextMenu.add(ContextMenuItemType.PutCommand    , "Test Put Command"                , e->testCommand(contextMenu.getClickedTreeNode(),false));
		contextMenu.addSeparator();
		contextMenu.add(ContextMenuItemType.MenuWithCommandList, "Show Command Usage", e->showCommandUsage(contextMenu.getClickedTreeNode()));

		selectedTreeViewType = TreeViewType.InterpretedDOM;
		JComboBox<TreeViewType> treeViewTypeComboBox = YamahaControl.createComboBox(TreeViewType.values());
		treeViewTypeComboBox.setSelectedItem(selectedTreeViewType);
		
		
		tree.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getButton()==MouseEvent.BUTTON3)
					contextMenu.activate(e.getX(), e.getY());
			}
		});
		
		treeViewTypeComboBox.addActionListener(e->{
			selectedTreeViewType = (TreeViewType)(treeViewTypeComboBox.getSelectedItem());
			showCommandList();
		});
		
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.X_AXIS));
		northPanel.add(YamahaControl.createButton("Get Data",true,e->{ selectedAddress = Config.selectAddress(mainWindow); readCommandList(); showCommandList(); }));
		northPanel.add(treeViewTypeComboBox);
		northPanel.add(YamahaControl.createButton("Write CommProtocol to File",true,e->Ctrl.writeCommProtocolToFile()));
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		contentPane.add(northPanel,BorderLayout.NORTH);
		contentPane.add(treeScrollPane,BorderLayout.CENTER);
		
		mainWindow = new JFrame("CommandList");
		mainWindow.setContentPane(contentPane);
		mainWindow.setDefaultCloseOperation(asStandAlone ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
		mainWindow.pack();
		mainWindow.setLocationRelativeTo(null);
		mainWindow.setVisible(true);
	}

	private void showCommandUsage(Object clickedTreeNode) {
		if (!(clickedTreeNode instanceof InterpretedDOMTreeNode.MenuNode)) return;
		InterpretedDOMTreeNode.BaseCommandListNode cmdListNode = ((InterpretedDOMTreeNode.MenuNode)clickedTreeNode).cmdListNode;
		if (cmdListNode==null) return;
		
		expandBranch();
		cmdListNode.showUsageInCommands(treeModel,true);
	}

	private void testCommand(Object clickedTreeNode, boolean saveResponse) {
		
		CallableCommand command = null;
		if (clickedTreeNode instanceof CallableCommand)
			command = (CallableCommand)clickedTreeNode;
		
		else if (clickedTreeNode instanceof ParsedTreeNode)
			command = ((ParsedTreeNode) clickedTreeNode).getCallableCommand();
		
		if (command==null) return;
		
		String xmlCommand = command.buildXmlCommand();
		if (selectedAddress==null) return;
		if (xmlCommand==null) return;
		HttpResponse response = Ctrl.testCommand(selectedAddress, xmlCommand, false);
		if (response!=null && saveResponse && fileChooser.showSaveDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			try { Files.write(file.toPath(), response.bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE); }
			catch (IOException e) { e.printStackTrace(); }
		}
	}

	private void expandBranch() {
		if (contextMenu.clickedTreePath==null) return;
		int row = tree.getRowForPath(contextMenu.clickedTreePath);
		
		TreePath selectionPath = tree.getSelectionPath();
		while (isParentPath( contextMenu.clickedTreePath, tree.getPathForRow(row) )) {
			tree.expandRow(row);
			++row;
		}
		if (selectionPath!=null)
			tree.scrollPathToVisible(selectionPath);
	}

	private boolean isParentPath(TreePath parentPath, TreePath childPath) {
		if (childPath==null) return false;
		if (parentPath.equals(childPath)) return true;
		return isParentPath(parentPath, childPath.getParentPath());
	}

	private void expandFullTree() {
		TreePath selectionPath = tree.getSelectionPath();
		for (int row=0; row<tree.getRowCount(); ++row)
			if (!tree.isExpanded(row))
				tree.expandRow(row);
		if (selectedTreeViewType==TreeViewType.InterpretedDOM)
			InterpretedDOMTreeNode.showUnknownTagNames();
		if (selectionPath!=null)
			tree.scrollPathToVisible(selectionPath);
	}

	private void showCommandList() {
		if (document==null) return;
		if (selectedTreeViewType==null)
			treeModel.setRoot(null);
		else
			if (findUnsupportedNodeTypes(document))
				JOptionPane.showMessageDialog(mainWindow, "Found unsupported node type in DOM. Please take a look into log for details.", "Unknown Node Type", JOptionPane.ERROR_MESSAGE);
			else {
				switch(selectedTreeViewType) {
				case DOM           : treeModel.setRoot(true, new DOMTreeNode(document)); break;
				case InterpretedDOM: treeModel.setRoot(true, InterpretedDOMTreeNode.createRootNode(document)); break;
				case ParsedCommands: treeModel.setRoot(true, new ParsedTreeNode(new ParsedCommandItem.DocumentItem(document))); break;
				}
				//treeModel.getR
				//tree.getSelec
			}
	}

	private String getClickedNodePath() {
		Object pathComp = contextMenu.getClickedTreeNode();
		if (pathComp instanceof DOMTreeNode           ) return XML.getPathStr(((DOMTreeNode)pathComp).node);;
		if (pathComp instanceof InterpretedDOMTreeNode) return XML.getPathStr(((InterpretedDOMTreeNode)pathComp).node);
		return null;
	}

	private String getClickedNodeText() {
		Object pathComp = contextMenu.getClickedTreeNode();
		if (pathComp == null) return null;
		return pathComp.toString();
	}

	private void readCommandList() {
		if (selectedAddress==null) return;
		boolean verbose = true;
		String content = Ctrl.http.getContentFromURL("http://"+selectedAddress+"/YamahaRemoteControl/desc.xml", verbose );
		document = content==null?null:XML.parse(content);
		if (verbose) System.out.println("done");
	}

	public static boolean findUnsupportedNodeTypes(Document document) {
		HashMap<Short, Integer> foundNodeTypes = new HashMap<>();
		addToNodeTypes(document,foundNodeTypes);
		boolean foundUnknownNodeTypes = false;
		for (Entry<Short, Integer> entry:foundNodeTypes.entrySet()) {
			switch (entry.getKey()) {
			case Node.DOCUMENT_NODE     : break;
			case Node.ELEMENT_NODE      : break;
			case Node.TEXT_NODE         : break;
			case Node.COMMENT_NODE      : break;
			case Node.CDATA_SECTION_NODE: break;
			default: foundUnknownNodeTypes = true; break;
			}
		}
		if (foundUnknownNodeTypes) {
			System.out.println("Found NodeTypes in DOM:");
			for (Entry<Short, Integer> entry:foundNodeTypes.entrySet()) {
				switch (entry.getKey()) {
				case Node.DOCUMENT_NODE     : System.out.println("   DOCUMENT: "+entry.getValue()); break;
				case Node.ELEMENT_NODE      : System.out.println("   ELEMENT : "+entry.getValue()); break;
				case Node.TEXT_NODE         : System.out.println("   TEXT    : "+entry.getValue()); break;
				case Node.COMMENT_NODE      : System.out.println("   COMMENT : "+entry.getValue()); break;
				case Node.CDATA_SECTION_NODE: System.out.println("   CDATA   : "+entry.getValue()); break;
				default: System.err.println("   Unknown["+entry.getKey()+"]: "+entry.getValue()); break;
				}
			}
		}
		return foundUnknownNodeTypes;
	}

	private static void addToNodeTypes(Node node, HashMap<Short, Integer> foundNodeTypes) {
		short nodeType = node.getNodeType();
		foundNodeTypes.put(nodeType, foundNodeTypes.getOrDefault(nodeType,0)+1);
		NodeList childNodes = node.getChildNodes();
		for (int i=0; i<childNodes.getLength(); ++i)
			addToNodeTypes(childNodes.item(i),foundNodeTypes);
	}

	public static void reportError(String className, Node node, String format, Object... values) {
		System.err.printf("[%s] %s [%s]%n", className, String.format(format, values), XML.getPathStr(node));
	}
	
	private static interface CallableGetCommand extends CallableCommand {}
	private static interface CallablePutCommand extends CallableCommand {}
	private static interface CallableCommand {
		public abstract String buildXmlCommand();
	}
	
	private final class MyTreeModel extends DefaultTreeModel {
		private static final long serialVersionUID = -4484078375995511354L;
		
		public MyTreeModel() {
			super(null);
		}

		public void setRoot(boolean keepSelection, BaseTreeNode<?> root) {
			if (!keepSelection) {
				super.setRoot(root);
				return;
			}
			
			Vector<Node> selectedXmlPath = getSelectedXmlPath();
			super.setRoot(root);
			setSelectedXmlPath(root, selectedXmlPath);
		}

		private Vector<Node> getSelectedXmlPath() {
			TreePath selectedPath = tree.getSelectionPath();
			
			Node node = null;
			while(selectedPath != null) {
				Object obj = selectedPath.getLastPathComponent();
				if (obj instanceof BaseTreeNode) {
					node = ((BaseTreeNode<?>) obj).node;
					if (node!=null) break;
				}
				selectedPath = selectedPath.getParentPath();
			}
			
			return node==null ? null : XML.getPath(node);
		}

		private void setSelectedXmlPath(BaseTreeNode<?> root, Vector<Node> selectedXmlPath) {
			if (selectedXmlPath == null) return;
			
			Object[] path = root.getSubNodePath(selectedXmlPath).toArray();
			if (path.length <= 0) return;
			
			TreePath selectedPath = new TreePath(path);
			tree.setSelectionPath(selectedPath);
			tree.scrollPathToVisible(selectedPath);
		}

		@Override
		public void setRoot(TreeNode root) {
			throw new UnsupportedOperationException();
		}
	}
	
	static class LanguageConfig {
		
		private HashMap<String,String> fieldNames;
		private HashMap<String,String> langCodes;
		
		public LanguageConfig() {
			fieldNames = new HashMap<>();
			langCodes = new HashMap<>();
		}
		public void setValues(Vector<ParsedCommandItem.LanguageItem> languages) {
			//setValue("test", "Title_1");
			for (ParsedCommandItem.LanguageItem item:languages)
				setValue(item.code, item.fieldName);
		}
		public void setDefaultValues() {
			//setValue("test", "Title_1");
			setValue("en", "Title_1");
		}
		
		private void setValue(String langCode, String fieldName) {
			fieldNames.put(langCode, fieldName);
			langCodes.put(fieldName, langCode);
		}
		String getFieldName(String langCode) { return fieldNames.get(langCode); }
		String getLangCode(String fieldName) { return langCodes.get(fieldName); }
		
		public Vector<String> getLangCodes() {
			return new Vector<>( fieldNames.keySet() );
		}
	}
	
	static class LanguageStrings {
		
		private HashMap<String,String> values;
		LanguageStrings() { values = new HashMap<>(); }
		
		boolean isEmpty() { return values.isEmpty(); }
		void add(String langCode, String value) { values.put(langCode, value); }
		String get(String langCode) { return values.get(langCode); }
		
		@Override public String toString() {
			String str = "";
			for (String langCode:values.keySet()) {
				if (!str.isEmpty())  str+=", ";
				str+="["+langCode+"]"+values.get(langCode);
			}
			return str;
		}

	}
	
	private interface Helper<V> {
		V createGet(String baseLabel, TagList baseTagList, String value);
		V createGet(String baseLabel, TagList baseTagList);
		V createPut(String baseLabel, TagList baseTagList, String tagListAddStr, String conn, String value);
		V createPut(String baseLabel, TagList baseTagList);
		void addTextTo(V parsedCommandNode, String format, Object...objects);
	}
	
	private static <V> V createCommandTreeNode(ComplexCommand complexCommand, Helper<V> helper) {
		V parsedCommandNode = null;
		String baseLabel;
		switch (complexCommand.type) {
		case Get:
			baseLabel = "GET["+complexCommand.cmd.cmdID+"]";
			if (complexCommand.cmd.type!=null) baseLabel += " <"+complexCommand.cmd.type+">";
			if (complexCommand.cmd.params.length==1) {
				ComplexCommand.CmdParam cp = complexCommand.cmd.params[0];
				String tagListStr = cp.valueTagList==null?"":(cp.valueTagList.toString()+" -> ");
				parsedCommandNode = helper.createGet(baseLabel, complexCommand.cmd.baseTagList, tagListStr+cp.param.toString());
			} else {
				parsedCommandNode = helper.createGet(baseLabel, complexCommand.cmd.baseTagList);
				for (int i=0; i<complexCommand.cmd.params.length; ++i) {
					ComplexCommand.CmdParam cp = complexCommand.cmd.params[i];
					String tagListStr = cp.valueTagList==null?"":(cp.valueTagList.toString()+" -> ");
					helper.addTextTo(parsedCommandNode, "Value %d:   %s%s", i, tagListStr, cp.param.toString());
				}
			}
			break;
		case Put:
			baseLabel = "PUT["+complexCommand.cmd.cmdID+"]";
			if (complexCommand.cmd.type!=null) baseLabel += " <"+complexCommand.cmd.type+">";
			if (complexCommand.cmd.params.length==1) {
				ComplexCommand.CmdParam cp = complexCommand.cmd.params[0];
				String tagListAddStr = cp.valueTagList==null?"":(",  "+cp.valueTagList.toString());
				parsedCommandNode = helper.createPut(baseLabel, complexCommand.cmd.baseTagList, tagListAddStr, "=", cp.param.toString());
			} else {
				parsedCommandNode = helper.createPut(baseLabel, complexCommand.cmd.baseTagList);
				for (int i=0; i<complexCommand.cmd.params.length; ++i) {
					ComplexCommand.CmdParam cp = complexCommand.cmd.params[i];
					String tagListStr = cp.valueTagList==null?"":(cp.valueTagList.toString()+" = ");
					helper.addTextTo(parsedCommandNode, "Value %d:   %s%s", i, tagListStr, cp.param.toString());
				}
			}
			break;
		}
		return parsedCommandNode;
	}
	
	protected static interface AttributeConsumer {
		public boolean consume(String attrName, String attrValue);
	}
	
	static class ComplexCommand {
		Cmd cmd;
		protected boolean error;
		private Node node;
		private ComplexCommandContext context;
		Type type;
		
		interface ComplexCommandContext {
			void parseAttributes(Node node, AttributeConsumer attrConsumer);
			String getContentOfSingleChildTextNode(Node node);
			BaseCommandList getCmdListFromParent();
			LanguageConfig getLanguageConfig();
		}
		interface BaseCommandList {
			TagList get(String cmdID);
		}
		
		ComplexCommand(Node node, Type type, ComplexCommandContext context) {
			this.node = node;
			this.type = type;
			this.context = context;
			cmd = null;
			error = false;
			this.context.parseAttributes(node,(attrName, attrValue) -> { error=true; return false; });
			parseChildNodes();
			if (!error) ParamValueOcc.checkParamValueOcc(this);
		}
		
		static class ParamValueOcc {
			static HashMap<ParamValueOcc,Integer> ParamValueOccurencies = new HashMap<>();
			
			private static void checkParamValueOcc(ComplexCommand complexCommand) {
				ParamValueOcc occ = new ParamValueOcc();
				occ.type = complexCommand.type;
				CmdParam[] params = complexCommand.cmd.params;
				for (int i=0; i<params.length; i++)
					occ.set(i,params[i].param.values.size());
				Integer n = ParamValueOccurencies.get(occ);
				if (n==null) n = 0;
				ParamValueOccurencies.put(occ,n+1);
			}
			public static void print() {
				System.out.println("ParamValue Occurencies:");
				ParamValueOccurencies.keySet()
					.stream()
					.sorted(Comparator.<ParamValueOcc,Type>comparing(occ->occ.type).thenComparing(occ->occ.n1).thenComparing(occ->occ.n2).thenComparing(occ->occ.n3))
					.forEach(occ->System.out.printf(" %4dx %s%n",ParamValueOccurencies.get(occ),occ));
			}

			public Type type;
			public int n1,n2,n3;
			ParamValueOcc() {
				type = null;
				n1 = n2 = n3 = -1;
			}
			public void set(int i, int n) {
				switch (i) {
				case 0: n1 = n; break;
				case 1: n2 = n; break;
				case 2: n3 = n; break;
				default: throw new IllegalArgumentException();
				}
			}
			@Override
			public int hashCode() {
				int hashCode = type==null?0:type.hashCode();
				hashCode = hashCode ^ (((n1&0xff)<<16) | ((n2&0xff)<<8) | (n3&0xff));
				return hashCode;
			}
			@Override
			public boolean equals(Object obj) {
				if (!(obj instanceof ParamValueOcc)) return false;
				ParamValueOcc other = (ParamValueOcc)obj;
				return this.type==other.type && this.n1==other.n1 && this.n2==other.n2 && this.n3==other.n3;
			}
			@Override
			public String toString() {
				return type + ":" + (n1<0?" ":n1) + "," + (n2<0?" ":n2) + "," + (n3<0?" ":n3);
			}
		}
		
		enum Type { Get,Put }
		
		TreeIcon getIcon() {
			switch (type) {
			case Get: return TreeIcon.Command_GET;
			case Put: return TreeIcon.Command_PUT;
			default: return TreeIcon.Command;
			}
		}

		public void reportError(String format, Object... values) {
			reportError(node, format, values);
		}
		public void reportError(Node node, String format, Object... values) {
			CommandList.reportError(this.getClass().getSimpleName(), node, format, values);
		}
		
		protected void parseChildNodes() {
			error = false;
			boolean cmdFound=false, param1Found=false, param2Found=false, param3Found=false;
			NodeList childNodes = node.getChildNodes();
			Param param1 = null;
			Param param2 = null;
			Param param3 = null;
			for (int i=0; i<childNodes.getLength(); ++i) {
				Node child = childNodes.item(i);
				
				if (child.getNodeType()!=Node.ELEMENT_NODE || !(child instanceof Element)) {
					reportError("Found child [%d] with unexpected node type. (found:%s, expected:%s)", i, XML.getLongName(child.getNodeType()), XML.getLongName(Node.ELEMENT_NODE));
					error = true;
					//children.add(new GenericXMLNode(this, child));
					continue;
				}
				
				Element childElement = (Element)child;
				switch (childElement.getTagName()) {
				case "Cmd":
					if (cmd!=null) reportError("Found additional \"%s\" node in command.",childElement.getTagName());
					else cmd = parseCmdNode(childElement);
					cmdFound = true;
					break;
					
				case "Param_1":
					if (param1!=null) reportError("Found additional \"%s\" node in command.",childElement.getTagName());
					else param1 = parseParamNode(childElement);
					param1Found = true;
					break;
					
				case "Param_2":
					if (param2!=null) reportError("Found additional \"%s\" node in command.",childElement.getTagName());
					else param2 = parseParamNode(childElement);
					param2Found = true;
					break;
					
				case "Param_3":
					if (param3!=null) reportError("Found additional \"%s\" node in command.",childElement.getTagName());
					else param3 = parseParamNode(childElement);
					param3Found = true;
					break;
					
				default:
					reportError("Found unexpected \"%s\" node in command.", childElement.getTagName());
					error = true;
					break;
				}
			}
			
			if (!cmdFound) { reportError("Can't find any \"Cmd\" node in command."); error=true; }
			if (!param1Found) { reportError("Can't find any \"Param_1\" node in command."); error=true; }
			if (!param2Found && param3Found) { reportError("Found \"Param_3\" node but no \"Param_2\" node in command."); error=true; }
			
			if (cmd!=null) {
				switch (cmd.params.length) {
				case 0: reportError("No values in Cmd node."); break;
				case 1: if (!param1Found ||  param2Found ||  param3Found) { reportError("Different number of values in Cmd node and number of Param nodes."); error=true; } break;
				case 2: if (!param1Found || !param2Found ||  param3Found) { reportError("Different number of values in Cmd node and number of Param nodes."); error=true; } break;
				case 3: if (!param1Found || !param2Found || !param3Found) { reportError("Different number of values in Cmd node and number of Param nodes."); error=true; } break;
				default: reportError("To many values (%d) in Cmd node.", cmd.params.length); error=true; break;
				}
				// "Sound_Video,Tone,Bass,Val=Param_1:Sound_Video,Tone,Bass,Exp=Param_2:Sound_Video,Tone,Bass,Unit=Param_3"
				for (int i=0; i<cmd.params.length; ++i) {
					CmdParam cmdParam = cmd.params[i];
					switch (cmdParam.paramID) {
					case "Param_1": cmdParam.param = param1; break;
					case "Param_2": cmdParam.param = param2; break;
					case "Param_3": cmdParam.param = param3; break;
					default: reportError("Found unexpected param name (\"%s\") in \"Cmd\" node.", cmdParam.paramID); error=true; break;
					}
				}
			}
		}
		
		private Cmd parseCmdNode(Element cmdNode) {
			Cmd cmd = new Cmd();
			context.parseAttributes(cmdNode, (attrName, attrValue) -> {
				switch (attrName) {
				case "ID"  : cmd.cmdID = attrValue; break;
				case "Type": cmd.type  = attrValue; break;
				default: error=true; return false;
				}
				return true;
			});
			
			BaseCommandList cmdList = context.getCmdListFromParent();
			if (cmdList==null) { reportError("Can't find command list for command node."); error=true; }
			if (cmd.cmdID==null) { reportError("Found \"Cmd\" child of command node with no ID."); error=true; }
			
			if (cmdList!=null && cmd.cmdID!=null) {
				cmd.baseTagList = cmdList.get(cmd.cmdID);
				if (cmd.baseTagList==null) {
					reportError("Found \"Cmd\" child of command node with ID \"%s\", that isn't associated with a command.", cmd.cmdID);
					error=true; return null;
				}
			} else { error=true; return null; }
			
			String cmdValueStr = context.getContentOfSingleChildTextNode(cmdNode);
			if (cmdValueStr==null) { error=true; return null; }
			
			String[] cmdValueParts = cmdValueStr.split(":");
			cmd.params = new CmdParam[cmdValueParts.length];
			for (int i=0; i<cmdValueParts.length; ++i) {
				String str = cmdValueParts[i];
				int pos = str.indexOf('=');
				if (pos<0) cmd.params[i] = new CmdParam(null,str);
				else       cmd.params[i] = new CmdParam(str.substring(0,pos),str.substring(pos+1));
			}
			
			return cmd;
		}
		
		private Param parseParamNode(Element paramNode) {
			Param param = new Param();
			context.parseAttributes(paramNode, (attrName, attrValue) -> { if ("Func".equals(attrName)) { param.func = attrValue; return true; } error=true; return false; });
			
			NodeList childNodes = paramNode.getChildNodes();
			for (int i=0; i<childNodes.getLength(); ++i) {
				Node child = childNodes.item(i);
				if (child.getNodeType()!=Node.ELEMENT_NODE || !(child instanceof Element)) {
					reportError("Found child [%d] inside of param node of command node with unexpected node type. (found:%s, expected:%s)", i, XML.getLongName(child.getNodeType()), XML.getLongName(Node.ELEMENT_NODE));
					error=true; continue;
				}
				
				Element childElement = (Element)child;
				switch (childElement.getTagName()) {
				case "Text"    : param.values.add((TextValue    )parseTextValue    (childElement)); break;
				case "Range"   : param.values.add((RangeValue   )parseRangeValue   (childElement)); break;
				case "Direct"  : param.values.add((DirectValue  )parseDirectValue  (childElement)); break;
				case "Indirect": param.values.add((IndirectValue)parseIndirectValue(childElement)); break;
				default:
					reportError("Found unexpected \"%s\" node in param node.", childElement.getTagName());
					error=true;
					break;
				}
			}
			return param;
		}
		
		private TextValue parseTextValue(Element childElement) {
			TextValue value = new TextValue();
			context.parseAttributes(childElement, (attrName, attrValue) -> { error=true; return false; });
			
			String textRangeStr = context.getContentOfSingleChildTextNode(childElement);
			if (textRangeStr==null) { error=true; return null; }
			
			// "1,15,UTF-8"
			String[] parts = textRangeStr.split(",");
			if (parts.length!=3) {
				reportError("Found unexpected number of parts in text value (\"%s\") of param node. (found:%d, expected:%d)", textRangeStr, parts.length, 3);
				error=true; return null;
			}
			
			try { value.minLength = Integer.parseInt(parts[0]); }
			catch (NumberFormatException e) {
				reportError("Can't parse 1st part (minLength) in text value (\"%s\") of param node. (part:\"%s\")", textRangeStr, parts[0]);
				error=true; return null;
			}
			
			try { value.maxLength = Integer.parseInt(parts[1]); }
			catch (NumberFormatException e) {
				reportError("Can't parse 2nd part (maxLength) in text value (\"%s\") of param node. (part:\"%s\")", textRangeStr, parts[1]);
				error=true; return null;
			}
			
			value.charset = parts[2];
			
			if (value.minLength>value.maxLength) {
				reportError("Parsed wrong minLength (%d) and maxLength (%d) of text value (\"%s\") of param node.", value.minLength, value.maxLength, textRangeStr);
				error=true; return null;
			}
			if (!value.charset.equalsIgnoreCase("UTF-8") && !value.charset.equalsIgnoreCase("Ascii") && !value.charset.equalsIgnoreCase("Latin-1")) {
				reportError("Found unexpected charset (%s) in text value (\"%s\") of param node.", value.charset, textRangeStr);
				error=true; return null; 
			}
			
			return value;
		}
	
		private RangeValue parseRangeValue(Element childElement) {
			RangeValue value = new RangeValue();
			context.parseAttributes(childElement, (attrName, attrValue) -> { error=true; return false; });
			
			String numberRangeStr = context.getContentOfSingleChildTextNode(childElement);
			if (numberRangeStr==null) { error=true; return null; }
			
			// "-805,165,5"
			String[] parts = numberRangeStr.split(",");
			if (parts.length!=3 && parts.length!=4) {
				reportError("Found unexpected number of parts in range value (\"%s\") of param node. (found:%d, expected:%s)", numberRangeStr, parts.length, "3 or 4");
				error=true; return null;
			}
			try { value.minValue = Integer.parseInt(parts[0]); }
			catch (NumberFormatException e) {
				reportError("Can't parse 1st part (minValue) in range value (\"%s\") of param node. (part:\"%s\")", numberRangeStr, parts[0]);
				error=true; return null;
			}
			try { value.maxValue = Integer.parseInt(parts[1]); }
			catch (NumberFormatException e) {
				reportError("Can't parse 2nd part (maxValue) in range value (\"%s\") of param node. (part:\"%s\")", numberRangeStr, parts[1]);
				error=true; return null;
			}
			try { value.stepWidth = Integer.parseInt(parts[2]); }
			catch (NumberFormatException e) {
				reportError("Can't parse 3rd part (stepWidth) in range value (\"%s\") of param node. (part:\"%s\")", numberRangeStr, parts[2]);
				error=true; return null;
			}
			
			if (parts.length==4)
				value.format = parts[3];
			
			if (value.minValue>value.maxValue) {
				reportError("Parsed wrong minValue (%d) and maxValue (%d) of range value (\"%s\") of param node.", value.minValue, value.maxValue, numberRangeStr);
				error=true; return null;
			}
			if (value.stepWidth<=0) {
				reportError("Parsed wrong stepWidth (%d) of range value (\"%s\") of param node.", value.stepWidth, numberRangeStr);
				error=true; return null;
			}
			
			return value;
		}
	
		private DirectValue parseDirectValue(Element childElement) {
			DirectValue value = new DirectValue();
			context.parseAttributes(childElement, (attrName, attrValue) -> {
				boolean expected = true;
				switch (attrName) {
				case "Func"    : value.func     = attrValue; break;
				case "Func_Ex" : value.funcEx   = attrValue; break;
				case "Playable": value.playable = attrValue; break;
				case "Icon_on" : value.iconOn   = attrValue; break;
				default: expected = false; break;
				}
				String langCode = context.getLanguageConfig().getLangCode(attrName);
				if (langCode!=null) {
					value.titles.add(langCode, attrValue);
					expected = true;
				}
				
				error = !expected; 
				return expected;
			});
			
			if (!childElement.hasChildNodes())
				value.isDummy = true;
			else {
				value.isDummy = false;
				value.value = context.getContentOfSingleChildTextNode(childElement);
				if (value.value==null) { error=true; return null; }
			}
			
			return value;
		}
	
		private IndirectValue parseIndirectValue(Element childElement) {
			IndirectValue value = new IndirectValue();
			context.parseAttributes(childElement, (attrName, attrValue) -> { if ("ID".equals(attrName)) { value.cmdID = attrValue; return true; } error=true; return false; });
			if (value.cmdID==null) {
				reportError("Found \"Indirect\" value of param node with no ID.");
				error=true; return null;
			}
			
			BaseCommandList cmdList = context.getCmdListFromParent();
			if (cmdList==null) reportError("Can't find command list for command node.");
			if (value.cmdID==null) reportError("Found \"Indirect\" value of param node with no ID.");
			
			if (cmdList!=null && value.cmdID!=null) {
				value.tagList = cmdList.get(value.cmdID);
				if (value.tagList==null) {
					reportError("Found \"Indirect\" value of param node with ID \"%s\", that isn't associated with a command.", value.cmdID);
					error=true; return null;
				}
			} else { error=true; return null; }
			
			return value;
		}
	
		protected static class Cmd {
			public String type;
			public String cmdID;
			public TagList baseTagList;
			public CmdParam[] params;
			
			public Cmd() {
				this.type = null;
				this.cmdID = null;
				this.baseTagList = null;
				this.params = null;
			}
			
			public Vector<IndirectValue> getIndirectValues() {
				Vector<IndirectValue> indirectValues = new Vector<>();
				for (CmdParam cmdParam:params)
					for (ParamValue paramValue:cmdParam.param.values)
						if (paramValue instanceof IndirectValue)
							indirectValues.add((IndirectValue) paramValue);
				return indirectValues;
			}
		}
	
		protected static class CmdParam {
			public TagList valueTagList;
			public String paramID;
			public Param param;
			public CmdParam(String valueTagList, String paramID) {
				this.valueTagList = valueTagList==null?null:new TagList(valueTagList);
				this.paramID = paramID;
				this.param = null;
			}
		}
	
		protected static class Param {
			public String func;
			public Vector<ParamValue> values;
			public Param() {
				this.func = null;
				this.values = new Vector<>();
			}
			public String mergeValues(String openBracket, String spearator, String closeBracket) {
				return openBracket+values.stream().map(v->v==null?"<null>":v.toString()).reduce(null,(a,b)->a!=null?(a+spearator+b):b)+closeBracket;
			}
			@Override public String toString() {
				return (func==null?"":"["+func+"]:") + mergeValues(""," | ","");
			}
		}
	
		protected static abstract class ParamValue {
			public abstract String getLabel();
			@Override public abstract String toString();
		}
	
		protected static class TextValue extends ParamValue {
			public int minLength;
			public int maxLength;
			public String charset;
			
			public TextValue() {
				this.minLength = Integer.MAX_VALUE;
				this.maxLength = Integer.MIN_VALUE;
				this.charset = null;
			}
			@Override public String toString() {
				return "Text: "+minLength+".."+maxLength+" ("+charset+")";
			}
			@Override public String getLabel() {
				return "Text";
			}
		}
	
		protected static class RangeValue extends ParamValue {
			public int minValue;
			public int maxValue;
			public int stepWidth;
			public String format;
			
			public RangeValue() {
				this.minValue = Integer.MAX_VALUE;
				this.maxValue = Integer.MIN_VALUE;
				this.stepWidth = -1;
				this.format = null;
			}
			@Override public String toString() {
				String range = minValue+".."+(stepWidth==1?"":("("+stepWidth+").."))+maxValue;
				if (format == null) return "Number: "+range;
				else                return "Label: "+format+" ("+range+")";
			}
			@Override public String getLabel() {
				if (format == null) return "Number";
				else                return "Label";
			}
		}
	
		protected static class DirectValue extends ParamValue {
			public String value;
			public boolean isDummy;
			
			public String func;
			public String funcEx;
			public String playable;
			public String iconOn;
			public LanguageStrings titles;
			
			DirectValue() {
				this.func     = null;
				this.funcEx   = null;
				this.playable = null;
				this.iconOn   = null;
				this.titles   = new LanguageStrings();
				
				this.value   = null;
				this.isDummy = false;
			}
			@Override public String toString() {
				if (isDummy) return "<no value>";
				return "\""+value+"\"";
				//return "Text: "+minLength+".."+maxLength+" letters (charset: "+charset+")";
			}
			
			String getTags() {
				String str = "";
				if (func  !=null) str += (str.isEmpty()?"":" | ")+func  ;
				if (funcEx!=null) str += (str.isEmpty()?"":" | ")+funcEx;
				return str;
			}
			public String getFullLabel() {
				String str = getLabel();
				if (iconOn  !=null) str += " IconOn:"  +iconOn  ;
				if (playable!=null) str += " Playable:"+playable;
				return str;
			}
			@Override public String getLabel() {
				String str = "";
				if (!titles.isEmpty()) str = titles.toString()+"   ";
				str += "["+getTags()+"]   ";
				return str;
			}
		}
		
		protected static class IndirectValue extends ParamValue {
			public TagList tagList;
			public String cmdID;
			public DeviceDefinedValue[] values;
					
			IndirectValue() {
				cmdID   = null;
				tagList = null;
				values  = null;
			}
			@Override public String toString() {
				return "Values [GET["+cmdID+"]:"+tagList.toString()+"]";
			}
			@Override public String getLabel() {
				return "Indirect";
			}
		}
		
		static class IndirectValues {
			private HashMap<TagList, Vector<IndirectValue>> values;
			IndirectValues() {
				values = new HashMap<>();
			}
			public void add(Vector<IndirectValue> indirectValues) {
				for (IndirectValue iv:indirectValues) {
					TagList tagList = iv.tagList;
					Vector<IndirectValue> ivArr = values.get(tagList);
					if (ivArr==null) values.put(tagList, ivArr = new Vector<>());
					ivArr.add(iv);
				}
			}
			public void forEach(BiConsumer<TagList, Vector<IndirectValue>> action) {
				values.forEach(action);
			}
			public void getValues(String address, TagList tagList) {
				Vector<IndirectValue> ivArr = values.get(tagList);
				if (ivArr==null) return;
				DeviceDefinedValue[] ddvArr = DeviceDefinedValue.getValues(address, tagList);
				for (IndirectValue iv:ivArr) iv.values = ddvArr;
			}
			public void getValues(String address) {
				values.forEach((tagList, ivArr) -> {
					DeviceDefinedValue[] ddvArr = DeviceDefinedValue.getValues(address, tagList);
					for (IndirectValue iv:ivArr) iv.values = ddvArr;
				});
			}
		}
		
		static class DeviceDefinedValue {
			public String ID;
			public String rw;
			public String title;
			public String srcName;
			public String srcNumber;
			
			public DeviceDefinedValue() {
				this.ID = null;
				this.rw = null;
				this.title = null;
				this.srcName = null;
				this.srcNumber = null;
			}
			
			public DeviceDefinedValue(DeviceDefinedValue ddv) {
				this.ID        = ddv.ID       ;
				this.rw        = ddv.rw       ;
				this.title     = ddv.title    ;
				this.srcName   = ddv.srcName  ;
				this.srcNumber = ddv.srcNumber;
			}

			static DeviceDefinedValue[] getValues(String address, TagList tagList) {
				return getValues(address, tagList, "");
			}
			static DeviceDefinedValue[] getValues(String address, TagList tagList, String infoString) {
				Node node = Ctrl.sendGetCommand_Node(address, infoString, tagList);
				if (node == null) return null;
				
				NodeList itemNodes = node.getChildNodes();
				DeviceDefinedValue[] ddvArr = new DeviceDefinedValue[itemNodes.getLength()];
				for (int i=0; i<itemNodes.getLength(); ++i)
					ddvArr[i] = parse(itemNodes.item(i));
				return ddvArr;
			}
			static DeviceDefinedValue parse(Node node) {
				DeviceDefinedValue ddv = new DeviceDefinedValue();
				XML.forEachChild(node, value->{
					switch (value.getNodeName()) {
					case "Param"     : ddv.ID        = XML.getSubValue(value); break;
					case "RW"        : ddv.rw        = XML.getSubValue(value); break;
					case "Title"     : ddv.title     = XML.getSubValue(value); break;
					case "Src_Name"  : ddv.srcName   = XML.getSubValue(value); break;
					case "Src_Number": ddv.srcNumber = XML.getSubValue(value); break;
					}
				});
				return ddv;
			}
		}
	}
	private static abstract class BaseTreeNode<Type extends BaseTreeNode<Type>> implements TreeNode {
		
		protected BaseTreeNode<?> parent;
		protected Node node;
		protected Vector<Type> children;
		
		BaseTreeNode(BaseTreeNode<Type> parent, Node node) {
			this.parent = parent;
			this.node = node;
			this.children = null;
		}
		
		public Vector<BaseTreeNode<?>> getSubNodePath(Vector<Node> xmlPath) {
			Vector<BaseTreeNode<?>> treePath = new Vector<>();
			if (xmlPath.isEmpty()) return treePath;
			if (node != xmlPath.get(0)) return treePath;
			
			treePath.add(this);
			
			BaseTreeNode<Type> child = this;
			int index = 1;
			while (index<xmlPath.size() && (child=child.getChild(xmlPath.get(index++)))!=null) {
				treePath.add(child);
			}
			
			return treePath;
		}
		
		public Type getChild(Node node) {
			if (children==null) createChildren();
			for (Type child:children)
				if (child.node==node)
					return child;
			return null;
		}

		protected abstract void createChildren();
		@Override public abstract String toString();
		
		@Override public TreeNode getParent()         { return parent; }
		@Override public boolean  getAllowsChildren() { return true; }
		@Override public boolean  isLeaf()            { return getChildCount()==0; }
		
		@Override public int               getChildCount()            { if (children==null) createChildren(); return children.size(); }
		@Override public int               getIndex(TreeNode node)    { if (children==null) createChildren(); return children.indexOf(node); }
		@Override public TreeNode          getChildAt(int childIndex) { if (children==null) createChildren(); return children.get(childIndex); }
		@Override public Enumeration<Type> children()                 { if (children==null) createChildren(); return children.elements(); }
		
	}
	
	private static final class DOMTreeNode extends BaseTreeNode<DOMTreeNode> {
	
		public DOMTreeNode(Document document) {
			this(null,document);
		}
		
		public DOMTreeNode(DOMTreeNode parent, Node node) {
			super(parent, node);
		}

		@Override protected void createChildren() {
			children = new Vector<>();
			NodeList childNodes = node.getChildNodes();
			for (int i=0; i<childNodes.getLength(); ++i)
				children.add(new DOMTreeNode(this, childNodes.item(i)));
		}
		
		@Override public String toString() {
			return XML.toString(node);
			//return "DOMTreeNode [parent=" + parent + ", node=" + node + ", children=" + children + "]";
		}
		
		@Override public int getChildCount()         { return node.getChildNodes().getLength(); }
	}
	
	private static abstract class InterpretedDOMTreeNode extends BaseTreeNode<InterpretedDOMTreeNode> {
		
		public static final HashMap<String,Integer> unknownTagNames = new HashMap<>();
		
		public static void showUnknownTagNames() {
			if (unknownTagNames.isEmpty()) return;
			System.out.println("Unknown TagNames in DOM:");
			for (Entry<String, Integer> entry:unknownTagNames.entrySet())
				System.out.printf("   %-20s: %d%n", entry.getKey(), entry.getValue());
		}
	
		public static InterpretedDOMTreeNode createRootNode(Document document) {
			unknownTagNames.clear();
			return new DocumentNode(document);
		}
	
		private static InterpretedDOMTreeNode createTreeNode(InterpretedDOMTreeNode parent, Node node) {
			switch (node.getNodeType()) {
			
			case Node.ELEMENT_NODE:
				return createTreeNode(parent, (Element)node);
			
			case Node.TEXT_NODE:
			case Node.COMMENT_NODE:
			case Node.CDATA_SECTION_NODE: {
				String formatStr = "\"%s\"";
				switch (node.getNodeType()) {
				case Node.TEXT_NODE         : formatStr = "\"%s\""; break;
				case Node.COMMENT_NODE      : formatStr = "<!-- %s -->"; break;
				case Node.CDATA_SECTION_NODE: formatStr = "[CDATA %s ]"; break;
				}
				parent.reportError("Found unparsed text node: %s", node.getNodeValue());
				return new TextNode(parent,node, formatStr, node.getNodeValue());
			}
			
			}
			throw new IllegalStateException("");
		}
	
		private static InterpretedDOMTreeNode createTreeNode(InterpretedDOMTreeNode parent, Element node) {
			switch (node.getTagName()) {
			case "Unit_Description": return new UnitDescriptionNode (parent, node);
			case "Language"        : return new LanguageNode        (parent, node);
			case "Menu"            : return new MenuNode            (parent, node);
			case "Cmd_List"        : return new BaseCommandListNode (parent, node);
			case "Put_1"           : return new SimplePutCommandNode(parent, node);
			case "Put_2"           : return ComplexCommandNode.createPutCommand(parent, node);
			case "Get"             : return ComplexCommandNode.createGetCommand(parent, node);
			}
			unknownTagNames.put(node.getNodeName(), 1+unknownTagNames.getOrDefault(node.getNodeName(),0));
			return new GenericXMLNode(parent,node);
			//return new TextNode(parent, "Unknown Element \"%s\"", node.getNodeName());
		}
		
		private TreeIcon icon;
	
		public InterpretedDOMTreeNode(InterpretedDOMTreeNode parent, Node node, TreeIcon icon) {
			super(parent, node);
			this.icon = icon;
		}
	
		public TreeIcon getIcon() {
			return icon;
		}
		
		public void reportError(String format, Object... values) {
			reportError(node, format, values);
		}
		public void reportError(Node node, String format, Object... values) {
			CommandList.reportError(this.getClass().getSimpleName(), node, format, values);
		}
	
		protected void createChildren(Node node) {
			children = new Vector<>();
			NodeList childNodes = node.getChildNodes();
			for (int i=0; i<childNodes.getLength(); ++i)
				children.add(createTreeNode(this, childNodes.item(i)));
		}
	
		protected static void createGenericNodes(Node source, InterpretedDOMTreeNode target) {
			NodeList childNodes = source.getChildNodes();
			for (int i=0; i<childNodes.getLength(); ++i) {
				Node child = childNodes.item(i);
				target.children.add(new GenericXMLNode(target, child));
			}
		}
		
		protected void parseAttributes(Node node, AttributeConsumer attrConsumer) {
			NamedNodeMap attributes = node.getAttributes();
			for (int i=0; i<attributes.getLength(); ++i) {
				Node attr = attributes.item(i);
				boolean wasExpected = attrConsumer.consume(attr.getNodeName(), attr.getNodeValue());
				if (!wasExpected)
					reportError(node, "Found unexpected attribute: %s=\"%s\"", attr.getNodeName(), attr.getNodeValue());
			}
		}

		protected String getContentOfSingleChildTextNode(Node node) {
			NodeList childNodes = node.getChildNodes();
			if (childNodes.getLength()!=1) {
				reportError(node,"Found unexpected number of values inside of node. (found:%d, expected:1)", childNodes.getLength());
				return null;
			}
			
			Node child = childNodes.item(0);
			if (child.getNodeType()!=Node.TEXT_NODE || !(child instanceof Text)) {
				reportError(node,"Found value [%d] inside of node with unexpected node type. (found:%s, expected:%s)", 0, XML.getLongName(child.getNodeType()), XML.getLongName(Node.TEXT_NODE));
				return null;
			}
			
			String str = child.getNodeValue();
			if (str==null) {
				reportError(node,"Content of text node [%d] inside of node is null.", 0);
				return null;
			}
			
			return str;
		}

		private static class GenericXMLNode extends InterpretedDOMTreeNode {
	
			public GenericXMLNode(InterpretedDOMTreeNode parent, Node node) {
				super(parent, node, null);
			}
	
			@Override public String toString() { return XML.toString(node); }
			@Override protected void createChildren() {
				children = new Vector<>();
				NodeList childNodes = node.getChildNodes();
				for (int i=0; i<childNodes.getLength(); ++i)
					children.add(new GenericXMLNode(this, childNodes.item(i)));
			}
		}
		
		private static class TextNode extends InterpretedDOMTreeNode {
			private String str;
			public TextNode(InterpretedDOMTreeNode parent, Node node, String format, Object... values) {
				this(parent,node,null,format,values);
			}
			public TextNode(InterpretedDOMTreeNode parent, Node node, TreeIcon icon, String format, Object... values) {
				super(parent,node,icon); str = String.format(format, values);
			}
			@Override public String toString() { return str; }
			@Override protected void createChildren() { children = new Vector<>(); }
		}
	
		private static class DocumentNode extends InterpretedDOMTreeNode {
			private Document document;
			public DocumentNode(Document document) {
				super(null,document,TreeIcon.Document);
				this.document = document;
			}
			@Override public String toString() { return "Function Description of Yamaha Device"; }
			@Override protected void createChildren() { createChildren(document); }
		}
	
		private static abstract class ElementNode extends InterpretedDOMTreeNode {
			
			ElementNode(InterpretedDOMTreeNode parent, Element node, TreeIcon icon) {
				super(parent,node,icon);
			}
			@Override protected void createChildren() { createChildren(node); }
	
			protected void parseAttributes(AttributeConsumer attrConsumer) {
				parseAttributes(node, attrConsumer);
			}
			
		}
		
		private static class UnitDescriptionNode extends ElementNode {
			
			private String unitName;
			private String version;
	
			public UnitDescriptionNode(InterpretedDOMTreeNode parent, Element node) {
				super(parent,node,TreeIcon.UnitDescription);
				this.unitName = "???";
				this.version = "???";
				// <Unit_Description Unit_Name="RX-V475" Version="1.2">
				parseAttributes((attrName, attrValue) -> {
					switch (attrName) {
					case "Unit_Name": unitName = attrValue; break;
					case "Version"  : version  = attrValue; break;
					default: return false;
					}
					return true;
				});
			}
			@Override public String toString() { return String.format("Yamaha Device \"%s\" [Ver%s]", unitName, version); }
		}
	
		private static class LanguageNode extends ElementNode {
			
			private String code;
			private String value;
	
			public LanguageNode(InterpretedDOMTreeNode parent, Element node) {
				super(parent,node,TreeIcon.Language);
				this.code = "???";
				// <Language Code="en">
				parseAttributes((attrName, attrValue) -> { if ("Code".equals(attrName)) { code=attrValue; return true; } return false; });
				value = getContentOfSingleChildTextNode(node);
				children = new Vector<>();
			}
			@Override public String toString() { return String.format("Language: %s [%s]", code, value); }
		}
	
		private static class MenuNode extends ElementNode {
		
			private BaseCommandListNode cmdListNode;
			private String func;
			private String funcEx;
			private String yncTag;
			private String title;
			private String listType;
			private String iconOn;
			private String playable;
	
			public MenuNode(InterpretedDOMTreeNode parent, Element node) {
				super(parent,node,TreeIcon.Menu);
				this.node = node;
				this.cmdListNode = null;
				this.func     = null;
				this.funcEx   = null;
				this.yncTag   = null;
				this.title    = null;
				this.listType = null;
				this.iconOn   = null;
				this.playable = null;
				// <Menu Func="Source_Device" Func_Ex="SD_iPod_USB" YNC_Tag="iPod_USB">
				// <Menu List_Type="Icon" Title_1="Input/Scene">
				// <Menu Func="Input" Func_Ex="Input" Icon_on="/YamahaRemoteControl/Icons/icon000.png" List_Type="Icon" Title_1="Input">
				// <Menu Func_Ex="Adaptive_DRC" Title_1="Adaptive DRC">
				parseAttributes((attrName, attrValue) -> {
					switch (attrName) {
					case "Func"     : func     = attrValue; break;
					case "Func_Ex"  : funcEx   = attrValue; break;
					case "YNC_Tag"  : yncTag   = attrValue; break;
					case "Title_1"  : title    = attrValue; break;
					case "List_Type": listType = attrValue; break;
					case "Icon_on"  : iconOn   = attrValue; break;
					case "Playable" : playable = attrValue; break;
					default: return false;
					}
					return true;
				});
			}
			
			public static BaseCommandList getCmdList(BaseTreeNode<?> node) {
				while (node instanceof MenuNode) {
					MenuNode menu = (MenuNode)node;
					if (menu.cmdListNode!=null) return menu.cmdListNode.commands;
					node = menu.parent;
				}
				return null;
			}
			
			@Override protected void createChildren() {
				children = new Vector<>();
				NodeList childNodes = node.getChildNodes();
				for (int i=0; i<childNodes.getLength(); ++i) {
					InterpretedDOMTreeNode treeNode = createTreeNode(this, childNodes.item(i));
					if (treeNode instanceof BaseCommandListNode)
						cmdListNode = (BaseCommandListNode)treeNode;
					children.add(treeNode);
				}
			}
		
			private String getTags() {
				String str = "";
				if (func  !=null) str += (str.isEmpty()?"":" | ")+func  ;
				if (funcEx!=null) str += (str.isEmpty()?"":" | ")+funcEx;
				if (yncTag!=null) str += (str.isEmpty()?"":" | ")+yncTag;
				return str;
			}
			@Override public String toString() {
				String str = "";
				if (title!=null) str = title+"   ";
				str += "["+getTags()+"]   ";
				if (listType!=null) str += " ListType:"+listType;
				if (iconOn  !=null) str += " IconOn:"  +iconOn  ;
				if (playable!=null) str += " Playable:"+playable;
				return str;
			}
		}
		
		private static class BaseCommandList implements ComplexCommand.BaseCommandList {
			private HashMap<String,BaseCommandDefineNode> commandNodes;
			
			BaseCommandList() {
				commandNodes = new HashMap<>();
			}
	
			public void put(String cmdID, BaseCommandDefineNode cmdNode) {
				commandNodes.put(cmdID, cmdNode);
			}
	
			@Override
			public TagList get(String cmdID) {
				BaseCommandDefineNode cmdNode = commandNodes.get(cmdID);
				if (cmdNode==null) return null;
				cmdNode.used = true;
				return cmdNode.tagList;
			}
	
			public void forEach(BiConsumer<? super String, ? super BaseCommandDefineNode> consumer) {
				commandNodes.forEach(consumer);
			}
		}
		
		private static class BaseCommandListNode extends ElementNode {
			
			private BaseCommandList commands;
			
			public BaseCommandListNode(InterpretedDOMTreeNode parent, Element node) {
				super(parent,node,TreeIcon.Command);
				this.commands = null;
				parseAttributes((attrName, attrValue) -> false);
				createChildren();
			}
	
			public void showUsageInCommands(DefaultTreeModel treeModel, boolean enable) {
				if (commands == null) return;
				commands.forEach((key,node)->{
					node.showUsage(enable);
					treeModel.nodeChanged(node);
				});
			}
	
			@Override public String toString() { return "Command List"; }
		
			@Override
			protected void createChildren() {
				children = new Vector<>();
				commands = new BaseCommandList();
				NodeList childNodes = node.getChildNodes();
				for (int i=0; i<childNodes.getLength(); ++i) {
					Node child = childNodes.item(i);
					if (child.getNodeType()!=Node.ELEMENT_NODE || !(child instanceof Element)) {
						reportError("Found child [%d] with unexpected node type. (found:%s, expected:%s)", i, XML.getLongName(child.getNodeType()), XML.getLongName(Node.ELEMENT_NODE));
						children.add(new GenericXMLNode(this, child));
						continue;
					}
					
					Element childElement = (Element)child;
					if (!"Define".equals(childElement.getTagName())) {
						reportError("Found child [%d] with unexpected tag name. (found:\"%s\", expected:\"Define\")", i, childElement.getTagName());
						children.add(new GenericXMLNode(this, child));
						continue;
					}
					
					BaseCommandDefineNode cmdNode = new BaseCommandDefineNode(this, childElement);
					commands.put(cmdNode.id,cmdNode);
					children.add(cmdNode);
				}
			}
		}
	
		private static class BaseCommandDefineNode extends ElementNode implements CallableGetCommand {
		
			public String id;
			public TagList tagList;
			private boolean showUsage;
			public boolean used;
	
			public BaseCommandDefineNode(InterpretedDOMTreeNode parent, Element node) {
				super(parent,node, TreeIcon.Command_GET);
				this.id = null;
				this.tagList = null;
				this.used = false;
				this.showUsage = false;
				
				// <Define ID="P7">command</Define>
				parseAttributes((attrName, attrValue) -> { if ("ID".equals(attrName)) { id=attrValue; return true; } return false; });
				children = new Vector<>();
				
				if (id == null)
					reportError("Found \"Define\" node with no ID.");
				
				String tagListStr = getContentOfSingleChildTextNode(this.node);
				if (tagListStr==null) {
					NodeList childNodes = this.node.getChildNodes();
					for (int i=0; i<childNodes.getLength(); ++i)
						children.add(new GenericXMLNode(this, childNodes.item(i)));
					return;
				}
				tagList = new TagList(tagListStr);
			}
			
			public void showUsage(boolean showUsage) {
				this.showUsage = showUsage;
			}
	
			@Override public String toString() { return String.format("%s%s: %s", !showUsage||used?"":"[unused] ", id, tagList); }
			@Override protected void createChildren() { throw new UnsupportedOperationException("Calling CommandNode.createChildren() is not allowed."); }
	
			@Override
			public String buildXmlCommand() {
				if (tagList==null) return null;
				return Ctrl.buildGetCommand(tagList);
			}
		}
	
		private static class SimplePutCommandNode extends ElementNode implements CallablePutCommand {
		
			private String cmdID;
			private TagList tagList;
			private String commandValue;
			private String func;
			private String funcEx;
			private String title;
			private String playable;
			private String layout;
			private String visible;
			
			public SimplePutCommandNode(InterpretedDOMTreeNode parent, Element node) {
				super(parent, node, TreeIcon.Command_PUT);
				this.cmdID  = null;
				this.tagList = null;
				this.commandValue = null;
				this.func   = null;
				this.funcEx = null;
				this.title    = null;
				this.layout   = null;
				this.visible  = null;
				this.playable = null;
				
				// <Put_1 Func="Event_On" ID="P1">
				parseAttributes((attrName, attrValue) -> {
					switch (attrName) { // Func_Ex
					case "ID"       : cmdID = attrValue; break;
					case "Func"     : func  = attrValue; break;
					case "Func_Ex"  : funcEx   = attrValue; break;
					case "Title_1"  : title    = attrValue; break;
					case "Layout"   : layout   = attrValue; break;
					case "Visible"  : visible  = attrValue; break;
					case "Playable" : playable = attrValue; break;
					default: return false;
					}
					return true;
				});
				
				children = new Vector<>();
				
				BaseCommandList cmdList = MenuNode.getCmdList(parent);
				if (cmdList==null) reportError("Can't find command list for \"Put_1\" node.");
				if (cmdID == null) reportError("Found \"Put_1\" node with no ID.");
				
				if (cmdList!=null && cmdID!=null) {
					tagList = cmdList.get(cmdID);
					if (tagList==null) reportError("Found \"Put_1\" node with ID \"%s\", that isn't associated with a command.", cmdID);
				}
				
				commandValue = getContentOfSingleChildTextNode(this.node);
				if (commandValue==null) {
					NodeList childNodes = this.node.getChildNodes();
					for (int i=0; i<childNodes.getLength(); ++i)
						children.add(new GenericXMLNode(this, childNodes.item(i)));
					return;
				}
				
				//children.add( new TextNode(this, ParsedTreeIcon.Command, "PUT[%s]     %s = %s", cmdID==null?"??":cmdID, tagList==null?"????":tagList, commandValue==null?"???":commandValue) );
			}
	
			@Override
			public String buildXmlCommand() {
				if (tagList==null || commandValue==null) return null;
				return Ctrl.buildPutCommand(tagList, commandValue);
			}
			
			private String getTags() {
				String str = "";
				if (func  !=null) str += (str.isEmpty()?"":" | ")+func  ;
				if (funcEx!=null) str += (str.isEmpty()?"":" | ")+funcEx;
				return str;
			}
			@Override public String toString() {
				String str = "";
				if (title!=null) str = title+"   ";
				str += "["+getTags()+"]   ";
				if (layout  !=null) str += " Layout:"  +layout;
				if (visible !=null) str += " Visible:" +visible;
				if (playable!=null) str += " Playable:"+playable;
	
				return String.format("%s     PUT[%s]     %s = %s", str, cmdID==null?"??":cmdID, tagList==null?"????":tagList, commandValue==null?"???":commandValue);
				//return str;
				//return XML.toString(node);
			}
	
			@Override protected void createChildren() {
				throw new UnsupportedOperationException("Calling Put1CommandNode.createChildren() is not allowed.");
			}
		}
	
		private static class ComplexCommandNode extends ElementNode implements ComplexCommand.ComplexCommandContext {
			protected String label;
			private LanguageConfig dummyLanguageConfig;
			
			ComplexCommandNode(InterpretedDOMTreeNode parent, Element node) {
				super(parent, node, TreeIcon.Command);
				this.label = "";
				children = new Vector<>();
				dummyLanguageConfig = new LanguageConfig();
				dummyLanguageConfig.setDefaultValues();
			}
			
			public static InterpretedDOMTreeNode createPutCommand(InterpretedDOMTreeNode parent, Element node) { return create(parent, node, ComplexCommand.Type.Put); }
			public static InterpretedDOMTreeNode createGetCommand(InterpretedDOMTreeNode parent, Element node) { return create(parent, node, ComplexCommand.Type.Get); }

			private static InterpretedDOMTreeNode create(InterpretedDOMTreeNode parent, Element node, ComplexCommand.Type type) {
				
				ComplexCommandNode ccNode = new ComplexCommandNode(parent, node);
				PlainCommandNode parsedCommandNode = null;
				
				ComplexCommand complexCommand = new ComplexCommand(node,type,ccNode);
				
				ccNode.label = type.toString();
				if      (complexCommand.cmd==null) { ccNode.label += ": No Cmd found"; complexCommand.error=true; }
				else if (complexCommand.cmd.baseTagList==null) { ccNode.label += ": No base "+type.toString().toUpperCase()+" command found"; complexCommand.error=true; }
				else if (complexCommand.cmd.params==null || complexCommand.cmd.params.length==0) { ccNode.label += ": No parameters found"; complexCommand.error=true; }
				else parsedCommandNode = createParsedCommand(parent, node, complexCommand);
				
				InterpretedDOMTreeNode result = parsedCommandNode != null ? parsedCommandNode : ccNode;
				
				if (complexCommand.error)
					createGenericNodes(node, result);
				
				return result;
			}

			private static PlainCommandNode createParsedCommand(InterpretedDOMTreeNode parent, Node node, ComplexCommand complexCommand) {
				PlainCommandNode parsedCommandNode = createCommandTreeNode(complexCommand, new Helper<PlainCommandNode>() {

					@Override public PlainCommandNode createGet(String baseLabel, TagList baseTagList, String value) {
						return new PlainGetCommandNode(parent, node, baseLabel, baseTagList, value);
					}
					@Override public PlainCommandNode createGet(String baseLabel, TagList baseTagList) {
						return new PlainGetCommandNode(parent, node, baseLabel, baseTagList);
					}

					@Override public PlainCommandNode createPut(String baseLabel, TagList baseTagList, String tagListAddStr, String conn, String value) {
						return new PlainCommandNode(parent, node, baseLabel, baseTagList, tagListAddStr, conn, value);
					}
					@Override public PlainCommandNode createPut(String baseLabel, TagList baseTagList) {
						return new PlainCommandNode(parent, node, baseLabel, baseTagList);
					}

					@Override public void addTextTo(PlainCommandNode parsedCommandNode, String format, Object... objects) {
						parsedCommandNode.add(new TextNode(parsedCommandNode,null, /*icon,*/ format, objects));
					}
				});
//				String baseLabel;
//				switch (complexCommand.type) {
//				case Get:
//					baseLabel = "GET["+complexCommand.cmd.cmdID+"]";
//					if (complexCommand.cmd.type!=null) baseLabel += " <"+complexCommand.cmd.type+">";
//					if (complexCommand.cmd.params.length==1) {
//						ComplexCommand.CmdParam cp = complexCommand.cmd.params[0];
//						String tagListStr = cp.valueTagList==null?"":(cp.valueTagList.toString()+" -> ");
//						parsedCommandNode = new PlainGetCommandNode(parent, node, baseLabel, complexCommand.cmd.baseTagList, tagListStr+cp.param.mergeValues(""," | ",""));
//					} else {
//						parsedCommandNode = new PlainGetCommandNode(parent, node, baseLabel, complexCommand.cmd.baseTagList);
//						for (int i=0; i<complexCommand.cmd.params.length; ++i) {
//							ComplexCommand.CmdParam cp = complexCommand.cmd.params[i];
//							String tagListStr = cp.valueTagList==null?"":(cp.valueTagList.toString()+" -> ");
//							parsedCommandNode.add(new TextNode(parsedCommandNode,null, /*icon,*/ "Value %d:   %s%s", i, tagListStr, cp.param.mergeValues(""," | ","")));
//						}
//					}
//					break;
//				case Put:
//					baseLabel = "PUT["+complexCommand.cmd.cmdID+"]";
//					if (complexCommand.cmd.type!=null) baseLabel += " <"+complexCommand.cmd.type+">";
//					if (complexCommand.cmd.params.length==1) {
//						ComplexCommand.CmdParam cp = complexCommand.cmd.params[0];
//						String tagListAddStr = cp.valueTagList==null?"":(",  "+cp.valueTagList.toString());
//						parsedCommandNode = new PlainCommandNode(parent, node, baseLabel, complexCommand.cmd.baseTagList, tagListAddStr, "=", cp.param.mergeValues(""," | ",""));
//					} else {
//						parsedCommandNode = new PlainCommandNode(parent, node, baseLabel, complexCommand.cmd.baseTagList);
//						for (int i=0; i<complexCommand.cmd.params.length; ++i) {
//							ComplexCommand.CmdParam cp = complexCommand.cmd.params[i];
//							String tagListStr = cp.valueTagList==null?"":(cp.valueTagList.toString()+" = ");
//							parsedCommandNode.add(new TextNode(parsedCommandNode,null, /*icon,*/ "Value %d:   %s%s", i, tagListStr, cp.param.mergeValues(""," | ","")));
//						}
//					}
//					break;
//				}
				return parsedCommandNode;
			}
			
			@Override public String toString() { return label; }
			
			@Override protected void createChildren() {
				throw new UnsupportedOperationException("Calling "+getClass().getSimpleName()+".createChildren() is not allowed.");
			}
			
			@Override
			public void parseAttributes(Node node, AttributeConsumer attrConsumer) {
				super.parseAttributes(node, attrConsumer);
			}
			
			@Override
			public String getContentOfSingleChildTextNode(Node node) {
				return super.getContentOfSingleChildTextNode(node);
			}

			@Override
			public ComplexCommand.BaseCommandList getCmdListFromParent() {
				return MenuNode.getCmdList(parent);
			}

			@Override
			public LanguageConfig getLanguageConfig() {
				return dummyLanguageConfig;
			}
		}
		
		private static class PlainCommandNode extends InterpretedDOMTreeNode {
			
			protected String label;
			protected TagList tagList;
			protected String tagListStr;
			private boolean withValue;
			private String conn;
			protected String value;
			
			private PlainCommandNode(InterpretedDOMTreeNode parent, Node node, TreeIcon icon, String label, TagList tagList, boolean withValue, String conn, String value) {
				super(parent, node, icon);
				this.label = label;
				this.tagList = tagList;
				this.tagListStr = tagList.toString();
				this.conn = conn;
				this.value = value;
				this.withValue = withValue;
				children = new Vector<>();
			}
			protected PlainCommandNode(InterpretedDOMTreeNode parent, Node node, TreeIcon icon, String label, TagList tagList                                                 ) { this(parent, node, icon, label, tagList, false, null , null); }
			protected PlainCommandNode(InterpretedDOMTreeNode parent, Node node, TreeIcon icon, String label, TagList tagList,                       String conn, String value) { this(parent, node, icon, label, tagList, true, conn, value ); }
			public    PlainCommandNode(InterpretedDOMTreeNode parent, Node node,                String label, TagList tagList                                                 ) { this(parent, node, TreeIcon.Command, label, tagList); }
			public    PlainCommandNode(InterpretedDOMTreeNode parent, Node node,                String label, TagList tagList, String tagListAddStr, String conn, String value) { this(parent, node, TreeIcon.Command, label, tagList, conn, value); tagListStr+=tagListAddStr; }
			
			public void add(InterpretedDOMTreeNode child) { children.add(child); }
			@Override public String toString() { return label+":    "+tagListStr+(withValue?("   "+conn+"   "+value):""); }
			@Override protected void createChildren() { throw new UnsupportedOperationException("Calling "+getClass().getSimpleName()+".createChildren() is not allowed."); }
		}
		
		private static class PlainGetCommandNode extends PlainCommandNode implements CallableGetCommand {
			PlainGetCommandNode(InterpretedDOMTreeNode parent, Node node, String label, TagList tagList) {
				super(parent, node, TreeIcon.Command_GET, label, tagList);
			}
			PlainGetCommandNode(InterpretedDOMTreeNode parent, Node node, String label, TagList tagList, String value) {
				super(parent, node, TreeIcon.Command_GET, label, tagList, "->", value);
			}
			@Override public String buildXmlCommand() { return Ctrl.buildGetCommand(tagList); }
		}
		
		@SuppressWarnings("unused")
		private static class PlainPutCommandNode extends PlainCommandNode implements CallablePutCommand {
			PlainPutCommandNode(InterpretedDOMTreeNode parent, Node node, String label, TagList tagList, String value) {
				super(parent, node, TreeIcon.Command_PUT, label, tagList, "=", value);
			}
			@Override public String buildXmlCommand() { return Ctrl.buildPutCommand(tagList, value); }
		}
		
	}
	
	private static class ParsedTreeNode extends BaseTreeNode<ParsedTreeNode> {

		private ParsedCommandItem item;
		
		public ParsedTreeNode(ParsedCommandItem.DocumentItem item) {
			this(null,item);
		}
		public ParsedTreeNode(ParsedTreeNode parent, ParsedCommandItem item) {
			super(parent,item.node);
			this.item = item;
		}
		
		public boolean isCallablePutCommand() {
			return (item instanceof CallablePutCommand);
		}
		
		public boolean isCallableGetCommand() {
			return (item instanceof CallableGetCommand);
		}
		
		public CallableCommand getCallableCommand() {
			if (item instanceof CallableCommand)
				return (CallableCommand) item;
			return null;
		}
		
		public TreeIcon getIcon() {
			if (item instanceof ParsedCommandItem.DocumentItem       ) return TreeIcon.Document;
			if (item instanceof ParsedCommandItem.UnitDescriptionItem) return TreeIcon.UnitDescription;
			if (item instanceof ParsedCommandItem.LanguageItem       ) return TreeIcon.Language;
			if (item instanceof ParsedCommandItem.MenuItem           ) return TreeIcon.Menu;
			if (item instanceof ParsedCommandItem.BaseCommandListItem) return TreeIcon.Command;
			if (item instanceof CallableGetCommand                   ) return TreeIcon.Command_GET;
			if (item instanceof CallablePutCommand                   ) return TreeIcon.Command_PUT;
			if (item instanceof ParsedCommandItem.CommandItem        ) return TreeIcon.Command;
			return null;
		}
		
		@Override
		protected void createChildren() {
			children = new Vector<>();
			for (ParsedCommandItem childItem:item.getChildren()) {
				if (childItem instanceof ParsedCommandItem.ComplexCommandItem) {
					children.add(ComplexCommandNode.create(this, (ParsedCommandItem.ComplexCommandItem) childItem));
				} else
					children.add(new ParsedTreeNode(this, childItem));
			}
		}

		@Override
		public String toString() {
			return item.toString();
		}
		
		private static class ComplexCommandNode extends ParsedTreeNode {
			
			protected String label;

			public ComplexCommandNode(ParsedTreeNode parent, ParsedCommandItem item) {
				super(parent, item);
				label = "";
			}
			
			@Override public String toString() { return label; }

			@Override public boolean isCallablePutCommand() { return this instanceof CallablePutCommand; }
			@Override public boolean isCallableGetCommand() { return this instanceof CallableGetCommand; }
			@Override public CallableCommand getCallableCommand() {
				if (this instanceof CallableCommand) return (CallableCommand) this;
				return null;
			}

			@Override
			public TreeIcon getIcon() {
				if (this instanceof CallableGetCommand) return TreeIcon.Command_GET;
				if (this instanceof CallablePutCommand) return TreeIcon.Command_PUT;
				if (this instanceof TextNode          ) return null;
				return TreeIcon.Command;
			}

			public static ComplexCommandNode create(ParsedTreeNode parent, ParsedCommandItem.ComplexCommandItem childItem) {
				
				ComplexCommand complexCommand = childItem.complexCommand;
				
				ComplexCommandNode result = new ComplexCommandNode(parent, childItem);
				result.label = complexCommand.type.toString();
				if      (complexCommand.cmd==null) { result.label += ": No Cmd found"; complexCommand.error=true; }
				else if (complexCommand.cmd.baseTagList==null) { result.label += ": No base "+complexCommand.type.toString().toUpperCase()+" command found"; complexCommand.error=true; }
				else if (complexCommand.cmd.params==null || complexCommand.cmd.params.length==0) { result.label += ": No parameters found"; complexCommand.error=true; }
				else result = createParsedCommand(parent, childItem, complexCommand);
				
				return result;
			}
		
			private static ComplexCommandNode createParsedCommand(ParsedTreeNode parent, ParsedCommandItem.ComplexCommandItem item, ComplexCommand complexCommand) {
				PlainCommandNode parsedCommandNode = createCommandTreeNode(complexCommand, new Helper<PlainCommandNode>() {
					
					@Override public PlainCommandNode createGet(String baseLabel, TagList baseTagList, String value) {
						return new PlainGetCommandNode(parent, item, baseLabel, baseTagList, value);
					}
					@Override public PlainCommandNode createGet(String baseLabel, TagList baseTagList) {
						return new PlainGetCommandNode(parent, item, baseLabel, baseTagList);
					}

					@Override public PlainCommandNode createPut(String baseLabel, TagList baseTagList, String tagListAddStr, String conn, String value) {
						return new PlainCommandNode(parent, item, baseLabel, baseTagList, tagListAddStr, conn, value);
					}
					@Override public PlainCommandNode createPut(String baseLabel, TagList baseTagList) {
						return new PlainCommandNode(parent, item, baseLabel, baseTagList);
					}

					@Override
					public void addTextTo(PlainCommandNode parsedCommandNode, String format, Object... objects) {
						parsedCommandNode.add(new TextNode(parsedCommandNode, item, format, objects));
					}
				});
				//PlainCommandNode parsedCommandNode = null;
//				String baseLabel;
//				switch (complexCommand.type) {
//				case Get:
//					baseLabel = "GET["+complexCommand.cmd.cmdID+"]";
//					if (complexCommand.cmd.type!=null) baseLabel += " <"+complexCommand.cmd.type+">";
//					if (complexCommand.cmd.params.length==1) {
//						ComplexCommand.CmdParam cp = complexCommand.cmd.params[0];
//						String tagListStr = cp.valueTagList==null?"":(cp.valueTagList.toString()+" -> ");
//						parsedCommandNode = new PlainGetCommandNode(parent, item, baseLabel, complexCommand.cmd.baseTagList, tagListStr+cp.param.mergeValues(""," | ",""));
//					} else {
//						parsedCommandNode = new PlainGetCommandNode(parent, item, baseLabel, complexCommand.cmd.baseTagList);
//						for (int i=0; i<complexCommand.cmd.params.length; ++i) {
//							ComplexCommand.CmdParam cp = complexCommand.cmd.params[i];
//							String tagListStr = cp.valueTagList==null?"":(cp.valueTagList.toString()+" -> ");
//							parsedCommandNode.add(new TextNode(parsedCommandNode, item, "Value %d:   %s%s", i, tagListStr, cp.param.mergeValues(""," | ","")));
//						}
//					}
//					break;
//				case Put:
//					baseLabel = "PUT["+complexCommand.cmd.cmdID+"]";
//					if (complexCommand.cmd.type!=null) baseLabel += " <"+complexCommand.cmd.type+">";
//					if (complexCommand.cmd.params.length==1) {
//						ComplexCommand.CmdParam cp = complexCommand.cmd.params[0];
//						String tagListAddStr = cp.valueTagList==null?"":(",  "+cp.valueTagList.toString());
//						parsedCommandNode = new PlainCommandNode(parent, item, baseLabel, complexCommand.cmd.baseTagList, tagListAddStr, "=", cp.param.mergeValues(""," | ",""));
//					} else {
//						parsedCommandNode = new PlainCommandNode(parent, item, baseLabel, complexCommand.cmd.baseTagList);
//						for (int i=0; i<complexCommand.cmd.params.length; ++i) {
//							ComplexCommand.CmdParam cp = complexCommand.cmd.params[i];
//							String tagListStr = cp.valueTagList==null?"":(cp.valueTagList.toString()+" = ");
//							parsedCommandNode.add(new TextNode(parsedCommandNode, item, "Value %d:   %s%s", i, tagListStr, cp.param.mergeValues(""," | ","")));
//						}
//					}
//					break;
//				}
				return parsedCommandNode;
			}
			
			private static class TextNode extends ComplexCommandNode {
				public TextNode(ParsedTreeNode parent, ParsedCommandItem item, String format, Object...values) {
					super(parent, item);
					label = String.format(Locale.ENGLISH, format, values);
				}
			}
			
			private static class PlainCommandNode extends ComplexCommandNode {
				
				protected String label;
				protected TagList tagList;
				protected String tagListStr;
				private boolean withValue;
				private String conn;
				protected String value;
				
				private PlainCommandNode(ParsedTreeNode parent, ParsedCommandItem item, String label, TagList tagList, boolean withValue, String conn, String value) {
					super(parent, item);
					this.label = label;
					this.tagList = tagList;
					this.tagListStr = tagList.toString();
					this.conn = conn;
					this.value = value;
					this.withValue = withValue;
					children = new Vector<>();
				}
				public    PlainCommandNode(ParsedTreeNode parent, ParsedCommandItem item, String label, TagList tagList                                                 ) { this(parent, item, label, tagList, false, null , null); }
				protected PlainCommandNode(ParsedTreeNode parent, ParsedCommandItem item, String label, TagList tagList,                       String conn, String value) { this(parent, item, label, tagList, true, conn, value ); }
				public    PlainCommandNode(ParsedTreeNode parent, ParsedCommandItem item, String label, TagList tagList, String tagListAddStr, String conn, String value) { this(parent, item, label, tagList, conn, value); tagListStr+=tagListAddStr; }
				
				public void add(ParsedTreeNode child) { children.add(child); }
				@Override public String toString() { return label+":    "+tagListStr+(withValue?("   "+conn+"   "+value):""); }
				@Override protected void createChildren() { throw new UnsupportedOperationException("Calling "+getClass().getSimpleName()+".createChildren() is not allowed."); }
			}
			
			private static class PlainGetCommandNode extends PlainCommandNode implements CallableGetCommand {
				PlainGetCommandNode(ParsedTreeNode parent, ParsedCommandItem item, String label, TagList tagList) {
					super(parent, item, label, tagList);
				}
				PlainGetCommandNode(ParsedTreeNode parent, ParsedCommandItem item, String label, TagList tagList, String value) {
					super(parent, item, label, tagList, "->", value);
				}
				@Override public String buildXmlCommand() { return Ctrl.buildGetCommand(tagList); }
			}
			
			@SuppressWarnings("unused")
			private static class PlainPutCommandNode extends PlainCommandNode implements CallablePutCommand {
				PlainPutCommandNode(ParsedTreeNode parent, ParsedCommandItem item, String label, TagList tagList, String value) {
					super(parent, item, label, tagList, "=", value);
				}
				@Override public String buildXmlCommand() { return Ctrl.buildPutCommand(tagList, value); }
			}
		}
	}
	
	static class GetGroups {
		final HashMap<String,Vector<ParsedCommandItem.ComplexCommandItem>> groups;

		private GetGroups() {
			groups = new HashMap<>();
		}

		public Vector<ComplexCommandItem> add(String tagList, ComplexCommandItem item) {
			Vector<ComplexCommandItem> group = groups.get(tagList);
			if (group==null) groups.put(tagList, group = new Vector<>());
			group.add(item);
			return group;
		}
		
	}
	
	static abstract class ParsedCommandItem {
		
		final ParsedCommandItem parent;
		final Node node;
		final GetGroups getGroups;
		final ComplexCommand.IndirectValues indirectValues;
		
		public ParsedCommandItem(ParsedCommandItem parent, Node node) {
			this.parent = parent;
			this.node = node;
			this.getGroups      = this.parent!=null ? this.parent.getGroups      : new GetGroups();
			this.indirectValues = this.parent!=null ? this.parent.indirectValues : new ComplexCommand.IndirectValues();
		}
		
		public Iterable<ParsedCommandItem> getChildren() { return this::getChildIterator; }
		public abstract Iterator<ParsedCommandItem> getChildIterator();
		@Override public abstract String toString();

		/*
		private static InterpretedDOMTreeNode createTreeNode(InterpretedDOMTreeNode parent, Element node) {
			switch (node.getTagName()) {
			case "Unit_Description": return new UnitDescriptionNode (parent, node);
			case "Language"        : return new LanguageNode        (parent, node);
			case "Menu"            : return new MenuNode            (parent, node);
			case "Cmd_List"        : return new BaseCommandListNode (parent, node);
			case "Put_1"           : return new SimplePutCommandNode(parent, node);
			case "Put_2"           : return PutCommandNode.create(parent, node);
			case "Get"             : return GetCommandNode.create(parent, node);
			}
			unknownTagNames.put(node.getNodeName(), 1+unknownTagNames.getOrDefault(node.getNodeName(),0));
			return new GenericXMLNode(parent,node);
			//return new TextNode(parent, "Unknown Element \"%s\"", node.getNodeName());
		}*/
		
		protected void parseAttributes(AttributeConsumer attrConsumer) {
			parseAttributes(node, attrConsumer);
		}
		protected void parseAttributes(Node node, AttributeConsumer attrConsumer) {
			NamedNodeMap attributes = node.getAttributes();
			for (int i=0; i<attributes.getLength(); ++i) {
				Node attr = attributes.item(i);
				boolean wasExpected = attrConsumer.consume(attr.getNodeName(), attr.getNodeValue());
				if (!wasExpected)
					reportError("Found unexpected attribute: %s=\"%s\"", attr.getNodeName(), attr.getNodeValue());
			}
		}

		private interface SubNodeConsumer {
			public boolean consume(Node subNode, short nodeType, String nodeName, String nodeValue);
		}
		protected void parseSubNodes(SubNodeConsumer subNodeConsumer) {
			NodeList childNodes = node.getChildNodes();
			for (int i=0; i<childNodes.getLength(); ++i) {
				Node child = childNodes.item(i);
				boolean wasExpected = subNodeConsumer.consume(child, child.getNodeType(), child.getNodeName(), child.getNodeValue());
				if (!wasExpected)
					reportError("Found unexpected xml node: [%s] %s", XML.getLongName(child.getNodeType()),  child.getNodeName());
			}
		}

		protected String getContentOfSingleChildTextNode(Node node) {
			NodeList childNodes = node.getChildNodes();
			if (childNodes.getLength()!=1) {
				reportError(node,"Found unexpected number of values inside of node. (found:%d, expected:1)", childNodes.getLength());
				return null;
			}
			
			Node child = childNodes.item(0);
			if (child.getNodeType()!=Node.TEXT_NODE || !(child instanceof Text)) {
				reportError(node,"Found value [%d] inside of node with unexpected node type. (found:%s, expected:%s)", 0, XML.getLongName(child.getNodeType()), XML.getLongName(Node.TEXT_NODE));
				return null;
			}
			
			String str = child.getNodeValue();
			if (str==null) {
				reportError(node,"Content of text node [%d] inside of node is null.", 0);
				return null;
			}
			
			return str;
		}
		
		public void reportError(String format, Object... values) {
			reportError(node, format, values);
		}
		public void reportError(Node node, String format, Object... values) {
			CommandList.reportError(this.getClass().getSimpleName(), node, format, values);
		}

		static class DocumentItem extends ParsedCommandItem {

			final Document document;
			UnitDescriptionItem unitDescription;
			
			public DocumentItem(Document document) {
				super(null,document);
				this.document = document;
				
				// <Unit_Description Unit_Name="RX-V475" Version="1.2">
				parseSubNodes((subNode, nodeType, nodeName, nodeValue) -> {
					if (nodeType!=Node.ELEMENT_NODE) return false;
					if (!"Unit_Description".equals(nodeName)) return false;
					unitDescription = new UnitDescriptionItem(this,subNode);
					return true;
				});
			}

			@Override public String toString() { return "Function Description of Yamaha Device"; }
			
			@Override public Iterator<ParsedCommandItem> getChildIterator() {
				return new Iterator<ParsedCommandItem>() {
					int index=0;
					@Override public boolean hasNext() { return index==0; }
					@Override public ParsedCommandItem next() { index++; return unitDescription; }
				};
			}
		}
		
		static class UnitDescriptionItem extends ParsedCommandItem {
			
			String unitName;
			String version;
			Vector<LanguageItem> languages;
			Vector<MenuItem> menues;
			LanguageConfig languageConfig;
	
			public UnitDescriptionItem(ParsedCommandItem parent, Node node) {
				super(parent,node);
				
				this.unitName = "???";
				this.version = "???";
				// <Unit_Description Unit_Name="RX-V475" Version="1.2">
				parseAttributes((attrName, attrValue) -> {
					switch (attrName) {
					case "Unit_Name": unitName = attrValue; break;
					case "Version"  : version  = attrValue; break;
					default: return false;
					}
					return true;
				});
				
				this.languages = new Vector<>();
				Vector<Node> languageNodes = XML.getSubNodes(node, "Language");
				for (Node subNode:languageNodes)
					languages.add(new LanguageItem(this,subNode));
				languageConfig = new LanguageConfig();
				languageConfig.setValues(languages);
				
				this.menues = new Vector<>();
				// <Language Code="en">
				// <Menu Func="Unit" Title_1="System" YNC_Tag="System">
				parseSubNodes((subNode, nodeType, nodeName, nodeValue) -> {
					if (nodeType!=Node.ELEMENT_NODE) return false;
					switch (nodeName) {
					case "Language": break;
					case "Menu"    : menues.add(new MenuItem(this,subNode,languageConfig)); break;
					default: return false;
					}
					return true;
				});
			}

			@Override public String toString() { return String.format("Yamaha Device \"%s\" [Ver%s]", unitName, version); }
			
			@Override public Iterator<ParsedCommandItem> getChildIterator() {
				return new Iterator<ParsedCommandItem>() {
					int index=0;
					@Override public boolean hasNext() { return index<languages.size()+menues.size(); }
					@Override public ParsedCommandItem next() {
						if (index<languages.size()) return languages.get(index++);
						return menues.get((index++)-languages.size());
					}
				};
			}
		}
		
		static class LanguageItem extends ParsedCommandItem {
			
			String code;
			String fieldName;
	
			public LanguageItem(ParsedCommandItem parent, Node node) {
				super(parent,node);
				this.code = "???";
				// <Language Code="en">
				parseAttributes((attrName, attrValue) -> {
					if ("Code".equals(attrName)) { code=attrValue; return true; }
					return false;
				});
				fieldName = getContentOfSingleChildTextNode(node);
			}
			@Override public String toString() { return String.format("Language: %s -> Field \"%s\"", code, fieldName); }
			
			@Override public Iterator<ParsedCommandItem> getChildIterator() {
				return new NoChildIterator();
			}
		}
		
		static class MenuItem extends ParsedCommandItem {
			
			private BaseCommandListItem cmdList;
			Vector<MenuItem> menues;
			Vector<CommandItem> commands;
			private Vector<ParsedCommandItem> subItems;
			
			String func;
			String funcEx;
			String yncTag;
			String listType;
			String iconOn;
			String playable;
			LanguageStrings titles;

			public MenuItem(ParsedCommandItem parent, Node node, LanguageConfig languageConfig) {
				super(parent, node);
				this.func     = null;
				this.funcEx   = null;
				this.yncTag   = null;
				this.listType = null;
				this.iconOn   = null;
				this.playable = null;
				this.titles   = new LanguageStrings();
				
				// <Menu Func="Source_Device" Func_Ex="SD_iPod_USB" YNC_Tag="iPod_USB">
				// <Menu List_Type="Icon" Title_1="Input/Scene">
				// <Menu Func="Input" Func_Ex="Input" Icon_on="/YamahaRemoteControl/Icons/icon000.png" List_Type="Icon" Title_1="Input">
				// <Menu Func_Ex="Adaptive_DRC" Title_1="Adaptive DRC">
				parseAttributes((attrName, attrValue) -> {
					boolean expected = true;
					switch (attrName) {
					case "Func"     : func     = attrValue; break;
					case "Func_Ex"  : funcEx   = attrValue; break;
					case "YNC_Tag"  : yncTag   = attrValue; break;
					case "List_Type": listType = attrValue; break;
					case "Icon_on"  : iconOn   = attrValue; break;
					case "Playable" : playable = attrValue; break;
					default: expected = false; break;
					}
					String langCode = languageConfig.getLangCode(attrName);
					if (langCode!=null) {
						titles.add(langCode,attrValue);
						expected = true;
					}
					return expected;
				});
				
				cmdList = null;
				if (XML.hasChild(this.node, "Cmd_List"))
					cmdList = new BaseCommandListItem(this,XML.getSubNode(this.node, "Cmd_List"));
				
				menues = new Vector<>();
				commands = new Vector<>();
				parseSubNodes((subNode, nodeType, nodeName, nodeValue) -> {
					if (nodeType!=Node.ELEMENT_NODE) return false;
					switch (nodeName) {
					case "Cmd_List": break;
					case "Menu"    : menues.add(new MenuItem(this,subNode,languageConfig)); break;
					case "Put_1"   : commands.add(new SimplePutCommandItem(this,subNode,languageConfig)); break;
					case "Put_2"   : commands.add(new ComplexCommandItem(this,subNode, ComplexCommand.Type.Put,languageConfig)); break;
					case "Get"     : commands.add(new ComplexCommandItem(this,subNode, ComplexCommand.Type.Get,languageConfig)); break;
					default: return false;
					}
					return true;
				});
				
				subItems = new Vector<ParsedCommandItem>();
				subItems.addAll(menues);
				subItems.addAll(commands);
				if (cmdList!=null) subItems.add(cmdList);
			}
			
			
			@Override public Iterator<ParsedCommandItem> getChildIterator() { return subItems.iterator(); }
			
			public static BaseCommandListItem.BaseCommandList getCmdListFromParent(ParsedCommandItem parent) {
				while (parent instanceof MenuItem) {
					MenuItem menu = (MenuItem)parent;
					if (menu.cmdList!=null) return menu.cmdList.commands;
					parent = menu.parent;
				}
				return null;
			}
		
			String getTags() {
				String str = "";
				if (func  !=null) str += (str.isEmpty()?"":" | ")+func  ;
				if (funcEx!=null) str += (str.isEmpty()?"":" | ")+funcEx;
				if (yncTag!=null) str += (str.isEmpty()?"":" | ")+yncTag;
				return str;
			}
			@Override public String toString() {
				String str = "";
				if (!titles.isEmpty()) str = titles.toString()+"   ";
				str += "["+getTags()+"]   ";
				if (listType!=null) str += " ListType:"+listType;
				if (iconOn  !=null) str += " IconOn:"  +iconOn  ;
				if (playable!=null) str += " Playable:"+playable;
				return str;
			}
		}
		
		private static class BaseCommandListItem extends ParsedCommandItem {

			public BaseCommandList commands;

			public BaseCommandListItem(ParsedCommandItem parent, Node node) {
				super(parent, node);
				commands = new BaseCommandList();
				parseSubNodes((subNode, nodeType, nodeName, nodeValue) -> {
					if (nodeType!=Node.ELEMENT_NODE) return false;
					if (!"Define".equals(nodeName)) return false;
					commands.add(new BaseCommandDefinitionItem(this, subNode));
					return true;
				});
			}
			
			@Override public String toString() { return "Command List"; }
			@Override public Iterator<ParsedCommandItem> getChildIterator() { return commands.iterator(); }
			
			static class BaseCommandList implements ComplexCommand.BaseCommandList {
				
				private HashMap<String,BaseCommandDefinitionItem> commands;
				private BaseCommandList() { commands = new HashMap<>(); }
				
				private void add(BaseCommandDefinitionItem item) {
					if (item.id==null || item.tagList==null) return;
					commands.put(item.id, item);
				}
				
				@Override
				public TagList get(String cmdID) {
					BaseCommandDefinitionItem item = commands.get(cmdID);
					if (item!=null) return item.tagList;
					return null;
				}

				private Iterator<ParsedCommandItem> iterator() {
					return commands
							.values()
							.stream()
							.sorted(Comparator.comparing(c->c.id))
							.map((BaseCommandDefinitionItem c)->(ParsedCommandItem)c)
							.iterator();
				}
			}
		}
		
		private static class BaseCommandDefinitionItem extends ParsedCommandItem implements CallableGetCommand {
			
			public String id;
			public TagList tagList;

			public BaseCommandDefinitionItem(ParsedCommandItem parent, Node node) {
				super(parent, node);
				this.id = null;
				this.tagList = null;
				
				// <Define ID="P7">command</Define>
				parseAttributes((attrName, attrValue) -> {
					if ("ID".equals(attrName)) { id=attrValue; return true; }
					return false;
				});
				
				String tagListStr = XML.getSubValue(node);
				if (tagListStr != null)
					tagList = new TagList(tagListStr);
			}
	
			@Override public String toString() { return String.format("%s: %s", id, tagList); }
			@Override public Iterator<ParsedCommandItem> getChildIterator() { return new NoChildIterator(); }

			@Override public String buildXmlCommand() {
				if (tagList==null) return null;
				return Ctrl.buildGetCommand(tagList);
			}
		}
		
		static abstract class CommandItem extends ParsedCommandItem {
			public CommandItem(ParsedCommandItem parent, Node node) {
				super(parent, node);
			}
		}

		static class SimplePutCommandItem extends CommandItem implements CallablePutCommand {
			
			String cmdID;
			
			private TagList tagList;
			String commandValue;
			
			LanguageStrings titles;
			private String func;
			private String funcEx;
			String playable;
			String layout;
			String visible;
		
			public SimplePutCommandItem(ParsedCommandItem parent, Node node, LanguageConfig languageConfig) {
				super(parent, node);
				this.cmdID  = null;
				this.tagList = null;
				this.commandValue = null;
				this.func   = null;
				this.funcEx = null;
				this.layout   = null;
				this.visible  = null;
				this.playable = null;
				this.titles   = new LanguageStrings();
				
				// <Put_1 Func="Event_On" ID="P1">
				parseAttributes((attrName, attrValue) -> {
					boolean expected = true;
					switch (attrName) { // Func_Ex
					case "ID"       : cmdID = attrValue; break;
					case "Func"     : func  = attrValue; break;
					case "Func_Ex"  : funcEx   = attrValue; break;
					case "Layout"   : layout   = attrValue; break;
					case "Visible"  : visible  = attrValue; break;
					case "Playable" : playable = attrValue; break;
					default: expected = false; break;
					}
					String langCode = languageConfig.getLangCode(attrName);
					if (langCode!=null) {
						titles.add(langCode, attrValue);
						expected = true;
					}
					return expected;
				});
				
				BaseCommandListItem.BaseCommandList cmdList = MenuItem.getCmdListFromParent(parent);
				if (cmdList==null) reportError("Can't find command list for \"Put_1\" node.");
				if (cmdID == null) reportError("Found \"Put_1\" node with no ID.");
				
				if (cmdList!=null && cmdID!=null) {
					tagList = cmdList.get(cmdID);
					if (tagList==null) reportError("Found \"Put_1\" node with ID \"%s\", that isn't associated with a command.", cmdID);
				}
				
				commandValue = getContentOfSingleChildTextNode(this.node);
			}
			
			@Override
			public Iterator<ParsedCommandItem> getChildIterator() { return new NoChildIterator(); }
		
			@Override
			public String buildXmlCommand() {
				if (tagList==null || commandValue==null) return null;
				return Ctrl.buildPutCommand(tagList, commandValue);
			}
			
			String getTags() {
				String str = "";
				if (func  !=null) str += (str.isEmpty()?"":" | ")+func  ;
				if (funcEx!=null) str += (str.isEmpty()?"":" | ")+funcEx;
				return str;
			}
			@Override public String toString() {
				String str = "";
				if (!titles.isEmpty()) str = titles.toString()+"   ";
				str += "["+getTags()+"]   ";
				if (layout  !=null) str += " Layout:"  +layout;
				if (visible !=null) str += " Visible:" +visible;
				if (playable!=null) str += " Playable:"+playable;
		
				return String.format("%s     PUT[%s]     %s = %s", str, cmdID==null?"??":cmdID, tagList==null?"????":tagList, commandValue==null?"???":commandValue);
				//return str;
				//return XML.toString(node);
			}
		}
		
		static class ComplexCommandItem extends CommandItem implements ComplexCommand.ComplexCommandContext {
			
			ComplexCommand complexCommand;
			private LanguageConfig languageConfig;
			@SuppressWarnings("unused")
			private Vector<ComplexCommandItem> getGroup;

			public ComplexCommandItem(ParsedCommandItem parent, Node node, ComplexCommand.Type type, LanguageConfig languageConfig) {
				super(parent, node);
				this.languageConfig = languageConfig;
				complexCommand = new ComplexCommand(node,type,this);
				indirectValues.add(complexCommand.cmd.getIndirectValues());
				
				if (type==ComplexCommand.Type.Get) {
					String tagList = complexCommand.cmd.baseTagList.toString();
					getGroup = getGroups.add(tagList,this);
				}
			}

			@Override public void parseAttributes(Node node, AttributeConsumer attrConsumer) { super.parseAttributes(node,attrConsumer); }
			@Override public String getContentOfSingleChildTextNode(Node node) { return super.getContentOfSingleChildTextNode(node); }
			@Override public ComplexCommand.BaseCommandList getCmdListFromParent() { return MenuItem.getCmdListFromParent(parent); }
			@Override public Iterator<ParsedCommandItem> getChildIterator() { return new NoChildIterator(); }
			@Override public LanguageConfig getLanguageConfig() { return languageConfig; }

			@Override
			public String toString() {
				return this.getClass().getName();
			}
		}
	}
	
	private static class NoChildIterator implements Iterator<ParsedCommandItem> {
		@Override public boolean hasNext() { return false; }
		@Override public ParsedCommandItem next() { throw new UnsupportedOperationException(); }
	}

	private static class TreeNodeRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 8315605637740823405L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
			Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			
			TreeIcon treeIcon = null;
			if (value instanceof InterpretedDOMTreeNode)
				treeIcon = ((InterpretedDOMTreeNode)value).getIcon();
			
			else if (value instanceof ParsedTreeNode)
				treeIcon = ((ParsedTreeNode)value).getIcon();
			
			//else if (value instanceof DOMTreeNode)
			//	treeIcon = ((DOMTreeNode)value).isLeaf()?null:TreeIcon.XMLTag;
			
			if (treeIcon!=null)
				setIcon(getTreeIcon(treeIcon));
			
			return component;
		}
		
	}
}
