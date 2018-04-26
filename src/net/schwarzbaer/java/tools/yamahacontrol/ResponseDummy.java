package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.gui.Tables.CheckBoxRendererComponent;
import net.schwarzbaer.gui.Tables.LabelRendererComponent;
import net.schwarzbaer.gui.Tables.SimplifiedColumnConfig;

public class ResponseDummy extends Ctrl.HttpInterface {
	
	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		Ctrl.readCommProtocolFromFile();
		new ResponseDummy().createGUI();
	}

	private StandardMainWindow mainWindow;

	ResponseDummy() {
		Ctrl.http = this;
	}
	
	public void createGUI() {
		
		JTextArea textArea = new JTextArea();
		textArea.setEditable(false);
		
		ResponseTableModel tableModel = new ResponseTableModel();
		JTable table = new JTable(tableModel);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setDefaultRenderer(String.class,tableModel);
		table.setDefaultRenderer(Boolean.class,tableModel);
		table.getSelectionModel().addListSelectionListener(e->{
			if (e.getValueIsAdjusting()) return;
			ResponseTableModel.TableEntry row = tableModel.getRow(table.getSelectedRow());
			if (row==null) textArea.setText("");
			else textArea.setText(XML.getXMLformatedString(row.protocolEntry.xml));
		});
		
		tableModel.setColumnWidths(table);
		tableModel.setTable(table);
		
		JScrollPane tableScrollPane = new JScrollPane(table);
		tableScrollPane.setPreferredSize(new Dimension(900,700));
		
		JScrollPane textScrollPane = new JScrollPane(textArea);
		textScrollPane.setPreferredSize(new Dimension(200,600));
		
		JPanel eastPanel = new JPanel(new BorderLayout(3,3));
		eastPanel.add(YamahaControl.createButton("Write CommProtocol to File",e->Ctrl.writeCommProtocolToFile(),true),BorderLayout.NORTH);
		eastPanel.add(textScrollPane,BorderLayout.CENTER);
		// TODO Auto-generated method stub
		
		JSplitPane contentPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,true,tableScrollPane,eastPanel);
		contentPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		
		mainWindow = new StandardMainWindow("ResponseDummy");
		mainWindow.startGUI(contentPane);
	}

	@Override
	public String sendCommand(String address, String command, boolean verbose) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getContentFromURL(String urlStr, boolean verbose) {
		// TODO Auto-generated method stub
		return null;
	}

	public static class ResponseTableModel extends Tables.SimplifiedTableModel<ResponseTableModel.ColumnID> implements TableCellRenderer {
		
		private enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			Name  ("Name",String .class,-1,-1,200,200),
			Select("#"   ,Boolean.class,-1,-1, 50, 50),
			XML   ("XML" ,String .class,-1,-1,630,630);
			
			private SimplifiedColumnConfig cfg;
			ColumnID(String name, Class<?> columnClass, int minWidth, int maxWidth, int prefWidth, int currentWidth) {
				cfg = new Tables.SimplifiedColumnConfig(name, columnClass, minWidth, maxWidth, prefWidth, currentWidth);
			}
			@Override public SimplifiedColumnConfig getColumnConfig() { return cfg; }
		}
		
		private Vector<TableEntry> rows;
		private HashMap<String,TableEntry.Command> commands;
		private LabelRendererComponent rendererLabel;
		private CheckBoxRendererComponent rendererCheckBox;
		private JTable table;

		ResponseTableModel() {
			super(ColumnID.values());
			table = null;
			commands = new HashMap<>();
			rows = TableEntry.createEntries(commands);
			rendererLabel = new Tables.LabelRendererComponent();
			rendererCheckBox = new Tables.CheckBoxRendererComponent();
			rendererLabel.setOpaque(true);
			rendererCheckBox.setOpaque(true);
			rendererCheckBox.setHorizontalAlignment(Tables.CheckBoxRendererComponent.CENTER);
		}

		public void setTable(JTable table) {
			this.table = table;
		}

		public TableEntry getRow(int rowIndex) {
			if (0<=rowIndex && rowIndex<rows.size()) return rows.get(rowIndex);
			return null;
		}

		@Override public int getRowCount() { return rows.size(); }

		@Override
		protected boolean isCellEditable(int rowIndex, int columnIndex, ColumnID columnID) {
			switch (columnID) {
			case Name  : return true;
			case XML   : return false;
			case Select: return rows.get(rowIndex).isResponse();
			}
			return false;
		}

		@Override
		protected void setValueAt(Object aValue, int rowIndex, int columnIndex, ColumnID columnID) {
			TableEntry row = rows.get(rowIndex);
			switch (columnID) {
			case Name  : row.protocolEntry.name = (String)aValue; break;
			case XML   : break;
			case Select: if (row.isResponse()) row.asResponse().select(); fireTableColumnUpdate(columnIndex); table.repaint(); break;
			}
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
			TableEntry row = rows.get(rowIndex);
			switch (columnID) {
			case Name  : return row.protocolEntry.name;
			case XML   : return (row.isCommand()?"Command: ":"Response: ")+row.protocolEntry.xml;
			case Select: return row.isResponse()?row.asResponse().isSelected:null;
			}
			return null;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int columnIndex) {
			TableEntry row = getRow(rowIndex);
			// ColumnID columnID = getColumnID(columnIndex);
			Component comp = null;
			
			if (value instanceof String) {
				comp=rendererLabel;
				rendererLabel.setText((String)value);
			} else if (value instanceof Boolean) {
				comp=rendererCheckBox;
				rendererCheckBox.setSelected((Boolean)value);
			} else {
				comp=rendererLabel;
				rendererLabel.setText("");
			}
			
			if (isSelected) {
				comp.setBackground(table.getSelectionBackground());
				comp.setForeground(table.getSelectionForeground());
			} else {
				comp.setForeground(table.getForeground());
				if (row!=null && row.isCommand()) comp.setBackground(row.asCommand().selectedResponse==null?Color.YELLOW:Color.GREEN);
				else comp.setBackground(table.getBackground());
			}
			
			return comp;
		}

		private static class TableEntry {
			
			Ctrl.ProtocolEntry protocolEntry;
			public TableEntry(Ctrl.ProtocolEntry protocolEntry) {
				this.protocolEntry = protocolEntry;
			}
		
			public boolean isCommand () { return this instanceof Command; }
			public boolean isResponse() { return this instanceof Response; }
			public Command  asCommand () { return (Command )this; }
			public Response asResponse() { return (Response)this; }
		
			public static Vector<TableEntry> createEntries(HashMap<String,Command> commands) {
				Vector<TableEntry> entries = new Vector<>();
				Vector<Ctrl.Command> list = Ctrl.getSortedCommProtocol();
				for (Ctrl.Command commandEntry:list) {
					Command command = new Command(commandEntry);
					commands.put(commandEntry.xml, command);
					entries.add(command);
					for (Ctrl.Response responseEntry:commandEntry.responses) {
						Response response = new Response(command,responseEntry);
						if (commandEntry.responses.size()==1) response.select();
						entries.add(response);
					}
				}
				return entries;
			}
			
			private static class Command extends TableEntry {
				private Response selectedResponse;
		
				public Command(Ctrl.Command entry) {
					super(entry);
					selectedResponse = null;
				}
		
				private void select(Response response) {
					if (selectedResponse!=null)
						selectedResponse.isSelected = false;
					
					selectedResponse = response;
					
					if (selectedResponse!=null)
						selectedResponse.isSelected = true;
				}
			}
			
			private static class Response extends TableEntry {
				private Command command;
				private boolean isSelected;
				
				public Response(Command command, Ctrl.Response response) {
					super(response);
					this.command = command;
					this.isSelected = false;
				}
				
				public void select() { command.select(this); }
			}
		}
	}

}
