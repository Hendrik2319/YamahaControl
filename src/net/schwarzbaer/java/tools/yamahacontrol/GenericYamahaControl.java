package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.w3c.dom.Document;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.CommandItem;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.DocumentItem;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.MenuItem;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.UnitDescriptionItem;

public class GenericYamahaControl {

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		Config.readConfig();
		Ctrl.readCommProtocolFromFile();

		openWindow(null);
	}
	
	public static void openWindow(String address) {
		
		if (address==null)
			address = Config.selectAddress(null);
		
		GenericYamahaControl commandList = new GenericYamahaControl(address);
		commandList.readCommandList();
		commandList.createGUI();
	}

	private String address;
	private DocumentItem commandList;
	private StandardMainWindow mainWindow;

	public GenericYamahaControl(String address) {
		this.address = address;
		commandList = null;
	}

	private void createGUI() {
		if (commandList==null) return;
		
		JComponent contentPane = createPanel(commandList);
		
		mainWindow = new StandardMainWindow("Generic YamahaControl");
		mainWindow.startGUI(contentPane);
	}

	private void readCommandList() {
		if (address==null) return;
		boolean verbose = true;
		String content = Ctrl.http.getContentFromURL("http://"+address+"/YamahaRemoteControl/desc.xml", verbose );
		Document document = content==null?null:XML.parse(content);
		if (document!=null) commandList = new DocumentItem(document);
		if (verbose) System.out.println("done");
	}

	private JComponent createPanel(ParsedCommandItem item) {
		if (item instanceof ParsedCommandItem.DocumentItem       ) return createPanel_(((ParsedCommandItem.DocumentItem) item).unitDescription);
		if (item instanceof ParsedCommandItem.UnitDescriptionItem) return createPanel_((ParsedCommandItem.UnitDescriptionItem) item);
		if (item instanceof ParsedCommandItem.MenuItem           ) return createPanel_((ParsedCommandItem.MenuItem           ) item);
		if (item instanceof ParsedCommandItem.CommandItem        ) return createPanel_((ParsedCommandItem.CommandItem        ) item);
		throw new IllegalArgumentException();
	}

	private JComponent createPanel_(UnitDescriptionItem item) {
		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.setBorder(BorderFactory.createTitledBorder(item.toString()));
		for (MenuItem menu:item.menues)
			tabbedPane.addTab(getTabTitle(menu), createPanel_(menu));
		return tabbedPane;
	}

	private JComponent createPanel_(MenuItem item) {
		
		JPanel commandList = null;
		if (!item.commands.isEmpty()) {
			commandList = new JPanel(new GridLayout(0,1,3,3));
			for (CommandItem cmd:item.commands) commandList.add(createPanel_(cmd));
		}
		
		JTabbedPane tabbedPane = null;
		if (!item.menues.isEmpty()) {
			tabbedPane = new JTabbedPane();
			for (MenuItem menu:item.menues) tabbedPane.addTab(getTabTitle(menu), createPanel_(menu));
		}
		
		if (commandList!=null && tabbedPane!=null) {
			JPanel panel = new JPanel(new BorderLayout(3,3));
			panel.add(commandList,BorderLayout.NORTH);
			panel.add(tabbedPane,BorderLayout.CENTER);
			return panel;
		}
		
		if (commandList!=null) return commandList;
		if (tabbedPane !=null) return tabbedPane;
		return new JLabel(item.toString());
	}

	private JComponent createPanel_(CommandItem item) {
		// TODO Auto-generated method stub
		return new JLabel(item.toString());
	}

	private String getTabTitle(MenuItem item) {
		if (!item.titles.isEmpty()) return item.titles.toString();
		
		String tags = item.getTags();
		if (!tags.isEmpty()) return tags;
		
		return item.toString();
	}

}
