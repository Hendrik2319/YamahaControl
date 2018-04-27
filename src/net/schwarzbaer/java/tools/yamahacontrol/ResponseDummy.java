package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.gui.Tables.CheckBoxRendererComponent;
import net.schwarzbaer.gui.Tables.LabelRendererComponent;
import net.schwarzbaer.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.Log;

public class ResponseDummy extends Ctrl.HttpInterface {
	
	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		Ctrl.readCommProtocolFromFile();
		new ResponseDummy().createGUI();
	}

	private StandardMainWindow mainWindow;
	private ResponseTableModel tableModel;
	private ContextMenuHandler contextMenu;

	ResponseDummy() {
		Ctrl.http = this;
		mainWindow = null;
		tableModel = null;
		contextMenu = null;
	}
	
	public void createGUI() {
		
		tableModel = new ResponseTableModel();
		JTable table = new JTable(tableModel);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setDefaultRenderer(String.class,tableModel);
		table.setDefaultRenderer(Boolean.class,tableModel);
		
		tableModel.setTable(table);
		tableModel.setColumnWidths(table);
		
		contextMenu = new ContextMenuHandler();
		contextMenu.add("Copy XML", e->{ if (contextMenu.clickedRow!=null) YamahaControl.copyToClipBoard(contextMenu.clickedRow.protocolEntry.xml); });
		contextMenu.add("Add New Command", e->{}); // TODO
		contextMenu.add("Add New Response", e->{}); // TODO
		
		JTextArea textArea = new JTextArea();
		textArea.setEditable(false);
		
		JScrollPane tableScrollPane = new JScrollPane(table);
		tableScrollPane.setPreferredSize(new Dimension(900,700));
		
		JScrollPane textScrollPane = new JScrollPane(textArea);
		textScrollPane.setPreferredSize(new Dimension(500,600));
		
		table.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getButton()==MouseEvent.BUTTON3)
					contextMenu.activate(table, e.getPoint());
			}
		});
		table.getSelectionModel().addListSelectionListener(e->{
			if (e.getValueIsAdjusting()) return;
			ResponseTableModel.TableEntry row = tableModel.getRow(table.convertRowIndexToModel(table.getSelectedRow()));
			float pos = getScrollPos(textScrollPane.getVerticalScrollBar());
			if (row==null) textArea.setText("");
			else textArea.setText(XML.getXMLformatedString(row.protocolEntry.xml));
			setScrollPos(textScrollPane.getVerticalScrollBar(),pos);
		});
		
		JPanel eastPanel = new JPanel(new BorderLayout(3,3));
		eastPanel.add(YamahaControl.createButton("Write CommProtocol to File",e->Ctrl.writeCommProtocolToFile(),true),BorderLayout.NORTH);
		eastPanel.add(textScrollPane,BorderLayout.CENTER);
		
		JSplitPane contentPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,true,tableScrollPane,eastPanel);
		contentPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		
		mainWindow = new StandardMainWindow("ResponseDummy");
		mainWindow.startGUI(contentPane);
	}
	
	private class ContextMenuHandler {
		
		private JPopupMenu contextMenu;
		private ResponseTableModel.TableEntry clickedRow;

		ContextMenuHandler() {
			contextMenu = new JPopupMenu();
			clickedRow = null;
		}

		public void activate(JTable table, Point clickPoint) {
			int viewRowIndex = table.rowAtPoint(clickPoint);
			table.setRowSelectionInterval(viewRowIndex, viewRowIndex);
			int modelRowIndex = table.convertRowIndexToModel(viewRowIndex);
			clickedRow = tableModel.getRow(modelRowIndex);
			contextMenu.show(table,clickPoint.x,clickPoint.y);
		}

		public void add(String title, ActionListener l) {
			JMenuItem menuItem = new JMenuItem(title);
			if (l!=null) menuItem.addActionListener(l);
			contextMenu.add(menuItem);
		}

		public void addSeparator() {
			contextMenu.addSeparator();
		}
	}

	private float getScrollPos(JScrollBar scrollBar) {
		int min = scrollBar.getMinimum();
		int max = scrollBar.getMaximum();
		int ext = scrollBar.getVisibleAmount();
		int val = scrollBar.getValue();
		return (val-min)/((float)max-ext-min);
	}

	private void setScrollPos(JScrollBar scrollBar, float pos) {
		int min = scrollBar.getMinimum();
		int max = scrollBar.getMaximum();
		int ext = scrollBar.getVisibleAmount();
		SwingUtilities.invokeLater(()->scrollBar.setValue(Math.round(pos*(max-ext-min)+min)));
	}

	@Override
	public String sendCommand(String address, String commandStr, boolean verbose) {
		ResponseTableModel.TableEntry.Command command = tableModel.commands.get(commandStr);
		if (command==null) {
			Log.info( getClass(), "Received a command: %s", commandStr);
			Log.warning( getClass(), "Command is currently not known. This command will be added to CommProtocol.");
			Ctrl.Command protocolEntry = new Ctrl.Command(commandStr);
			Ctrl.commprotocol.put(commandStr, protocolEntry);
			tableModel.rebuildRows();
			return null;
		}
		Log.info( getClass(), "Received a command: \"%s\" %s", command.protocolEntry.name, commandStr);
		if (command.selectedResponse==null) {
			Log.error( getClass(), "No response selected for this command.");
			return null;
		}
		
		String response = command.selectedResponse.protocolEntry.xml;
		Log.info( getClass(), "Response: %s", response);
		return response;
	}

	@Override
	public String getContentFromURL(String urlStr, boolean verbose) {
		if (urlStr==null) return null;
		if (urlStr.startsWith("http://") && urlStr.endsWith("/YamahaRemoteControl/desc.xml")) {
			InputStream inputStream = getClass().getResourceAsStream("/RX-V475 - desc.xml.xml");
			if (inputStream==null) return null;
			try (BufferedReader input = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));) {
				if (verbose) System.out.println("Read content of \"desc.xml\" from file \"RX-V475 - desc.xml.xml\" ...");
				char[] chars = new char[100000];
				int n; StringBuilder sb = new StringBuilder();
				while ( (n=input.read(chars))!=-1 ) sb.append(chars, 0, n);
				if (verbose) System.out.println("done");
				return sb.toString();
			} catch (IOException e) {
				if (verbose) System.out.println("error occured");
			}
		}
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

		private void rebuildRows() {
			rows = TableEntry.createEntries(commands);
			fireTableUpdate();
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
				if (row!=null && row.isCommand()) {
					Color c;
					if (row.protocolEntry instanceof Ctrl.Command && ((Ctrl.Command)row.protocolEntry).responses.isEmpty())
						c = Color.RED;
					else if (row.asCommand().selectedResponse==null)
						c = Color.YELLOW;
					else
						c = Color.GREEN;
					comp.setBackground(c);
				}
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
				
				commands.clear();
				Vector<TableEntry> entries = new Vector<>();
				Vector<Ctrl.Command> list = Ctrl.getSortedCommProtocol();
				
				for (Ctrl.Command commandEntry:list) {
					Command command = new Command(commandEntry);
					commands.put(commandEntry.xml, command);
					entries.add(command);
					
					Vector<Ctrl.Response> responses = new Vector<>(commandEntry.responses);
					responses.sort(Comparator.nullsLast(Comparator.comparing(r->r.xml)));
					
					for (Ctrl.Response responseEntry:responses) {
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
