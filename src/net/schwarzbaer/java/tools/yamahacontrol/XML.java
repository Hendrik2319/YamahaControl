package net.schwarzbaer.java.tools.yamahacontrol;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Vector;
import java.util.function.Consumer;

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

import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.Log;

final class XML {
	
	public static class TagList {
		private final String tagListStr;
		public final String[] tagList;

		TagList(String tagListStr) {
			this.tagListStr = tagListStr;
			this.tagList = tagListStr.split(",");
		}

		public String toXML(String value) {
			String xmlStr = value;
			for (int i=tagList.length-1; i>=0; --i)
				xmlStr = "<"+tagList[i]+">"+xmlStr+"</"+tagList[i]+">";
			return xmlStr;
		}

		@Override
		public String toString() {
			return tagListStr;
		}

		public TagList addBefore(String tagListStr) {
			return new TagList(tagListStr+','+this.tagListStr);
		}

		public TagList addAfter(String tagListStr) {
			return new TagList(this.tagListStr+','+tagListStr);
		}
	}
	
	public static Document parse(String xmlStr) {
		if (xmlStr==null) return null;
		try {
			return DocumentBuilderFactory
					.newInstance()
					.newDocumentBuilder()
					.parse(new InputSource(new StringReader(xmlStr)));
		} catch (SAXException | IOException | ParserConfigurationException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String getXMLformatedString(String xmlStr) {
		StringBuilder sb = new StringBuilder();
		showXMLformated(sb,xmlStr);
		return sb.toString();
	}

	public static void showXMLformated(String xmlStr) {
		showXMLformated(XML.parse(xmlStr));
	}

	public static void showXMLformated(Document document) {
		StringBuilder sb = new StringBuilder();
		showXMLformated(sb,document);
		System.out.print(sb);
	}

	public static void showXMLformated(StringBuilder sb, String xmlStr) {
		showXMLformated(sb, XML.parse(xmlStr));
	}

	public static void showXMLformated(StringBuilder sb, Document document) {
		if (document==null) return;
		showXMLformated(sb,"",document);
	}

	private static void showXMLformated(StringBuilder sb, String indent, Node node) {
		sb.append(indent+XML.toString(node)+"\r\n");
		NodeList childNodes = node.getChildNodes();
		for (int i=0; i<childNodes.getLength(); ++i)
			showXMLformated(sb,indent+"|   ", childNodes.item(i));
	}

	public static String getPath(Node node) {
		if (node==null) return "<null>";
		String str = getShortName(node.getNodeType())+":"+node.getNodeName();
		
		Node parent = node.getParentNode();
		if (parent==null) return str;
		
		NodeList childNodes = parent.getChildNodes();
		String index = "??";
		for (int i=0; i<childNodes.getLength(); ++i)
			if (childNodes.item(i)==node) { index = ""+i; break; }
		
		return getPath(parent)+".["+index+"]"+str;
	}

	public static Vector<Node> getChildNodesByNodeName(Node node, String nodeName) {
		Vector<Node> nodes = new Vector<>();
		getChildNodesByNodeName(node, nodeName, nodes);
		return nodes;
	}

	public static void getChildNodesByNodeName(Node node, String nodeName, Vector<Node> nodes) {
		NodeList childNodes = node.getChildNodes();
		for (int i=0; i<childNodes.getLength(); ++i) {
			Node child = childNodes.item(i);
			if (nodeName.equalsIgnoreCase(child.getNodeName()))
				nodes.add(child);
		}
	}

	public static boolean hasChild(Node node, String nodeName) {
		return !getChildNodesByNodeName(node,nodeName).isEmpty();
	}

	public static Node getSubNode(Node node, String... tagList) {
		Vector<Node> nodes = XML.getSubNodes(node, tagList);
		if (nodes.isEmpty()) { Log.error(XML.class, "Can't find subnode: Node=%s TagList=%s", XML.getPath(node), Arrays.toString(tagList)); return null; }
		if (nodes.size()>1) Log.warning(XML.class, "Found more than one subnode: Node=%s TagList=%s", XML.getPath(node), Arrays.toString(tagList));
		return nodes.get(0);
	}

	public static Vector<Node> getSubNodes(Node node, String... tagList) {
		Vector<Node> nodes=new Vector<>(), subNodes;
		nodes.add(node);
		for (String part:tagList) {
			subNodes = new Vector<>();
			for (Node node__:nodes)
				getChildNodesByNodeName(node__, part, subNodes);
			nodes = subNodes;
		}
		return nodes;
	}

	public static Vector<Node> getNodes(Document document, TagList tagList) {
		return getSubNodes(document, tagList.tagList);
	}

	public static String getNodeAttribute(Document document, TagList tagList, String attrName) {
		Vector<Node> nodes = getNodes(document, tagList);
		if (nodes.isEmpty()) return null;
		
		NamedNodeMap attrs = nodes.get(0).getAttributes();
		if (attrs==null) return null;
		
		Node attr = attrs.getNamedItem(attrName);
		if (attr==null) return null;
		
		return attr.getNodeValue();
	}
	
	public static Integer getSubValue_Int(Node node, String... tagList) {
		String valueStr = getSubValue(node, tagList);
		if (valueStr==null) return null;
		try { return Integer.parseInt(valueStr); }
		catch (NumberFormatException e) { return null; }
	}
	
	public static String getSubValue(Node node, String... tagList) {
		if (tagList.length==0) return getContentOfSingleChildTextNode(node);
		Node value = getSubNode(node, tagList);
		if (value==null) return null;
		return getContentOfSingleChildTextNode(value);
	}

	public static <T extends Device.Value> T getSubValue(Node node, T[] values, String... tagList) {
		String str = getSubValue(node,tagList);
		if (str!=null)
			for (T val:values)
				if (str.equals(val.getLabel()))
					return val;
		return null;
	}

	private static String getContentOfSingleChildTextNode(Node node) {
		NodeList childNodes = node.getChildNodes();
		if (childNodes.getLength()!=1) return null;
		
		Node child = childNodes.item(0);
		if (child.getNodeType()!=Node.TEXT_NODE || !(child instanceof Text)) return null;
		
		String value = child.getNodeValue();
		if (value==null) return null;
		return value.replace("&gt;",">").replace("&lt;","<").replace("&amp;","&");
	}
	
	public static void forEachChild(Node node, Consumer<Node> consumer) {
		if (node==null) return;
		NodeList childNodes = node.getChildNodes();
		for (int i=0; i<childNodes.getLength(); ++i)
			consumer.accept(childNodes.item(i));
	}
	
	public static String getShortName(short nodeType) {
		switch (nodeType) {
		case Node.DOCUMENT_NODE     : return "Doc";
		case Node.ELEMENT_NODE      : return "El";
		case Node.TEXT_NODE         : return "Txt";
		case Node.COMMENT_NODE      : return "//";
		case Node.CDATA_SECTION_NODE: return "CD";
		default: return ""+nodeType;
		}
	}
	
	public static String getLongName(short nodeType) {
		switch (nodeType) {
		case Node.DOCUMENT_NODE     : return "DOCUMENT";
		case Node.ELEMENT_NODE      : return "ELEMENT";
		case Node.TEXT_NODE         : return "TEXT";
		case Node.COMMENT_NODE      : return "COMMENT";
		case Node.CDATA_SECTION_NODE: return "CDATA_SECTION";
		default: return "Unknown Type "+nodeType;
		}
	}

	public static String toString(Node node) {
		switch (node.getNodeType()) {
		case Node.DOCUMENT_NODE     : return toString((Document    )node);
		case Node.ELEMENT_NODE      : return toString((Element     )node);
		case Node.TEXT_NODE         : return toString((Text        )node);
		case Node.COMMENT_NODE      : return toString((Comment     )node);
		case Node.CDATA_SECTION_NODE: return toString((CDATASection)node);
		}
		return String.format("[%d] %s", node.getNodeType(), node.getNodeName());
	}
	
	public static String toString(Comment comment) {
		return String.format("<!-- %s -->", comment.getNodeValue());
	}
	public static String toString(CDATASection cdataSection) {
		return String.format("[CDATA %s ]", cdataSection.getNodeValue());
	}
	public static String toString(Text text) {
		return String.format("\"%s\"", text.getNodeValue());
	}
	public static String toString(Element element) {
		StringBuilder sb = new StringBuilder();
		NamedNodeMap attributes = element.getAttributes();
		for (int i=0; i<attributes.getLength(); ++i) {
			Node attr = attributes.item(i);
			sb.append(String.format(" %s=\"%s\"", attr.getNodeName(), attr.getNodeValue()));
		}
		return String.format("<%s%s>", element.getNodeName(), sb.toString());
	}
	public static String toString(Document document) {
		String str = "Document "+document.getNodeName();
		if (document.getDoctype    ()!=null) str += " DocType:"    +document.getDoctype    ();
		if (document.getBaseURI    ()!=null) str += " BaseURI:"    +document.getBaseURI    ();
		if (document.getDocumentURI()!=null) str += " DocURI:"     +document.getDocumentURI();
		if (document.getXmlEncoding()!=null) str += " XmlEncoding:"+document.getXmlEncoding();
		if (document.getXmlVersion ()!=null) str += " XmlVersion:" +document.getXmlVersion ();
		return str;
	}
}
