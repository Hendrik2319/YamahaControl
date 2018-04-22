package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
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
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class CommandList {

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
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
		JScrollPane treeScrollPane = new JScrollPane(tree);
		treeScrollPane.setPreferredSize(new Dimension(800,800));
		tree.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getButton()==MouseEvent.BUTTON3) {
					contextMenuTarget = tree.getPathForLocation(e.getX(), e.getY());
					if (contextMenuTarget!=null) contextMenu_node.show(tree, e.getX(), e.getY());
					//else                         contextMenu_tree.show(tree, e.getX(), e.getY());
				}
			}
		});
		
		contextMenu_node = new JPopupMenu("Contextmenu");
		contextMenu_node.add(createMenuItem("Copy Value to Clipboard",e->copyToClipBoard(getClickedNodeText())));

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

	private void showCommandList(JTree tree, DefaultTreeModel treeModel) {
		if (document==null) return;
		if (selectedTreeViewType==null)
			treeModel.setRoot(null);
		else
			switch(selectedTreeViewType) {
			case DOM: treeModel.setRoot(new DOMTreeNode(document)); break;
			case Parsed_Experimental: treeModel.setRoot(ParsedTreeNode_Exp.createTreeNode(document)); break;
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
		
		DocumentBuilder docBuilder = null;
		try { docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder(); }
		catch (ParserConfigurationException e) { e.printStackTrace(); }
		
		document = null;
		if (docBuilder!=null)
			try { document = docBuilder.parse(new InputSource(new StringReader(content))); }
			catch (SAXException | IOException e) { e.printStackTrace(); }
		
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
			switch (node.getNodeType()) {
			case Node.DOCUMENT_NODE     : return toString((Document    )node);
			case Node.ELEMENT_NODE      : return toString((Element     )node);
			case Node.TEXT_NODE         : return toString((Text        )node);
			case Node.COMMENT_NODE      : return toString((Comment     )node);
			case Node.CDATA_SECTION_NODE: return toString((CDATASection)node);
			}
			
			return String.format("[%d] %s", node.getNodeType(), node.getNodeName());
			//return "DOMTreeNode [parent=" + parent + ", node=" + node + ", children=" + children + "]";
		}
		
		private String toString(Comment comment) {
			return String.format("<!-- %s -->", comment.getNodeValue());
		}
		private String toString(CDATASection cdataSection) {
			return String.format("[CDATA %s ]", cdataSection.getNodeValue());
		}
		private String toString(Text text) {
			return String.format("\"%s\"", text.getNodeValue());
		}
		private String toString(Element element) {
			StringBuilder sb = new StringBuilder();
			NamedNodeMap attributes = element.getAttributes();
			for (int i=0; i<attributes.getLength(); ++i) {
				Node attr = attributes.item(i);
				sb.append(String.format(" %s=\"%s\"", attr.getNodeName(), attr.getNodeValue()));
			}
			return String.format("<%s%s>", element.getNodeName(), sb.toString());
		}
		private String toString(Document document) {
			String str = "Document "+document.getNodeName();
			if (document.getDoctype    ()!=null) str += " DocType:"    +document.getDoctype    ();
			if (document.getBaseURI    ()!=null) str += " BaseURI:"    +document.getBaseURI    ();
			if (document.getDocumentURI()!=null) str += " DocURI:"     +document.getDocumentURI();
			if (document.getXmlEncoding()!=null) str += " XmlEncoding:"+document.getXmlEncoding();
			if (document.getXmlVersion ()!=null) str += " XmlVersion:" +document.getXmlVersion ();
			return str;
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

	private static abstract class ParsedTreeNode_Exp implements TreeNode {
	
		public static ParsedTreeNode_Exp createTreeNode(Document document) {
			return new DocumentNode(document);
		}

		private static ParsedTreeNode_Exp createTreeNode(ParsedTreeNode_Exp parent, Node node) {
			switch (node.getNodeType()) {
			case Node.ELEMENT_NODE: break;
			}
			
			return null;
		}

		protected ParsedTreeNode_Exp parent;
		protected Vector<ParsedTreeNode_Exp> children;

		public ParsedTreeNode_Exp(ParsedTreeNode_Exp parent) {
			this.parent = parent;
			this.children = null;
		}

		protected abstract void createChildren();
		@Override public abstract String toString();

		@Override public TreeNode getParent()         { return parent; }
		@Override public boolean  getAllowsChildren() { return true; }
		@Override public boolean  isLeaf()            { return getChildCount()==0; }

		@Override public int         getChildCount()            { if (children==null) createChildren(); return children.size(); }
		@Override public int         getIndex(TreeNode node)    { if (children==null) createChildren(); return children.indexOf(node); }
		@Override public TreeNode    getChildAt(int childIndex) { if (children==null) createChildren(); return children.get(childIndex); }
		@SuppressWarnings("rawtypes")
		@Override public Enumeration children()                 { if (children==null) createChildren(); return children.elements(); }
		
		public static class DocumentNode extends ParsedTreeNode_Exp {
		
			private Document document;

			public DocumentNode(Document document) {
				super(null);
				this.document = document;
			}

			@Override protected void createChildren() {
				children = new Vector<>();
				NodeList childNodes = document.getChildNodes();
				for (int i=0; i<childNodes.getLength(); ++i)
					children.add(createTreeNode(this, childNodes.item(i)));
			}

			@Override public String toString() { return "Function Description of Yamaha Device"; }
		}
	
	}

}
