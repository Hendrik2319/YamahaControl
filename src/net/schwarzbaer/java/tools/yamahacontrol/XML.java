package net.schwarzbaer.java.tools.yamahacontrol;

import java.io.IOException;
import java.io.StringReader;

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

final class XML {

	public static Document parse(String xmlStr) {
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

	public static void showXMLformated(String xmlStr) {
		Document document = XML.parse(xmlStr);
		if (document==null) return;
		showXMLformated("",document);
	}

	public static void showXMLformated(String indent, Node node) {
		System.out.println(indent+XML.toString(node));
		NodeList childNodes = node.getChildNodes();
		for (int i=0; i<childNodes.getLength(); ++i)
			showXMLformated(indent+"|   ", childNodes.item(i));
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
