package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import javax.activation.DataHandler;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
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

import net.schwarzbaer.gui.IconSource;

public class CommandList {
	
	private static IconSource.CachedIcons<ParsedTreeIcons> parsedTreeIcons = null;
	private enum ParsedTreeIcons { Menu, Command, Language, UnitDescription, Document }
	
	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		IconSource<ParsedTreeIcons> parsedTreeIconsIS = new IconSource<ParsedTreeIcons>(16,16);
		parsedTreeIconsIS.readIconsFromResource("/ParsedTreeIcons.png");
		parsedTreeIcons = parsedTreeIconsIS.cacheIcons(ParsedTreeIcons.values());
		
		Config.readConfig();
		
		//  http://rx-v475/YamahaRemoteControl/desc.xml
		
		CommandList commandList = new CommandList();
		commandList.createGUI();
//		String addr = "192.168.2.34";
//		String addr = "RX_V475";
//		commandList.readCommandList(addr,true);
	}

	private Document document;
	private JFrame mainWindow;
	private JPopupMenu contextMenu_node;
	private JPopupMenu contextMenu_tree;
	private TreePath contextMenuTarget;
	private TreeViewType selectedTreeViewType;

	CommandList() {
		document = null;
		contextMenuTarget = null;
		selectedTreeViewType = null;
	}
	
	private enum TreeViewType { DOM, Parsed_Experimental } 
	
	private void createGUI() {
		
		DefaultTreeModel treeModel = new DefaultTreeModel(null);
		JTree tree = new JTree(treeModel);
		tree.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getButton()==MouseEvent.BUTTON3) {
					contextMenuTarget = tree.getPathForLocation(e.getX(), e.getY());
					if (contextMenuTarget!=null) contextMenu_node.show(tree, e.getX(), e.getY());
					else                         contextMenu_tree.show(tree, e.getX(), e.getY());
				}
			}
		});
		
		JScrollPane treeScrollPane = new JScrollPane(tree);
		treeScrollPane.setPreferredSize(new Dimension(800,800));
		
		contextMenu_node = new JPopupMenu("Contextmenu");
		contextMenu_node.add(createMenuItem("Copy Value to Clipboard",e->copyToClipBoard(getClickedNodeText())));
		contextMenu_node.add(createMenuItem("Copy Path to Clipboard" ,e->copyToClipBoard(getClickedNodePath())));
		
		contextMenu_tree = new JPopupMenu("Contextmenu");
		contextMenu_tree.add(createMenuItem("Expand Full Tree",e->expandFullTree(tree)));

		selectedTreeViewType = TreeViewType.DOM;
		JComboBox<TreeViewType> treeViewTypeComboBox = createComboBox(TreeViewType.values(),null);
		treeViewTypeComboBox.setSelectedItem(selectedTreeViewType);
		treeViewTypeComboBox.addActionListener(e->{
			selectedTreeViewType = (TreeViewType)(treeViewTypeComboBox.getSelectedItem());
			showCommandList(tree,treeModel);
		});
		
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.X_AXIS));
		northPanel.add(createButton("Get Data",e->{readCommandList();showCommandList(tree,treeModel);}));
		northPanel.add(treeViewTypeComboBox);
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		contentPane.add(northPanel,BorderLayout.NORTH);
		contentPane.add(treeScrollPane,BorderLayout.CENTER);
		
		mainWindow = new JFrame("YamahaControl - CommandList");
		mainWindow.setContentPane(contentPane);
		mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainWindow.pack();
		mainWindow.setLocationRelativeTo(null);
		mainWindow.setVisible(true);
	}

	private void expandFullTree(JTree tree) {
		for (int row=0; row<tree.getRowCount(); ++row)
			if (!tree.isExpanded(row))
				tree.expandRow(row);
		if (selectedTreeViewType==TreeViewType.Parsed_Experimental)
			ParsedTreeNode_Exp.showUnknownTagNames();
	}

	private void showCommandList(JTree tree, DefaultTreeModel treeModel) {
		if (document==null) return;
		if (selectedTreeViewType==null)
			treeModel.setRoot(null);
		else
			if (findUnsupportedNodeTypes(document))
				JOptionPane.showMessageDialog(mainWindow, "Found unsupported node type in DOM. Please take a look into log for details.", "Unknown Node Type", JOptionPane.ERROR_MESSAGE);
			else
				switch(selectedTreeViewType) {
				case DOM                :
					treeModel.setRoot(new DOMTreeNode(document));
					tree.setCellRenderer(null);
					break;
				case Parsed_Experimental:
					treeModel.setRoot(ParsedTreeNode_Exp.createRootNode(document));
					tree.setCellRenderer(new ParsedTreeNodeTreeCellRenderer());
					break;
				}
	}

	private JButton createButton(String title, ActionListener l) {
		JButton button = new JButton(title);
		button.addActionListener(l);
		return button;
	}

	private <A> JComboBox<A> createComboBox(A[] values, ActionListener l) {
		JComboBox<A> comboBox = new JComboBox<A>(values);
		if (l!=null) comboBox.addActionListener(l);
		return comboBox;
	}

	private JMenuItem createMenuItem(String label, ActionListener l) {
		JMenuItem menuItem = new JMenuItem(label);
		if (l!=null) menuItem.addActionListener(l);
		return menuItem;
	}

	private String getClickedNodePath() {
		Object pathComp = contextMenuTarget.getLastPathComponent();
		if (pathComp instanceof DOMTreeNode       ) return XML.getPath(((DOMTreeNode)pathComp).node);;
		if (pathComp instanceof ParsedTreeNode_Exp) return XML.getPath(((ParsedTreeNode_Exp)pathComp).getNode());
		return null;
	}

	private String getClickedNodeText() {
		if (contextMenuTarget==null) return null;
		Object pathComp = contextMenuTarget.getLastPathComponent();
		if (pathComp!=null) return pathComp.toString();
		return null;
	}

	public static void copyToClipBoard(String str) {
		if (str==null) return;
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Clipboard clipboard = toolkit.getSystemClipboard();
		DataHandler content = new DataHandler(str,"text/plain");
		try { clipboard.setContents(content,null); }
		catch (IllegalStateException e1) { e1.printStackTrace(); }
	}

	private void readCommandList() {
		//Object selectedAddress = JOptionPane.showInputDialog(mainWindow, "message", "title", JOptionPane.QUESTION_MESSAGE, null, Config.knownIPs.toArray(), null);
		String selectedAddress = Config.selectAddress(mainWindow);
		if (selectedAddress!=null)
			readCommandList(selectedAddress.toString(), true);
	}

	private void readCommandList(String addr, boolean verbose) {
		String content = requestContent("http://"+addr+"/YamahaRemoteControl/desc.xml", verbose);
		document = content==null?null:XML.parse(content);
		if (verbose) System.out.println("done");
	}

	private String requestContent(String urlStr, boolean verbose) {
		if (verbose) System.out.println("URL: "+urlStr);
		
		URL url;
		try { url = new URL(urlStr); }
		catch (MalformedURLException e2) { e2.printStackTrace(); return null; }
		
		if (verbose) System.out.println("Open Connection ...");
		HttpURLConnection connection;
		try { connection = (HttpURLConnection)url.openConnection(); }
		catch (IOException e) { e.printStackTrace(); return null; }
		
		connection.setDoInput(true);
		
		try { connection.connect(); }
		catch (IOException e) { e.printStackTrace(); return null; }
		
		if (verbose) {
			Map<String, List<String>> headerFields = connection.getHeaderFields();
			for (Entry<String, List<String>> entry:headerFields.entrySet()) {
				String key = entry.getKey();
				StringBuilder values = new StringBuilder();
				entry.getValue().stream().forEach(str->values.append(" \"").append(str).append("\""));
				System.out.printf("Header[%-20s]: %s%n", key, values.toString());
			}
			try { System.out.println("ResponseCode   : "+connection.getResponseCode   ()); } catch (IOException e) {}
			try { System.out.println("ResponseMessage: "+connection.getResponseMessage()); } catch (IOException e) {}
			System.out.println("ContentLength  : "+connection.getContentLength  ());
			System.out.println("ContentType    : "+connection.getContentType    ());
			System.out.println("ContentEncoding: "+connection.getContentEncoding());
		}
		
		Object content;
		try { content = connection.getContent(); }
		catch (IOException e) { e.printStackTrace(); connection.disconnect(); return null; }
		if (verbose) System.out.println("Content: "+content); 
		
		String contentStr = null;
		if (content instanceof InputStream) {
			InputStream input = (InputStream)content;
			byte[] responseBytes = new byte[connection.getContentLength()];
			int n,pos=0;
			try { while ( (n=input.read(responseBytes, pos, responseBytes.length-pos))>=0 ) pos += n; }
			catch (IOException e) { e.printStackTrace(); if (verbose) System.out.println("abort reading response");}
			
			if (verbose) {
				String bytesRead = pos!=responseBytes.length?(" "+pos+" of "+responseBytes.length+" bytes "):"";
				if (pos<1000) {
					System.out.println("Content (bytes read): "+bytesRead+""+Arrays.toString(responseBytes));
				}
			}
			
			contentStr = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(responseBytes, 0, pos)).toString();
			
			if (verbose) {
				if (contentStr.length()>1000) {
					System.out.println("Content (as String): "+contentStr.substring(0, 100)+" ...");
					System.out.println("                     ... "+contentStr.substring(contentStr.length()-100,contentStr.length()));
				} else
					System.out.println("Content (as String): "+contentStr);
			}
		}
		
		connection.disconnect();
		
		return contentStr;
	}

	public static boolean findUnsupportedNodeTypes(Document document) {
		HashMap<Short, Integer> foundNodeTypes = new HashMap<>();
		addToNodeTypes(document,foundNodeTypes);
		System.out.println("Found NodeTypes in DOM:");
		boolean foundUnknownNodeTypes = false;
		for (Entry<Short, Integer> entry:foundNodeTypes.entrySet()) {
			switch (entry.getKey()) {
			case Node.DOCUMENT_NODE     : System.out.println("   DOCUMENT: "+entry.getValue()); break;
			case Node.ELEMENT_NODE      : System.out.println("   ELEMENT : "+entry.getValue()); break;
			case Node.TEXT_NODE         : System.out.println("   TEXT    : "+entry.getValue()); break;
			case Node.COMMENT_NODE      : System.out.println("   COMMENT : "+entry.getValue()); break;
			case Node.CDATA_SECTION_NODE: System.out.println("   CDATA   : "+entry.getValue()); break;
			default: System.err.println("   Unknown["+entry.getKey()+"]: "+entry.getValue()); foundUnknownNodeTypes = true; break;
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

	private static final class DOMTreeNode implements TreeNode {
	
		private DOMTreeNode parent;
		private Node node;
		private Vector<DOMTreeNode> children;

		public DOMTreeNode(Document document) {
			this(null,document);
		}
		
		public DOMTreeNode(DOMTreeNode parent, Node node) {
			this.parent = parent;
			this.node = node;
			this.children = null;
		}

		private void createChildren() {
			children = new Vector<>();
			NodeList childNodes = node.getChildNodes();
			for (int i=0; i<childNodes.getLength(); ++i)
				children.add(new DOMTreeNode(this, childNodes.item(i)));
		}
		
		@Override
		public String toString() {
			return XML.toString(node);
			//return "DOMTreeNode [parent=" + parent + ", node=" + node + ", children=" + children + "]";
		}
		
		@Override public TreeNode getParent()        { return parent; }
		@Override public boolean getAllowsChildren() { return true; }
		@Override public boolean isLeaf()            { return getChildCount()==0; }
		@Override public int getChildCount()         { return node.getChildNodes().getLength(); }
		
		@Override public int         getIndex(TreeNode node)    { if (children==null) createChildren(); return children.indexOf(node); }
		@Override public TreeNode    getChildAt(int childIndex) { if (children==null) createChildren(); return children.get(childIndex); }
		@SuppressWarnings("rawtypes")
		@Override public Enumeration children()                 { if (children==null) createChildren(); return children.elements(); }
	
	}
	
	private static class ParsedTreeNodeTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 8315605637740823405L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
			Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			if (value instanceof ParsedTreeNode_Exp)
				setIcon(parsedTreeIcons.getCachedIcon(((ParsedTreeNode_Exp)value).getIcon()));
			else
				setIcon(null);
			return component;
		}
		
	}
	
	private static abstract class ParsedTreeNode_Exp implements TreeNode {
		
		public static final HashMap<String,Integer> unknownTagNames = new HashMap<>();
		
		public static void showUnknownTagNames() {
			if (unknownTagNames.isEmpty()) return;
			System.out.println("Unknown TagNames in DOM:");
			for (Entry<String, Integer> entry:unknownTagNames.entrySet())
				System.out.printf("   %-20s: %d%n", entry.getKey(), entry.getValue());
		}

		public static ParsedTreeNode_Exp createRootNode(Document document) {
			unknownTagNames.clear();
			return new DocumentNode(document);
		}

		private static ParsedTreeNode_Exp createTreeNode(ParsedTreeNode_Exp parent, Node node) {
			switch (node.getNodeType()) {
			case Node.ELEMENT_NODE      : return createTreeNode(parent, (Element)node);
			case Node.TEXT_NODE         : return new TextNode(parent, "\"%s\""     , node.getNodeValue());
			case Node.COMMENT_NODE      : return new TextNode(parent, "<!-- %s -->", node.getNodeValue());
			case Node.CDATA_SECTION_NODE: return new TextNode(parent, "[CDATA %s ]", node.getNodeValue());
			}
			throw new IllegalStateException("");
		}

		private static ParsedTreeNode_Exp createTreeNode(ParsedTreeNode_Exp parent, Element node) {
			switch (node.getTagName()) {
			case "Unit_Description": return new UnitDescriptionNode(parent, node);
			case "Language"        : return new LanguageNode       (parent, node);
			case "Menu"            : return new MenuNode           (parent, node);
			case "Cmd_List"        : return new BaseCommandListNode(parent, node);
			case "Put_1"           : return new Put1CommandNode    (parent, node);
			case "Put_2"           : return new Put2CommandNode    (parent, node);
			case "Get"             : return new GetCommandNode     (parent, node);
			}
			unknownTagNames.put(node.getNodeName(), 1+unknownTagNames.getOrDefault(node.getNodeName(),0));
			return new GenericXMLNode(parent,node);
			//return new TextNode(parent, "Unknown Element \"%s\"", node.getNodeName());
		}
		
		protected ParsedTreeNode_Exp parent;
		protected Vector<ParsedTreeNode_Exp> children;
		private ParsedTreeIcons icon;

		public ParsedTreeNode_Exp(ParsedTreeNode_Exp parent, ParsedTreeIcons icon) {
			this.parent = parent;
			this.icon = icon;
			this.children = null;
		}

		public ParsedTreeIcons getIcon() {
			return icon;
		}

		@Override public abstract String toString();
		public abstract Node getNode();
		public void reportError(String format, Object... values) {
			CommandList.reportError(this.getClass().getSimpleName(), getNode(), format, values);
		}

		protected abstract void createChildren();
		protected void createChildren(Node node) {
			children = new Vector<>();
			NodeList childNodes = node.getChildNodes();
			for (int i=0; i<childNodes.getLength(); ++i)
				children.add(createTreeNode(this, childNodes.item(i)));
		}
		
		///////////////////////////////////////////////
		// TreeNode methods
		@Override public TreeNode getParent()         { return parent; }
		@Override public boolean  getAllowsChildren() { return true; }
		@Override public boolean  isLeaf()            { return getChildCount()==0; }

		@Override public int         getChildCount()            { if (children==null) createChildren(); return children.size(); }
		@Override public int         getIndex(TreeNode node)    { if (children==null) createChildren(); return children.indexOf(node); }
		@Override public TreeNode    getChildAt(int childIndex) { if (children==null) createChildren(); return children.get(childIndex); }
		@SuppressWarnings("rawtypes")
		@Override public Enumeration children()                 { if (children==null) createChildren(); return children.elements(); }
		///////////////////////////////////////////////
		
		private static class GenericXMLNode extends ParsedTreeNode_Exp {

			private Node node;
			public GenericXMLNode(ParsedTreeNode_Exp parent, Node node) {
				super(parent, null);
				this.node = node;
			}

			@Override public String toString() { return XML.toString(node); }
			@Override public Node getNode() { return node; }
			@Override
			protected void createChildren() {
				children = new Vector<>();
				NodeList childNodes = node.getChildNodes();
				for (int i=0; i<childNodes.getLength(); ++i)
					children.add(new GenericXMLNode(this, childNodes.item(i)));
			}
		}
		
		private static class TextNode extends ParsedTreeNode_Exp {
			private String str;
			public TextNode(ParsedTreeNode_Exp parent, String format, Object... values) {
				this(parent,null,format,values);
			}
			public TextNode(ParsedTreeNode_Exp parent, ParsedTreeIcons icon, String format, Object... values) {
				super(parent,icon); str = String.format(format, values);
			}
			@Override public String toString() { return str; }
			@Override protected void createChildren() { children = new Vector<>(); }
			@Override public Node getNode() { return null; }
		}

		private static class DocumentNode extends ParsedTreeNode_Exp {
			private Document document;
			public DocumentNode(Document document) {
				super(null,ParsedTreeIcons.Document);
				this.document = document;
			}
			@Override public String toString() { return "Function Description of Yamaha Device"; }
			@Override protected void createChildren() { createChildren(document); }
			@Override public Node getNode() { return document; }
		}

		private static abstract class ElementNode extends ParsedTreeNode_Exp {
			protected Element node;

			ElementNode(ParsedTreeNode_Exp parent, Element node, ParsedTreeIcons icon) {
				super(parent,icon);
				this.node = node;
			}
			
			@Override protected void createChildren() { createChildren(node); }
			@Override public Node getNode() { return node; }

			protected void parseAttributes(AttributeConsumer attrConsumer) {
				NamedNodeMap attributes = node.getAttributes();
				for (int i=0; i<attributes.getLength(); ++i) {
					Node attr = attributes.item(i);
					boolean wasExpected = attrConsumer.consume(attr.getNodeName(), attr.getNodeValue());
					if (!wasExpected)
						reportError("Found unexpected attribute: %s=\"%s\"", attr.getNodeName(), attr.getNodeValue());
				}
			}
			
			protected static interface AttributeConsumer {
				public boolean consume(String attrName, String attrValue);
			}
		}

		private static class UnitDescriptionNode extends ElementNode {
			
			private String unitName;
			private String version;

			public UnitDescriptionNode(ParsedTreeNode_Exp parent, Element node) {
				super(parent,node,ParsedTreeIcons.UnitDescription);
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

			public LanguageNode(ParsedTreeNode_Exp parent, Element node) {
				super(parent,node,ParsedTreeIcons.Language);
				this.code = "???";
				// <Language Code="en">
				parseAttributes((attrName, attrValue) -> { if ("Code".equals(attrName)) { code=attrValue; return true; } return false; });
			}
			@Override public String toString() { return String.format("Language: %s", code); }
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

			public MenuNode(ParsedTreeNode_Exp parent, Element node) {
				super(parent,node,ParsedTreeIcons.Menu);
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
			
			public static HashMap<String, String> getCmdListFromParent(ParsedTreeNode_Exp parent) {
				if (parent instanceof MenuNode) {
					MenuNode menu = ((MenuNode)parent);
					if (menu.cmdListNode!=null) return menu.cmdListNode.commands;
					return getCmdListFromParent(menu.parent);
				}
				return null;
			}
			
			@Override protected void createChildren() {
				children = new Vector<>();
				NodeList childNodes = node.getChildNodes();
				for (int i=0; i<childNodes.getLength(); ++i) {
					ParsedTreeNode_Exp treeNode = createTreeNode(this, childNodes.item(i));
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

		private static class BaseCommandListNode extends ElementNode {
			
			private HashMap<String,String> commands;
			
			public BaseCommandListNode(ParsedTreeNode_Exp parent, Element node) {
				super(parent,node,ParsedTreeIcons.Command);
				this.commands = null;
				parseAttributes((attrName, attrValue) -> false);
				createChildren();
			}

			@Override public String toString() { return "Command List"; }
		
			@Override
			protected void createChildren() {
				children = new Vector<>();
				commands = new HashMap<>();
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
					commands.put(cmdNode.id,cmdNode.command);
					children.add(cmdNode);
				}
			}
		}

		private static class BaseCommandDefineNode extends ElementNode {
		
			public String id;
			public String command;

			public BaseCommandDefineNode(ParsedTreeNode_Exp parent, Element node) {
				super(parent,node,ParsedTreeIcons.Command);
				this.id = null;
				this.command = null;
				// <Define ID="P7">command</Define>
				parseAttributes((attrName, attrValue) -> { if ("ID".equals(attrName)) { id=attrValue; return true; } return false; });
				children = new Vector<>();
				
				if (id == null)
					reportError("Found \"Define\" node with no ID.");
				
				NodeList childNodes = this.node.getChildNodes();
				if (childNodes.getLength()!=1) {
					reportError("Found unexpected number of commands inside of \"Define\" node. (found:%d, expected:1)", childNodes.getLength());
					for (int i=0; i<childNodes.getLength(); ++i)
						children.add(new GenericXMLNode(this, childNodes.item(i)));
					return;
				}
				
				Node child = childNodes.item(0);
				if (child.getNodeType()!=Node.TEXT_NODE || !(child instanceof Text)) {
					reportError("Found child [%d] with unexpected node type. (found:%s, expected:%s)", 0, XML.getLongName(child.getNodeType()), XML.getLongName(Node.TEXT_NODE));
					children.add(new GenericXMLNode(this, child));
					return;
				}
				
				command = child.getNodeValue();
			}
			
			@Override public String toString() { return String.format("%s: %s", id, command); }
			@Override protected void createChildren() { throw new UnsupportedOperationException("Caling CommandNode.createChildren() is not allowed."); }
		}

		private static class Put1CommandNode extends ElementNode {
		
			private String cmdID;
			private String func;
			private String funcEx;
			private String title;
			private String playable;
			private String layout;
			private String visible;
			private String commandValue;
			private String command;

			public Put1CommandNode(ParsedTreeNode_Exp parent, Element node) {
				super(parent, node, ParsedTreeIcons.Command);
				this.cmdID  = null;
				this.command = null;
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
				
				if (cmdID == null)
					reportError("Found \"Put_1\" node with no ID.");
				else {
					HashMap<String, String> cmdList = MenuNode.getCmdListFromParent(parent);
					if (cmdList!=null) command = cmdList.get(cmdID);
				}
				
				NodeList childNodes = this.node.getChildNodes();
				if (childNodes.getLength()!=1) {
					reportError("Found unexpected number of value inside of \"Put_1\" node. (found:%d, expected:1)", childNodes.getLength());
					for (int i=0; i<childNodes.getLength(); ++i)
						children.add(new GenericXMLNode(this, childNodes.item(i)));
					return;
				}
				
				Node child = childNodes.item(0);
				if (child.getNodeType()!=Node.TEXT_NODE || !(child instanceof Text)) {
					reportError("Found child [%d] with unexpected node type. (found:%s, expected:%s)", 0, XML.getLongName(child.getNodeType()), XML.getLongName(Node.TEXT_NODE));
					children.add(new GenericXMLNode(this, child));
					return;
				}
				
				commandValue = child.getNodeValue();
				
				children.add( new TextNode(this, ParsedTreeIcons.Command, "PUT[%s]     %s = %s", cmdID==null?"??":cmdID, command==null?"????":command, commandValue==null?"???":commandValue) );
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
				return str;
				//return XML.toString(node);
			}

			@Override protected void createChildren() {
				throw new UnsupportedOperationException("Caling Put1CommandNode.createChildren() is not allowed.");
			}
		}

		private static class Put2CommandNode extends ElementNode {
		
			public Put2CommandNode(ParsedTreeNode_Exp parent, Element node) {
				super(parent, node, ParsedTreeIcons.Command);
				
				// TODO Auto-generated constructor stub
			}
		
			@Override
			public String toString() {
				// TODO Auto-generated method stub
				return XML.toString(node);
			}
		
		}

		private static class GetCommandNode extends ElementNode {
		
			public GetCommandNode(ParsedTreeNode_Exp parent, Element node) {
				super(parent, node, ParsedTreeIcons.Command);
				// TODO Auto-generated constructor stub
			}
		
			@Override
			public String toString() {
				// TODO Auto-generated method stub
				return XML.toString(node);
			}
		
		}
	}

	public static void reportError(String className, Node node, String format, Object... values) {
		System.err.printf("[%s] %s [%s]%n", className, String.format(format, values), XML.getPath(node));
	}
}
