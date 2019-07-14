package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.TitledBorder;

import org.w3c.dom.Document;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.LanguageConfig;
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
		
		
		LanguageConfig languageConfig = commandList.unitDescription.languageConfig;
		Vector<String> langCodes = languageConfig.getLangCodes();
		String langCode = langCodes.isEmpty()?null:langCodes.firstElement();
		
		GUIComp base = GUIComp.createBase(commandList,langCode,languageConfig);
		
		JPanel languagePanel = new JPanel(new BorderLayout(3,3));
		languagePanel.add(new JLabel("Language: "),BorderLayout.WEST);
		
		if (langCodes.size()>1) {
			JComboBox<String> langSelect = new JComboBox<String>(langCodes);
			langSelect.addActionListener(e->GUIComp.setLanguage(base,(String)langSelect.getSelectedItem()));
			languagePanel.add(langSelect,BorderLayout.CENTER);
		} else if (langCodes.size()==1)
			languagePanel.add(new JLabel(langCode),BorderLayout.CENTER);
		else
			languagePanel.add(new JLabel("???"),BorderLayout.CENTER);
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		contentPane.add(languagePanel, BorderLayout.NORTH);
		contentPane.add(base.comp, BorderLayout.CENTER);
		
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

	private static abstract class GUIComp {
		
		@SuppressWarnings("unused")
		private GUIComp parent;
		private Vector<GUIComp> children;
		
		protected ParsedCommandItem item;
		protected JComponent comp;
		protected String langCode;
		protected LanguageConfig languageConfig;
		
		protected GUIComp(ParsedCommandItem item, String langCode, LanguageConfig languageConfig) {
			this.parent = null;
			this.item = item;
			this.langCode = langCode;
			this.languageConfig = languageConfig;
			comp = null;
			children = new Vector<>();
		}
		protected GUIComp(GUIComp parent, ParsedCommandItem item) {
			this(item, parent.langCode, parent.languageConfig);
			this.parent = parent;
		}
		
		protected abstract void setLanguage(String langCode);
		
		public static void setLanguage(GUIComp comp, String langCode) {
			comp.setLanguage(langCode);
			for (GUIComp child:comp.children)
				setLanguage(child,langCode);
		}

		protected void addChild(GUIComp child) { children.add(child); }
		
		private static GUIComp createBase(DocumentItem item, String langCode, LanguageConfig languageConfig) {
			return new UnitDescriptionPanel(item.unitDescription, langCode, languageConfig);
		}

		private static String getMenuTitle(MenuItem item, String langCode) {
			String title = item.titles.get(langCode);
			String tags = item.getTags();
			
			String str = "";
			if (title!=null) str += title;
			if (!tags.isEmpty()) str += (!str.isEmpty()?"  ":"")+"["+tags+"]";
			
			if (!str.isEmpty()) return str;
			return item.toString();
		}

		private static class CommandPanel extends GUIComp {

			protected CommandPanel(GUIComp parent, CommandItem item) {
				super(parent, item);
				JPanel panel = new JPanel(new BorderLayout(3,3));
				panel.add(new JLabel(this.item.toString()),BorderLayout.CENTER);
				comp = panel;
			}

			@Override
			protected void setLanguage(String langCode) {
				this.langCode = langCode;
				// TODO Auto-generated method stub
			}
		}
		
		private static class UnitDescriptionPanel extends GUIComp {

			protected UnitDescriptionPanel(UnitDescriptionItem item, String langCode, LanguageConfig languageConfig) {
				super(item, langCode, languageConfig);
				
				JTabbedPane tabbedPane = new JTabbedPane();
				tabbedPane.setBorder(BorderFactory.createTitledBorder(item.toString()));
				for (MenuItem menu:item.menues)
					MenuTabPanel.addMenuTab(tabbedPane, this, menu);
				comp = tabbedPane;
			}

			@Override protected void setLanguage(String langCode) {
				this.langCode = langCode;
			}
		}
		
		private static class MenuPanel extends GUIComp {
			
			protected MenuItem item;
			private TitledBorder border;

			protected MenuPanel(GUIComp parent, MenuItem item_) {
				super(parent, item_);
				this.item = item_;
				
				JPanel commandList = null;
				if (!item.commands.isEmpty())
					commandList = createCommandList(new JPanel());
				
				MenuItem[] tabMenus   = item.menues.stream().filter(m->!m.menues.isEmpty()).toArray(n->new MenuItem[n]);
				MenuItem[] plainMenus = item.menues.stream().filter(m-> m.menues.isEmpty()).toArray(n->new MenuItem[n]);
				
				JTabbedPane menuTabs = null;
				if (tabMenus.length>0)
					menuTabs = createMenuTabs(new JTabbedPane(),tabMenus);
				
				JPanel menuList = null;
				if (plainMenus.length>0)
					menuList = createPlainMenus(new JPanel(),plainMenus);
				
				border = BorderFactory.createTitledBorder(getMenuTitle(item, langCode));
				if (commandList==null && menuTabs==null && menuList==null) { comp = new JLabel("L: "+item.toString()); return; }
				if (commandList!=null && menuTabs==null && menuList==null) { commandList.setBorder(border); comp = commandList; return; }
				if (commandList==null && menuTabs!=null && menuList==null) { menuTabs   .setBorder(border); comp = menuTabs   ; return; }
				if (commandList==null && menuTabs==null && menuList!=null) { menuList   .setBorder(border); comp = menuList   ; return; }
				
				JPanel panel = new JPanel(new BorderLayout(3,3));
				if (commandList!=null) panel.add(commandList, menuTabs!=null || menuList!=null ? BorderLayout.NORTH : BorderLayout.CENTER);
				if (menuTabs   !=null) panel.add(menuTabs   , BorderLayout.CENTER);
				if (menuList   !=null) panel.add(menuList   , menuTabs!=null ? BorderLayout.SOUTH : BorderLayout.CENTER);
				JScrollPane scrollPane = new JScrollPane(panel);
				scrollPane.setBorder(border);
				comp = scrollPane;
			}
			
			@Override
			protected void setLanguage(String langCode) {
				this.langCode = langCode;
				border.setTitle(getMenuTitle(item, this.langCode));
				// TODO Auto-generated method stub
			}

			private JPanel createCommandList(JPanel commandList) {
				commandList.setLayout(new GridBagLayout());
				GridBagConstraints c = new GridBagConstraints();
				c.fill = GridBagConstraints.BOTH;
				c.gridwidth = GridBagConstraints.REMAINDER;
				for (CommandItem cmd:item.commands) {
					CommandPanel commandPanel = new CommandPanel(this,cmd);
					addChild(commandPanel);
					commandList.add(commandPanel.comp,c);
				}
				return commandList;
			}
			
			private JPanel createPlainMenus(JPanel menuList, MenuItem[] menus) {
				menuList.setLayout(new GridBagLayout());
				GridBagConstraints c = new GridBagConstraints();
				c.fill = GridBagConstraints.BOTH;
				c.gridwidth = GridBagConstraints.REMAINDER;
				for (MenuItem menu:menus) {
					MenuPanel menuPanel = new MenuPanel(this,menu);
					addChild(menuPanel);
					menuList.add(menuPanel.comp,c);
				}
				return menuList;
			}
			
			private JTabbedPane createMenuTabs(JTabbedPane tabbedPane, MenuItem[] menus) {
				for (MenuItem menu:menus)
					MenuTabPanel.addMenuTab(tabbedPane, this, menu);
				return tabbedPane;
			}
		}
		
		private static class MenuTabPanel extends MenuPanel {

			private JTabbedPane tabbedPane;
			private int tabIndex;

			private MenuTabPanel(GUIComp parent, MenuItem item, JTabbedPane tabbedPane, int tabIndex) {
				super(parent, item);
				this.tabbedPane = tabbedPane;
				this.tabIndex = tabIndex;
			}

			@Override
			protected void setLanguage(String langCode) {
				super.setLanguage(langCode);
				tabbedPane.setTitleAt(tabIndex, getTabTitle());
			}

			public String getTabTitle() { return getMenuTitle(item, langCode); }

			private static void addMenuTab(JTabbedPane tabbedPane, GUIComp parent, MenuItem menu) {
				MenuTabPanel menuTab = new MenuTabPanel(parent,menu,tabbedPane,tabbedPane.getTabCount());
				parent.addChild(menuTab);
				tabbedPane.addTab(menuTab.getTabTitle(), menuTab.comp);
			}
		}
	}
}
