package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.TitledBorder;

import org.w3c.dom.Document;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ComplexCommand;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ComplexCommand.CmdParam;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ComplexCommand.DirectValue;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ComplexCommand.IndirectValue;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ComplexCommand.ParamValue;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ComplexCommand.RangeValue;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ComplexCommand.TextValue;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ComplexCommand.Type;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.LanguageConfig;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.CommandItem;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.ComplexCommandItem;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.DocumentItem;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.MenuItem;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.SimplePutCommandItem;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.ParsedCommandItem.UnitDescriptionItem;
import net.schwarzbaer.java.tools.yamahacontrol.CommandList.TreeIcon;

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
		mainWindow.limitSizeToFractionOfScreenSize(0.9f);
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
		
		protected JComponent comp;
		protected String langCode;
		protected LanguageConfig languageConfig;
		
		protected GUIComp(String langCode, LanguageConfig languageConfig) {
			this.parent = null;
			this.langCode = langCode;
			this.languageConfig = languageConfig;
			comp = null;
			children = new Vector<>();
		}
		protected GUIComp(GUIComp parent) {
			this(parent.langCode, parent.languageConfig);
			this.parent = parent;
		}
		
		protected abstract void updateAfterLanguageChange();
		
		public static void setLanguage(GUIComp comp, String langCode) {
			comp.langCode = langCode;
			comp.updateAfterLanguageChange();
			for (GUIComp child:comp.children)
				setLanguage(child,langCode);
		}
	
		protected void addChild(GUIComp child) { children.add(child); }
		
		private static GUIComp createBase(DocumentItem item, String langCode, LanguageConfig languageConfig) {
			return new UnitDescriptionPanel(item.unitDescription, langCode, languageConfig);
		}
	}

	private static class UnitDescriptionPanel extends GUIComp {
	
		protected UnitDescriptionPanel(UnitDescriptionItem item, String langCode, LanguageConfig languageConfig) {
			super(langCode, languageConfig);
			
			JTabbedPane tabbedPane = new JTabbedPane();
			for (MenuItem menu:item.menues)
				MenuTabPanel.addMenuTab(tabbedPane, this, menu);
			comp = tabbedPane;
			comp.setBorder(BorderFactory.createTitledBorder(item.toString()));
		}
	
		@Override protected void updateAfterLanguageChange() {}
	}

	private static class MenuPanel extends GUIComp {
		
		protected MenuItem item;
		private TitledBorder border;
	
		protected MenuPanel(GUIComp parent, MenuItem item_) {
			super(parent);
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
			
			if      (commandList==null && menuTabs==null && menuList==null) { comp = new JLabel("L: "+item.toString()); }
			else if (commandList!=null && menuTabs==null && menuList==null) { comp = commandList; }
			else if (commandList==null && menuTabs!=null && menuList==null) { comp = menuTabs; }
			else if (commandList==null && menuTabs==null && menuList!=null) { comp = menuList; }
			else {
				JPanel panel = new JPanel(new BorderLayout(3,3));
				if (commandList!=null) panel.add(commandList, menuTabs!=null || menuList!=null ? BorderLayout.NORTH : BorderLayout.CENTER);
				if (menuTabs   !=null) panel.add(menuTabs   , BorderLayout.CENTER);
				if (menuList   !=null) panel.add(menuList   , menuTabs!=null ? BorderLayout.SOUTH : BorderLayout.CENTER);
				comp = new JScrollPane(panel);
			}
			
			border = BorderFactory.createTitledBorder(getTitle());
			comp.setBorder(border);
		}
		
		@Override
		protected void updateAfterLanguageChange() {
			border.setTitle(getTitle());
		}
	
		public String getTitle() {
			String title = item.titles.get(langCode);
			String tags = item.getTags();
			
			String str = "";
			if (title!=null) str += title;
			if (!tags.isEmpty()) str += (!str.isEmpty()?"  ":"")+"["+tags+"]";
			
			if (!str.isEmpty()) return str;
			return item.toString();
		}
	
		private JPanel createCommandList(JPanel commandList) {
			commandList.setLayout(new GridBagLayout());
			//GridBagConstraints c = new GridBagConstraints();
			//c.fill = GridBagConstraints.BOTH;
			//c.gridwidth = GridBagConstraints.REMAINDER;
			for (CommandItem cmd:item.commands) {
				CommandPanel commandPanel = CommandPanel.create(this,cmd);
				addChild(commandPanel);
				commandPanel.addTo(commandList);
				//commandList.add(commandPanel.comp,c);
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
		protected void updateAfterLanguageChange() {
			super.updateAfterLanguageChange();
			tabbedPane.setTitleAt(tabIndex, getTitle());
		}
	
		static void addMenuTab(JTabbedPane tabbedPane, GUIComp parent, MenuItem menu) {
			MenuTabPanel menuTab = new MenuTabPanel(parent,menu,tabbedPane,tabbedPane.getTabCount());
			parent.addChild(menuTab);
			tabbedPane.addTab(menuTab.getTitle(), menuTab.comp);
		}
	}

	private static abstract class CommandPanel extends GUIComp {
	
		protected CommandPanel(GUIComp parent) {
			super(parent);
			//JPanel panel = new JPanel(new BorderLayout(3,3));
			//panel.add(new JLabel(this.item.toString()),BorderLayout.CENTER);
			comp = null; //panel;
		}
	
		public abstract void addTo(JPanel commandList);
	
		public static CommandPanel create(GUIComp parent, CommandItem item) {
			if      (item instanceof SimplePutCommandItem) return new SimplePutCommandPanel(parent,(SimplePutCommandItem) item);
			else if (item instanceof ComplexCommandItem  ) {
				ComplexCommandItem commandItem = (ComplexCommandItem) item;
				switch (commandItem.complexCommand.type) {
				case Get: return new ComplexGetCommandPanel(parent,commandItem);
				case Put: return new ComplexPutCommandPanel(parent,commandItem);
				}
			}
			return null;
		}
	
		private static class SimplePutCommandPanel extends CommandPanel {
	
			private SimplePutCommandItem item;
			private JLabel title;
	
			public SimplePutCommandPanel(GUIComp parent, SimplePutCommandItem item) {
				super(parent);
				this.item = item;
			}
	
			@Override protected void updateAfterLanguageChange() {
				title.setText(getTitle());
			}
	
			@Override public void addTo(JPanel commandList) {
				GridBagConstraints c = new GridBagConstraints();
				c.fill = GridBagConstraints.BOTH;
				setInsets(c, 0,2,0,2);
				setLineMid(c);
				setWeights(c,0,0);
				
				title = new JLabel(getTitle());
				JLabel value = new JLabel(item.commandValue);
				value.setHorizontalAlignment(JLabel.CENTER);
				
				commandList.add(new JLabel("PUT["+item.cmdID+"]: "), c);
				commandList.add(title, c);
				commandList.add(value, setGridWidth(setWeights(c,1,0), 1) );
				commandList.add(createButton("Send",TreeIcon.Command_PUT,e->{
					// TODO
				}), setLineEnd(setWeights(c,0,0)));
			}
	
			private String getTitle() {
				String title = item.titles.get(langCode);
				String tags = item.getTags();
				
				String str = "";
				if (title!=null) str += title;
				if (!tags.isEmpty()) str += (!str.isEmpty()?"  ":"")+"["+tags+"]";
				
				return str;
				//if (!str.isEmpty()) return str;
				//return item.toString();
			}
		}
		
		private static abstract class ComplexCommandPanel extends CommandPanel {
	
			protected ComplexCommandItem item;
			protected JTextField[] valueFields;
	
			public ComplexCommandPanel(GUIComp parent, ComplexCommandItem item) {
				super(parent);
				this.item = item;
				valueFields = null;
			}
			
			@Override protected void updateAfterLanguageChange() {}
			
			@Override public void addTo(JPanel commandList) {
				GridBagConstraints c = new GridBagConstraints();
				c.fill = GridBagConstraints.BOTH;
				setInsets(c, 0,2,0,2);
				setLineMid(c);
				
				Type commandType = item.complexCommand.type;
				commandList.add(new JLabel(commandType.toString().toUpperCase()+"["+item.complexCommand.cmd.cmdID+"]: "), c);
				commandList.add(createVariantSelector(), c);
				
				JPanel valuePanel = new JPanel(new GridBagLayout());
				CmdParam[] cmdParams = item.complexCommand.cmd.params;
				valueFields = new JTextField[cmdParams.length];
				for (int i=0; i<cmdParams.length; i++) {
					CmdParam cmdParam = cmdParams[i];
					if (cmdParam.param.func!=null) valuePanel.add(new JLabel("["+cmdParam.param.func+"]: "), setWeights(c,0,0));
					valueFields[i] = new JTextField(10);
					valuePanel.add(valueFields[i], setWeights(c,1,0));
				}
				commandList.add(valuePanel, setWeights(c,1,0));
				
				//commandList.add(new JLabel(item.toString()), setGridWidth(c, 2));
				
				commandList.add(createButton("Send",item.complexCommand.getIcon(),e->{
					// TODO
				}), setLineEnd(setWeights(c,0,0)));
			}
	
			protected abstract JComponent createVariantSelector();

			ParamVariant[] getVariants(ComplexCommandItem commandItem) {
				int n = 0;
				for (CmdParam cmdParam:commandItem.complexCommand.cmd.params)
					n = Math.max(n, cmdParam.param.values.size());
				
				ParamVariant[] variants = new ParamVariant[n];
				for (int i=0; i<variants.length; i++) {
					variants[i] = new ParamVariant(i);
					CmdParam[] cmdParams = commandItem.complexCommand.cmd.params;
					for (int j=0; j<cmdParams.length; j++) {
						CmdParam cmdParam = cmdParams[j];
						if (i<cmdParam.param.values.size())
							variants[i].set(j,cmdParam.param.values.get(i));
					}
				}
				
				return variants;
			}
			
			static class ParamVariant {
	
				private int index;
				private ParamValue[] values;

				public ParamVariant(int index) {
					this.index = index;
					values = new ParamValue[] {null,null,null};
				}
	
				public void set(int i, ParamValue paramValue) { values[i] = paramValue; }
				public ParamValue get(int i) { return values[i]; }
				
				@Override public String toString() { return "["+index+"]"; }
			}
		}
		
		private static class ComplexGetCommandPanel extends ComplexCommandPanel {
	
			public ComplexGetCommandPanel(GUIComp parent, ComplexCommandItem item) {
				super(parent, item);
			}

			@Override
			protected JComponent createVariantSelector() {
				return new JLabel("-");
			}
		}
		private static class ComplexPutCommandPanel extends ComplexCommandPanel {

			private ParamVariant selectedVariant;

			public ComplexPutCommandPanel(GUIComp parent, ComplexCommandItem item) {
				super(parent, item);
				selectedVariant = null;
			}

			@Override
			public void addTo(JPanel commandList) {
				super.addTo(commandList);
				setSelectedVariant(selectedVariant);
			}

			@Override
			protected JComponent createVariantSelector() {
				ParamVariant[] variants = getVariants(item);
				if (variants.length>1) {
					JComboBox<ParamVariant> comboBox = new JComboBox<>(variants);
					comboBox.addActionListener(e->setSelectedVariant(getSelectedVariant(variants, comboBox)));
					selectedVariant = getSelectedVariant(variants, comboBox);
					return comboBox;
				} else {
					if (variants.length==1) selectedVariant = variants[0];
					return new JLabel("-");
				}
			}

			private ParamVariant getSelectedVariant(ParamVariant[] variants, JComboBox<ParamVariant> comboBox) {
				int index = comboBox.getSelectedIndex();
				if (index<0) return null;
				else return variants[index];
			}

			private void setSelectedVariant(ParamVariant paramVariant) {
				selectedVariant = paramVariant;
				for (int i=0; i<valueFields.length; i++)
					if (paramVariant==null) {
						valueFields[i].setText("");
						valueFields[i].setEditable(false);
						valueFields[i].setEnabled(false);
					} else {
						ParamValue value = paramVariant.get(i);
						if (value instanceof ComplexCommand.TextValue    ) setValueField(valueFields[i],(ComplexCommand.TextValue    ) value);
						if (value instanceof ComplexCommand.RangeValue   ) setValueField(valueFields[i],(ComplexCommand.RangeValue   ) value);
						if (value instanceof ComplexCommand.DirectValue  ) setValueField(valueFields[i],(ComplexCommand.DirectValue  ) value);
						if (value instanceof ComplexCommand.IndirectValue) setValueField(valueFields[i],(ComplexCommand.IndirectValue) value);
					}
			}

			private void setValueField(JTextField field, IndirectValue paramValue) {
				// TODO Auto-generated method stub
			}

			private void setValueField(JTextField field, DirectValue paramValue) {
				// TODO Auto-generated method stub
			}

			private void setValueField(JTextField field, RangeValue paramValue) {
				// TODO Auto-generated method stub
			}

			private void setValueField(JTextField field, TextValue paramValue) {
				// TODO Auto-generated method stub
			}
			
			
		}
	
		private static JButton createButton(String title, TreeIcon treeIcon, ActionListener al) {
			JButton comp = new JButton(title);
			if (treeIcon!=null) comp.setIcon(CommandList.getTreeIcon(treeIcon));
			comp.addActionListener(al);
			return comp;
		}
	
		@SuppressWarnings("unused")
		private static void reset(GridBagConstraints c) {
			c.gridx = GridBagConstraints.RELATIVE;
			c.gridy = GridBagConstraints.RELATIVE;
			c.weightx = 0;
			c.weighty = 0;
			c.fill = GridBagConstraints.NONE;
			c.gridwidth = 1;
			c.insets = new Insets(0,0,0,0);
		}
	
		private static GridBagConstraints setWeights(GridBagConstraints c, double weightx, double weighty) {
			c.weightx = weightx;
			c.weighty = weighty;
			return c;
		}
	
		@SuppressWarnings("unused")
		private static GridBagConstraints setGridPos(GridBagConstraints c, int gridx, int gridy) {
			c.gridx = gridx;
			c.gridy = gridy;
			return c;
		}
	
		private static GridBagConstraints setInsets(GridBagConstraints c, int top, int left, int bottom, int right) {
			c.insets = new Insets(top, left, bottom, right);
			return c;
		}
	
		private static GridBagConstraints setLineEnd(GridBagConstraints c) {
			c.gridwidth = GridBagConstraints.REMAINDER;
			return c;
		}
	
		private static GridBagConstraints setLineMid(GridBagConstraints c) {
			c.gridwidth = 1;
			return c;
		}
	
		private static GridBagConstraints setGridWidth(GridBagConstraints c, int gridwidth) {
			c.gridwidth = gridwidth;
			return c;
		}
	}
}
